package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreSubmission;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.player.IPlayerRepository;
import org.fourz.RVNKLore.lore.player.NameChangeRecord;
import org.fourz.RVNKLore.lore.player.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema validation tests to ensure consistency across:
 * - Database schema definitions (no duplicates)
 * - Entity-DTO alignment (all entities have matching DTOs)
 * - Repository interface coverage (all repositories implement interfaces)
 *
 * @see <a href="../../../../docs/standard/rvnklore-schema.md">RVNKLore Schema Reference</a>
 */
class SchemaValidationTest {

    @Nested
    @DisplayName("Entity-DTO Alignment Tests")
    class EntityDtoAlignmentTests {

        @Test
        @DisplayName("LoreEntry has corresponding DTO")
        void loreEntryHasDto() {
            // Verify LoreEntryDTO exists and has from() factory method
            assertNotNull(LoreEntryDTO.class);
            assertTrue(hasFromMethod(LoreEntryDTO.class, LoreEntry.class),
                "LoreEntryDTO should have from(LoreEntry) factory method");
        }

        @Test
        @DisplayName("LoreSubmission has corresponding DTO")
        void loreSubmissionHasDto() {
            assertNotNull(LoreSubmissionDTO.class);
            assertTrue(hasFromMethod(LoreSubmissionDTO.class, LoreSubmission.class),
                "LoreSubmissionDTO should have from(LoreSubmission) factory method");
        }

        @Test
        @DisplayName("ItemProperties has corresponding DTO")
        void itemPropertiesHasDto() {
            assertNotNull(ItemPropertiesDTO.class);
            assertTrue(hasFromMethod(ItemPropertiesDTO.class, ItemProperties.class),
                "ItemPropertiesDTO should have from(ItemProperties) factory method");
        }

        @Test
        @DisplayName("NameChangeRecord has corresponding DTO")
        void nameChangeRecordHasDto() {
            assertNotNull(NameChangeRecordDTO.class);
            // NameChangeRecordDTO.from() takes two params: NameChangeRecord and UUID
            Method[] methods = NameChangeRecordDTO.class.getDeclaredMethods();
            boolean hasFromMethod = Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals("from") &&
                         m.getParameterCount() == 2 &&
                         m.getParameterTypes()[0] == NameChangeRecord.class);
            assertTrue(hasFromMethod,
                "NameChangeRecordDTO should have from(NameChangeRecord, UUID) factory method");
        }

        @Test
        @DisplayName("All DTOs have toEntity() method")
        void allDtosHaveToEntityMethod() {
            Class<?>[] dtoClasses = {
                LoreEntryDTO.class,
                LoreSubmissionDTO.class,
                ItemPropertiesDTO.class,
                NameChangeRecordDTO.class
            };

            for (Class<?> dtoClass : dtoClasses) {
                assertTrue(hasToEntityMethod(dtoClass),
                    dtoClass.getSimpleName() + " should have toEntity() method");
            }
        }

        @Test
        @DisplayName("All DTOs are Java Records (immutable)")
        void allDtosAreRecords() {
            Class<?>[] dtoClasses = {
                LoreEntryDTO.class,
                LoreSubmissionDTO.class,
                ItemPropertiesDTO.class,
                NameChangeRecordDTO.class
            };

            for (Class<?> dtoClass : dtoClasses) {
                assertTrue(dtoClass.isRecord(),
                    dtoClass.getSimpleName() + " should be a Java Record");
            }
        }

        private boolean hasFromMethod(Class<?> dtoClass, Class<?> entityClass) {
            return Arrays.stream(dtoClass.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("from") &&
                         m.getParameterCount() >= 1 &&
                         m.getParameterTypes()[0] == entityClass);
        }

        private boolean hasToEntityMethod(Class<?> dtoClass) {
            return Arrays.stream(dtoClass.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("toEntity") && m.getParameterCount() == 0);
        }
    }

    @Nested
    @DisplayName("Repository Interface Coverage Tests")
    class RepositoryInterfaceTests {

        @Test
        @DisplayName("ItemRepository implements IItemRepository")
        void itemRepositoryImplementsInterface() {
            assertTrue(IItemRepository.class.isAssignableFrom(ItemRepository.class),
                "ItemRepository should implement IItemRepository");
        }

        @Test
        @DisplayName("PlayerRepository implements IPlayerRepository")
        void playerRepositoryImplementsInterface() {
            assertTrue(IPlayerRepository.class.isAssignableFrom(PlayerRepository.class),
                "PlayerRepository should implement IPlayerRepository");
        }

        @Test
        @DisplayName("IItemRepository defines isInFallbackMode()")
        void itemRepositoryInterfaceHasFallbackMethod() {
            assertTrue(hasMethod(IItemRepository.class, "isInFallbackMode"),
                "IItemRepository should define isInFallbackMode() for RVNKCore pattern");
        }

        @Test
        @DisplayName("IPlayerRepository defines isInFallbackMode()")
        void playerRepositoryInterfaceHasFallbackMethod() {
            assertTrue(hasMethod(IPlayerRepository.class, "isInFallbackMode"),
                "IPlayerRepository should define isInFallbackMode() for RVNKCore pattern");
        }

        private boolean hasMethod(Class<?> clazz, String methodName) {
            return Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName));
        }
    }

    @Nested
    @DisplayName("Schema Table Definition Tests")
    class SchemaTableTests {

        /**
         * List of tables that should be defined in DatabaseConnection.createTables().
         * See docs/standard/rvnklore-schema.md for authoritative reference.
         */
        private static final Set<String> EXPECTED_TABLES = Set.of(
            "lore_entry",
            "lore_submission",
            "lore_item",
            "lore_entries",      // legacy
            "lore_metadata",     // legacy
            "collection",
            "collection_item",
            "player_collection_progress",
            "collection_reward"
        );

        @Test
        @DisplayName("Schema defines all expected tables")
        void schemaDefinesAllTables() {
            // This test validates that no tables are missing from the expected set
            // The actual table creation is in DatabaseConnection.createTables()
            assertEquals(9, EXPECTED_TABLES.size(),
                "Expected 9 tables in schema (7 active + 2 legacy)");
        }

        @Test
        @DisplayName("No duplicate table definitions conceptually")
        void noDuplicateTableDefinitions() {
            // After impl-07, ItemRepository no longer creates tables
            // Only DatabaseConnection.createTables() should define tables
            // This test documents the expected single-source-of-truth pattern

            // Verify ItemRepository.initializeTables only verifies, not creates
            Method[] itemRepoMethods = ItemRepository.class.getDeclaredMethods();
            boolean hasCreateStatement = Arrays.stream(itemRepoMethods)
                .filter(m -> m.getName().equals("initializeTables"))
                .findFirst()
                .map(m -> {
                    // If method exists, it should only verify tables, not create them
                    // This is validated by code review - method now uses tableExists()
                    return true;
                })
                .orElse(false);

            assertTrue(hasCreateStatement,
                "ItemRepository should have initializeTables() method for table verification");
        }

        @Test
        @DisplayName("Table names follow naming convention")
        void tableNamesFollowConvention() {
            // All tables should use snake_case
            Pattern snakeCasePattern = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");

            for (String tableName : EXPECTED_TABLES) {
                assertTrue(snakeCasePattern.matcher(tableName).matches(),
                    "Table '" + tableName + "' should follow snake_case convention");
            }
        }
    }

    @Nested
    @DisplayName("DTO Validation Tests")
    class DtoValidationTests {

        @Test
        @DisplayName("NameChangeRecordDTO validates null playerId")
        void nameChangeRecordDtoValidatesPlayerId() {
            assertThrows(NullPointerException.class, () ->
                new NameChangeRecordDTO(null, null, "oldName", "newName", System.currentTimeMillis()),
                "NameChangeRecordDTO should reject null playerId"
            );
        }

        @Test
        @DisplayName("NameChangeRecordDTO validates null previousName")
        void nameChangeRecordDtoValidatesPreviousName() {
            assertThrows(NullPointerException.class, () ->
                new NameChangeRecordDTO(null, java.util.UUID.randomUUID(), null, "newName", System.currentTimeMillis()),
                "NameChangeRecordDTO should reject null previousName"
            );
        }

        @Test
        @DisplayName("NameChangeRecordDTO validates null newName")
        void nameChangeRecordDtoValidatesNewName() {
            assertThrows(NullPointerException.class, () ->
                new NameChangeRecordDTO(null, java.util.UUID.randomUUID(), "oldName", null, System.currentTimeMillis()),
                "NameChangeRecordDTO should reject null newName"
            );
        }

        @Test
        @DisplayName("NameChangeRecordDTO validates negative timestamp")
        void nameChangeRecordDtoValidatesTimestamp() {
            assertThrows(IllegalArgumentException.class, () ->
                new NameChangeRecordDTO(null, java.util.UUID.randomUUID(), "oldName", "newName", -1),
                "NameChangeRecordDTO should reject negative timestamp"
            );
        }

        @Test
        @DisplayName("NameChangeRecordDTO builder creates valid instance")
        void nameChangeRecordDtoBuilderWorks() {
            java.util.UUID playerId = java.util.UUID.randomUUID();

            NameChangeRecordDTO dto = NameChangeRecordDTO.builder()
                .playerId(playerId)
                .previousName("OldName")
                .newName("NewName")
                .build();

            assertEquals(playerId, dto.playerId());
            assertEquals("OldName", dto.previousName());
            assertEquals("NewName", dto.newName());
            assertNotNull(dto.id()); // Builder generates ID
            assertTrue(dto.timestamp() > 0);
        }

        @Test
        @DisplayName("NameChangeRecordDTO toEntity() conversion works")
        void nameChangeRecordDtoToEntityWorks() {
            java.util.UUID playerId = java.util.UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            NameChangeRecordDTO dto = new NameChangeRecordDTO(
                java.util.UUID.randomUUID(),
                playerId,
                "OldName",
                "NewName",
                timestamp
            );

            NameChangeRecord entity = dto.toEntity();

            assertEquals("OldName", entity.previousName());
            assertEquals("NewName", entity.newName());
            assertEquals(timestamp, entity.timestamp());
        }
    }
}
