package org.fourz.RVNKLore.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.util.Debug;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Service for handling database backup and export operations
 */
public class DatabaseBackupService {
    private final RVNKLore plugin;
    private final Debug debug;
    private final DatabaseConnection connection;
    private final String storageType;
    
    public DatabaseBackupService(RVNKLore plugin, DatabaseConnection connection) {
        this.plugin = plugin;
        this.connection = connection;
        this.debug = Debug.createDebugger(plugin, "DatabaseBackupService", Level.FINE);
        this.storageType = plugin.getConfigManager().getStorageType();
    }
    
    /**
     * Execute database backup
     * 
     * @param backupPath the path where to store the backup
     * @return true if successful, false otherwise
     */
    public boolean backupDatabase(String backupPath) {
        debug.debug("Backing up database to: " + backupPath);
        
        if (storageType.equalsIgnoreCase("sqlite")) {
            return backupSQLiteDatabase(backupPath);
        } else {
            return backupMySQLDatabase(backupPath);
        }
    }
    
    /**
     * Backup SQLite database by copying the file
     */
    private boolean backupSQLiteDatabase(String backupPath) {
        try {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/lore.db";
            File sourceFile = new File(dbPath);
            File destFile = new File(backupPath);
            
            // Ensure parent directory exists
            destFile.getParentFile().mkdirs();
            
            // Copy the file
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            debug.info("SQLite database backup created at: " + backupPath);
            return true;
        } catch (Exception e) {
            debug.error("Failed to backup SQLite database", e);
            return false;
        }
    }
    
    /**
     * Backup MySQL database by exporting data to JSON
     */
    private boolean backupMySQLDatabase(String backupPath) {
        try {
            // Create JSON backup using the repository
            LoreEntryRepository repository = new LoreEntryRepository(plugin, connection);
            List<LoreEntry> allEntries = repository.getAllLoreEntries();
            
            return exportLoreEntriesToFile(allEntries, backupPath);
        } catch (Exception e) {
            debug.error("Failed to backup MySQL database", e);
            return false;
        }
    }
    
    /**
     * Export lore entries to a file
     */
    private boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        try {
            debug.debug("Exporting " + entries.size() + " lore entries to file: " + filePath);
            
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            List<JSONObject> jsonEntries = new ArrayList<>();
            for (LoreEntry entry : entries) {
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
            
            debug.info("Exported " + entries.size() + " lore entries to " + filePath);
            return true;
        } catch (Exception e) {
            debug.error("Failed to export lore entries to file", e);
            return false;
        }
    }
}
