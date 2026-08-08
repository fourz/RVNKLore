package org.fourz.RVNKLore.service;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for rarity-weighted RNG item selection from lore_item_rng_pool.
 * Registered with RVNKCore ServiceRegistry for cross-plugin access.
 */
public interface IRngItemService {

    /**
     * Roll a random item from the pool, optionally filtered by rarity tier.
     * Selection is weighted by the {@code weight} column — higher weight = more likely.
     *
     * @param poolId     The pool identifier (maps to lore_item_rng_pool.pool_id)
     * @param rarityTier Rarity tier filter (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY),
     *                   or null to select across all active tiers in the pool
     * @return Future containing the rolled ItemStack, or empty if pool is empty or unknown
     */
    CompletableFuture<Optional<ItemStack>> roll(String poolId, String rarityTier);

    /**
     * List all active item IDs in a pool, optionally filtered by rarity tier.
     *
     * @param poolId     The pool identifier
     * @param rarityTier Rarity tier filter, or null for all tiers
     * @return Future containing list of lore_item database IDs
     */
    CompletableFuture<List<Integer>> getPoolItemIds(String poolId, String rarityTier);

    /**
     * List a pool's weighted entries (item id + weight + rarity tier), for consumers that need the
     * pool's composition — e.g. building a loot table or a weighted find-table. GH#1670.
     *
     * @param poolId     The pool identifier
     * @param rarityTier Rarity tier filter, or null for all active tiers
     * @return Future containing the weighted entries (empty if pool is empty/unknown)
     */
    CompletableFuture<List<PoolItemEntry>> getPoolEntries(String poolId, String rarityTier);

    /**
     * Render a pool as a vanilla {@code minecraft:chest} loot-table JSON string (for the "bake a pool
     * into a datapack loot table" lane). Each entry maps a lore item to a weighted loot entry
     * (material + custom_model_data, best-effort). GH#1670.
     *
     * <p><b>Fidelity caveat:</b> a vanilla loot table cannot carry the PDC {@code lore_item_id} tag, so
     * baked items are visual look-alikes, not registered lore items. For true lore items (identity,
     * resolve-back, collection/vote hooks) use {@link #roll(String, String)} to fill containers directly.
     *
     * <p><b>Player heads are skipped, not baked</b> (#1914): {@code lore_item} never persists profile
     * or texture data, so a {@code PLAYER_HEAD} entry could only bake as an anonymous Steve. Such
     * entries are logged at ERROR and omitted; a pool containing nothing else yields an empty result.
     * The dynamic {@link #roll(String, String)} lane has the same gap, so this is parity, not a
     * bake-only regression. Mob skulls carry their own appearance and bake normally.
     *
     * @param poolId     The pool identifier
     * @param rarityTier Rarity tier filter, or null for all active tiers
     * @return Future containing the loot-table JSON, or empty if the pool has no resolvable items
     */
    CompletableFuture<Optional<String>> poolToLootTableJson(String poolId, String rarityTier);

    /**
     * Check if the service is in fallback mode.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();
}
