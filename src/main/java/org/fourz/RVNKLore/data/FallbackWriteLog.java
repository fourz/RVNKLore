package org.fourz.RVNKLore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable journal of writes made while the plugin was running on its SQLite fallback, replayed to
 * the primary database on recovery (#1833).
 *
 * <p><b>Why a statement journal rather than a dirty-row set.</b> Recording {@code (table, primary
 * key)} would need the key value pulled out of each statement's parameters, whose position differs
 * per call site. Recording the statement itself sidesteps that entirely, and it preserves
 * <em>ordering</em> — an INSERT followed by an UPDATE followed by a DELETE must replay in that
 * order, which a set of dirty rows cannot express. Deletes in particular have no representation in a
 * dirty-row model, and they are 17 of RVNKLore's 57 write statements.</p>
 *
 * <p><b>Why this is safe to replay blindly.</b> Only per-server tables are journalled — shared
 * content tables are refused writes during fallback (the #1833 fail-closed decision), because an
 * outage here is a connectivity outage and the authoritative tier keeps writing those rows
 * throughout. Per-server rows have no other writer, so a replayed statement cannot clobber anyone.
 * {@link LoreTableScope} is the single place that classification lives.</p>
 *
 * <p>The journal is written to disk on every append so an outage spanning a restart still
 * reconciles, and entries are cleared only after a confirmed write to the primary.</p>
 */
public class FallbackWriteLog {

    /** Attempts before an entry is quarantined rather than retried forever (#1833 operator call). */
    private static final int MAX_ATTEMPTS = 3;

    private final RVNKLore plugin;
    private final LogManager logger;
    private final File journalFile;
    private final File quarantineFile;
    private final Gson gson = new GsonBuilder().create();

    private final List<JournalEntry> pending = new ArrayList<>();
    private final AtomicBoolean replayInProgress = new AtomicBoolean(false);

    public FallbackWriteLog(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "FallbackWriteLog");
        File dataFolder = plugin.getDataFolder();
        this.journalFile = new File(dataFolder, ".reconcile-pending");
        this.quarantineFile = new File(dataFolder, ".reconcile-failed");
        load();
    }

    // ==================== Recording ====================

    /**
     * Wrap a PreparedStatement so parameter binds are captured as the caller's setter runs.
     *
     * <p>A dynamic proxy is used rather than a hand-written delegate because PreparedStatement has
     * well over a hundred methods; only the {@code setX(int, ...)} family needs intercepting and
     * everything else passes straight through.</p>
     *
     * @param real    the statement actually being executed
     * @param sink    receives each captured bind
     * @return a proxy to hand to the caller's PreparedStatementSetter
     */
    static PreparedStatement recordingProxy(PreparedStatement real, List<Bind> sink) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                sink.add(new Bind((Integer) args[0], name, args[1]));
            } else if ("setNull".equals(name) && args != null && args.length >= 2 && args[0] instanceof Integer) {
                sink.add(new Bind((Integer) args[0], "setNull", args[1]));
            }
            try {
                return method.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                FallbackWriteLog.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                handler);
    }

    /**
     * Journal a write that was applied to the fallback store.
     *
     * <p>No-op for shared tables — those are refused before reaching here — and for statements whose
     * target table cannot be determined, which are logged rather than silently dropped.</p>
     *
     * @param sql   the executed statement
     * @param binds the parameters captured while executing it
     */
    public synchronized void record(String sql, List<Bind> binds) {
        String table = extractTable(sql);
        if (table == null) {
            logger.warning("Fallback write could not be journalled — no table parsed from: "
                    + abbreviate(sql) + ". It will NOT be reconciled on recovery.");
            return;
        }
        if (LoreTableScope.isShared(table)) {
            // Reachable in the normal, non-clustered case: with clustering off nothing is genuinely
            // shared, the gate stays open, and these writes are per-server in effect — so they must
            // be journalled like any other or they would be lost on recovery.
            //
            // If clustering IS on, the gate should have refused this write upstream. Journal it
            // anyway and say so loudly: preserving the data and flagging the bypass is a better
            // failure than dropping it silently, which is the exact loss this class exists to stop.
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isClusterEnabled()) {
                logger.warning("A shared-table write (" + table + ") reached the fallback store while"
                        + " clustering is enabled — the #1833 gate should have refused it. Journalling"
                        + " it so nothing is lost, but replaying it may conflict with the authoritative"
                        + " tier; review before recovery.");
            }
        }
        pending.add(new JournalEntry(sql, binds));
        persist();
    }

    /** @return number of journalled writes awaiting replay. */
    public synchronized int pendingCount() {
        return pending.size();
    }

    /** @return true if there is nothing to reconcile. */
    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    // ==================== Replay ====================

    /**
     * Replay every journalled write onto the recovered primary connection.
     *
     * <p>Must be called while the primary is live and <b>before</b> the fallback connection is
     * closed, so a failure can still leave the journal intact for the next attempt. Entries are
     * removed only after the write succeeds; an entry that fails {@link #MAX_ATTEMPTS} times is
     * quarantined to {@code .reconcile-failed} and dropped from the pending set, so the loop
     * converges instead of warning about the same row forever.</p>
     *
     * @param primary the recovered primary connection
     * @return a short human-readable summary, or null if there was nothing to do
     */
    public String replayTo(Connection primary) {
        List<JournalEntry> snapshot;
        synchronized (this) {
            if (pending.isEmpty()) {
                return null;
            }
            if (!replayInProgress.compareAndSet(false, true)) {
                return null;
            }
            snapshot = new ArrayList<>(pending);
        }
        int replayed = 0;
        int quarantined = 0;
        int stillPending = 0;
        try {
            logger.warning("Primary database recovered — replaying " + snapshot.size()
                    + " outage-era write(s)");
            for (JournalEntry entry : snapshot) {
                try {
                    apply(primary, entry);
                    synchronized (this) {
                        pending.remove(entry);
                    }
                    replayed++;
                } catch (Exception e) {
                    entry.attempts++;
                    if (entry.attempts >= MAX_ATTEMPTS) {
                        quarantine(entry, e);
                        synchronized (this) {
                            pending.remove(entry);
                        }
                        quarantined++;
                    } else {
                        stillPending++;
                        logger.warning("Replay failed (attempt " + entry.attempts + "/" + MAX_ATTEMPTS
                                + ") for " + abbreviate(entry.sql) + ": " + e.getMessage());
                    }
                }
            }
            persist();
        } finally {
            replayInProgress.set(false);
        }

        String summary = "Reconcile: " + replayed + " replayed"
                + (quarantined > 0 ? ", " + quarantined + " quarantined to .reconcile-failed" : "")
                + (stillPending > 0 ? ", " + stillPending + " still pending" : "");
        if (quarantined > 0) {
            logger.error(summary + " — quarantined writes were NOT applied; inspect "
                    + quarantineFile.getName(), null);
        } else if (stillPending > 0) {
            logger.warning(summary);
        } else {
            logger.info(summary);
        }
        return summary;
    }

    private void apply(Connection primary, JournalEntry entry) throws SQLException {
        try (PreparedStatement stmt = primary.prepareStatement(entry.sql)) {
            for (Bind bind : entry.binds) {
                bind.applyTo(stmt);
            }
            stmt.executeUpdate();
        }
    }

    // ==================== Persistence ====================

    private synchronized void persist() {
        try {
            File parent = journalFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create data folder for the reconcile journal");
            }
            if (pending.isEmpty()) {
                if (journalFile.exists() && !journalFile.delete()) {
                    logger.warning("Could not remove the now-empty reconcile journal");
                }
                return;
            }
            JsonArray arr = new JsonArray();
            for (JournalEntry e : pending) {
                arr.add(e.toJson());
            }
            Files.write(journalFile.toPath(), gson.toJson(arr).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to persist the reconcile journal — outage-era writes may be lost "
                    + "if the server restarts before recovery", e);
        }
    }

    private synchronized void load() {
        if (!journalFile.exists()) {
            return;
        }
        try {
            String raw = new String(Files.readAllBytes(journalFile.toPath()), StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                pending.add(JournalEntry.fromJson(arr.get(i).getAsJsonObject()));
            }
            if (!pending.isEmpty()) {
                logger.warning("Loaded " + pending.size() + " un-reconciled write(s) from a previous "
                        + "outage — they will replay when the primary database recovers");
            }
        } catch (Exception e) {
            logger.error("Could not read the reconcile journal; it will be left in place for manual "
                    + "inspection rather than discarded", e);
            pending.clear();
        }
    }

    private synchronized void quarantine(JournalEntry entry, Exception cause) {
        try {
            JsonObject o = entry.toJson();
            o.addProperty("failureReason", String.valueOf(cause.getMessage()));
            String line = gson.toJson(o) + System.lineSeparator();
            Files.write(quarantineFile.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("Could not quarantine an unreconcilable write; it is being dropped: "
                    + abbreviate(entry.sql), e);
        }
    }

    // ==================== Helpers ====================

    /**
     * Pull the target table out of an INSERT/UPDATE/DELETE.
     *
     * <p>Returns null for anything it cannot classify, which the caller treats as a loud failure
     * rather than a silent skip — an unjournalled write is exactly the data loss this exists to
     * prevent.</p>
     */
    static String extractTable(String sql) {
        if (sql == null) {
            return null;
        }
        String s = sql.trim().replaceAll("\\s+", " ");
        String upper = s.toUpperCase(Locale.ROOT);
        int idx;
        if (upper.startsWith("INSERT INTO ")) {
            idx = "INSERT INTO ".length();
        } else if (upper.startsWith("INSERT OR REPLACE INTO ")) {
            idx = "INSERT OR REPLACE INTO ".length();
        } else if (upper.startsWith("UPDATE ")) {
            idx = "UPDATE ".length();
        } else if (upper.startsWith("DELETE FROM ")) {
            idx = "DELETE FROM ".length();
        } else {
            return null;
        }
        String rest = s.substring(idx).trim();
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ' ' || c == '(' || c == ';') {
                end = i;
                break;
            }
        }
        String table = rest.substring(0, end).replace("`", "").replace("\"", "").trim();
        return table.isEmpty() ? null : table;
    }

    private static String abbreviate(String sql) {
        if (sql == null) {
            return "(null)";
        }
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() <= 120 ? one : one.substring(0, 117) + "...";
    }

    // ==================== Model ====================

    /** A single captured parameter bind. */
    static final class Bind {
        final int index;
        final String setter;
        final Object value;

        Bind(int index, String setter, Object value) {
            this.index = index;
            this.setter = setter;
            this.value = value;
        }

        void applyTo(PreparedStatement stmt) throws SQLException {
            switch (setter) {
                case "setNull":
                    stmt.setNull(index, value instanceof Number ? ((Number) value).intValue()
                            : java.sql.Types.NULL);
                    break;
                case "setInt":
                    stmt.setInt(index, ((Number) value).intValue());
                    break;
                case "setLong":
                    stmt.setLong(index, ((Number) value).longValue());
                    break;
                case "setDouble":
                    stmt.setDouble(index, ((Number) value).doubleValue());
                    break;
                case "setBoolean":
                    stmt.setBoolean(index, Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "setTimestamp":
                    stmt.setTimestamp(index, new java.sql.Timestamp(((Number) value).longValue()));
                    break;
                default:
                    // setString and anything else round-trips as text; the column type drives coercion.
                    if (value == null) {
                        stmt.setNull(index, java.sql.Types.VARCHAR);
                    } else {
                        stmt.setString(index, String.valueOf(value));
                    }
            }
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("i", index);
            o.addProperty("s", setter);
            if (value == null) {
                o.add("v", null);
            } else if (value instanceof java.sql.Timestamp) {
                o.addProperty("s2", "setTimestamp");
                o.addProperty("v", ((java.sql.Timestamp) value).getTime());
            } else if (value instanceof Number) {
                o.addProperty("v", (Number) value);
            } else if (value instanceof Boolean) {
                o.addProperty("v", (Boolean) value);
            } else {
                o.addProperty("v", String.valueOf(value));
            }
            return o;
        }

        static Bind fromJson(JsonObject o) {
            int i = o.get("i").getAsInt();
            String s = o.has("s2") ? o.get("s2").getAsString() : o.get("s").getAsString();
            Object v = null;
            if (o.has("v") && !o.get("v").isJsonNull()) {
                com.google.gson.JsonPrimitive p = o.get("v").getAsJsonPrimitive();
                if (p.isNumber()) {
                    v = p.getAsNumber();
                } else if (p.isBoolean()) {
                    v = p.getAsBoolean();
                } else {
                    v = p.getAsString();
                }
            }
            return new Bind(i, s, v);
        }
    }

    /** One journalled statement plus its captured binds. */
    static final class JournalEntry {
        final String sql;
        final List<Bind> binds;
        int attempts;

        JournalEntry(String sql, List<Bind> binds) {
            this.sql = sql;
            this.binds = binds;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("sql", sql);
            o.addProperty("attempts", attempts);
            JsonArray arr = new JsonArray();
            for (Bind b : binds) {
                arr.add(b.toJson());
            }
            o.add("binds", arr);
            return o;
        }

        static JournalEntry fromJson(JsonObject o) {
            List<Bind> binds = new ArrayList<>();
            JsonArray arr = o.getAsJsonArray("binds");
            for (int i = 0; i < arr.size(); i++) {
                binds.add(Bind.fromJson(arr.get(i).getAsJsonObject()));
            }
            JournalEntry e = new JournalEntry(o.get("sql").getAsString(), binds);
            e.attempts = o.has("attempts") ? o.get("attempts").getAsInt() : 0;
            return e;
        }
    }
}
