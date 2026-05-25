package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.service.IItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the quest_item_presets integration.
 * Verifies that IItemRepository and IItemService declare getPresetsForQuest,
 * that the DatabaseConnection constant exists, and that ItemRepository
 * implements the interface method.
 */
@DisplayName("QuestItemPreset contract tests")
class QuestItemPresetContractTest {

    @Nested
    @DisplayName("Interface method declarations")
    class InterfaceDeclarations {

        @Test
        @DisplayName("IItemRepository declares getPresetsForQuest(String)")
        void itemRepositoryInterfaceHasGetPresetsForQuest() {
            assertTrue(hasMethod(IItemRepository.class, "getPresetsForQuest", String.class),
                "IItemRepository must declare getPresetsForQuest(String)");
        }

        @Test
        @DisplayName("IItemService declares getPresetsForQuest(String)")
        void itemServiceInterfaceHasGetPresetsForQuest() {
            assertTrue(hasMethod(IItemService.class, "getPresetsForQuest", String.class),
                "IItemService must declare getPresetsForQuest(String)");
        }

        @Test
        @DisplayName("IItemRepository.getPresetsForQuest returns CompletableFuture")
        void itemRepositoryMethodReturnsCompletableFuture() throws NoSuchMethodException {
            Method method = IItemRepository.class.getMethod("getPresetsForQuest", String.class);
            assertEquals(CompletableFuture.class, method.getReturnType(),
                "getPresetsForQuest must return CompletableFuture");
        }

        @Test
        @DisplayName("IItemService.getPresetsForQuest returns CompletableFuture")
        void itemServiceMethodReturnsCompletableFuture() throws NoSuchMethodException {
            Method method = IItemService.class.getMethod("getPresetsForQuest", String.class);
            assertEquals(CompletableFuture.class, method.getReturnType(),
                "getPresetsForQuest must return CompletableFuture");
        }

        @Test
        @DisplayName("ItemRepository implements IItemRepository")
        void itemRepositoryImplementsInterface() {
            assertTrue(IItemRepository.class.isAssignableFrom(ItemRepository.class),
                "ItemRepository must implement IItemRepository");
        }
    }

    @Nested
    @DisplayName("DatabaseConnection schema constants")
    class SchemaConstants {

        @Test
        @DisplayName("TABLE_QUEST_ITEM_PRESETS constant exists")
        void questItemPresetsConstantExists() throws NoSuchFieldException {
            var field = DatabaseConnection.class.getDeclaredField("TABLE_QUEST_ITEM_PRESETS");
            assertNotNull(field, "DatabaseConnection must declare TABLE_QUEST_ITEM_PRESETS");
        }

        @Test
        @DisplayName("TABLE_QUEST_ITEM_PRESETS value follows snake_case convention")
        void questItemPresetsConstantFollowsConvention() throws Exception {
            var field = DatabaseConnection.class.getDeclaredField("TABLE_QUEST_ITEM_PRESETS");
            field.setAccessible(true);
            String value = (String) field.get(null);
            assertTrue(value != null && value.matches("[a-z][a-z0-9_]*"),
                "TABLE_QUEST_ITEM_PRESETS value must be snake_case: " + value);
            assertTrue(value.contains("quest") || value.contains("preset"),
                "Table name should reference quest or preset: " + value);
        }
    }

    @Nested
    @DisplayName("IItemService contract — getPresetsForQuest stub")
    class ServiceContractStub {

        private static class StubItemService implements IItemService {
            @Override
            public CompletableFuture<java.util.Optional<org.bukkit.inventory.ItemStack>> createLoreItem(String itemName) {
                return CompletableFuture.completedFuture(java.util.Optional.empty());
            }
            @Override
            public CompletableFuture<org.bukkit.inventory.ItemStack> createLoreItem(
                    org.fourz.RVNKLore.lore.item.ItemType type, String name, ItemProperties properties) {
                return CompletableFuture.completedFuture(null);
            }
            @Override
            public CompletableFuture<Boolean> giveItemToPlayer(String itemName, org.bukkit.entity.Player player) {
                return CompletableFuture.completedFuture(false);
            }
            @Override
            public CompletableFuture<List<String>> getAllItemNames() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override
            public CompletableFuture<List<ItemProperties>> getAllItemsWithProperties() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override
            public CompletableFuture<Boolean> registerLoreItem(java.util.UUID loreEntryId, ItemProperties properties) {
                return CompletableFuture.completedFuture(false);
            }
            @Override
            public CompletableFuture<Void> refreshCache() {
                return CompletableFuture.completedFuture(null);
            }
            @Override
            public CompletableFuture<List<ItemProperties>> getPresetsForQuest(String questId) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override
            public boolean isInFallbackMode() { return false; }
        }

        @Test
        @DisplayName("getPresetsForQuest returns non-null CompletableFuture")
        void getPresetsForQuestReturnsNonNull() {
            IItemService service = new StubItemService();
            CompletableFuture<List<ItemProperties>> future = service.getPresetsForQuest("any_quest");
            assertNotNull(future);
        }

        @Test
        @DisplayName("getPresetsForQuest result joins without throwing")
        void getPresetsForQuestJoinsCleanly() {
            IItemService service = new StubItemService();
            List<ItemProperties> result = service.getPresetsForQuest("test_quest").join();
            assertNotNull(result);
        }

        @Test
        @DisplayName("getPresetsForQuest can be chained async")
        void getPresetsForQuestSupportsAsyncChaining() {
            IItemService service = new StubItemService();
            int count = service.getPresetsForQuest("test_quest")
                .thenApply(List::size)
                .join();
            assertEquals(0, count);
        }
    }

    private boolean hasMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return Arrays.stream(clazz.getMethods())
            .anyMatch(m -> m.getName().equals(name) &&
                Arrays.equals(m.getParameterTypes(), paramTypes));
    }
}
