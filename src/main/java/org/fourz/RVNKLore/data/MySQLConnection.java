package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.*;

/**
 * MySQL implementation of database connection using RVNKCore's ConnectionProvider.
 *
 * <p>Uses the MySQLDialect for database-specific SQL generation.
 * RVNKCore manages connection pooling and lifecycle.
 */
public class MySQLConnection extends DatabaseConnection {
    private final MySQLSettingsDTO settings;
    private final int poolSize;
    private final int connectionTimeout;
    private final int idleTimeout;
    private final int maxLifetime;

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect, MySQLSettingsDTO settings) {
        super(plugin, dialect);
        this.settings = settings;
        this.poolSize = plugin.getConfig().getInt("storage.mysql.poolSize", 10);
        this.connectionTimeout = plugin.getConfig().getInt("storage.mysql.connectionTimeout", 30000);
        this.idleTimeout = plugin.getConfig().getInt("storage.mysql.idleTimeout", 600000);
        this.maxLifetime = plugin.getConfig().getInt("storage.mysql.maxLifetime", 1800000);
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing MySQL connection via RVNKCore ConnectionProviderFactory...");
        lastConnectionError = null;

        DatabaseConfig config = DatabaseConfig.builder()
                .type("mysql")
                .host(settings.getHost())
                .port(settings.getPort())
                .database(settings.getDatabase())
                .username(settings.getUsername())
                .password(settings.getPassword())
                .useSSL(settings.isUseSSL())
                .maxConnections(poolSize)
                .minIdleConnections(Math.max(2, poolSize / 2))
                .connectionTimeoutMs(connectionTimeout)
                .idleTimeoutMs((long) idleTimeout)
                .maxLifetimeMs((long) maxLifetime)
                .build();

        rvnkProvider = new ConnectionProviderFactory(plugin).createConnectionProvider(config);
        logger.debug("Connected to MySQL database via RVNKCore (pool size: " + poolSize + ")");
    }

    @Override
    public String getDatabaseInfo() {
        if (rvnkProvider == null || !rvnkProvider.isValid()) {
            return "No active MySQL connection";
        }

        try (Connection conn = rvnkProvider.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            StringBuilder info = new StringBuilder();

            info.append("MySQL: ")
                .append(metaData.getDatabaseProductName())
                .append(" ")
                .append(metaData.getDatabaseProductVersion());

            // Check server variables for additional info
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'version%'")) {
                while (rs.next()) {
                    info.append(", ").append(rs.getString(1)).append(": ").append(rs.getString(2));
                }
            } catch (Exception e) {
                // Not critical if this fails
            }

            return info.toString();
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
            logger.error("Error checking if database is read-only", e);
            return true;
        }
    }

    @Override
    public String getDatabaseType() {
        return "mysql";
    }
}
