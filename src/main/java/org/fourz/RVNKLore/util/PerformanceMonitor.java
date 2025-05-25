package org.fourz.RVNKLore.util;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Utility for monitoring performance of different operations
 * to help identify bottlenecks and issues
 */
public class PerformanceMonitor {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    private boolean enabled = false;
    
    public PerformanceMonitor(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "PerformanceMonitor", Level.FINE);
    }
    
    /**
     * Enable or disable performance monitoring
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        debug.debug("Performance monitoring " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Start timing an operation
     * @param operation Name of the operation
     * @return A timer object to be passed to stopOperation
     */
    public OperationTimer startOperation(String operation) {
        if (!enabled) return new OperationTimer(operation, 0);
        
        long startTime = System.nanoTime();
        return new OperationTimer(operation, startTime);
    }
    
    /**
     * Stop timing an operation and record stats
     * @param timer The timer returned from startOperation
     */
    public void stopOperation(OperationTimer timer) {
        if (!enabled || timer.startTime == 0) return;
        
        long endTime = System.nanoTime();
        long duration = endTime - timer.startTime;
        
        operationStats.computeIfAbsent(timer.operation, k -> new OperationStats())
            .recordOperation(duration);
    }
    
    /**
     * Get a report of all timed operations
     * @return A formatted string with performance statistics
     */
    public String getPerformanceReport() {
        if (operationStats.isEmpty()) {
            return "No performance data collected yet.";
        }
        
        StringBuilder report = new StringBuilder("=== Performance Report ===\n");
        
        // Sort operations by average time (descending)
        Map<String, OperationStats> sortedStats = operationStats.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().getAverageTimeMs(), 
                                             e1.getValue().getAverageTimeMs()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new));
        
        for (Map.Entry<String, OperationStats> entry : sortedStats.entrySet()) {
            OperationStats stats = entry.getValue();
            report.append(String.format("%-30s: avg=%.2fms, min=%.2fms, max=%.2fms, count=%d\n",
                entry.getKey(),
                stats.getAverageTimeMs(),
                stats.getMinTimeMs(),
                stats.getMaxTimeMs(),
                stats.getCount()));
        }
        
        return report.toString();
    }
    
    /**
     * Reset all performance statistics
     */
    public void reset() {
        operationStats.clear();
        debug.debug("Performance statistics reset");
    }
    
    /**
     * Timer object returned by startOperation and passed to stopOperation
     */
    public static class OperationTimer {
        private final String operation;
        private final long startTime;
        
        public OperationTimer(String operation, long startTime) {
            this.operation = operation;
            this.startTime = startTime;
        }
    }
    
    /**
     * Statistics for a single operation type
     */
    private static class OperationStats {
        private long totalTime = 0;
        private long minTime = Long.MAX_VALUE;
        private long maxTime = 0;
        private int count = 0;
        
        public void recordOperation(long timeNanos) {
            totalTime += timeNanos;
            minTime = Math.min(minTime, timeNanos);
            maxTime = Math.max(maxTime, timeNanos);
            count++;
        }
        
        public double getAverageTimeMs() {
            return count > 0 ? (totalTime / 1_000_000.0) / count : 0;
        }
        
        public double getMinTimeMs() {
            return minTime == Long.MAX_VALUE ? 0 : minTime / 1_000_000.0;
        }
        
        public double getMaxTimeMs() {
            return maxTime / 1_000_000.0;
        }
        
        public int getCount() {
            return count;
        }
    }
}
