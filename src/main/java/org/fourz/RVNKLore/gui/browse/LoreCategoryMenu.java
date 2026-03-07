package org.fourz.RVNKLore.gui.browse;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.gui.ItemBuilder;
import org.fourz.RVNKLore.gui.PaginatedMenu;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Menu for browsing lore entries of a specific type.
 */
public class LoreCategoryMenu extends PaginatedMenu<LoreEntry> {

    private final RVNKLore plugin;
    private final LoreType type;

    public LoreCategoryMenu(RVNKLore plugin, Player viewer, LoreType type) {
        super(viewer, buildTitle(type), getEntriesForType(plugin, type));
        this.plugin = plugin;
        this.type = type;
    }

    private static String buildTitle(LoreType type) {
        if (type == null) {
            return ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "All Lore";
        }
        String typeName = type.name().charAt(0) + type.name().substring(1).toLowerCase();
        return ChatColor.DARK_PURPLE + "✦ " + typeName + " Lore";
    }

    private static List<LoreEntry> getEntriesForType(RVNKLore plugin, LoreType type) {
        return plugin.getLoreManager().getAllLoreEntriesSync().stream()
            .filter(e -> type == null || e.getType() == type)
            .sorted(Comparator.comparing(LoreEntry::getName))
            .collect(Collectors.toList());
    }

    @Override
    protected ItemStack createItemDisplay(LoreEntry entry) {
        Material material = getMaterialForType(entry.getType());

        String typeColor = getTypeColor(entry.getType());
        String shortId = entry.getId().substring(0, 8);

        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("&7Type: " + typeColor + formatTypeName(entry.getType()));

        // Truncate description
        String desc = entry.getDescription();
        if (desc != null && !desc.isEmpty()) {
            if (desc.length() > 40) {
                desc = desc.substring(0, 37) + "...";
            }
            lore.add("");
            lore.add("&8" + desc);
        }

        lore.add("");
        lore.add("&7ID: &8" + shortId);
        lore.add("");

        // TODO: Check if discovered
        lore.add("&eClick for details");

        return new ItemBuilder(material)
            .name(typeColor + entry.getName())
            .lore(lore)
            .build();
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, LoreEntry entry) {
        LoreDetailMenu detailMenu = new LoreDetailMenu(plugin, viewer, entry);
        detailMenu.setParent(this);
        detailMenu.open();
    }

    /**
     * Get material for a lore type.
     */
    private Material getMaterialForType(LoreType type) {
        if (type == null) return Material.PAPER;
        switch (type) {
            case LANDMARK: return Material.GRASS_BLOCK;
            case CITY: return Material.BRICKS;
            case PLAYER: return Material.PLAYER_HEAD;
            case FACTION: return Material.PURPLE_BANNER;
            case ITEM: return Material.DIAMOND_SWORD;
            case HEAD: return Material.ZOMBIE_HEAD;
            case EVENT: return Material.CLOCK;
            case PATH: return Material.COMPASS;
            case QUEST: return Material.WRITABLE_BOOK;
            case ENCHANTMENT: return Material.ENCHANTED_BOOK;
            case GENERIC: return Material.PAPER;
            default: return Material.PAPER;
        }
    }

    /**
     * Get color code for a lore type.
     */
    private String getTypeColor(LoreType type) {
        if (type == null) return "&f";
        switch (type) {
            case LANDMARK: return "&2";
            case CITY: return "&6";
            case PLAYER: return "&b";
            case FACTION: return "&5";
            case ITEM: return "&e";
            case HEAD: return "&9";
            case EVENT: return "&c";
            case PATH: return "&a";
            case QUEST: return "&d";
            case ENCHANTMENT: return "&3";
            case GENERIC: return "&7";
            default: return "&f";
        }
    }

    /**
     * Format type name for display.
     */
    private String formatTypeName(LoreType type) {
        if (type == null) return "Unknown";
        String name = type.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
