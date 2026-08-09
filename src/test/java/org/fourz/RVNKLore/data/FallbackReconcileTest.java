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

    @Nested
    @DisplayName("Cluster routing (#1834)")
    class ClusterRouting {

        @Test
        @DisplayName("Content statements route to the cluster pool")
        void contentRoutesToCluster() {
            // These are the statements a member tier must send to the authoritative database.
            assertTrue(LoreTableScope.isShared(FallbackWriteLog.extractTable(
                    "INSERT INTO rvnklore_lore_entry (id, entry_type, name) VALUES (?, ?, ?)")));
            assertTrue(LoreTableScope.isShared(FallbackWriteLog.extractTable(
                    "UPDATE rvnklore_lore_submission SET content = ? WHERE entry_id = ?")));
            assertTrue(LoreTableScope.isShared(FallbackWriteLog.extractTable(
                    "DELETE FROM rvnklore_collection_reward WHERE id = ?")));
        }

        @Test
        @DisplayName("World-bearing statements stay on the local pool")
        void worldBearingStaysLocal() {
            // Sharing these would merge coordinates from worlds that only share a name.
            assertTrue(LoreTableScope.isPerServer(FallbackWriteLog.extractTable(
                    "INSERT INTO rvnklore_lore_discovery (player_uuid, entry_id) VALUES (?, ?)")));
            assertTrue(LoreTableScope.isPerServer(FallbackWriteLog.extractTable(
                    "DELETE FROM rvnklore_lore_location WHERE entry_id = ?")));
            assertTrue(LoreTableScope.isPerServer(FallbackWriteLog.extractTable(
                    "UPDATE rvnklore_lore_map SET lore_entry_id = NULL WHERE lore_entry_id = ?")));
        }

        @Test
        @DisplayName("The #1839 delete cleanup routes each statement to a different pool")
        void deleteCleanupSplitsAcrossPools() {
            // deleteLoreEntry issues these four in order. The first three are per-server and the
            // last is cluster-shared, which is precisely why routing is per-statement and why the
            // delete is no longer one transaction once the pools differ.
            String[] localFirst = {
                "DELETE FROM rvnklore_lore_location WHERE entry_id = ?",
                "DELETE FROM rvnklore_lore_discovery WHERE entry_id = ?",
                "UPDATE rvnklore_lore_map SET lore_entry_id = NULL WHERE lore_entry_id = ?"
            };
            for (String sql : localFirst) {
                assertTrue(LoreTableScope.isPerServer(FallbackWriteLog.extractTable(sql)),
                        "cleanup must hit the local pool: " + sql);
            }
            assertTrue(LoreTableScope.isShared(FallbackWriteLog.extractTable(
                    "DELETE FROM rvnklore_lore_entry WHERE id = ?")),
                    "the entry itself lives in the cluster pool");
        }

        @Test
        @DisplayName("An unparseable statement routes local, never to the cluster")
        void unparseableRoutesLocal() {
            // Routing an unrecognised statement to the cluster would let an unknown write reach the
            // authoritative canon. Defaulting local keeps a mistake confined to one server.
            assertNull(FallbackWriteLog.extractTable("SELECT 1"));
            assertTrue(LoreTableScope.isPerServer(FallbackWriteLog.extractTable("SELECT 1")));
        }
    }

    @Nested
    @DisplayName("Replay dependency awareness")
    class Dependencies {

        /** Build an entry the way {@link FallbackWriteLog#record} does, binds in column order. */
        private FallbackWriteLog.JournalEntry entry(String sql, Object... values) {
            java.util.List<FallbackWriteLog.Bind> binds = new java.util.ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                binds.add(new FallbackWriteLog.Bind(i + 1, "setString", values[i]));
            }
            return new FallbackWriteLog.JournalEntry(sql, binds,
                    FallbackWriteLog.extractColumns(sql), FallbackWriteLog.extractTable(sql));
        }

        @Test
        @DisplayName("Column lists parse from INSERTs and are ignored for UPDATE/DELETE")
        void columnParsing() {
            assertEquals(java.util.List.of("id", "name", "type"), FallbackWriteLog.extractColumns(
                    "INSERT INTO lore_entry (id, name, type) VALUES (?, ?, ?)"));
            // Prefixed, newline-wrapped, and glued-paren forms all appear in the real SQL.
            assertEquals(java.util.List.of("entry_id", "content"), FallbackWriteLog.extractColumns(
                    "INSERT INTO rvnklore_lore_submission\n  (entry_id, content)\n  VALUES (?, ?)"));
            assertEquals(java.util.List.of("a", "b"),
                    FallbackWriteLog.extractColumns("INSERT INTO lore_map(a, b) VALUES (?, ?)"));
            // An UPDATE cannot bring a row into existence, so it needs no dependency tracking.
            assertTrue(FallbackWriteLog.extractColumns(
                    "UPDATE lore_entry SET name = ? WHERE id = ?").isEmpty());
            assertTrue(FallbackWriteLog.extractColumns(
                    "DELETE FROM lore_entry WHERE id = ?").isEmpty());
        }

        @Test
        @DisplayName("A VALUES tuple is never mistaken for a column list")
        void doesNotParseValuesAsColumns() {
            // Misreading this as columns would misalign every bind index and silently attribute the
            // wrong value to a foreign key — worse than having no dependency tracking at all.
            assertTrue(FallbackWriteLog.extractColumns(
                    "INSERT INTO lore_entry VALUES (?, ?, ?)").isEmpty());
        }

        @Test
        @DisplayName("The schema's foreign keys are declared")
        void foreignKeysDeclared() {
            assertTrue(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_SUBMISSION));
            assertTrue(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_ITEM));
            assertTrue(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_METADATA));
            assertTrue(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_COLLECTION_ITEM));
            // lore_entry is referenced by others but declares no key of its own.
            assertFalse(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_ENTRY));
            assertTrue(LoreTableDependencies.isParent(DatabaseConnection.TABLE_LORE_ENTRY));
            assertTrue(LoreTableDependencies.isParent(DatabaseConnection.TABLE_LORE_ITEM));
            // World-bearing tables had their keys into lore_entry dropped by #1839.
            assertFalse(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_DISCOVERY));
            assertFalse(LoreTableDependencies.hasParents(DatabaseConnection.TABLE_LORE_LOCATION));
        }

        @Test
        @DisplayName("The observed #1840 orphan pair is linked parent-to-child")
        void orphanPairIsLinked() {
            // The exact shape from the #1833 verification: an entry insert and a submission insert
            // for the same entry. The submission must recognise the entry as its parent.
            FallbackWriteLog.JournalEntry parent = entry(
                    "INSERT INTO lore_entry (id, name, type) VALUES (?, ?, ?)",
                    "abc-123", "Some Entry", "CITY");
            FallbackWriteLog.JournalEntry child = entry(
                    "INSERT INTO lore_submission (id, entry_id, content) VALUES (?, ?, ?)",
                    "sub-1", "abc-123", "body");

            String parentKey = parent.parentKey();
            assertNotNull(parentKey, "a lore_entry insert must expose an identity to depend on");
            assertTrue(child.parentRefs().contains(parentKey),
                    "the submission must point at the entry it references");
            assertEquals(parentKey, child.firstRefIn(java.util.Set.of(parentKey)));
        }

        @Test
        @DisplayName("A child of a different parent row is not held back")
        void unrelatedChildNotBlocked() {
            // Blocking on table identity alone would stall every submission behind one bad entry.
            // Matching must be on the row's id, not just its table.
            FallbackWriteLog.JournalEntry failedParent = entry(
                    "INSERT INTO lore_entry (id, name) VALUES (?, ?)", "abc-123", "Entry A");
            FallbackWriteLog.JournalEntry otherChild = entry(
                    "INSERT INTO lore_submission (id, entry_id) VALUES (?, ?)", "sub-2", "zzz-999");

            assertNull(otherChild.firstRefIn(java.util.Set.of(failedParent.parentKey())),
                    "a submission for a different entry must still replay");
        }

        @Test
        @DisplayName("Prefixed and bare table names match each other across the journal")
        void prefixMatching() {
            // A journal written on a prefixed tier must match keys derived from bare DDL names, or
            // dependency tracking silently does nothing on exactly the servers that run clustering.
            FallbackWriteLog.JournalEntry parent = entry(
                    "INSERT INTO rvnklore_lore_entry (id, name) VALUES (?, ?)", "abc-123", "E");
            FallbackWriteLog.JournalEntry child = entry(
                    "INSERT INTO rvnklore_lore_submission (id, entry_id) VALUES (?, ?)", "s", "abc-123");
            assertTrue(child.parentRefs().contains(parent.parentKey()));

            FallbackWriteLog.JournalEntry barePrefixMix = entry(
                    "INSERT INTO lore_submission (id, entry_id) VALUES (?, ?)", "s", "abc-123");
            assertTrue(barePrefixMix.parentRefs().contains(parent.parentKey()),
                    "a bare child must match a prefixed parent");
        }

        @Test
        @DisplayName("Composite membership rows depend on both of their parents")
        void compositeParents() {
            FallbackWriteLog.JournalEntry membership = entry(
                    "INSERT INTO collection_item (collection_id, item_id) VALUES (?, ?)",
                    "coll-1", "item-9");
            java.util.List<String> refs = membership.parentRefs();
            assertEquals(2, refs.size(), "collection_item has two foreign keys");
            assertTrue(refs.stream().anyMatch(r -> r.endsWith("#coll-1")));
            assertTrue(refs.stream().anyMatch(r -> r.endsWith("#item-9")));
        }

        @Test
        @DisplayName("A journal written before this change still gets dependency tracking on reload")
        void legacyJournalEntriesRehydrate() {
            // Older .reconcile-pending files carry no columns/table fields. Treating them as
            // dependency-free would let a restart reintroduce the orphaning this change removes.
            com.google.gson.JsonObject legacy = new com.google.gson.JsonObject();
            legacy.addProperty("sql",
                    "INSERT INTO lore_submission (id, entry_id, content) VALUES (?, ?, ?)");
            legacy.addProperty("attempts", 1);
            com.google.gson.JsonArray binds = new com.google.gson.JsonArray();
            String[] values = {"sub-1", "abc-123", "body"};
            for (int i = 0; i < values.length; i++) {
                com.google.gson.JsonObject b = new com.google.gson.JsonObject();
                b.addProperty("i", i + 1);
                b.addProperty("s", "setString");
                b.addProperty("v", values[i]);
                binds.add(b);
            }
            legacy.add("binds", binds);

            FallbackWriteLog.JournalEntry rehydrated = FallbackWriteLog.JournalEntry.fromJson(legacy);
            assertEquals(1, rehydrated.attempts, "attempt count must survive the reload");
            assertTrue(rehydrated.parentRefs().stream().anyMatch(r -> r.endsWith("#abc-123")),
                    "columns and table must be re-derived from the SQL for a legacy entry");
        }

        @Test
        @DisplayName("Binds recorded out of order still resolve to the right column")
        void outOfOrderBinds() {
            // The recording proxy captures setX calls in whatever order the call site makes them, so
            // position in the list cannot be trusted — only the recorded index can.
            java.util.List<FallbackWriteLog.Bind> binds = new java.util.ArrayList<>();
            binds.add(new FallbackWriteLog.Bind(2, "setString", "abc-123"));
            binds.add(new FallbackWriteLog.Bind(1, "setString", "sub-1"));
            String sql = "INSERT INTO lore_submission (id, entry_id) VALUES (?, ?)";
            FallbackWriteLog.JournalEntry e = new FallbackWriteLog.JournalEntry(sql, binds,
                    FallbackWriteLog.extractColumns(sql), FallbackWriteLog.extractTable(sql));

            assertTrue(e.parentRefs().stream().anyMatch(r -> r.endsWith("#abc-123")),
                    "entry_id must read from bind index 2, not list position");
        }

        @Test
        @DisplayName("A row with no bound foreign key claims no parents")
        void noParentsWhenUnbound() {
            // A NULL foreign key is legitimate on some rows; inventing a dependency on "null" would
            // hold back writes behind a parent that never existed.
            FallbackWriteLog.JournalEntry e = entry(
                    "INSERT INTO lore_submission (id, entry_id) VALUES (?, ?)", "sub-1", null);
            assertTrue(e.parentRefs().isEmpty());
            // And a table with no declared keys never claims one.
            assertTrue(entry("INSERT INTO lore_discovery (player_uuid, world) VALUES (?, ?)",
                    "u", "world").parentRefs().isEmpty());
        }
    }
}
