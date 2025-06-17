package org.fourz.RVNKLore.lore.item.enchant;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;

import java.util.Map;

/**
 * Manages enchanted item creation and distribution with configurable properties.
 * Acts as the central registry for all enchanted items within the lore system.
 * 
 * This manager consolidates the functionality previously handled by EnchantedItemGenerator
 * and provides additional features for enchantment profiles, tiers, and rarity management.
 */
public class EnchantManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final EnchantedItemGenerator generator;
    
    public EnchantManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "EnchantManager");
        this.generator = new EnchantedItemGenerator(plugin);
        
        logger.info("EnchantManager initialized");
    }
    
    /**
     * Create an enchanted item using ItemProperties configuration.
     * 
     * @param properties The item properties including enchantments and metadata
     * @return The enchanted ItemStack
     */
    public ItemStack createEnchantedItem(ItemProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("ItemProperties cannot be null");
        }
        
        Material material = properties.getMaterial();
        if (material == null) {
            material = Material.ENCHANTED_BOOK; // Default for enchanted items
        }
        
        String displayName = properties.getDisplayName();
        Map<Enchantment, Integer> enchantments = properties.getEnchantments();
        
        ItemStack item = generator.createEnchantedItem(material, enchantments, displayName);
        
        // Apply additional properties
        if (properties.getLore() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setLore(properties.getLore());
                item.setItemMeta(meta);
            }
        }
        
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(properties.getCustomModelData());
            item.setItemMeta(meta);
        }
        
        
        logger.debug("Created enchanted item through EnchantManager: " + displayName);
        return item;
    }
    
    /**
     * Create an enchanted item with the given material, enchantments, and display name.
     * This method provides backward compatibility with the original EnchantedItemGenerator API.
     *
     * @param material The item material
     * @param enchantments Map of enchantments and their levels
     * @param displayName The display name for the item
     * @return The enchanted ItemStack
     */
    public ItemStack createEnchantedItem(Material material, Map<Enchantment, Integer> enchantments, String displayName) {
        return generator.createEnchantedItem(material, enchantments, displayName);
    }
    
    /**
     * Create an enchanted item using an enchantment profile.
     * 
     * @param material The base material for the item
     * @param profile The enchantment profile containing enchantments and tier
     * @param displayName The display name for the item
     * @return The enchanted ItemStack
     */
    public ItemStack createEnchantedItem(Material material, EnchantmentProfile profile, String displayName) {
        if (profile == null) {
            // Explicitly cast null to resolve method ambiguity
            return createEnchantedItem(material, (Map<Enchantment, Integer>) null, displayName);
        }
        
        ItemStack item = createEnchantedItem(material, profile.getEnchantments(), displayName);
        
        // Apply tier-specific modifications
        if (profile.getTier() != null) {
            applyTierEffects(item, profile.getTier());
        }
        
        return item;
    }
    
    /**
     * Create an enchanted item using a template.
     * 
     * @param template The enchantment template containing base configuration
     * @param displayName The display name for the item
     * @return The enchanted ItemStack
     */
    public ItemStack createEnchantedItem(EnchantmentTemplate template, String displayName) {
        if (template == null) {
            throw new IllegalArgumentException("EnchantmentTemplate cannot be null");
        }
        
        return createEnchantedItem(template.getMaterial(), template.getBaseEnchantments(), displayName);
    }
    
    /**
     * Apply tier-specific effects to an enchanted item.
     * 
     * @param item The item to modify
     * @param tier The enchantment tier
     */
    private void applyTierEffects(ItemStack item, EnchantmentTier tier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Apply tier-specific display name coloring
        String currentName = meta.getDisplayName();
        if (currentName != null && !currentName.isEmpty()) {
            String tierColor = getTierColor(tier);
            if (!currentName.startsWith("§")) {
                meta.setDisplayName(tierColor + currentName);
            }
        }
        
        item.setItemMeta(meta);
    }
    
    /**
     * Get the color code for an enchantment tier.
     * 
     * @param tier The enchantment tier
     * @return The color code string
     */
    private String getTierColor(EnchantmentTier tier) {
        switch (tier) {
            case COMMON:
                return "§f"; // White
            case UNCOMMON:
                return "§a"; // Green
            case RARE:
                return "§9"; // Blue
            case EPIC:
                return "§5"; // Purple
            case LEGENDARY:
                return "§6"; // Gold
            default:
                return "§7"; // Gray
        }
    }
    
    /**
     * Get the underlying enchanted item generator.
     * 
     * @return The EnchantedItemGenerator instance
     */
    public EnchantedItemGenerator getGenerator() {
        return generator;
    }
    
    /**
     * Shutdown the enchant manager and clean up resources.
     */
    public void shutdown() {
        logger.info("EnchantManager shutdown");
    }
}
