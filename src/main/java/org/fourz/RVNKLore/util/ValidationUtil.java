package org.fourz.RVNKLore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.UUID;

/**
 * Utility class for validation of lore entries and related data
 */
public class ValidationUtil {
    
    /**
     * Validate a location to ensure the world exists
     * 
     * @param location The location to validate
     * @throws LoreException if validation fails
     */
    public static void validateLocation(Location location) throws LoreException {
        if (location == null) {
            throw new LoreException("Location cannot be null", LoreExceptionType.VALIDATION_ERROR);
        }
        
        if (location.getWorld() == null) {
            throw new LoreException("World cannot be null", LoreExceptionType.WORLD_NOT_FOUND);
        }
        
        // Check if the world exists on the server
        String worldName = location.getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new LoreException("World '" + worldName + "' not found", LoreExceptionType.WORLD_NOT_FOUND);
        }
    }
    
    /**
     * Validate that a string is not null or empty
     * 
     * @param value The string to validate
     * @param fieldName The name of the field being validated
     * @throws LoreException if validation fails
     */
    public static void validateRequiredString(String value, String fieldName) throws LoreException {
        if (value == null || value.trim().isEmpty()) {
            throw new LoreException(fieldName + " cannot be empty", LoreExceptionType.VALIDATION_ERROR);
        }
    }
    
    /**
     * Validate a lore entry based on its type
     * 
     * @param entry The lore entry to validate
     * @throws LoreException if validation fails
     */
    public static void validateLoreEntry(LoreEntry entry) throws LoreException {
        if (entry == null) {
            throw new LoreException("Lore entry cannot be null", LoreExceptionType.VALIDATION_ERROR);
        }
        
        validateRequiredString(entry.getName(), "Name");
        validateRequiredString(entry.getDescription(), "Description");
        
        // Different validations based on lore type
        LoreType type = entry.getType();
        if (type == null) {
            throw new LoreException("Lore type cannot be null", LoreExceptionType.VALIDATION_ERROR);
        }
        
        // Location-based lore requires a location
        if (isLocationType(type)) {
            Location location = entry.getLocation();
            if (location == null) {
                throw new LoreException("Location is required for " + type.getDescription() + " lore", 
                        LoreExceptionType.VALIDATION_ERROR);
            }
            
            validateLocation(location);
        }
        
        // Head-based lore requires NBT data
        if (isHeadType(type) && (entry.getNbtData() == null || entry.getNbtData().isEmpty())) {
            throw new LoreException("NBT data is required for " + type.getDescription() + " lore", 
                    LoreExceptionType.VALIDATION_ERROR);
        }
    }
    
    /**
     * Check if a lore type is location-based
     */
    public static boolean isLocationType(LoreType type) {
        return type == LoreType.LANDMARK || type == LoreType.CITY || type == LoreType.PATH;
    }
    
    /**
     * Check if a lore type is head/hat-based
     */
    public static boolean isHeadType(LoreType type) {
        return type == LoreType.PLAYER_HEAD || type == LoreType.MOB_HEAD || 
               type == LoreType.HEAD || type == LoreType.HAT;
    }
    
    /**
     * Check if a lore type is character-based
     */
    public static boolean isCharacterType(LoreType type) {
        return type == LoreType.PLAYER || type == LoreType.FACTION;
    }
    
    /**
     * Check if a lore type is gameplay-based
     */
    public static boolean isGameplayType(LoreType type) {
        return type == LoreType.ENCHANTMENT || type == LoreType.ITEM || type == LoreType.QUEST;
    }
    
    /**
     * Validate a UUID string
     * 
     * @param uuidStr The UUID string to validate
     * @return The parsed UUID
     * @throws LoreException if the UUID is invalid
     */
    public static UUID validateAndParseUUID(String uuidStr) throws LoreException {
        if (uuidStr == null || uuidStr.trim().isEmpty()) {
            throw new LoreException("UUID cannot be empty", LoreExceptionType.VALIDATION_ERROR);
        }
        
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new LoreException("Invalid UUID format: " + uuidStr, LoreExceptionType.INVALID_FORMAT);
        }
    }
    
    /**
     * Validate player permissions
     * 
     * @param player The player to check
     * @param permission The permission to check for
     * @throws LoreException if the player doesn't have the required permission
     */
    public static void validatePermission(Player player, String permission) throws LoreException {
        if (!player.hasPermission(permission) && !player.isOp()) {
            throw new LoreException("You don't have permission to perform this action", 
                    LoreExceptionType.PERMISSION_ERROR);
        }
    }
}
