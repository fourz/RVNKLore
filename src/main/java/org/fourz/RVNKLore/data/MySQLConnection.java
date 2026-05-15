package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.*;

/**
 * MySQL implementation that reuses RVNKCore's shared ConnectionProvider.
 * No separate HikariCP pool is created — lifecycle is owned by RVNKCore.
 */
public class MySQLConnection extends DatabaseConnection {

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect) {
        super(plugin, dialect);
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Acquiring MySQL ConnectionProvider from RVNKCore...");
        lastConnectionError = null;

        RVNKCore core = RVNKCore.getInstance();
        if (core == null || !core.isInitialized()) {
            throw new SQLException("RVNKCore is not initialized — MySQL ConnectionProvider unavailable");
        }
        ConnectionProvider provider = core.getService(ConnectionProvider.class);
        if (provider == null || !provider.isValid()) {
            throw new SQLException("RVNKCore ConnectionProvider is null or invalid");
        }
        rvnkProvider = provider;
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
