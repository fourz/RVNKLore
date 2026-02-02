package org.fourz.RVNKLore.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.io.File;
import java.sql.*;

/**
 * SQLite implementation of database connection using HikariCP connection pooling.
 *
 * <p>Uses the SQLiteDialect for database-specific SQL generation.
 * HikariCP manages connection lifecycle, preventing "connection closed" errors
 * during concurrent async operations.
 */
public class SQLiteConnection extends DatabaseConnection {
    private final File databaseFile;

    public SQLiteConnection(RVNKLore plugin, SQLDialect dialect) {
        super(plugin, dialect);
        this.databaseFile = new File(plugin.getDataFolder(), "lore.db");
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing SQLite connection pool...");
        lastConnectionError = null;

        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load pool configuration from config.yml
        int poolSize = plugin.getConfig().getInt("storage.sqlite.poolSize", 10);
        int connectionTimeout = plugin.getConfig().getInt("storage.sqlite.connectionTimeout", 30000);
        int idleTimeout = plugin.getConfig().getInt("storage.sqlite.idleTimeout", 600000);
        int maxLifetime = plugin.getConfig().getInt("storage.sqlite.maxLifetime", 1800000);
        boolean useWAL = plugin.getConfig().getBoolean("storage.sqlite.optimizations.useWAL", true);
        boolean normalSync = plugin.getConfig().getBoolean("storage.sqlite.optimizations.normalSync", true);

        // Set up HikariCP configuration for SQLite
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(1, poolSize / 2));
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);

        // SQLite optimizations
        if (useWAL) {
            config.addDataSourceProperty("journal_mode", "WAL");
        }
        if (normalSync) {
            config.addDataSourceProperty("synchronous", "NORMAL");
        }
        config.addDataSourceProperty("foreign_keys", "ON");

        // Create the connection pool
        connectionPool = new HikariDataSource(config);

        // Verify connection and set PRAGMAs
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            if (useWAL) {
                stmt.execute("PRAGMA journal_mode = WAL");
            }
            if (normalSync) {
                stmt.execute("PRAGMA synchronous = NORMAL");
            }
        }

        logger.debug("Connected to SQLite database with HikariCP pool (size: " + poolSize + ")");
    }

    @Override
    public String getDatabaseInfo() {
        if (connectionPool == null || connectionPool.isClosed()) {
            return "No active SQLite connection pool";
        }

        try (Connection conn = connectionPool.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            StringBuilder info = new StringBuilder();

            info.append("SQLite: ")
                .append(metaData.getDatabaseProductName())
                .append(" ")
                .append(metaData.getDatabaseProductVersion());

            // Get SQLite pragma info
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    info.append(", Journal Mode: ").append(rs.getString(1));
                }
            } catch (Exception e) {
                // Not critical if this fails
            }

            // Add pool stats
            info.append(", Pool: ")
                .append(connectionPool.getHikariPoolMXBean().getActiveConnections())
                .append("/")
                .append(connectionPool.getHikariPoolMXBean().getTotalConnections())
                .append(" active/total");

            return info.toString();
        } catch (SQLException e) {
            logger.error("Failed to get database info", e);
            return "Error retrieving SQLite info: " + e.getMessage();
        }
    }

    @Override
    public boolean isReadOnly() {
        if (connectionPool == null || connectionPool.isClosed()) {
            return true; // No connection means effectively read-only
        }

        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            // Check if we can write to the database
            stmt.execute("CREATE TABLE IF NOT EXISTS rw_test (id INTEGER)");
            stmt.execute("INSERT INTO rw_test VALUES (1)");
            stmt.execute("DELETE FROM rw_test WHERE id = 1");
            return false; // If we get here, it's not read-only
        } catch (SQLException e) {
            logger.debug("Database appears to be read-only: " + e.getMessage());
            return true;
        }
    }
}
