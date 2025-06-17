package org.fourz.RVNKLore.util;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Base64;

/**
 * Utility class for working with UUIDs.
 * Provides methods to handle full UUIDs for item identification
 * when duplicate names might exist.
 */
public class UuidUtil {
    
    /**
     * Converts a string representation of a UUID to a UUID object
     * 
     * @param uuidString The UUID string to convert
     * @return The UUID object, or null if invalid
     */
    public static UUID fromString(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Generate a new random UUID
     * 
     * @return A new random UUID
     */
    public static UUID generateRandomUuid() {
        return UUID.randomUUID();
    }
    
    /**
     * Checks if a string is a valid UUID
     * 
     * @param uuidString The string to check
     * @return True if the string is a valid UUID
     */
    public static boolean isValidUuid(String uuidString) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
