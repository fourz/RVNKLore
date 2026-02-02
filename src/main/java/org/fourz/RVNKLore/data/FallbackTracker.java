package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe tracker for database fallback mode.
 *
 * <p>Tracks consecutive database failures and manages transitions into/out of
 * fallback mode. When in fallback mode, database operations should be skipped
 * and alternative storage (cache-based) should be used.</p>
 *
 * <p>Recovery is time-based: after the recovery period elapses, the next
 * database operation will attempt to reconnect.</p>
 *
 * <p>Pattern based on RVNKWorlds FallbackTracker for cross-plugin consistency.</p>
 */
public class FallbackTracker {

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicBoolean inFallbackMode = new AtomicBoolean(false);

    private final int maxFailuresBeforeFallback;
    private final long recoveryTimeMs;
    private final LogManager logger;

    /**
     * Creates a new FallbackTracker with configuration from plugin.
     *
     * @param plugin The plugin instance for configuration and logging
     */
    public FallbackTracker(RVNKLore plugin) {
        this.maxFailuresBeforeFallback = plugin.getConfig().getInt(
            "database.fallback.maxFailuresBeforeFallback", 3);
        int recoveryMins = plugin.getConfig().getInt(
            "database.fallback.recoveryTimeMinutes", 5);
        this.recoveryTimeMs = recoveryMins * 60 * 1000L;
        this.logger = LogManager.getInstance(plugin, "FallbackTracker");
    }

    /**
     * Creates a FallbackTracker with explicit configuration.
     * Useful for testing.
     *
     * @param maxFailures     Number of consecutive failures before entering fallback
     * @param recoveryTimeMs  Milliseconds to wait before attempting recovery
     * @param logger          LogManager instance
     */
    public FallbackTracker(int maxFailures, long recoveryTimeMs, LogManager logger) {
        this.maxFailuresBeforeFallback = maxFailures;
        this.recoveryTimeMs = recoveryTimeMs;
        this.logger = logger;
    }

    /**
     * Check if currently in fallback mode.
     *
     * <p>Also checks if recovery time has elapsed and automatically exits
     * fallback mode if so, allowing the next operation to attempt database access.</p>
     *
     * @return true if in fallback mode and recovery time has not elapsed
     */
    public boolean isInFallbackMode() {
        if (!inFallbackMode.get()) {
            return false;
        }

        // Check if recovery time has passed
        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        if (elapsed > recoveryTimeMs) {
            // Attempt recovery - exit fallback mode
            if (inFallbackMode.compareAndSet(true, false)) {
                consecutiveFailures.set(0);
                logger.info("Recovery time elapsed (" + (recoveryTimeMs / 60000) +
                    " min) - exiting fallback mode, will attempt database reconnection");
            }
            return false;
        }

        return true;
    }

    /**
     * Record a database operation failure.
     *
     * <p>Increments failure counter and enters fallback mode if threshold is reached.</p>
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (!inFallbackMode.get() && failures >= maxFailuresBeforeFallback) {
            if (inFallbackMode.compareAndSet(false, true)) {
                logger.warning("Entering fallback mode after " + failures +
                    " consecutive database failures. Recovery will be attempted in " +
                    (recoveryTimeMs / 60000) + " minutes.");
            }
        } else if (!inFallbackMode.get()) {
            logger.warning("Database failure " + failures + "/" + maxFailuresBeforeFallback);
        }
    }

    /**
     * Record a successful database operation.
     *
     * <p>Resets the failure counter. Note: this does not immediately exit fallback mode;
     * that happens via time-based recovery in {@link #isInFallbackMode()}.</p>
     */
    public void recordSuccess() {
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures > 0) {
            logger.debug("Database operation succeeded, reset failure counter from " + previousFailures);
        }
    }

    /**
     * Force exit from fallback mode.
     * Use with caution - primarily for testing or manual intervention.
     */
    public void forceExitFallback() {
        inFallbackMode.set(false);
        consecutiveFailures.set(0);
        logger.info("Forced exit from fallback mode");
    }

    /**
     * Force entry into fallback mode.
     * Use with caution - primarily for testing.
     */
    public void forceEnterFallback() {
        inFallbackMode.set(true);
        lastFailureTime.set(System.currentTimeMillis());
        logger.info("Forced entry into fallback mode");
    }

    /**
     * Get current consecutive failure count.
     *
     * @return number of consecutive failures
     */
    public int getFailureCount() {
        return consecutiveFailures.get();
    }

    /**
     * Get time remaining until recovery attempt (milliseconds).
     *
     * @return milliseconds until recovery, or 0 if not in fallback mode
     */
    public long getTimeUntilRecovery() {
        if (!inFallbackMode.get()) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        return Math.max(0, recoveryTimeMs - elapsed);
    }

    /**
     * Get diagnostic information for debugging.
     *
     * @return Map containing fallback status details
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> info = new HashMap<>();
        info.put("inFallbackMode", inFallbackMode.get());
        info.put("consecutiveFailures", consecutiveFailures.get());
        info.put("maxFailuresBeforeFallback", maxFailuresBeforeFallback);
        info.put("recoveryTimeMs", recoveryTimeMs);
        info.put("timeUntilRecoveryMs", getTimeUntilRecovery());
        return info;
    }
}
