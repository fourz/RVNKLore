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
    private volatile boolean wasInvalid;
    
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
     * Start periodic health checks.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                logger.error("Error during database health check", e);
            }
        }, INITIAL_DELAY_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        logger.info("Database health check service started");
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
     * Initialize the health check service
     */
    public void initialize() {
        scheduler.scheduleAtFixedRate(
            this::performHealthCheck,
            INITIAL_DELAY_SECONDS,
            CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        logger.info("Database health check service initialized");
    }

    /**
     * Check database health and attempt reconnection if needed
     */
    private void performHealthCheck() {
        if (!databaseManager.validateConnection()) {
            int attempts = reconnectAttempts.get();
            if (attempts < MAX_RECONNECT_ATTEMPTS) {
                long backoffSeconds = (long) Math.pow(2, attempts);
                logger.warning("Database connection invalid. Attempting reconnect in " + backoffSeconds + " seconds. (Attempt " + (attempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                
                try {
                    Thread.sleep(backoffSeconds * 1000);
                    databaseManager.reconnect();
                    if (databaseManager.validateConnection()) {
                        logger.info("Database connection restored successfully");
                        reconnectAttempts.set(0);
                    } else {
                        reconnectAttempts.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Reconnection attempt interrupted", e);
                } catch (Exception e) {
                    reconnectAttempts.incrementAndGet();
                    logger.error("Error during reconnection attempt", e);
                }
            } else {
                logger.error("Maximum reconnection attempts (" + MAX_RECONNECT_ATTEMPTS + ") reached. Manual intervention required.", new RuntimeException("Max reconnection attempts exceeded"));
            }
        } else if (reconnectAttempts.get() > 0) {
            // Reset counter if connection is valid and we had previous attempts
            reconnectAttempts.set(0);
        }
    }
}
