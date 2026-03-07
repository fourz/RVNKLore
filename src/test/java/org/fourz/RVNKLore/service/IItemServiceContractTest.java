package org.fourz.RVNKLore.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for IItemService interface.
 * Verifies interface method signatures and async return types.
 * These tests validate that implementations adhere to the interface contract.
 */
class IItemServiceContractTest {

    /**
     * Minimal test implementation of IItemService for contract verification.
     */
    private static class TestItemService implements IItemService {
        private boolean fallbackMode = false;

        @Override
        public CompletableFuture<Optional<ItemStack>> createLoreItem(String itemName) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<ItemStack> createLoreItem(ItemType type, String name, ItemProperties properties) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> giveItemToPlayer(String itemName, Player player) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<List<String>> getAllItemNames() {
            return CompletableFuture.completedFuture(List.of("item1", "item2"));
        }

        @Override
        public CompletableFuture<List<ItemProperties>> getAllItemsWithProperties() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> registerLoreItem(UUID loreEntryId, ItemProperties properties) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Void> refreshCache() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isInFallbackMode() {
            return fallbackMode;
        }

        public void setFallbackMode(boolean fallbackMode) {
            this.fallbackMode = fallbackMode;
        }
    }

    @Test
    @DisplayName("Interface can be implemented")
    void testInterfaceImplementable() {
        IItemService service = new TestItemService();
        assertNotNull(service);
    }

    @Test
    @DisplayName("createLoreItem(String) returns CompletableFuture<Optional<ItemStack>>")
    void testCreateLoreItemByName() {
        IItemService service = new TestItemService();

        CompletableFuture<Optional<ItemStack>> future = service.createLoreItem("test_item");

        assertNotNull(future);
        Optional<ItemStack> result = future.join();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllItemNames returns CompletableFuture<List<String>>")
    void testGetAllItemNames() {
        IItemService service = new TestItemService();

        CompletableFuture<List<String>> future = service.getAllItemNames();

        assertNotNull(future);
        List<String> names = future.join();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("item1"));
        assertTrue(names.contains("item2"));
    }

    @Test
    @DisplayName("registerLoreItem returns CompletableFuture<Boolean>")
    void testRegisterLoreItem() {
        IItemService service = new TestItemService();

        CompletableFuture<Boolean> future = service.registerLoreItem(UUID.randomUUID(), null);

        assertNotNull(future);
        assertTrue(future.join());
    }

    @Test
    @DisplayName("refreshCache returns CompletableFuture<Void>")
    void testRefreshCache() {
        IItemService service = new TestItemService();

        CompletableFuture<Void> future = service.refreshCache();

        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
    }

    @Test
    @DisplayName("isInFallbackMode is synchronous boolean")
    void testFallbackMode() {
        TestItemService service = new TestItemService();

        assertFalse(service.isInFallbackMode());

        service.setFallbackMode(true);
        assertTrue(service.isInFallbackMode());
    }

    @Test
    @DisplayName("Async methods can be chained")
    void testAsyncChaining() {
        IItemService service = new TestItemService();

        CompletableFuture<Integer> chainedResult = service.getAllItemNames()
            .thenApply(List::size)
            .thenApply(size -> size * 2);

        assertEquals(4, chainedResult.join());
    }

    @Test
    @DisplayName("Async methods support exception handling")
    void testAsyncExceptionHandling() {
        IItemService service = new TestItemService();

        CompletableFuture<String> result = service.createLoreItem("nonexistent")
            .thenApply(opt -> opt.map(item -> "found").orElse("not found"))
            .exceptionally(ex -> "error: " + ex.getMessage());

        assertEquals("not found", result.join());
    }
}
