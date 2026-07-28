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

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect) {
        super(plugin, dialect);
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing MySQL connection...");
        lastConnectionError = null;

        MySQLSettingsDTO mysql = plugin.getConfigManager().getDatabaseSettings().getMysqlSettings();
        if (mysql == null) {
            throw new SQLException("MySQL settings not configured in storage.mysql.*");
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
            .minIdleConnections(0)
            .connectionTimeoutMs(30000L)
            .idleTimeoutMs(120000L)
            .maxLifetimeMs(180000L)
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
            logger.debug("MySQL reconnect requested but pool is valid (Hikari self-healed) — skipping teardown");
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
