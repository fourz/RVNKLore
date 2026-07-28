package org.fourz.RVNKLore.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies each lore table as cluster-shared content or per-server data.
 *
 * <p>This is the single source of the routing decided in the #1803 spec (§3). Two features consume
 * it and must not disagree:</p>
 *
 * <ul>
 *   <li><b>#1833</b> — the fallback write gate. Shared tables are refused writes while the plugin is
 *       running on its SQLite fallback, because an outage here is a <em>connectivity</em> outage: the
 *       authoritative tier keeps writing those same rows throughout, so a local write would diverge
 *       from the canon with no safe way to merge it back.</li>
 *   <li><b>#1834</b> — the connection split. Shared tables are served from the cluster pool,
 *       per-server tables from the local pool.</li>
 * </ul>
 *
 * <p><b>World-bearing tables stay per-server.</b> {@code lore_location}, {@code lore_discovery} and
 * {@code lore_map} all store a world name plus coordinates, which are meaningless across tiers —
 * two servers can each have a world called {@code world}. They stay local until a {@code server_id}
 * discriminator exists.</p>
 *
 * <p>Player progress tables are treated as shared: they carry no FK into {@code rvnk_players} and
 * progress should follow the player across the network. Note this means a reward claimed on one
 * server cannot be re-claimed on another — intended, and called out in #1834.</p>
 */
public final class LoreTableScope {

    /**
     * Cluster-shared content. Refused during fallback (#1833); served from the cluster pool (#1834).
     */
    private static final Set<String> SHARED;

    static {
        Set<String> shared = new HashSet<>();
        // Lore content proper
        shared.add(DatabaseConnection.TABLE_LORE_ENTRY);
        shared.add(DatabaseConnection.TABLE_LORE_SUBMISSION);
        shared.add(DatabaseConnection.TABLE_LORE_ITEM);
        shared.add(DatabaseConnection.TABLE_LORE_METADATA);
        // Collections
        shared.add(DatabaseConnection.TABLE_COLLECTION);
        shared.add(DatabaseConnection.TABLE_COLLECTION_ITEM);
        shared.add(DatabaseConnection.TABLE_COLLECTION_REWARD);
        // Item authoring / RNG
        shared.add(DatabaseConnection.TABLE_QUEST_ITEM_PRESETS);
        shared.add(DatabaseConnection.TABLE_LORE_ITEM_RNG_POOL);
        // Player progress — follows the player across the network
        shared.add(DatabaseConnection.TABLE_PLAYER_COLLECTION_PROGRESS);
        shared.add(DatabaseConnection.TABLE_PLAYER_COLLECTION_ITEMS);
        shared.add(DatabaseConnection.TABLE_PLAYER_ACHIEVEMENT);
        shared.add(DatabaseConnection.TABLE_PLAYER_REWARD_CLAIM);
        SHARED = Collections.unmodifiableSet(shared);
    }

    private LoreTableScope() {
    }

    /**
     * Whether a table holds cluster-shared content.
     *
     * <p>Accepts either a bare table name or a prefixed one (e.g. {@code rvnklore_lore_entry}), since
     * SQL in the repositories is built through {@code DatabaseConnection.table(...)} which applies the
     * configured prefix.</p>
     *
     * @param tableName bare or prefixed table name; null/blank is treated as per-server
     * @return true if the table is cluster-shared
     */
    public static boolean isShared(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        String name = tableName.trim().toLowerCase(Locale.ROOT);
        // Strip backticks/quotes a dialect may have added.
        name = name.replace("`", "").replace("\"", "");
        for (String shared : SHARED) {
            // Exact match, or prefixed form ending in the bare name.
            if (name.equals(shared) || name.endsWith("_" + shared)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a table is per-server and therefore safe to write during fallback and replay on
     * recovery — no other tier writes it, so a replayed row cannot clobber anyone.
     *
     * @param tableName bare or prefixed table name
     * @return true if the table is per-server
     */
    public static boolean isPerServer(String tableName) {
        return !isShared(tableName);
    }

    /** @return the shared table names, for diagnostics. */
    public static Set<String> sharedTables() {
        return SHARED;
    }
}
