package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.data.model.LoreLocation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for lore_location spatial data operations.
 * All methods return CompletableFuture for async operations per RVNKCore standard.
 */
public interface ILocationRepository {

    CompletableFuture<LoreLocation> save(LoreLocation location);

    CompletableFuture<List<LoreLocation>> findByEntryId(String entryId);

    CompletableFuture<LoreLocation> findPrimaryByEntryId(String entryId);

    CompletableFuture<List<LoreLocation>> findNearby(String world, double x, double z, double radius);

    CompletableFuture<Boolean> deleteByEntryId(String entryId);

    CompletableFuture<Boolean> deleteById(int locationId);

    CompletableFuture<Integer> countByWorld(String worldName);
}
