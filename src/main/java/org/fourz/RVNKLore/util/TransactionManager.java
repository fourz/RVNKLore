package org.fourz.RVNKLore.util;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages plugin transactions for tracking operations and debugging
 */
public class TransactionManager {
    private final LogManager logger;
    private final Map<String, TransactionInfo> transactions = new ConcurrentHashMap<>();
    private final AtomicInteger transactionCount = new AtomicInteger(0);
    
    private static TransactionManager instance;
    
    private TransactionManager(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin, "TransactionManager");
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
        logger.debug("Starting transaction: " + id + " [" + type + "]");
        return id;
    }
    
    /**
     * Complete a transaction
     */
    public void completeTransaction(String id, boolean success) {
        TransactionInfo info = transactions.get(id);
        if (info == null) {
            logger.warning("Attempted to complete unknown transaction: " + id);
            return;
        }
        info.complete(success);
        long duration = info.getDuration();
        if (success) {
            logger.debug("Transaction completed successfully: " + id + " (" + duration + "ms)");
        } else {
            logger.warning("Transaction failed: " + id + " (" + duration + "ms)");
        }
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
            logger.warning("Attempted to add note to unknown transaction: " + id);
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
