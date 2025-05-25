package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.HeadUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified handler for all head-related lore entries (player, mob, custom heads, and hats)
 */
public class CommonHeadHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Constants for metadata keys
    public static final String META_HEAD_TYPE = "head_type";
    public static final String META_HEAD_OWNER = "head_owner";
    public static final String META_TEXTURE_DATA = "texture_data";
    public static final String META_MOB_TYPE = "mob_type";
    public static final String META_CUSTOM_MODEL_DATA = "custom_model_data";
    
    // Constants for head types
    public static final String TYPE_PLAYER = "player";
    public static final String TYPE_MOB = "mob";
    public static final String TYPE_CUSTOM = "custom";
    public static final String TYPE_HAT = "hat";
    
    public CommonHeadHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CommonHeadHandler");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing common head handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            logger.debug("Head lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            logger.debug("Head lore validation failed: Description is required");
            return false;
        }
        
        // Validate based on head type
        String headType = entry.getMetadata(META_HEAD_TYPE);
        if (headType == null || headType.isEmpty()) {
            logger.debug("Head lore validation failed: Head type is required");
            return false;
        }
        
        switch (headType) {
            case TYPE_PLAYER:
                if (entry.getMetadata(META_HEAD_OWNER) == null) {
                    logger.debug("Player head validation failed: Owner is required");
                    return false;
                }
                break;
                
            case TYPE_MOB:
                if (entry.getMetadata(META_MOB_TYPE) == null) {
                    logger.debug("Mob head validation failed: Mob type is required");
                    return false;
                }
                break;
                
            case TYPE_CUSTOM:
                if (entry.getMetadata(META_TEXTURE_DATA) == null) {
                    logger.debug("Custom head validation failed: Texture data is required");
                    return false;
                }
                break;
                
            case TYPE_HAT:
                // Hats may use either texture data or custom model data
                break;
                
            default:
                logger.debug("Unknown head type: " + headType);
                return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta == null) {
            logger.warning("Failed to get SkullMeta for head item");
            return item;
        }
        
        // Apply common properties
        meta.setDisplayName(ChatColor.GOLD + entry.getName());
        
        // Apply head-type specific properties
        String headType = entry.getMetadata(META_HEAD_TYPE);
        if (headType == null) {
            headType = TYPE_CUSTOM; // Default to custom if not specified
        }
        
        try {
            applyHeadTypeProperties(meta, entry, headType);
        } catch (Exception e) {
            logger.error("Error applying head properties", e);
        }
        
        // Create lore text
        List<String> lore = createLoreText(entry, headType);
        meta.setLore(lore);
        
        // If custom model data is specified, apply it
        String customModelData = entry.getMetadata(META_CUSTOM_MODEL_DATA);
        if (customModelData != null && !customModelData.isEmpty()) {
            try {
                meta.setCustomModelData(Integer.parseInt(customModelData));
            } catch (NumberFormatException e) {
                logger.debug("Invalid custom model data: " + customModelData);
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Apply properties specific to the head type
     */
    private void applyHeadTypeProperties(SkullMeta meta, LoreEntry entry, String headType) {
        switch (headType) {
            case TYPE_PLAYER:
                String owner = entry.getMetadata(META_HEAD_OWNER);
                if (owner != null && !owner.isEmpty()) {
                    meta.setOwner(owner);
                }
                break;
                
            case TYPE_MOB:
                String mobType = entry.getMetadata(META_MOB_TYPE);
                if (mobType != null && !mobType.isEmpty()) {
                    try {
                        EntityType entityType = EntityType.valueOf(mobType.toUpperCase());
                        // Apply mob skull texture - using a hypothetical HeadUtil class
                        HeadUtil.applyMobTexture(meta, entityType);
                    } catch (IllegalArgumentException e) {
                        logger.debug("Invalid mob type: " + mobType);
                    }
                }
                break;
                
            case TYPE_CUSTOM:
            case TYPE_HAT:
                String textureData = entry.getMetadata(META_TEXTURE_DATA);
                if (textureData != null && !textureData.isEmpty()) {
                    // Apply custom texture data - using a hypothetical HeadUtil class
                    HeadUtil.applyTextureData(meta, textureData);
                }
                break;
        }
    }

    /**
     * Create the lore text based on head type
     */
    private List<String> createLoreText(LoreEntry entry, String headType) {
        List<String> lore = new ArrayList<>();
        
        // Add type-specific header
        switch (headType) {
            case TYPE_PLAYER:
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Player Head");
                String owner = entry.getMetadata(META_HEAD_OWNER);
                if (owner != null && !owner.isEmpty()) {
                    lore.add(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + owner);
                }
                break;
                
            case TYPE_MOB:
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Mob Head");
                String mobType = entry.getMetadata(META_MOB_TYPE);
                if (mobType != null && !mobType.isEmpty()) {
                    lore.add(ChatColor.GRAY + "Mob: " + ChatColor.YELLOW + formatMobType(mobType));
                }
                break;
                
            case TYPE_CUSTOM:
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Custom Head");
                break;
                
            case TYPE_HAT:
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Decorative Hat");
                break;
        }
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            lore.add(ChatColor.GRAY + "Created by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        lore.add("");
        
        // Split description into lines for better readability
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            lore.add(ChatColor.WHITE + line);
        }
        
        return lore;
    }

    /**
     * Format mob type for display
     */
    private String formatMobType(String mobType) {
        if (mobType == null || mobType.isEmpty()) return "";
        
        // Convert SNAKE_CASE to Title Case
        String[] words = mobType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        
        return result.toString().trim();
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        String headType = entry.getMetadata(META_HEAD_TYPE);
        if (headType == null) {
            headType = TYPE_CUSTOM; // Default
        }
        
        // Display header based on type
        player.sendMessage(ChatColor.GOLD + "==== " + entry.getName() + " ====");
        
        switch (headType) {
            case TYPE_PLAYER:
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Player Head");
                String owner = entry.getMetadata(META_HEAD_OWNER);
                if (owner != null && !owner.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + owner);
                }
                break;
                
            case TYPE_MOB:
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Mob Head");
                String mobType = entry.getMetadata(META_MOB_TYPE);
                if (mobType != null && !mobType.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "Mob: " + ChatColor.YELLOW + formatMobType(mobType));
                }
                break;
                
            case TYPE_CUSTOM:
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Custom Head");
                break;
                
            case TYPE_HAT:
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Decorative Hat");
                break;
        }
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Created by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.HEAD; // Use HEAD as the common type
    }
}
