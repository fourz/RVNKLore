package org.fourz.RVNKLore.exception;

/**
 * Custom exception for lore-related errors
 */
public class LoreException extends Exception {
    
    private final LoreExceptionType type;
    
    public LoreException(String message, LoreExceptionType type) {
        super(message);
        this.type = type;
    }
    
    public LoreException(String message, Throwable cause, LoreExceptionType type) {
        super(message, cause);
        this.type = type;
    }
    
    public LoreExceptionType getType() {
        return type;
    }
    
    /**
     * Types of lore exceptions for more specific error handling
     */
    public enum LoreExceptionType {
        VALIDATION_ERROR,
        DATABASE_ERROR,
        PERMISSION_ERROR,
        WORLD_NOT_FOUND,
        LORE_NOT_FOUND,
        INVALID_FORMAT,
        UNKNOWN_ERROR
    }
}
