package org.fourz.RVNKLore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnectionProvider;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling database backup and export operations
 */
public class DatabaseBackupService {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public DatabaseBackupService(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseBackupService");
        this.databaseManager = databaseManager;
    }

    /**
     * Execute database backup (async)
     * @param backupPath the path where to store the backup
     * @return CompletableFuture<Boolean> true if successful, false otherwise
     */
    public CompletableFuture<Boolean> backupDatabase(String backupPath) {
        String storageType = plugin.getConfigManager().getStorageType();
        if ("sqlite".equalsIgnoreCase(storageType)) {
            return backupSQLiteDatabase(backupPath);
        } else {
            return backupMySQLDatabase(backupPath);
        }
    }

    /**
     * Backup SQLite database by copying the file (async)
     */
    private CompletableFuture<Boolean> backupSQLiteDatabase(String backupPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConnectionProvider provider = databaseManager.getConnectionProvider();
                if (!(provider instanceof SQLiteConnectionProvider)) {
                    logger.error("ConnectionProvider is not SQLiteConnectionProvider", null);
                    return false;
                }
                File sourceFile = ((SQLiteConnectionProvider) provider).getDatabaseFile();
                File destFile = new File(backupPath);
                destFile.getParentFile().mkdirs();
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("SQLite database backup created at: " + backupPath);
                return true;
            } catch (Exception e) {
                logger.error("Failed to backup SQLite database", e);
                return false;
            }
        });
    }

    /**
     * Backup MySQL database by exporting data to JSON (async)
     */
    private CompletableFuture<Boolean> backupMySQLDatabase(String backupPath) {
        LoreEntryRepository repository = new LoreEntryRepository(plugin, databaseManager);
        return repository.getAllLoreEntries()
            .thenCompose(entries -> exportLoreEntriesToFile(entries, backupPath))
            .exceptionally(e -> {
                logger.error("Failed to backup MySQL database", e);
                return false;
            });
    }

    /**
     * Export lore entries to a file (async)
     */
    private CompletableFuture<Boolean> exportLoreEntriesToFile(List<LoreEntryDTO> entries, String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Exporting " + entries.size() + " lore entries to file: " + filePath);
                File file = new File(filePath);
                file.getParentFile().mkdirs();

                List<JSONObject> jsonEntries = new ArrayList<>();
                for (LoreEntryDTO entry : entries) {
                    jsonEntries.add(entry.toJson());
                }

                JSONObject result = new JSONObject();
                result.put("lore_entries", jsonEntries);
                result.put("exported_at", new Date().toString());
                result.put("entry_count", entries.size());

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonContent = gson.toJson(result);

                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(jsonContent);
                }

                logger.info("Exported " + entries.size() + " lore entries to " + filePath);
                return true;
            } catch (Exception e) {
                logger.error("Failed to export lore entries to file", e);
                return false;
            }
        });
    }
}
