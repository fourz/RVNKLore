package org.fourz.RVNKLore.lore.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.DatabaseHelper;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.service.IRngItemService;
import org.fourz.RVNKLore.service.PoolItemEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Rarity-weight RNG item roller backed by lore_item_rng_pool.
 *
 * <p>Selection algorithm: build a weighted list of active pool entries
 * (filtered by rarity_tier when provided), sum the weights, then pick
 * by a uniform random draw in [0, totalWeight). Falls back to empty
 * when the pool is empty or the DB is unavailable.</p>
 */
public class RngItemServiceImpl implements IRngItemService {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseHelper dbHelper;
    private final ItemManager itemManager;
    private boolean fallbackMode = false;

    public RngItemServiceImpl(RVNKLore plugin, DatabaseConnection dbConnection, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "RngItemServiceImpl");
        this.dbHelper = new DatabaseHelper(plugin);
        this.itemManager = itemManager;
    }

    /**
     * Resolve the live database connection at use time.
     *
     * <p>The connection passed to the constructor is deliberately ignored: it is captured during
     * plugin startup and would go stale the moment a fallback or recovery swap replaced it (#1835).
     * The constructor parameter is retained so existing call sites keep compiling.</p>
     *
     * @return the current connection, or null when the database is unavailable
     */
    private DatabaseConnection dbConnection() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        return dbManager == null ? null : dbManager.getDatabaseConnection();
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> roll(String poolId, String rarityTier) {
        return getWeightedEntries(poolId, rarityTier).thenCompose(entries -> {
            if (entries.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
            int draw = ThreadLocalRandom.current().nextInt(totalWeight);
            int cursor = 0;
            int chosenId = -1;
            for (PoolEntry e : entries) {
                cursor += e.weight;
                if (draw < cursor) {
                    chosenId = e.loreItemId;
                    break;
                }
            }

            if (chosenId < 0) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            return itemManager.createLoreItem(chosenId);
        });
    }

    @Override
    public CompletableFuture<List<Integer>> getPoolItemIds(String poolId, String rarityTier) {
        return getWeightedEntries(poolId, rarityTier)
            .thenApply(entries -> entries.stream()
                .map(e -> e.loreItemId)
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<PoolItemEntry>> getPoolEntries(String poolId, String rarityTier) {
        return getWeightedEntries(poolId, rarityTier)
            .thenApply(entries -> entries.stream()
                .map(e -> new PoolItemEntry(e.loreItemId, e.weight, e.rarityTier))
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<String>> poolToLootTableJson(String poolId, String rarityTier) {
        return getWeightedEntries(poolId, rarityTier).thenApply(entries -> {
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            JsonArray lootEntries = new JsonArray();
            for (PoolEntry e : entries) {
                Optional<ItemProperties> opt = itemManager.getItemPropertiesById(e.loreItemId).join();
                if (opt.isEmpty()) {
                    continue;
                }
                ItemProperties p = opt.get();
                Material mat = p.getMaterial();
                if (mat == null) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", mat.getKey().toString());
                entry.addProperty("weight", Math.max(1, e.weight));

                JsonArray functions = new JsonArray();
                JsonObject setCount = new JsonObject();
                setCount.addProperty("function", "minecraft:set_count");
                setCount.addProperty("count", 1);
                functions.add(setCount);
                // custom_model_data is carried by buildIdentityComponents() via set_components.
                // It used to be emitted here as a standalone
                //   {"function":"minecraft:set_custom_model_data","value":<int>}
                // which is the pre-1.21.2 shape. In the component era that field is silently
                // ignored — no parse error, no warning — but the function still creates the
                // component, so every baked item rolled with an EMPTY
                //   "minecraft:custom_model_data": {}
                // and the CMD was lost. Verified on Dev against a three-way loot table (#1674).
                // #1677: restore full lore identity into the baked (static) table so a poolbake chest
                // rolls the real item — name, rarity lore, the rvnklore PDC id, and book pages — not a
                // bare vanilla item. set_components is the component-era canonical carrier. Verify the
                // emitted JSON with `/lore item pool preview <pool>` (#1679) before deploying.
                JsonObject components = buildIdentityComponents(p);
                if (components.size() > 0) {
                    JsonObject setComponents = new JsonObject();
                    setComponents.addProperty("function", "minecraft:set_components");
                    setComponents.add("components", components);
                    functions.add(setComponents);
                }
                // PDC identity via set_custom_data with an explicit SNBT tag — the linchpin of #1677.
                // The JSON set_components custom_data path stores small ints as bytes (21b), which
                // PersistentDataType.INTEGER cannot read back; an SNBT integer literal stays TAG_Int,
                // matching what the roll build path writes (verified on Dev, #1678).
                JsonObject setCustomData = new JsonObject();
                setCustomData.addProperty("function", "minecraft:set_custom_data");
                setCustomData.addProperty("tag", buildPdcSnbt(p, e.loreItemId));
                functions.add(setCustomData);
                entry.add("functions", functions);
                lootEntries.add(entry);
            }
            if (lootEntries.isEmpty()) {
                return Optional.empty();
            }
            JsonObject rolls = new JsonObject();
            rolls.addProperty("type", "minecraft:uniform");
            rolls.addProperty("min", 1);
            rolls.addProperty("max", 3);
            JsonObject pool = new JsonObject();
            pool.add("rolls", rolls);
            pool.addProperty("bonus_rolls", 0);
            pool.add("entries", lootEntries);
            JsonArray pools = new JsonArray();
            pools.add(pool);
            JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:chest");
            table.add("pools", pools);
            return Optional.of(new GsonBuilder().setPrettyPrinting().create().toJson(table));
        });
    }

    /**
     * Build the {@code minecraft:set_components} payload that restores a lore item's identity in a
     * baked (static) loot table (#1677). Covers custom_name, lore, the {@code rvnklore} PDC in
     * custom_data (so the rolled item is resolvable as a lore item), and written_book_content pages
     * for books that carry them. Component-format (1.20.5+/component era) — verify against a live
     * server via {@code /lore item pool preview} before trusting on a new MC build.
     */
    private JsonObject buildIdentityComponents(ItemProperties p) {
        JsonObject components = new JsonObject();

        // custom_model_data — component-era shape is a struct of typed lists, not a scalar.
        // Inside set_components this is the RAW component ({"floats":[N]}); the {"mode","values"}
        // ListOperation wrapper applies only to the standalone set_custom_model_data function.
        // Emitted as a float because the component stores floats — a resource pack keyed on the
        // legacy integer CMD must match on the float list. Verified on Dev: rolls back as
        // "minecraft:custom_model_data": {floats: [N.0f]} (#1674).
        if (p.getCustomModelData() > 0) {
            JsonArray cmdFloats = new JsonArray();
            cmdFloats.add(p.getCustomModelData());
            JsonObject cmdComp = new JsonObject();
            cmdComp.add("floats", cmdFloats);
            components.add("minecraft:custom_model_data", cmdComp);
        }

        // Display name — component object so it renders without the default-italic of a bare string.
        String name = p.getDisplayName();
        if (name != null && !name.isEmpty()) {
            JsonObject nameComp = new JsonObject();
            nameComp.addProperty("text", name);
            nameComp.addProperty("italic", false);
            components.add("minecraft:custom_name", nameComp);
        }

        // Rarity/lore lines — best-effort visual fidelity.
        List<String> lore = p.getLore();
        if (lore != null && !lore.isEmpty()) {
            JsonArray loreArr = new JsonArray();
            for (String line : lore) {
                JsonObject lineComp = new JsonObject();
                lineComp.addProperty("text", line == null ? "" : line);
                lineComp.addProperty("italic", false);
                loreArr.add(lineComp);
            }
            components.add("minecraft:lore", loreArr);
        }

        // Book pages — only when the source item actually has page content (blank test items stay
        // blank, matching the dynamic-roll lane; see #1675 blank-pages note).
        Material mat = p.getMaterial();
        if ((mat == Material.WRITTEN_BOOK || mat == Material.WRITABLE_BOOK)
                && p.getPages() != null && !p.getPages().isEmpty()) {
            JsonArray pagesArr = new JsonArray();
            for (String pg : p.getPages()) {
                JsonObject page = new JsonObject();
                page.addProperty("raw", pg == null ? "" : pg);
                pagesArr.add(page);
            }
            JsonObject bookContent = new JsonObject();
            bookContent.add("pages", pagesArr);
            if (name != null && !name.isEmpty()) {
                JsonObject titleObj = new JsonObject();
                titleObj.addProperty("raw", name);
                bookContent.add("title", titleObj);
            }
            bookContent.addProperty("author", "");
            components.add("minecraft:written_book_content", bookContent);
        }

        return components;
    }

    /**
     * Build the SNBT {@code tag} for {@code minecraft:set_custom_data} that reconstructs the Bukkit
     * PDC block ({@code custom_data.PublicBukkitValues}). Uses an SNBT integer literal for
     * {@code lore_item_id} so it lands as TAG_Int (readable via {@code PersistentDataType.INTEGER}),
     * unlike the JSON components path which stores it as a byte (#1677).
     */
    private String buildPdcSnbt(ItemProperties p, int loreItemId) {
        StringBuilder pbv = new StringBuilder();
        pbv.append("\"rvnklore:lore_item_id\":").append(loreItemId);
        if (p.getLoreEntryId() != null && !p.getLoreEntryId().isEmpty()) {
            pbv.append(",\"rvnklore:lore_entry_id\":").append(quoteSnbt(p.getLoreEntryId()));
        }
        if (p.getDisplayName() != null && !p.getDisplayName().isEmpty()) {
            pbv.append(",\"rvnklore:lore_item_name\":").append(quoteSnbt(p.getDisplayName()));
        }
        return "{PublicBukkitValues:{" + pbv + "}}";
    }

    /** Quote and escape a string as an SNBT double-quoted string literal. */
    private String quoteSnbt(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }

    private CompletableFuture<List<PoolEntry>> getWeightedEntries(String poolId, String rarityTier) {
        return CompletableFuture.supplyAsync(() -> {
            String table = dbConnection().table(DatabaseConnection.TABLE_LORE_ITEM_RNG_POOL);
            String sql;
            Object[] params;

            if (rarityTier != null && !rarityTier.isEmpty()) {
                sql = "SELECT lore_item_id, weight, rarity_tier FROM " + table +
                      " WHERE pool_id = ? AND rarity_tier = ? AND is_active = 1 ORDER BY weight DESC";
                params = new Object[]{poolId, rarityTier.toUpperCase()};
            } else {
                sql = "SELECT lore_item_id, weight, rarity_tier FROM " + table +
                      " WHERE pool_id = ? AND is_active = 1 ORDER BY weight DESC";
                params = new Object[]{poolId};
            }

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> {
                        stmt.setString(1, poolId);
                        if (params.length > 1) stmt.setString(2, (String) params[1]);
                    },
                    rs -> {
                        List<PoolEntry> entries = new ArrayList<>();
                        while (rs.next()) {
                            entries.add(new PoolEntry(rs.getInt("lore_item_id"), rs.getInt("weight"),
                                    rs.getString("rarity_tier")));
                        }
                        return entries;
                    });
            } catch (LoreException e) {
                logger.error("Failed to query RNG pool '" + poolId + "': " + e.getMessage(), e);
                fallbackMode = true;
                return new ArrayList<>();
            }
        });
    }

    private record PoolEntry(int loreItemId, int weight, String rarityTier) {}
}
