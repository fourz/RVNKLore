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
     * Check if the service is in fallback mode.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();
}
