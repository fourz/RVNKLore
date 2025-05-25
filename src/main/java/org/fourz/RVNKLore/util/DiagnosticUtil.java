package org.fourz.RVNKLore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Utility class for diagnosing plugin health and performance
 */
public class DiagnosticUtil {
    private final RVNKLore plugin;
    private final Debug debug;

    public DiagnosticUtil(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "DiagnosticUtil", Level.FINE);
    }

    /**
     * Run full diagnostic scan and return results
     * @return List of diagnostic messages
     */
    public List<String> runDiagnostics() {
        List<String> results = new ArrayList<>();
        
        results.add("===== RVNKLore Diagnostics =====");
        results.add("Plugin Version: " + plugin.getDescription().getVersion());
        results.add("Server Version: " + Bukkit.getVersion());
        
        // Memory diagnostics
        addMemoryDiagnostics(results);
        
        // Check all handlers
        addHandlerDiagnostics(results);
        
        // Check database connectivity
        addDatabaseDiagnostics(results);
        
        // Check lore entries
        addLoreDiagnostics(results);
        
        // Check plugin dependencies
        addDependencyDiagnostics(results);
        
        return results;
    }
    
    private void addMemoryDiagnostics(List<String> results) {
        results.add("\n----- Memory Usage -----");
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        long maxMemory = heapUsage.getMax() / (1024 * 1024);
        
        results.add("Heap Memory: " + usedMemory + "MB / " + maxMemory + "MB");
        
        Runtime runtime = Runtime.getRuntime();
        results.add("Free Memory: " + (runtime.freeMemory() / (1024 * 1024)) + "MB");
        results.add("Total Memory: " + (runtime.totalMemory() / (1024 * 1024)) + "MB");
        results.add("Available Processors: " + runtime.availableProcessors());
    }
    
    private void addHandlerDiagnostics(List<String> results) {
        results.add("\n----- Handler Status -----");
        HandlerFactory factory = plugin.getHandlerFactory();
        Map<LoreType, LoreHandler> handlers = factory.getAllHandlers();
        
        results.add("Registered Handlers: " + handlers.size());
        
        // Check for missing handlers
        int missingHandlers = 0;
        for (LoreType type : LoreType.values()) {
            if (!handlers.containsKey(type)) {
                results.add("Missing handler for: " + type);
                missingHandlers++;
            }
        }
        
        if (missingHandlers == 0) {
            results.add("All lore types have handlers registered");
        } else {
            results.add("WARNING: " + missingHandlers + " lore types are missing handlers");
        }
    }
    
    /**
     * Adds database diagnostics information to the results list
     */
    private void addDatabaseDiagnostics(List<String> results) {
        results.add("\n----- Database Status -----");
        
        try {
            boolean connected = plugin.getDatabaseManager().isConnected();
            results.add("Database Connection: " + (connected ? "OK" : "FAILED"));
            
            if (connected) {
                // Get database type (SQLite or MySQL)
                String dbType = plugin.getConfigManager().getStorageType();
                results.add("Database Type: " + dbType.toUpperCase());
                
                // Get entry counts
                int totalEntries = plugin.getLoreManager().getAllLoreEntries().size();
                results.add("Total Entries in Cache: " + totalEntries);
                
                // Check database versions/info if available
                try {
                    String dbInfo = plugin.getDatabaseManager().getDatabaseInfo();
                    if (dbInfo != null && !dbInfo.isEmpty()) {
                        results.add("Database Info: " + dbInfo);
                    }
                } catch (Exception e) {
                    // Silently handle this - not critical
                    debug.debug("Could not retrieve database version info: " + e.getMessage());
                }
                
                // Check for database-specific issues
                if (dbType.equalsIgnoreCase("sqlite")) {
                    // Check if SQLite database is in read-only mode
                    try {
                        boolean readOnly = plugin.getDatabaseManager().isReadOnly();
                        if (readOnly) {
                            results.add("WARNING: Database is in READ-ONLY mode");
                        }
                    } catch (Exception e) {
                        debug.debug("Could not check if database is read-only: " + e.getMessage());
                    }
                }
            } else {
                results.add("WARNING: Database connection failed - plugin functionality will be limited");
                
                // Try to get more specific error information
                String lastError = plugin.getDatabaseManager().getLastConnectionError();
                if (lastError != null && !lastError.isEmpty()) {
                    results.add("Last Connection Error: " + lastError);
                }
                
                results.add("Suggestion: Check database configuration in config.yml");
            }
        } catch (Exception e) {
            results.add("ERROR checking database: " + e.getMessage());
            debug.error("Error during database diagnostics", e);
        }
    }
    
    private void addLoreDiagnostics(List<String> results) {
        results.add("\n----- Lore Status -----");
        LoreManager loreManager = plugin.getLoreManager();
        
        int totalEntries = loreManager.getAllLoreEntries().size();
        int approvedEntries = loreManager.getApprovedLoreEntries().size();
        
        results.add("Total Lore Entries: " + totalEntries);
        results.add("Approved Entries: " + approvedEntries);
        
        // Check for invalid entries
        int invalidEntries = 0;
        for (LoreEntry entry : loreManager.getAllLoreEntries()) {
            if (!entry.isValid()) {
                invalidEntries++;
                debug.debug("Invalid lore entry: " + entry.getId() + " - " + entry.getName());
            }
        }
        
        if (invalidEntries > 0) {
            results.add("WARNING: " + invalidEntries + " invalid lore entries detected");
        } else {
            results.add("All lore entries are valid");
        }
        
        // Report entries by type
        results.add("\nEntries by Type:");
        for (LoreType type : LoreType.values()) {
            int count = loreManager.getLoreEntriesByType(type).size();
            if (count > 0) {
                results.add("  " + type + ": " + count);
            }
        }
    }
    
    private void addDependencyDiagnostics(List<String> results) {
        results.add("\n----- Plugin Dependencies -----");
        String[] dependencies = plugin.getDescription().getDepend().toArray(new String[0]);
        
        if (dependencies.length == 0) {
            results.add("No dependencies required");
            return;
        }
        
        for (String dependency : dependencies) {
            Plugin dependencyPlugin = Bukkit.getPluginManager().getPlugin(dependency);
            if (dependencyPlugin != null && dependencyPlugin.isEnabled()) {
                results.add(dependency + ": OK (v" + dependencyPlugin.getDescription().getVersion() + ")");
            } else {
                results.add(dependency + ": MISSING or DISABLED");
            }
        }
    }
    
    /**
     * Log diagnostic results to console
     */
    public void logDiagnostics() {
        List<String> results = runDiagnostics();
        for (String line : results) {
            debug.info(line);
        }
    }
    
    /**
     * Check system health and return true if everything is functioning properly
     * @return true if system checks pass
     */
    public boolean checkSystemHealth() {
        // Check database connection
        boolean databaseOk = plugin.getDatabaseManager().isConnected();
        
        // Check handlers
        HandlerFactory factory = plugin.getHandlerFactory();
        boolean handlersOk = true;
        for (LoreType type : LoreType.values()) {
            try {
                LoreHandler handler = factory.getHandler(type);
                if (handler == null) {
                    handlersOk = false;
                    break;
                }
            } catch (Exception e) {
                debug.error("Error checking handler for type: " + type, e);
                handlersOk = false;
                break;
            }
        }
        
        // Check memory
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        boolean memoryOk = freeMemory > 50; // At least 50MB free
        
        return databaseOk && handlersOk && memoryOk;
    }
}
