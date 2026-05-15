package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.*;

/**
 * MySQL implementation that reuses RVNKCore's shared ConnectionProvider.
 * No separate HikariCP pool is created — lifecycle is owned by RVNKCore.
 *
 * Uses reflection to get RVNKCore instance to match the rest of RVNKLore's
 * class-loader-safe access pattern (avoids direct static reference to RVNKCore).
 */
public class MySQLConnection extends DatabaseConnection {

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect) {
        super(plugin, dialect);
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Acquiring MySQL ConnectionProvider from RVNKCore...");
        lastConnectionError = null;

        try {
            // Use RVNKCore's classloader for the class lookup so the ServiceRegistry key
            // matches exactly — avoids PlugMan/hot-reload classloader mismatch.
            ClassLoader rvnkCoreLoader = plugin.getServer().getPluginManager()
                .getPlugin("RVNKCore").getClass().getClassLoader();

            Class<?> rvnkCoreClass = rvnkCoreLoader.loadClass("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                throw new SQLException("RVNKCore instance is null — MySQL ConnectionProvider unavailable");
            }
            Boolean initialized = (Boolean) rvnkCoreClass.getMethod("isInitialized").invoke(coreInstance);
            if (!Boolean.TRUE.equals(initialized)) {
                throw new SQLException("RVNKCore is not initialized — MySQL ConnectionProvider unavailable");
            }
            Object registry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (registry == null) {
                throw new SQLException("RVNKCore ServiceRegistry is null");
            }
            Class<?> cpClass = rvnkCoreLoader.loadClass("org.fourz.rvnkcore.database.connection.ConnectionProvider");
            Object provider = registry.getClass()
                .getMethod("getService", Class.class)
                .invoke(registry, cpClass);
            if (provider == null) {
                throw new SQLException("ConnectionProvider not registered in RVNKCore ServiceRegistry");
            }
            rvnkProvider = (ConnectionProvider) provider;
            if (!rvnkProvider.isValid()) {
                rvnkProvider = null;
                throw new SQLException("RVNKCore ConnectionProvider is not valid");
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to acquire RVNKCore ConnectionProvider: " + e.getMessage(), e);
        }
        logger.info("MySQL: reusing RVNKCore shared pool — no new connection created (#920)");
        logger.debug("Using RVNKCore shared MySQL pool");
    }

    @Override
    public void close() {
        // The pool is owned by RVNKCore — do not close it here.
        rvnkProvider = null;
        logger.debug("Released reference to RVNKCore ConnectionProvider");
    }

    @Override
    public boolean reconnect() {
        try {
            initialize();
            return rvnkProvider != null && rvnkProvider.isValid();
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to re-acquire RVNKCore ConnectionProvider", e);
            return false;
        }
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
