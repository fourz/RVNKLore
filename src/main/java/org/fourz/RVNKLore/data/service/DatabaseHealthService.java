package org.fourz.RVNKLore.data.service;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages periodic health checks for the database connection.
 * Provides automatic reconnection with exponential backoff.
 */
public class DatabaseHealthService {
    private final ScheduledExecutorService scheduler;
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final AtomicInteger reconnectAttempts;
    private volatile long lastLogTime;
    private volatile boolean wasInvalid = false;
    
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_DELAY_SECONDS = 30;
    private static final int CHECK_INTERVAL_SECONDS = 60; // Increased from 30
    private static final int LOG_THROTTLE_MS = 300000; // 5 minutes

    /**
     * Create a new DatabaseHealthService.
     *
     * @param databaseManager The database manager to monitor
     * @param plugin The RVNKLore plugin instance
     */
    public DatabaseHealthService(DatabaseManager databaseManager, RVNKLore plugin) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "DatabaseHealthService");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.reconnectAttempts = new AtomicInteger(0);
    }

    /**
     * Returns true if the database is SQLite.
     */
    private boolean isSQLite() {
        return databaseManager.getConnectionProvider() instanceof org.fourz.RVNKLore.data.connection.provider.SQLiteConnectionProvider;
    }

    /**
     * Returns the health check interval in seconds, optimized for SQLite.
     */
    private int getHealthCheckIntervalSeconds() {
        return isSQLite() ? 180 : CHECK_INTERVAL_SECONDS; // 3 min for SQLite, 1 min for MySQL
    }

    /**
     * Start periodic health checks (with database-specific optimization).
     */
    public void start() {
        initialize();
    }

    /**
     * Stop the health check service.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Database health check service stopped");
    }

    /**
     * Initialize the health check service with database-specific settings
     */
    public void initialize() {
        scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    performHealthCheck();
                } catch (Exception e) {
                    logger.error("Error during database health check", e);
                }
            },
            INITIAL_DELAY_SECONDS,
            getHealthCheckIntervalSeconds(),
            TimeUnit.SECONDS
        );
        logger.info("Database health check service initialized with " + 
                   (isSQLite() ? "SQLite" : "MySQL") + "-specific settings");
    }

    /**
     * Check database health and attempt reconnection if needed.
     * Uses SQLite-specific validation for SQLite connections.
     */
    private void performHealthCheck() {
        boolean isConnectionValid = databaseManager.validateConnection();
        
        // If connection is invalid, attempt reconnection with backoff
        if (!isConnectionValid) {
            int attempts = reconnectAttempts.get();
            if (attempts < MAX_RECONNECT_ATTEMPTS) {
                long backoffSeconds = (long) Math.pow(2, attempts);
                logger.warning("Database connection invalid. Attempting reconnect in " + backoffSeconds + 
                              " seconds. (Attempt " + (attempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                
                try {
                    Thread.sleep(backoffSeconds * 1000);
                    boolean reconnected = databaseManager.reconnect();
                    
                    if (reconnected && databaseManager.validateConnection()) {
                        logger.info("Database connection restored successfully");
                        reconnectAttempts.set(0);
                    } else {
                        reconnectAttempts.incrementAndGet();
                        if (isSQLite()) {
                            // For SQLite, we'll try to recreate the file if it's missing
                            logger.info("Attempting to recreate SQLite database file if missing");
                            databaseManager.getConnectionProvider().getConnection().close();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Reconnection attempt interrupted", e);
                } catch (Exception e) {
                    reconnectAttempts.incrementAndGet();
                    logger.error("Error during reconnection attempt", e);
                }
            } else {
                // Only log the max attempts error if we haven't logged it recently
                long now = System.currentTimeMillis();
                if (now - lastLogTime > LOG_THROTTLE_MS) {
                    lastLogTime = now;
                    logger.error("Maximum reconnection attempts (" + MAX_RECONNECT_ATTEMPTS + 
                                ") reached. Manual intervention required.", 
                                new RuntimeException("Max reconnection attempts exceeded"));
                }
            }
        } else if (reconnectAttempts.get() > 0) {
            // Reset counter if connection is valid and we had previous attempts
            reconnectAttempts.set(0);
            if (wasInvalid) {
                logger.info("Database connection has been restored");
                wasInvalid = false;
            }
        }
    }
}
