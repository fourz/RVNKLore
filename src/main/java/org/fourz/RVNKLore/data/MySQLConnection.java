package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.*;

/**
 * MySQL implementation — reads credentials from RVNKLore's own storage.mysql.*
 * config and creates its own HikariCP pool via ConnectionProviderFactory.
 *
 * Follows the BarterShops pattern: explicit DatabaseConfig built from plugin's
 * own ConfigManager, passed directly to ConnectionProviderFactory.
 */
public class MySQLConnection extends DatabaseConnection {

    /**
     * Cross-host safety ceilings (#1822, following #1817). The database sits on another host and
     * network gear silently drops idle TCP with no FIN, so a connection must be retired before the
     * network kills it. Config may ask for <em>lower</em> values, never higher.
     */
    private static final long MAX_SAFE_IDLE_TIMEOUT_MS = 120000L;
    private static final long MAX_SAFE_MAX_LIFETIME_MS = 180000L;

    /** Config path this connection reads its credentials and pool settings from. */
    private final String configPath;

    /** Explicit settings, used for the cluster pool; null means read the primary storage block. */
    private final MySQLSettingsDTO settingsOverride;

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect) {
        this(plugin, dialect, null, "storage.mysql");
    }

    /**
     * Create a connection against an explicit MySQL target.
     *
     * <p>Used for the cluster pool (#1834), which reads {@code cluster.mysql.*} rather than
     * {@code storage.mysql.*}. Everything else — the cross-host pool ceilings, the provider
     * plumbing — is deliberately shared, so the cluster pool inherits the same #1822 protections
     * as the primary rather than quietly getting different ones.</p>
     *
     * @param settingsOverride explicit target; null falls back to the configured storage block
     * @param configPath       config path the pool timeouts are read from
     */
    public MySQLConnection(RVNKLore plugin, SQLDialect dialect,
                           MySQLSettingsDTO settingsOverride, String configPath) {
        super(plugin, dialect);
        this.settingsOverride = settingsOverride;
        this.configPath = configPath;
    }

    /**
     * Resolve a pool timeout from config, clamped to the cross-host ceiling.
     *
     * <p>Before #1837 these values were hardcoded and {@code storage.mysql.idleTimeout} /
     * {@code maxLifetime} were never parsed at all — the live configs declared 600000/1800000 while
     * the pool quietly ran at 120000/180000. The numbers happened to be the safe ones, but an
     * operator reading the config saw something that was not true, and tuning them during an
     * incident would have done nothing. Reading then clamping makes the config honest without
     * giving up the guarantee.</p>
     *
     * @param key      config key under {@code storage.mysql}
     * @param ceiling  the highest value permitted regardless of config
     * @param fallback value when the key is absent
     * @return the effective value
     */
    private long resolvePoolTimeout(String key, long ceiling, long fallback) {
        long requested = plugin.getConfig().getLong(configPath + "." + key, fallback);
        if (requested > ceiling) {
            logger.warning("Capping pool " + key + " for cross-host safety: " + requested + " -> "
                    + ceiling + "ms (config exceeded the safe ceiling)");
            return ceiling;
        }
        return requested;
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing MySQL connection...");
        lastConnectionError = null;

        MySQLSettingsDTO mysql = settingsOverride != null
            ? settingsOverride
            : plugin.getConfigManager().getDatabaseSettings().getMysqlSettings();
        if (mysql == null) {
            throw new SQLException("MySQL settings not configured in " + configPath + ".*");
        }

        DatabaseConfig config = DatabaseConfig.builder()
            .type("mysql")
            .host(mysql.getHost())
            .port(mysql.getPort())
            .database(mysql.getDatabase())
            .username(mysql.getUsername())
            .password(mysql.getPassword())
            .useSSL(mysql.isUseSSL())
            .maxConnections(mysql.getPoolSize())
            // Cross-host MySQL (#1822, following #1817): the DB is on a different host and network
            // gear silently drops idle TCP with no FIN. minIdle>0 holds connections that then go
            // stale ("No operations allowed after connection closed"), and a long idleTimeout lets
            // them sit past the network's drop window. Hold none idle, and retire/replace before the
            // network kills them. These match RVNKCore/RVNKWorlds' proven-stable values.
            //
            // #1837: read from config and clamp, rather than hardcode. The keys were previously
            // declared in every live config.yml and silently ignored.
            .minIdleConnections(0)
            .connectionTimeoutMs(plugin.getConfig().getLong(configPath + ".connectionTimeout", 30000L))
            .idleTimeoutMs(resolvePoolTimeout("idleTimeout", MAX_SAFE_IDLE_TIMEOUT_MS, MAX_SAFE_IDLE_TIMEOUT_MS))
            .maxLifetimeMs(resolvePoolTimeout("maxLifetime", MAX_SAFE_MAX_LIFETIME_MS, MAX_SAFE_MAX_LIFETIME_MS))
            .build();

        try {
            rvnkProvider = new ConnectionProviderFactory(plugin).createConnectionProvider(config);
        } catch (Exception e) {
            throw new SQLException("Failed to create MySQL ConnectionProvider: " + e.getMessage(), e);
        }

        if (!rvnkProvider.isValid()) {
            rvnkProvider = null;
            throw new SQLException("MySQL ConnectionProvider not valid after initialization");
        }
        logger.info("MySQL: connected to " + mysql.getHost() + ":" + mysql.getPort() + "/" + mysql.getDatabase());
    }

    @Override
    public void close() {
        if (rvnkProvider != null) {
            rvnkProvider.close();
            rvnkProvider = null;
        }
        logger.debug("MySQL connection pool closed");
    }

    @Override
    public boolean reconnect() {
        // With socketTimeout now applied at the provider level (#1629 P4), HikariCP evicts a dropped
        // cross-host connection and self-heals without a full pool teardown. Recreating the pool
        // orphans any in-flight borrows and takes RVNKLore's DB offline for the reconnect window, so
        // only do it when the pool is genuinely unusable. This reconnect previously cycled the whole
        // pool on a single transient validation failure (#1629).
        if (rvnkProvider != null && rvnkProvider.isValid()) {
            logger.debug("MySQL reconnect requested but pool is valid (Hikari self-healed) - skipping teardown");
            return true;
        }
        try {
            if (rvnkProvider != null) {
                rvnkProvider.close();
                rvnkProvider = null;
            }
            initialize();
            return rvnkProvider != null && rvnkProvider.isValid();
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to reconnect MySQL", e);
            return false;
        }
    }

    @Override
    public String getDatabaseInfo() {
        if (rvnkProvider == null || !rvnkProvider.isValid()) {
            return "No active MySQL connection";
        }
        try (Connection conn = rvnkProvider.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return "MySQL: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            logger.error("Failed to get database info", e);
            return "Error retrieving MySQL info: " + e.getMessage();
        }
    }

    @Override
    public boolean isReadOnly() {
        if (rvnkProvider == null || !rvnkProvider.isValid()) {
            return true;
        }
        try (Connection conn = rvnkProvider.getConnection()) {
            return conn.isReadOnly();
        } catch (SQLException e) {
            logger.error("Error checking read-only state", e);
            return true;
        }
    }

    @Override
    public String getDatabaseType() {
        return "mysql";
    }
}
