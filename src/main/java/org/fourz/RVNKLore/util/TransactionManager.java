package org.fourz.RVNKLore.util;

import org.fourz.RVNKLore.RVNKLore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Manages plugin transactions for tracking operations and debugging
 */
public class TransactionManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<String, TransactionInfo> transactions = new ConcurrentHashMap<>();
    private final AtomicInteger transactionCount = new AtomicInteger(0);
    
    private static TransactionManager instance;
    
    private TransactionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "TransactionManager", plugin.getConfigManager().getLogLevel());
    }
    
    public static synchronized TransactionManager getInstance(RVNKLore plugin) {
        if (instance == null) {
            instance = new TransactionManager(plugin);
        }
        return instance;
    }
    
    /**
     * Start a new transaction and get its ID
     */
    public String startTransaction(String type) {
        String id = UUID.randomUUID().toString();
        TransactionInfo info = new TransactionInfo(type, System.currentTimeMillis());
        transactions.put(id, info);
        transactionCount.incrementAndGet();
        debug.debug("Starting transaction: " + id + " [" + type + "]");
        return id;
    }
    
    /**
     * Complete a transaction
     */
    public void completeTransaction(String id, boolean success) {
        TransactionInfo info = transactions.get(id);
        if (info == null) {
            debug.warning("Attempted to complete unknown transaction: " + id);
            return;
        }
        
        info.complete(success);
        long duration = info.getDuration();
        
        if (success) {
            debug.debug("Transaction completed successfully: " + id + " (" + duration + "ms)");
        } else {
            debug.warning("Transaction failed: " + id + " (" + duration + "ms)");
        }
        
        // Remove old transactions to avoid memory leaks
        cleanupOldTransactions();
    }
    
    /**
     * Get transaction information
     */
    public TransactionInfo getTransaction(String id) {
        return transactions.get(id);
    }
    
    /**
     * Add a note to a transaction
     */
    public void addTransactionNote(String id, String note) {
        TransactionInfo info = transactions.get(id);
        if (info == null) {
            debug.warning("Attempted to add note to unknown transaction: " + id);
            return;
        }
        
        info.addNote(note);
    }
    
    /**
     * Clean up old transactions to prevent memory leaks
     */
    private void cleanupOldTransactions() {
        // Keep last 100 transactions or those less than 1 hour old
        long cutoff = System.currentTimeMillis() - (60 * 60 * 1000);
        
        if (transactions.size() > 100) {
            transactions.entrySet().removeIf(entry -> 
                entry.getValue().isComplete() && entry.getValue().getStartTime() < cutoff);
        }
    }
    
    /**
     * Get the total number of transactions processed
     */
    public int getTransactionCount() {
        return transactionCount.get();
    }
    
    /**
     * Class to hold transaction information
     */
    public static class TransactionInfo {
        private final String type;
        private final long startTime;
        private long endTime;
        private boolean success;
        private boolean complete;
        private final StringBuilder notes = new StringBuilder();
        
        public TransactionInfo(String type, long startTime) {
            this.type = type;
            this.startTime = startTime;
            this.complete = false;
        }
        
        public void complete(boolean success) {
            this.endTime = System.currentTimeMillis();
            this.success = success;
            this.complete = true;
        }
        
        public void addNote(String note) {
            if (notes.length() > 0) {
                notes.append("\n");
            }
            notes.append(note);
        }
        
        public long getDuration() {
            if (!complete) {
                return System.currentTimeMillis() - startTime;
            }
            return endTime - startTime;
        }
        
        public String getType() {
            return type;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public String getNotes() {
            return notes.toString();
        }
    }
}
