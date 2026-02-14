package org.fourz.RVNKLore.service;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for ICollectionService interface.
 * Verifies interface method signatures and async return types.
 * These tests validate that implementations adhere to the interface contract.
 */
class ICollectionServiceContractTest {

    /**
     * Minimal test implementation of ICollectionService for contract verification.
     */
    private static class TestCollectionService implements ICollectionService {
        private boolean fallbackMode = false;
        private final Map<String, ItemCollection> collections = new HashMap<>();

        public TestCollectionService() {
            // Add some test data
            collections.put("test_collection", new ItemCollection("test_collection", "Test Collection", "A test collection"));
        }

        @Override
        public CompletableFuture<Optional<ItemCollection>> createCollection(String id, String name, String description) {
            ItemCollection collection = new ItemCollection(id, name, description);
            collections.put(id, collection);
            return CompletableFuture.completedFuture(Optional.of(collection));
        }

        @Override
        public CompletableFuture<Optional<ItemCollection>> getCollection(String id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(collections.get(id)));
        }

        @Override
        public CompletableFuture<Map<String, ItemCollection>> getAllCollections() {
            return CompletableFuture.completedFuture(new HashMap<>(collections));
        }

        @Override
        public CompletableFuture<Map<String, ItemCollection>> getCollectionsByTheme(String themeId) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        @Override
        public CompletableFuture<Boolean> addItemToCollection(String collectionId, ItemStack item) {
            return CompletableFuture.completedFuture(collections.containsKey(collectionId));
        }

        @Override
        public CompletableFuture<Boolean> removeItemFromCollection(String collectionId, ItemStack item) {
            return CompletableFuture.completedFuture(collections.containsKey(collectionId));
        }

        @Override
        public CompletableFuture<List<ItemStack>> getCollectionItems(String collectionId) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        @Override
        public CompletableFuture<Double> getPlayerProgress(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(0.5);
        }

        @Override
        public CompletableFuture<Boolean> updatePlayerProgress(UUID playerId, String collectionId, double progress) {
            return CompletableFuture.completedFuture(progress >= 0.0 && progress <= 1.0);
        }

        @Override
        public CompletableFuture<Boolean> grantCollectionReward(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> saveCollection(ItemCollection collection) {
            if (collection != null) {
                collections.put(collection.getId(), collection);
                return CompletableFuture.completedFuture(true);
            }
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public boolean isInFallbackMode() {
            return fallbackMode;
        }

        @Override
        public CompletableFuture<Boolean> trackItemDiscovery(UUID playerId, String collectionId, int itemId) {
            return CompletableFuture.completedFuture(collections.containsKey(collectionId));
        }

        @Override
        public CompletableFuture<List<ItemStack>> getPlayerCollectionItems(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        @Override
        public CompletableFuture<Integer> getCollectedItemCount(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public CompletableFuture<List<ItemStack>> getMissingItems(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        @Override
        public CompletableFuture<Double> calculateItemBasedProgress(UUID playerId, String collectionId) {
            return CompletableFuture.completedFuture(0.0);
        }

        public void setFallbackMode(boolean fallbackMode) {
            this.fallbackMode = fallbackMode;
        }
    }

    @Test
    @DisplayName("Interface can be implemented")
    void testInterfaceImplementable() {
        ICollectionService service = new TestCollectionService();
        assertNotNull(service);
    }

    @Test
    @DisplayName("createCollection returns CompletableFuture<Optional<ItemCollection>>")
    void testCreateCollection() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<Optional<ItemCollection>> future = service.createCollection(
            "new_collection", "New Collection", "A new collection"
        );

        assertNotNull(future);
        Optional<ItemCollection> result = future.join();
        assertTrue(result.isPresent());
        assertEquals("new_collection", result.get().getId());
        assertEquals("New Collection", result.get().getName());
    }

    @Test
    @DisplayName("getCollection returns CompletableFuture<Optional<ItemCollection>>")
    void testGetCollection() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<Optional<ItemCollection>> future = service.getCollection("test_collection");

        assertNotNull(future);
        Optional<ItemCollection> result = future.join();
        assertTrue(result.isPresent());
        assertEquals("Test Collection", result.get().getName());
    }

    @Test
    @DisplayName("getCollection returns empty Optional for non-existent collection")
    void testGetNonExistentCollection() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<Optional<ItemCollection>> future = service.getCollection("nonexistent");

        Optional<ItemCollection> result = future.join();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllCollections returns CompletableFuture<Map<String, ItemCollection>>")
    void testGetAllCollections() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<Map<String, ItemCollection>> future = service.getAllCollections();

        assertNotNull(future);
        Map<String, ItemCollection> result = future.join();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("test_collection"));
    }

    @Test
    @DisplayName("getPlayerProgress returns CompletableFuture<Double>")
    void testGetPlayerProgress() {
        ICollectionService service = new TestCollectionService();
        UUID playerId = UUID.randomUUID();

        CompletableFuture<Double> future = service.getPlayerProgress(playerId, "test_collection");

        assertNotNull(future);
        Double progress = future.join();
        assertNotNull(progress);
        assertTrue(progress >= 0.0 && progress <= 1.0);
    }

    @Test
    @DisplayName("updatePlayerProgress validates progress range")
    void testUpdatePlayerProgress() {
        ICollectionService service = new TestCollectionService();
        UUID playerId = UUID.randomUUID();

        // Valid progress
        assertTrue(service.updatePlayerProgress(playerId, "test", 0.5).join());

        // Edge cases
        assertTrue(service.updatePlayerProgress(playerId, "test", 0.0).join());
        assertTrue(service.updatePlayerProgress(playerId, "test", 1.0).join());
    }

    @Test
    @DisplayName("saveCollection returns CompletableFuture<Boolean>")
    void testSaveCollection() {
        ICollectionService service = new TestCollectionService();
        ItemCollection collection = new ItemCollection("save_test", "Save Test", "Testing save");

        CompletableFuture<Boolean> future = service.saveCollection(collection);

        assertNotNull(future);
        assertTrue(future.join());
    }

    @Test
    @DisplayName("isInFallbackMode is synchronous boolean")
    void testFallbackMode() {
        TestCollectionService service = new TestCollectionService();

        assertFalse(service.isInFallbackMode());

        service.setFallbackMode(true);
        assertTrue(service.isInFallbackMode());
    }

    @Test
    @DisplayName("Async methods support chaining with thenCompose")
    void testAsyncComposition() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<String> result = service.createCollection("composed", "Composed", "Test")
            .thenCompose(opt -> opt.map(c ->
                service.getCollection(c.getId())
                    .thenApply(found -> found.map(ItemCollection::getName).orElse("not found"))
            ).orElse(CompletableFuture.completedFuture("creation failed")));

        assertEquals("Composed", result.join());
    }

    @Test
    @DisplayName("Async methods support exception handling")
    void testAsyncExceptionHandling() {
        ICollectionService service = new TestCollectionService();

        CompletableFuture<String> result = service.getCollection("nonexistent")
            .thenApply(opt -> opt.map(ItemCollection::getName).orElse("not found"))
            .exceptionally(ex -> "error: " + ex.getMessage());

        assertEquals("not found", result.join());
    }
}
