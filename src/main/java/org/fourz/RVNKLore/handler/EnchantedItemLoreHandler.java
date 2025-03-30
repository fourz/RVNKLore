package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handler for creating lore entries for enchanted items
 */
public class EnchantedItemLoreHandler extends DefaultLoreHandler {
    
    public EnchantedItemLoreHandler(RVNKLore plugin) {
        super(plugin);
    }
    
    @Override
    public void initialize() {
        debug.debug("Initializing enchanted item lore handler");
    }

    /**
     * Listen for enchant item events to create lore entries for special enchantments
     */
    @EventHandler
    public void onItemEnchant(EnchantItemEvent event) {
        if (isNotableEnchantment(event.getEnchantsToAdd())) {
            createEnchantmentLoreEntry(event);
        }
    }
    
    /**
     * Determine if this enchantment combination is significant enough to record
     */
    private boolean isNotableEnchantment(Map<Enchantment, Integer> enchants) {
        return enchants.values().stream().anyMatch(level -> level >= 4) || 
               enchants.size() >= 3;
    }
    
    /**
     * Create a lore entry for a significant enchanted item
     */
    private void createEnchantmentLoreEntry(EnchantItemEvent event) {
        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.ITEM);
        entry.setName("Enchantment of " + event.getItem().getType().name());
        
        StringBuilder desc = new StringBuilder();
        desc.append(event.getEnchanter().getName())
            .append(" created a powerful enchanted item at the cost of ")
            .append(event.getExpLevelCost())
            .append(" levels.");
        
        entry.setDescription(desc.toString());
        entry.setLocation(event.getEnchantBlock().getLocation());
        entry.setSubmittedBy(event.getEnchanter().getName());
        
        // Add metadata
        entry.addMetadata("enchanter_uuid", event.getEnchanter().getUniqueId().toString());
        entry.addMetadata("exp_cost", String.valueOf(event.getExpLevelCost()));
        
        // Auto-approve since it's system-generated
        entry.setApproved(true);
        
        plugin.getLoreManager().addLoreEntry(entry);
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Enchanted Item");
            lore.add("");
            lore.add(ChatColor.WHITE + entry.getDescription());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ITEM;
    }
}
