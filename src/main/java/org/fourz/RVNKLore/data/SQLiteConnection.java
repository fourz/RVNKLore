package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.io.File;
import java.sql.*;

/**
 * SQLite implementation of database connection using RVNKCore's ConnectionProvider.
 *
 * <p>Uses the SQLiteDialect for database-specific SQL generation.
 * RVNKCore manages connection pooling and lifecycle.
 */
public class SQLiteConnection extends DatabaseConnection {
    private final SQLiteSettingsDTO settings;

    public SQLiteConnection(RVNKLore plugin, SQLDialect dialect, SQLiteSettingsDTO settings) {
        super(plugin, dialect);
        this.settings = settings;
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing SQLite connection via RVNKCore ConnectionProviderFactory...");
        lastConnectionError = null;

        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // DatabaseConfig.sqlite() resolves the filename relative to plugin.getDataFolder()
        String filename = new File(settings.getFilePath()).getName();
        DatabaseConfig config = DatabaseConfig.sqlite(filename);

        rvnkProvider = new ConnectionProviderFactory(plugin).createConnectionProvider(config);

        // Apply SQLite PRAGMAs after pool is established
        boolean useWAL = plugin.getConfig().getBoolean("storage.sqlite.optimizations.useWAL", true);
        boolean normalSync = plugin.getConfig().getBoolean("storage.sqlite.optimizations.normalSync", true);

        try (Connection conn = rvnkProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            if (useWAL) {
                stmt.execute("PRAGMA journal_mode = WAL");
            }
            if (normalSync) {
                stmt.execute("PRAGMA synchronous = NORMAL");
            }
        }

        logger.debug("Connected to SQLite database via RVNKCore (" + filename + ")");
    }

    @Override
    public String getDatabaseInfo() {
        if (rvnkProvider == null || !rvnkProvider.isValid()) {
            return "No active SQLite connection";
        }

        try (Connection conn = rvnkProvider.getConnection()) {
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

            return info.toString();
        } catch (SQLException e) {
            logger.error("Failed to get database info", e);
            return "Error retrieving SQLite info: " + e.getMessage();
        }
    }

    @Override
    public boolean isReadOnly() {
        if (rvnkProvider == null || !rvnkProvider.isValid()) {
            return true;
        }

        try (Connection conn = rvnkProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            // Check if we can write to the database
            stmt.execute("CREATE TABLE IF NOT EXISTS rw_test (id INTEGER)");
            stmt.execute("INSERT INTO rw_test VALUES (1)");
            stmt.execute("DELETE FROM rw_test WHERE id = 1");
            return false;
        } catch (SQLException e) {
            logger.debug("Database appears to be read-only: " + e.getMessage());
            return true;
        }
    }

    @Override
    public String getDatabaseType() {
        return "sqlite";
    }
}
