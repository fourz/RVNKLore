package org.fourz.RVNKLore.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parent/child relationships between lore tables, as declared by the schema's foreign keys.
 *
 * <p>Used by {@link FallbackWriteLog} to replay outage-era writes in dependency order rather than
 * merely in recorded order (#1833). Recorded order alone is necessary but not sufficient: a child
 * row can commit successfully while its parent is still failing, and if the parent is ultimately
 * quarantined the child is left orphaned, pointing at a row that will never exist.</p>
 *
 * <p><b>Observed, not theoretical.</b> During the #1833 verification a {@code lore_entry} insert and
 * a {@code lore_submission} insert for the same entry were both journalled. The entry insert failed
 * on a duplicate key and was correctly quarantined after three attempts — but the submission had
 * already succeeded on the first attempt, leaving two orphaned submissions that had to be cleaned up
 * by hand (#1840).</p>
 *
 * <p><b>Why holding back beats propagating at quarantine time.</b> {@code MAX_ATTEMPTS} is counted
 * across recovery events, not within one replay pass — each pass attempts a given entry once. So by
 * the time a parent quarantines on pass three, its dependents committed on pass one. Cascading a
 * quarantine would arrive two passes too late. Dependents must therefore be held back as soon as a
 * parent <em>fails</em>, and only quarantined alongside it once it is abandoned.</p>
 *
 * <p>Mirrors the {@code FOREIGN KEY} clauses in {@link DatabaseConnection}'s DDL. Every relationship
 * here is between two cluster-shared tables — per-server tables had their keys into
 * {@code lore_entry} dropped by #1839, because a foreign key cannot span databases. Keep this in step
 * with the DDL: a relationship missing here is an orphan this class will not prevent.</p>
 */
public final class LoreTableDependencies {

    /** A foreign key from some child column into a parent table's {@code id}. */
    public static final class Dep {
        /** Column on the child table holding the parent's id. */
        public final String column;
        /** Bare name of the referenced parent table. */
        public final String parentTable;

        Dep(String column, String parentTable) {
            this.column = column;
            this.parentTable = parentTable;
        }

        @Override
        public String toString() {
            return column + " -> " + parentTable + "(id)";
        }
    }

    /** Bare child table name to the foreign keys it declares. */
    private static final Map<String, List<Dep>> DEPENDENCIES;

    static {
        Map<String, List<Dep>> m = new HashMap<>();

        // -> lore_entry
        put(m, DatabaseConnection.TABLE_LORE_SUBMISSION,
                new Dep("entry_id", DatabaseConnection.TABLE_LORE_ENTRY));
        put(m, DatabaseConnection.TABLE_LORE_ITEM,
                new Dep("lore_entry_id", DatabaseConnection.TABLE_LORE_ENTRY));
        put(m, DatabaseConnection.TABLE_LORE_METADATA,
                new Dep("lore_id", DatabaseConnection.TABLE_LORE_ENTRY));

        // -> lore_item
        put(m, DatabaseConnection.TABLE_QUEST_ITEM_PRESETS,
                new Dep("lore_item_id", DatabaseConnection.TABLE_LORE_ITEM));
        put(m, DatabaseConnection.TABLE_LORE_ITEM_RNG_POOL,
                new Dep("lore_item_id", DatabaseConnection.TABLE_LORE_ITEM));

        // -> collection + lore_item (composite membership rows)
        put(m, DatabaseConnection.TABLE_COLLECTION_ITEM,
                new Dep("collection_id", DatabaseConnection.TABLE_COLLECTION),
                new Dep("item_id", DatabaseConnection.TABLE_LORE_ITEM));
        put(m, DatabaseConnection.TABLE_PLAYER_COLLECTION_ITEMS,
                new Dep("collection_id", DatabaseConnection.TABLE_COLLECTION),
                new Dep("item_id", DatabaseConnection.TABLE_LORE_ITEM));

        // -> collection_reward
        put(m, DatabaseConnection.TABLE_PLAYER_REWARD_CLAIM,
                new Dep("reward_id", DatabaseConnection.TABLE_COLLECTION_REWARD));

        DEPENDENCIES = Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, List<Dep>> m, String child, Dep... deps) {
        List<Dep> list = new ArrayList<>();
        Collections.addAll(list, deps);
        m.put(bareName(child), Collections.unmodifiableList(list));
    }

    private LoreTableDependencies() {
    }

    /**
     * Foreign keys declared by a table.
     *
     * @param tableName bare or prefixed table name (e.g. {@code rvnklore_lore_submission})
     * @return the table's foreign keys, empty if it has none
     */
    public static List<Dep> parentsOf(String tableName) {
        String bare = matchBare(tableName);
        if (bare == null) {
            return Collections.emptyList();
        }
        List<Dep> deps = DEPENDENCIES.get(bare);
        return deps == null ? Collections.<Dep>emptyList() : deps;
    }

    /** @return true if the table has at least one foreign key into another lore table. */
    public static boolean hasParents(String tableName) {
        return !parentsOf(tableName).isEmpty();
    }

    /**
     * Whether a table is referenced as a parent by any other table — i.e. whether an orphan is
     * possible if its rows fail to land.
     *
     * @param tableName bare or prefixed table name
     * @return true if some table declares a foreign key into this one
     */
    public static boolean isParent(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        // Deliberately not routed through matchBare: that only resolves tables which are *keys* of
        // this map — i.e. tables that declare a foreign key. The most important parent of all,
        // lore_entry, declares none, so resolving parents that way reports it as not-a-parent and
        // silently disables dependency tracking for the exact case #1840 hit.
        String name = bareName(tableName);
        for (List<Dep> deps : DEPENDENCIES.values()) {
            for (Dep d : deps) {
                String parent = bareName(d.parentTable);
                if (name.equals(parent) || name.endsWith("_" + parent)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resolve a possibly-prefixed, possibly-quoted table name to the bare name this class keys on.
     *
     * <p>Repository SQL is built through {@code DatabaseConnection.table(...)}, which applies the
     * configured prefix — so {@code rvnklore_lore_entry} and {@code lore_entry} must resolve alike.</p>
     *
     * @param tableName bare or prefixed name
     * @return the matching bare name, or null if it is not a known lore table in this map
     */
    private static String matchBare(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String name = bareName(tableName);
        if (DEPENDENCIES.containsKey(name)) {
            return name;
        }
        // Prefixed form: match the longest known bare name it ends with, so that a table whose name
        // is a suffix of another cannot shadow it.
        String best = null;
        for (String known : DEPENDENCIES.keySet()) {
            if (name.endsWith("_" + known) && (best == null || known.length() > best.length())) {
                best = known;
            }
        }
        return best;
    }

    /**
     * Normalise a table name for comparison — lowercase, no backticks or quotes.
     *
     * <p>Package-private so {@link FallbackWriteLog} can key its parent/child sets the same way, and
     * a prefixed name recorded in the journal still matches a bare one in the map.</p>
     */
    static String bareName(String tableName) {
        if (tableName == null) {
            return "";
        }
        return tableName.trim().toLowerCase(Locale.ROOT).replace("`", "").replace("\"", "");
    }

    /**
     * Resolve a prefixed name down to its known bare form when possible, for use as a map key.
     *
     * @param tableName bare or prefixed name
     * @return the known bare name, or the normalised input when the table is not in this map
     */
    static String canonical(String tableName) {
        String bare = matchBare(tableName);
        return bare != null ? bare : bareName(tableName);
    }

    /**
     * Resolve a parent-table name to a canonical key, matching whatever form the journal recorded.
     *
     * <p>Parent tables are not necessarily keys of {@link #DEPENDENCIES} — {@code lore_entry} has no
     * foreign keys of its own — so {@link #matchBare} cannot resolve them. Strip a known prefix by
     * comparing against the parent names actually declared here.</p>
     *
     * @param tableName bare or prefixed name
     * @return canonical key for parent/child matching
     */
    static String canonicalParent(String tableName) {
        String name = bareName(tableName);
        String best = null;
        for (List<Dep> deps : DEPENDENCIES.values()) {
            for (Dep d : deps) {
                String parent = bareName(d.parentTable);
                if (name.equals(parent)) {
                    return parent;
                }
                if (name.endsWith("_" + parent) && (best == null || parent.length() > best.length())) {
                    best = parent;
                }
            }
        }
        return best != null ? best : name;
    }

    /** @return the full child-to-foreign-key map, for diagnostics. */
    public static Map<String, List<Dep>> all() {
        return DEPENDENCIES;
    }
}
