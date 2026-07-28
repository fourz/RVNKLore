package org.fourz.RVNKLore.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the #1833 reconcile-on-recovery building blocks.
 *
 * <p>These cover the two pure pieces the feature's correctness rests on: deciding which tables may
 * be written during fallback, and pulling the target table out of a statement. Both fail silently if
 * wrong — a mis-parsed statement would skip the fail-closed gate and go unjournalled.</p>
 */
@DisplayName("Fallback reconcile (#1833)")
class FallbackReconcileTest {

    @Nested
    @DisplayName("LoreTableScope")
    class Scope {

        @Test
        @DisplayName("Cluster-shared content tables are classified shared")
        void sharedTables() {
            assertTrue(LoreTableScope.isShared(DatabaseConnection.TABLE_LORE_ENTRY));
            assertTrue(LoreTableScope.isShared(DatabaseConnection.TABLE_LORE_SUBMISSION));
            assertTrue(LoreTableScope.isShared(DatabaseConnection.TABLE_LORE_ITEM));
            assertTrue(LoreTableScope.isShared(DatabaseConnection.TABLE_COLLECTION));
            assertTrue(LoreTableScope.isShared(DatabaseConnection.TABLE_LORE_ITEM_RNG_POOL));
        }

        @Test
        @DisplayName("World-bearing tables stay per-server — they carry a world name")
        void worldBearingTablesArePerServer() {
            // These store world + coordinates, which do not mean the same thing on two tiers.
            // Sharing them is the collision #1834 explicitly avoids.
            assertTrue(LoreTableScope.isPerServer(DatabaseConnection.TABLE_LORE_LOCATION));
            assertTrue(LoreTableScope.isPerServer(DatabaseConnection.TABLE_LORE_DISCOVERY));
            assertTrue(LoreTableScope.isPerServer(DatabaseConnection.TABLE_LORE_MAP));
        }

        @Test
        @DisplayName("Prefixed table names classify the same as bare ones")
        void prefixedNamesResolve() {
            // Repositories build SQL through DatabaseConnection.table(), which applies the
            // configured prefix (rvnklore_ on the live tiers). The gate must still recognise them.
            assertTrue(LoreTableScope.isShared("rvnklore_lore_entry"));
            assertTrue(LoreTableScope.isShared("RVNKLORE_LORE_ENTRY"));
            assertTrue(LoreTableScope.isPerServer("rvnklore_lore_discovery"));
        }

        @Test
        @DisplayName("Unknown and blank names default to per-server, never shared")
        void unknownDefaultsToPerServer() {
            // Defaulting an unrecognised table to "shared" would refuse legitimate writes during an
            // outage; defaulting to per-server keeps availability and is the safer failure.
            assertTrue(LoreTableScope.isPerServer("some_other_plugin_table"));
            assertTrue(LoreTableScope.isPerServer(null));
            assertTrue(LoreTableScope.isPerServer(""));
        }
    }

    @Nested
    @DisplayName("Statement table extraction")
    class Extraction {

        @Test
        @DisplayName("Parses INSERT, UPDATE, DELETE and SQLite INSERT OR REPLACE")
        void parsesAllWriteShapes() {
            assertEquals("lore_discovery",
                    FallbackWriteLog.extractTable("INSERT INTO lore_discovery (a, b) VALUES (?, ?)"));
            assertEquals("lore_entry",
                    FallbackWriteLog.extractTable("UPDATE lore_entry SET name = ? WHERE id = ?"));
            assertEquals("lore_location",
                    FallbackWriteLog.extractTable("DELETE FROM lore_location WHERE entry_id = ?"));
            // AchievementRepository emits this shape on SQLite.
            assertEquals("player_achievement",
                    FallbackWriteLog.extractTable("INSERT OR REPLACE INTO player_achievement (a) VALUES (?)"));
        }

        @Test
        @DisplayName("Handles prefixes, newlines, and a table glued to its column list")
        void handlesRealWorldFormatting() {
            assertEquals("rvnklore_lore_submission", FallbackWriteLog.extractTable(
                    "INSERT INTO rvnklore_lore_submission\n  (entry_id, content)\n  VALUES (?, ?)"));
            // Concatenated SQL sometimes leaves no space before the paren.
            assertEquals("lore_map",
                    FallbackWriteLog.extractTable("INSERT INTO lore_map(a, b) VALUES (?, ?)"));
        }

        @Test
        @DisplayName("Returns null for reads and unparseable statements rather than guessing")
        void returnsNullRatherThanGuessing() {
            // A wrong guess here would misroute the fail-closed gate. The caller treats null as a
            // loud failure, which is the safe direction.
            assertNull(FallbackWriteLog.extractTable("SELECT * FROM lore_entry"));
            assertNull(FallbackWriteLog.extractTable(""));
            assertNull(FallbackWriteLog.extractTable(null));
        }

        @Test
        @DisplayName("A shared-table statement is recognised end to end")
        void gateSeesSharedStatement() {
            // This is the composed path the fallback gate actually walks.
            String sql = "UPDATE rvnklore_lore_entry SET name = ? WHERE id = ?";
            String table = FallbackWriteLog.extractTable(sql);
            assertNotNull(table);
            assertTrue(LoreTableScope.isShared(table),
                    "a shared-table write must be refused during fallback");
        }

        @Test
        @DisplayName("A per-server statement is journalled, not refused")
        void gateAllowsPerServerStatement() {
            String sql = "INSERT INTO rvnklore_lore_discovery (player_uuid, entry_id) VALUES (?, ?)";
            String table = FallbackWriteLog.extractTable(sql);
            assertNotNull(table);
            assertTrue(LoreTableScope.isPerServer(table),
                    "per-server writes must stay available during an outage");
        }
    }
}
