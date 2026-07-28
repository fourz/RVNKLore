package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.service.IRngItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for Phase 3 — lore_item_rng_pool schema constant and IRngItemService interface.
 */
@DisplayName("Phase 3 — RNG pool schema and service contract")
class RngPoolSchemaTest {

    @Nested
    @DisplayName("DatabaseConnection schema constants")
    class SchemaConstants {

        @Test
        @DisplayName("TABLE_LORE_ITEM_RNG_POOL constant exists")
        void rngPoolConstantExists() throws NoSuchFieldException {
            var field = DatabaseConnection.class.getDeclaredField("TABLE_LORE_ITEM_RNG_POOL");
            assertNotNull(field);
        }

        @Test
        @DisplayName("TABLE_LORE_ITEM_RNG_POOL follows snake_case convention")
        void rngPoolConstantFollowsConvention() throws Exception {
            var field = DatabaseConnection.class.getDeclaredField("TABLE_LORE_ITEM_RNG_POOL");
            field.setAccessible(true);
            String value = (String) field.get(null);
            assertNotNull(value);
            assertTrue(value.matches("[a-z][a-z0-9_]*"),
                "TABLE_LORE_ITEM_RNG_POOL value must be snake_case: " + value);
            assertTrue(value.contains("rng") || value.contains("pool"),
                "Table name should reference rng or pool: " + value);
        }
    }

    @Nested
    @DisplayName("IRngItemService interface declarations")
    class ServiceInterface {

        @Test
        @DisplayName("roll(String, String) is declared")
        void rollMethodDeclared() {
            assertTrue(hasMethod(IRngItemService.class, "roll", String.class, String.class));
        }

        @Test
        @DisplayName("getPoolItemIds(String, String) is declared")
        void getPoolItemIdsDeclared() {
            assertTrue(hasMethod(IRngItemService.class, "getPoolItemIds", String.class, String.class));
        }

        @Test
        @DisplayName("isInFallbackMode() is declared")
        void fallbackModeDeclared() {
            assertTrue(hasMethod(IRngItemService.class, "isInFallbackMode"));
        }

        @Test
        @DisplayName("roll returns CompletableFuture")
        void rollReturnsCompletableFuture() throws NoSuchMethodException {
            Method m = IRngItemService.class.getMethod("roll", String.class, String.class);
            assertEquals(CompletableFuture.class, m.getReturnType());
        }

        @Test
        @DisplayName("getPoolItemIds returns CompletableFuture")
        void getPoolItemIdsReturnsCompletableFuture() throws NoSuchMethodException {
            Method m = IRngItemService.class.getMethod("getPoolItemIds", String.class, String.class);
            assertEquals(CompletableFuture.class, m.getReturnType());
        }
    }

    @Nested
    @DisplayName("IRngItemService stub contract")
    class StubContract {

        private final IRngItemService stub = new IRngItemService() {
            @Override
            public CompletableFuture<Optional<org.bukkit.inventory.ItemStack>> roll(String poolId, String rarityTier) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            @Override
            public CompletableFuture<List<Integer>> getPoolItemIds(String poolId, String rarityTier) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override
            public CompletableFuture<List<org.fourz.RVNKLore.service.PoolItemEntry>> getPoolEntries(
                    String poolId, String rarityTier) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override
            public CompletableFuture<Optional<String>> poolToLootTableJson(String poolId, String rarityTier) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            @Override
            public boolean isInFallbackMode() { return false; }
        };

        @Test
        @DisplayName("roll returns non-null Future")
        void rollReturnsNonNull() {
            assertNotNull(stub.roll("pool1", "RARE"));
        }

        @Test
        @DisplayName("roll joins without throwing")
        void rollJoinsCleanly() {
            assertDoesNotThrow(() -> stub.roll("pool1", null).join());
        }

        @Test
        @DisplayName("getPoolItemIds returns non-null empty list")
        void getPoolItemIdsReturnsEmpty() {
            List<Integer> ids = stub.getPoolItemIds("pool1", "COMMON").join();
            assertNotNull(ids);
            assertTrue(ids.isEmpty());
        }

        @Test
        @DisplayName("isInFallbackMode returns false by default")
        void fallbackModeDefault() {
            assertFalse(stub.isInFallbackMode());
        }

        @Test
        @DisplayName("roll can be chained with thenApply")
        void rollSupportsChaining() {
            boolean present = stub.roll("p", "EPIC")
                .thenApply(Optional::isPresent)
                .join();
            assertFalse(present);
        }
    }

    private boolean hasMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return Arrays.stream(clazz.getMethods())
            .anyMatch(m -> m.getName().equals(name) &&
                Arrays.equals(m.getParameterTypes(), paramTypes));
    }
}
