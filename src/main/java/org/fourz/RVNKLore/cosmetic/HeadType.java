package org.fourz.RVNKLore.cosmetic;

/**
 * Enumeration of head types for classification and handling logic.
 * Each type represents a different source and handling method for head textures.
 */
public enum HeadType {
    /**
     * Player head using actual player skin data
     */
    PLAYER,
    
    /**
     * Mob head using predefined mob textures
     */
    MOB,
    
    /**
     * Custom head with manually provided texture data
     */
    CUSTOM,
    
    /**
     * Animated head with multiple texture frames
     */
    ANIMATED,
    
    /**
     * Cosmetic hat or accessory head
     */
    HAT
}
