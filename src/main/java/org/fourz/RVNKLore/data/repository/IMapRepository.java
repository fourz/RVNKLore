package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.lore.map.LoreMap;
import org.fourz.RVNKLore.lore.map.MapSubtype;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for lore_map cross-server map storage.
 */
public interface IMapRepository {

    CompletableFuture<Optional<LoreMap>> save(LoreMap map);

    CompletableFuture<Optional<LoreMap>> findById(int mapId);

    CompletableFuture<List<LoreMap>> findByEntryId(String entryId);

    CompletableFuture<List<LoreMap>> findBySubtype(MapSubtype subtype);

    CompletableFuture<Boolean> updatePixelData(int mapId, String base64PixelData);

    CompletableFuture<Boolean> deleteById(int mapId);

    CompletableFuture<Boolean> deleteByEntryId(String entryId);
}
