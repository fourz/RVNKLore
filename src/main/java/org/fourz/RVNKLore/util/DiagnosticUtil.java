package org.fourz.RVNKLore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Utility class for diagnosing plugin health and performance
 */
public class DiagnosticUtil {
    private final RVNKLore plugin;
    private final LogManager logger;

    public DiagnosticUtil(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DiagnosticUtil");
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
        addMemoryDiagnostics(results);
        addHandlerDiagnostics(results);
        addDatabaseDiagnostics(results);
        addLoreDiagnosticsAsync(results);
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
                String dbType = plugin.getConfigManager().getStorageType();
                results.add("Database Type: " + dbType.toUpperCase());
                try {
                    String dbInfo = plugin.getDatabaseManager().getDatabaseInfo();
                    if (dbInfo != null && !dbInfo.isEmpty()) {
                        results.add("Database Info: " + dbInfo);
                    }
                } catch (Exception e) {
                    logger.debug("Could not retrieve database version info: " + e.getMessage());
                }
                if (dbType.equalsIgnoreCase("sqlite")) {
                    try {
                        boolean readOnly = plugin.getDatabaseManager().isReadOnly();
                        if (readOnly) {
                            results.add("WARNING: Database is in READ-ONLY mode");
                        }
                    } catch (Exception e) {
                        logger.debug("Could not check if database is read-only: " + e.getMessage());
                    }
                }
            } else {
                results.add("WARNING: Database connection failed - plugin functionality will be limited");
                String lastError = plugin.getDatabaseManager().getLastConnectionError();
                if (lastError != null && !lastError.isEmpty()) {
                    results.add("Last Connection Error: " + lastError);
                }
                results.add("Suggestion: Check database configuration in config.yml");
            }
        } catch (Exception e) {
            results.add("ERROR checking database: " + e.getMessage());
            logger.error("Error during database diagnostics", e);
        }
    }

    /**
     * Adds lore diagnostics using async DatabaseManager methods and DTOs
     */
    private void addLoreDiagnosticsAsync(List<String> results) {
        results.add("\n----- Lore Status -----");
        try {
            List<LoreEntryDTO> allEntries = plugin.getDatabaseManager().getAllLoreEntries().get();
            long approvedEntries = allEntries.stream().filter(LoreEntryDTO::isApproved).count();
            results.add("Total Lore Entries: " + allEntries.size());
            results.add("Approved Entries: " + approvedEntries);
            long invalidEntries = allEntries.stream().filter(dto -> !isValidLoreEntry(dto)).count();
            if (invalidEntries > 0) {
                results.add("WARNING: " + invalidEntries + " invalid lore entries detected");
            } else {
                results.add("All lore entries are valid");
            }
            results.add("\nEntries by Type:");
            for (LoreType type : LoreType.values()) {
                long count = allEntries.stream().filter(dto -> type.name().equalsIgnoreCase(dto.getEntryType())).count();
                if (count > 0) {
                    results.add("  " + type + ": " + count);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            results.add("ERROR retrieving lore entries: " + e.getMessage());
            logger.error("Error during lore diagnostics", e);
        }
    }

    /**
     * Checks if a LoreEntryDTO is valid (basic validation)
     */
    private boolean isValidLoreEntry(LoreEntryDTO dto) {
        return dto.getName() != null && !dto.getName().isEmpty()
            && dto.getDescription() != null && !dto.getDescription().isEmpty();
    }

    /**
     * Log diagnostic results to console
     */
    public void logDiagnostics() {
        List<String> results = runDiagnostics();
        for (String line : results) {
            logger.info(line);
        }
    }

    /**
     * Check system health and return true if everything is functioning properly
     * @return true if system checks pass
     */
    public boolean checkSystemHealth() {
        boolean databaseOk = plugin.getDatabaseManager().isConnected();
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
                logger.error("Error checking handler for type: " + type, e);
                handlersOk = false;
                break;
            }
        }
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        boolean memoryOk = freeMemory > 50;
        return databaseOk && handlersOk && memoryOk;
    }
}
