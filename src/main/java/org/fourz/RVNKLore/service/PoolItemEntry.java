package org.fourz.RVNKLore.service;

/**
 * One weighted entry in a lore RNG pool — exposed cross-plugin so consumers (e.g. RVNKWorlds loot)
 * can inspect a pool's composition without re-querying the DB. GH#1670.
 *
 * @param loreItemId the {@code lore_item} database id
 * @param weight     selection weight (higher = more likely)
 * @param rarityTier the entry's rarity tier bucket (e.g. COMMON, RARE, LEGENDARY)
 */
public record PoolItemEntry(int loreItemId, int weight, String rarityTier) {}
