package org.fourz.RVNKLore.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
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
        return runDiagnostics(false);
    }

    /**
     * Run full diagnostic scan and return results
     * @param verbose Include verbose details
     * @return List of diagnostic messages
     */
    public List<String> runDiagnostics(boolean verbose) {
        List<String> results = new ArrayList<>();

        results.add("===== RVNKLore Diagnostics =====");
        results.add("Plugin Version: " + plugin.getDescription().getVersion());
        results.add("Server Version: " + Bukkit.getVersion());

        // Memory diagnostics
        addMemoryDiagnostics(results, verbose);

        // Check all handlers
        addHandlerDiagnostics(results, verbose);

        // Check database connectivity
        addDatabaseDiagnostics(results, verbose);

        // Check lore entries
        addLoreDiagnostics(results, verbose);

        // Check plugin dependencies
        addDependencyDiagnostics(results, verbose);

        return results;
    }

    /**
     * Run diagnostics with console-friendly output format
     * @param sender Command sender (console or player)
     * @param verbose Include verbose details
     */
    public void runDiagnostics(CommandSender sender, boolean verbose) {
        boolean isConsole = !(sender instanceof Player);
        String prefix = isConsole ? "[RVNKLore] " : "";

        sender.sendMessage(prefix + "=== SYSTEM DIAGNOSTICS ===");

        // Database status
        addDatabaseStatus(sender, prefix, verbose);

        // ServiceRegistry status (RVNKCore integration)
        addServiceRegistryStatus(sender, prefix, verbose);

        // Fallback mode status
        addFallbackStatus(sender, prefix, verbose);

        // Lore entry counts
        addLoreStats(sender, prefix, verbose);

        // Memory usage
        addMemoryStats(sender, prefix, verbose);

        // Handler status
        addHandlerStatus(sender, prefix, verbose);

        if (verbose) {
            // Additional verbose diagnostics
            addDependencyStatus(sender, prefix);
            addPluginInfo(sender, prefix);
        }

        sender.sendMessage(prefix + "=== END DIAGNOSTICS ===");
    }

    private void addDatabaseStatus(CommandSender sender, String prefix, boolean verbose) {
        try {
            boolean connected = plugin.getDatabaseManager().isConnected();
            String dbType = plugin.getConfigManager().getStorageType();

            if (connected) {
                String dbInfo = "";
                try {
                    String info = plugin.getDatabaseManager().getDatabaseInfo();
                    if (info != null && !info.isEmpty()) {
                        dbInfo = " (" + info + ")";
                    }
                } catch (Exception e) {
                    // Ignore if getDatabaseInfo() not available
                }

                sender.sendMessage(prefix + "Database: " + dbType.toUpperCase() + " CONNECTED" + dbInfo);

                // HikariCP pool status (if available)
                addConnectionPoolStatus(sender, prefix, verbose);
            } else {
                sender.sendMessage(prefix + "Database: " + dbType.toUpperCase() + " DISCONNECTED");
                String lastError = plugin.getDatabaseManager().getLastConnectionError();
                if (lastError != null && !lastError.isEmpty()) {
                    sender.sendMessage(prefix + "  Last Error: " + lastError);
                }
            }
        } catch (Exception e) {
            sender.sendMessage(prefix + "Database: ERROR - " + e.getMessage());
        }
    }

    private void addConnectionPoolStatus(CommandSender sender, String prefix, boolean verbose) {
        try {
            // Use reflection to check if HikariCP is available and get pool stats
            Class<?> hikariDataSourceClass = Class.forName("com.zaxxer.hikari.HikariDataSource");

            // Try to get the datasource from DatabaseManager via reflection
            Object dataSource = plugin.getDatabaseManager().getClass().getMethod("getDataSource").invoke(plugin.getDatabaseManager());

            if (dataSource != null && hikariDataSourceClass.isInstance(dataSource)) {
                // Get HikariPoolMXBean for stats
                Object poolMXBean = hikariDataSourceClass.getMethod("getHikariPoolMXBean").invoke(dataSource);

                if (poolMXBean != null) {
                    Class<?> poolMXBeanClass = poolMXBean.getClass();
                    int activeConnections = (Integer) poolMXBeanClass.getMethod("getActiveConnections").invoke(poolMXBean);
                    int idleConnections = (Integer) poolMXBeanClass.getMethod("getIdleConnections").invoke(poolMXBean);
                    int totalConnections = (Integer) poolMXBeanClass.getMethod("getTotalConnections").invoke(poolMXBean);

                    sender.sendMessage(prefix + "Connection Pool: " + activeConnections + " active, " + idleConnections + " idle, " + totalConnections + " total");

                    if (verbose) {
                        int threadsAwaitingConnection = (Integer) poolMXBeanClass.getMethod("getThreadsAwaitingConnection").invoke(poolMXBean);
                        if (threadsAwaitingConnection > 0) {
                            sender.sendMessage(prefix + "  WARNING: " + threadsAwaitingConnection + " threads awaiting connection");
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // HikariCP not available - this is expected if still using basic JDBC
            if (verbose) {
                sender.sendMessage(prefix + "Connection Pool: Not using HikariCP");
            }
        } catch (Exception e) {
            // Method not available or other error - skip pool stats
            logger.debug("Could not retrieve HikariCP pool stats: " + e.getMessage());
        }
    }

    private void addServiceRegistryStatus(CommandSender sender, String prefix, boolean verbose) {
        if (plugin.isRVNKCoreAvailable()) {
            sender.sendMessage(prefix + "RVNKCore Integration: ACTIVE");

            // Check service registration status
            int registeredServices = 0;
            int totalServices = 5; // ILoreService, IItemService, ICollectionService, ISubmissionService, IPlayerService

            List<String> serviceStatus = new ArrayList<>();

            // Check each service via reflection
            try {
                Plugin rvnkCorePlugin = Bukkit.getPluginManager().getPlugin("RVNKCore");
                if (rvnkCorePlugin != null) {
                    Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
                    Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
                    Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);

                    // Check each service
                    String[] serviceNames = {"ILoreService", "IItemService", "ICollectionService", "ISubmissionService", "IPlayerService"};

                    for (String serviceName : serviceNames) {
                        try {
                            Class<?> serviceInterface = Class.forName("org.fourz.RVNKLore.service." + serviceName);
                            Object service = serviceRegistry.getClass().getMethod("getService", Class.class).invoke(serviceRegistry, serviceInterface);

                            if (service != null) {
                                registeredServices++;
                                serviceStatus.add(prefix + "  - " + serviceName + ": OK");
                            } else {
                                serviceStatus.add(prefix + "  - " + serviceName + ": NOT REGISTERED");
                            }
                        } catch (Exception e) {
                            serviceStatus.add(prefix + "  - " + serviceName + ": ERROR");
                        }
                    }
                }
            } catch (Exception e) {
                sender.sendMessage(prefix + "  Could not verify service registration: " + e.getMessage());
            }

            sender.sendMessage(prefix + "ServiceRegistry: " + registeredServices + "/" + totalServices + " services registered");

            if (verbose || registeredServices < totalServices) {
                for (String status : serviceStatus) {
                    sender.sendMessage(status);
                }
            }
        } else {
            sender.sendMessage(prefix + "RVNKCore Integration: INACTIVE (standalone mode)");
        }
    }

    private void addFallbackStatus(CommandSender sender, String prefix, boolean verbose) {
        try {
            // Check if database has fallback tracker
            Object fallbackTracker = plugin.getDatabaseManager().getClass().getMethod("getFallbackTracker").invoke(plugin.getDatabaseManager());

            if (fallbackTracker != null) {
                boolean inFallback = (Boolean) fallbackTracker.getClass().getMethod("isInFallbackMode").invoke(fallbackTracker);

                if (inFallback) {
                    sender.sendMessage(prefix + "Fallback Mode: ACTIVE (database issues detected)");
                    if (verbose) {
                        int failureCount = (Integer) fallbackTracker.getClass().getMethod("getConsecutiveFailures").invoke(fallbackTracker);
                        sender.sendMessage(prefix + "  Consecutive Failures: " + failureCount);
                    }
                } else {
                    sender.sendMessage(prefix + "Fallback Mode: INACTIVE");
                }
            }
        } catch (Exception e) {
            // Fallback tracker not available - this is fine
            if (verbose) {
                sender.sendMessage(prefix + "Fallback Mode: Not implemented");
            }
        }
    }

    private void addLoreStats(CommandSender sender, String prefix, boolean verbose) {
        try {
            LoreManager loreManager = plugin.getLoreManager();
            int totalEntries = loreManager.getAllLoreEntriesSync().size();

            sender.sendMessage(prefix + "Total Entries: " + totalEntries);

            if (verbose) {
                int approvedEntries = loreManager.getApprovedLoreEntriesSync().size();
                sender.sendMessage(prefix + "  Approved: " + approvedEntries + " (" + (totalEntries > 0 ? (approvedEntries * 100 / totalEntries) : 0) + "%)");

                sender.sendMessage(prefix + "Entries by Type:");
                for (LoreType type : LoreType.values()) {
                    int count = loreManager.getLoreEntriesByTypeSync(type).size();
                    if (count > 0) {
                        sender.sendMessage(prefix + "  " + type + ": " + count);
                    }
                }
            }
        } catch (Exception e) {
            sender.sendMessage(prefix + "Lore Stats: ERROR - " + e.getMessage());
        }
    }

    private void addMemoryStats(CommandSender sender, String prefix, boolean verbose) {
        if (verbose) {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

            long usedMemory = heapUsage.getUsed() / (1024 * 1024);
            long maxMemory = heapUsage.getMax() / (1024 * 1024);
            long percentUsed = maxMemory > 0 ? (usedMemory * 100 / maxMemory) : 0;

            sender.sendMessage(prefix + "Memory: " + usedMemory + "MB / " + maxMemory + "MB (" + percentUsed + "%)");

            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            sender.sendMessage(prefix + "  Free: " + freeMemory + "MB");
        }
    }

    private void addHandlerStatus(CommandSender sender, String prefix, boolean verbose) {
        if (verbose) {
            HandlerFactory factory = plugin.getHandlerFactory();
            Map<LoreType, LoreHandler> handlers = factory.getAllHandlers();

            sender.sendMessage(prefix + "Handlers: " + handlers.size() + " registered");

            // Check for missing handlers
            int missingHandlers = 0;
            for (LoreType type : LoreType.values()) {
                if (!handlers.containsKey(type)) {
                    sender.sendMessage(prefix + "  WARNING: Missing handler for " + type);
                    missingHandlers++;
                }
            }

            if (missingHandlers == 0) {
                sender.sendMessage(prefix + "  All lore types have handlers");
            }
        }
    }

    private void addDependencyStatus(CommandSender sender, String prefix) {
        String[] dependencies = plugin.getDescription().getDepend().toArray(new String[0]);

        if (dependencies.length == 0) {
            sender.sendMessage(prefix + "Dependencies: None required");
            return;
        }

        sender.sendMessage(prefix + "Dependencies:");
        for (String dependency : dependencies) {
            Plugin dependencyPlugin = Bukkit.getPluginManager().getPlugin(dependency);
            if (dependencyPlugin != null && dependencyPlugin.isEnabled()) {
                sender.sendMessage(prefix + "  - " + dependency + ": OK (v" + dependencyPlugin.getDescription().getVersion() + ")");
            } else {
                sender.sendMessage(prefix + "  - " + dependency + ": MISSING or DISABLED");
            }
        }
    }

    private void addPluginInfo(CommandSender sender, String prefix) {
        sender.sendMessage(prefix + "Plugin Info:");
        sender.sendMessage(prefix + "  Version: " + plugin.getDescription().getVersion());
        sender.sendMessage(prefix + "  API Version: " + plugin.getDescription().getAPIVersion());
        sender.sendMessage(prefix + "  Authors: " + String.join(", ", plugin.getDescription().getAuthors()));
    }

    private void addMemoryDiagnostics(List<String> results, boolean verbose) {
        results.add("\n----- Memory Usage -----");
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        long maxMemory = heapUsage.getMax() / (1024 * 1024);

        results.add("Heap Memory: " + usedMemory + "MB / " + maxMemory + "MB");

        if (verbose) {
            Runtime runtime = Runtime.getRuntime();
            results.add("Free Memory: " + (runtime.freeMemory() / (1024 * 1024)) + "MB");
            results.add("Total Memory: " + (runtime.totalMemory() / (1024 * 1024)) + "MB");
            results.add("Available Processors: " + runtime.availableProcessors());
        }
    }

    private void addHandlerDiagnostics(List<String> results, boolean verbose) {
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
    private void addDatabaseDiagnostics(List<String> results, boolean verbose) {
        results.add("\n----- Database Status -----");
        try {
            boolean connected = plugin.getDatabaseManager().isConnected();
            results.add("Database Connection: " + (connected ? "OK" : "FAILED"));
            if (connected) {
                String dbType = plugin.getConfigManager().getStorageType();
                results.add("Database Type: " + dbType.toUpperCase());
                int totalEntries = plugin.getLoreManager().getAllLoreEntriesSync().size();
                results.add("Total Entries in Cache: " + totalEntries);
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

    private void addLoreDiagnostics(List<String> results, boolean verbose) {
        results.add("\n----- Lore Status -----");
        LoreManager loreManager = plugin.getLoreManager();
        int totalEntries = loreManager.getAllLoreEntriesSync().size();
        int approvedEntries = loreManager.getApprovedLoreEntriesSync().size();
        results.add("Total Lore Entries: " + totalEntries);
        results.add("Approved Entries: " + approvedEntries);

        if (verbose) {
            int invalidEntries = 0;
            for (LoreEntry entry : loreManager.getAllLoreEntriesSync()) {
                if (!entry.isValid()) {
                    invalidEntries++;
                    logger.debug("Invalid lore entry: " + entry.getId() + " - " + entry.getName());
                }
            }
            if (invalidEntries > 0) {
                results.add("WARNING: " + invalidEntries + " invalid lore entries detected");
            } else {
                results.add("All lore entries are valid");
            }
        }

        results.add("\nEntries by Type:");
        for (LoreType type : LoreType.values()) {
            int count = loreManager.getLoreEntriesByTypeSync(type).size();
            if (count > 0) {
                results.add("  " + type + ": " + count);
            }
        }
    }

    private void addDependencyDiagnostics(List<String> results, boolean verbose) {
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
