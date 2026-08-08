package org.fourz.RVNKLore.lore.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
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
import java.util.Map;
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
                // #1914 workstream A: refuse to bake a player head rather than emit an anonymous one.
                //
                // The obvious fix here looked like "the bake forgot the profile component, carry it
                // like #1844 carried enchantments". It is not that. There is no profile to carry:
                // the head payload does not survive the database round trip on ANY lane.
                //   - ItemRepository.buildItemPropertiesJson persists exactly custom props, lore_text,
                //     is_glow, skull_texture, pages, custom_model_data and enchantments. headVariant,
                //     textureData, ownerName and the whole metadata map are never written.
                //   - ItemPropertiesDTO does carry those fields, but it is used ONLY by the REST
                //     serializer (LoreApiEndpointImpl) — it is not in the persistence path at all.
                //   - skullTexture is written and read back (ItemRepository 618/1842) and then has
                //     ZERO consumers. Nothing anywhere applies it to a SkullMeta.
                // UPDATE: the first two points above still hold — headVariant/textureData/metadata are
                // still not persisted — but skull_texture IS, and createLoreItemInternal now applies it
                // (ItemManager, #1914). So the DYNAMIC ROLL lane produces a properly textured head.
                //
                // That flips the reason this guard exists without changing the verdict. Previously both
                // lanes were equally blank and emitting a profile here would have made the bake better
                // than the roll. Now the roll is correct and the BAKE is the one that would ship a blank
                // head — a real parity break in the other direction. Refusing is still right, because a
                // blank head in a reward chest is indistinguishable from a texture that failed to load,
                // which is exactly the silent failure class #1844 existed to end.
                //
                // The fix is to emit a "minecraft:profile" component built from p.getSkullTexture(),
                // then delete this guard. Until that lands, refuse. Mob skulls are deliberately NOT
                // guarded — a ZOMBIE_HEAD carries its identity in the material and bakes correctly.
                if (isPlayerHead(mat)) {
                    logger.error("Refusing to bake lore item " + e.loreItemId + " ('" + p.getDisplayName()
                        + "', " + mat + ") into pool '" + poolId + "': player-head textures are not"
                        + " persisted by lore_item, so this would bake a blank head. Remove it from the"
                        + " pool, or give the item a material that carries its own appearance"
                        + " (CARVED_PUMPKIN + custom_model_data is how the hat set does it).");
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

        // Enchantments + glow (#1844). Two separate reasons an item carries the enchantments
        // component:
        //   (a) real enchantments off ItemProperties — ENCHANTED items;
        //   (b) the glow flag, which the spawn path fakes with UNBREAKING 1 + hidden enchants
        //       so the item glints without advertising stats (#1843, ItemManager).
        // The bake dropped BOTH: this class had no enchantment handling at all, so an ENCHANTED
        // lore item baked to a plain vanilla one with only a name and lore. Unlike the CMD bug
        // this was an omission, not a wrong format.
        //
        // Component shape confirmed on 26.2 by reading a live item: a FLAT map of namespaced key
        // to level ({"minecraft:unbreaking": 1}) — no {"levels":{...}} wrapper. Key string is
        // built the same way ItemRepository.appendEnchantJson does it, so the bake and the DB
        // round-trip agree on one format.
        Map<Enchantment, Integer> enchants = p.getEnchantments();
        boolean glow = p.isGlow();
        JsonObject enchComp = new JsonObject();
        if (enchants != null) {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String key = enchantmentKey(e.getKey());
                if (key != null) {
                    enchComp.addProperty(key, e.getValue());
                }
            }
        }
        // Glow on an otherwise-unenchanted item: mirror the spawn path's UNBREAKING 1 stand-in
        // rather than inventing different semantics for the bake lane.
        if (glow && enchComp.size() == 0) {
            enchComp.addProperty("minecraft:unbreaking", 1);
        }
        if (enchComp.size() > 0) {
            components.add("minecraft:enchantments", enchComp);
            // Matches ItemManager's HIDE_ENCHANTS: the glint should read as an aura, not gear
            // stats. Note this hides real enchantments too when both are set — the same tradeoff
            // the spawn path already makes, kept identical on purpose.
            if (glow) {
                JsonArray hidden = new JsonArray();
                hidden.add("minecraft:enchantments");
                JsonObject tooltip = new JsonObject();
                tooltip.add("hidden_components", hidden);
                components.add("minecraft:tooltip_display", tooltip);
            }
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
     * Is this the player-head family, whose appearance lives entirely in a profile component?
     *
     * <p>Deliberately narrow. Mob skulls ({@code ZOMBIE_HEAD}, {@code WITHER_SKELETON_SKULL}, …) get
     * their texture from the material and bake correctly, so guarding them would reject items that
     * work. Only {@code PLAYER_HEAD}/{@code PLAYER_WALL_HEAD} render as an anonymous Steve without
     * profile data that {@code lore_item} does not store.</p>
     */
    private boolean isPlayerHead(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    /**
     * Namespaced key for an enchantment ({@code "minecraft:sharpness"}) as the
     * {@code minecraft:enchantments} component expects it.
     *
     * <p>{@link Enchantment#getKey()} is deprecated as of 1.21.4, but this module builds against
     * spigot-api, where {@code Registry#getKey(T)} is not available — only {@code get(key)} and
     * iteration. Rather than scan the registry on every entry, this matches
     * {@code ItemRepository.appendEnchantJson}, which already serializes enchantments to the DB
     * the same way. One format for the DB round-trip and the baked table is worth more here than
     * dodging a warning; revisit together if the module ever moves to paper-api.</p>
     */
    @SuppressWarnings("deprecation")
    private String enchantmentKey(Enchantment enchantment) {
        org.bukkit.NamespacedKey key = enchantment.getKey();
        return key != null ? key.toString() : null;
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
