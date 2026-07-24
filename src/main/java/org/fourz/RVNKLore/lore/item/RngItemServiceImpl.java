package org.fourz.RVNKLore.lore.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.DatabaseHelper;
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
    private final DatabaseConnection dbConnection;
    private final DatabaseHelper dbHelper;
    private final ItemManager itemManager;
    private boolean fallbackMode = false;

    public RngItemServiceImpl(RVNKLore plugin, DatabaseConnection dbConnection, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "RngItemServiceImpl");
        this.dbConnection = dbConnection;
        this.dbHelper = new DatabaseHelper(plugin);
        this.itemManager = itemManager;
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
                // Best-effort visual fidelity. custom_model_data function format is MC-version sensitive;
                // the RVNKWorlds Dev test (#1674) validates the emitted table loads on the server.
                if (p.getCustomModelData() > 0) {
                    JsonObject cmd = new JsonObject();
                    cmd.addProperty("function", "minecraft:set_custom_model_data");
                    cmd.addProperty("value", p.getCustomModelData());
                    functions.add(cmd);
                }
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

    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }

    private CompletableFuture<List<PoolEntry>> getWeightedEntries(String poolId, String rarityTier) {
        return CompletableFuture.supplyAsync(() -> {
            String table = dbConnection.table(DatabaseConnection.TABLE_LORE_ITEM_RNG_POOL);
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
