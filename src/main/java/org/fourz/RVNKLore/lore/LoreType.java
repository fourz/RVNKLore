package org.fourz.RVNKLore.lore;

/**
 * Enum representing different types of lore entries
 */
public enum LoreType {
    PLAYER_HEAD("Player head"),
    MOB_HEAD("Mob head"),
    HEAD("Custom player with custom texture"),    
    HAT("Wearable head item with custom model"),
    LANDMARK("Notable location or structure"),
    CITY("Player-built settlement or city"),
    PATH("Road, trail, or navigation path"),
    ENCHANTMENT("Custom enchantment or magical effect"),    
    ITEM("Item with special lore"),
    PLAYER("Server players"),
    FACTION("Faction-related lore"),
    QUEST("Quest-related lore"),    
    OTHER("Miscellaneous lore entry");    
    private final String description;
    
    LoreType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static LoreType fromString(String typeStr) {
        try {
            return valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
