package org.fourz.RVNKLore.gui.browse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.gui.ItemBuilder;
import org.fourz.RVNKLore.gui.MenuHolder;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu showing details of a single lore entry.
 */
public class LoreDetailMenu extends MenuHolder {

    private final RVNKLore plugin;
    private final LoreEntry entry;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy");

    public LoreDetailMenu(RVNKLore plugin, Player viewer, LoreEntry entry) {
        super(viewer, ChatColor.DARK_PURPLE + "✦ " + entry.getName(), 54);
        this.plugin = plugin;
        this.entry = entry;
    }

    @Override
    protected void build() {
        inventory.clear();
        clickHandlers.clear();

        // Fill border
        fillBorder();

        // Main info item (center top)
        inventory.setItem(4, createMainInfoItem());

        // Type info
        inventory.setItem(20, createTypeItem());

        // Description
        inventory.setItem(22, createDescriptionItem());

        // Author/Submitted by
        inventory.setItem(24, createAuthorItem());

        // Location (if applicable)
        if (entry.getLocation() != null) {
            inventory.setItem(30, createLocationItem());
        }

        // Metadata
        if (entry.hasMetadata()) {
            inventory.setItem(32, createMetadataItem());
        }

        // Date info
        inventory.setItem(40, createDateItem());

        // Action buttons
        addActionButtons();

        // Navigation
        if (parent != null) {
            inventory.setItem(45, ItemBuilder.backButton());
            setClickHandler(45, event -> goBack());
        }

        inventory.setItem(53, ItemBuilder.closeButton());
        setClickHandler(53, event -> close());
    }

    /**
     * Fill border with glass.
     */
    private void fillBorder() {
        var filler = ItemBuilder.filler();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
            inventory.setItem(45 + i, filler);
        }
        inventory.setItem(9, filler);
        inventory.setItem(17, filler);
        inventory.setItem(18, filler);
        inventory.setItem(26, filler);
        inventory.setItem(27, filler);
        inventory.setItem(35, filler);
        inventory.setItem(36, filler);
        inventory.setItem(44, filler);
    }

    /**
     * Create main info item.
     */
    private org.bukkit.inventory.ItemStack createMainInfoItem() {
        String typeColor = getTypeColor(entry.getType());
        return new ItemBuilder(getMaterialForType(entry.getType()))
            .name(typeColor + "&l" + entry.getName())
            .lore(
                "",
                "&7ID: &8" + entry.getId().substring(0, 8),
                "&7Status: " + (entry.isApproved() ? "&aApproved" : "&cPending"),
                "",
                "&eThis is a " + formatTypeName(entry.getType()) + " entry"
            )
            .glow()
            .build();
    }

    /**
     * Create type info item.
     */
    private org.bukkit.inventory.ItemStack createTypeItem() {
        String typeColor = getTypeColor(entry.getType());
        return new ItemBuilder(Material.NAME_TAG)
            .name("&eType")
            .lore(
                "",
                typeColor + formatTypeName(entry.getType()),
                "",
                "&7" + getTypeDescription(entry.getType())
            )
            .build();
    }

    /**
     * Create description item.
     */
    private org.bukkit.inventory.ItemStack createDescriptionItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");

        String desc = entry.getDescription();
        if (desc == null || desc.isEmpty()) {
            lore.add("&8No description available.");
        } else {
            // Word wrap description
            String[] words = desc.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() + word.length() > 35) {
                    lore.add("&7" + line.toString().trim());
                    line = new StringBuilder();
                }
                line.append(word).append(" ");
            }
            if (line.length() > 0) {
                lore.add("&7" + line.toString().trim());
            }
        }

        return new ItemBuilder(Material.BOOK)
            .name("&eDescription")
            .lore(lore)
            .build();
    }

    /**
     * Create author item.
     */
    private org.bukkit.inventory.ItemStack createAuthorItem() {
        String author = entry.getSubmittedBy();
        if (author == null) author = "Unknown";

        return new ItemBuilder(Material.PLAYER_HEAD)
            .name("&eAuthor")
            .lore(
                "",
                "&7Submitted by:",
                "&f" + author
            )
            .build();
    }

    /**
     * Create location item.
     */
    private org.bukkit.inventory.ItemStack createLocationItem() {
        Location loc = entry.getLocation();
        return new ItemBuilder(Material.COMPASS)
            .name("&eLocation")
            .lore(
                "",
                "&7World: &f" + loc.getWorld().getName(),
                "&7X: &f" + (int) loc.getX(),
                "&7Y: &f" + (int) loc.getY(),
                "&7Z: &f" + (int) loc.getZ()
            )
            .build();
    }

    /**
     * Create metadata item.
     */
    private org.bukkit.inventory.ItemStack createMetadataItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");

        var metadata = entry.getAllMetadata();
        for (var meta : metadata.entrySet()) {
            String key = meta.getKey().replace("_", " ");
            key = key.substring(0, 1).toUpperCase() + key.substring(1);
            lore.add("&7" + key + ": &f" + meta.getValue());
        }

        return new ItemBuilder(Material.PAPER)
            .name("&eAdditional Info")
            .lore(lore)
            .build();
    }

    /**
     * Create date item.
     */
    private org.bukkit.inventory.ItemStack createDateItem() {
        String dateStr = "Unknown";
        if (entry.getCreatedAt() != null) {
            dateStr = DATE_FORMAT.format(entry.getCreatedAt());
        }

        return new ItemBuilder(Material.CLOCK)
            .name("&eCreated")
            .lore(
                "",
                "&7Date: &f" + dateStr
            )
            .build();
    }

    /**
     * Add action buttons.
     */
    private void addActionButtons() {
        // Get Book button
        inventory.setItem(48, new ItemBuilder(Material.WRITTEN_BOOK)
            .name("&aGet as Book")
            .lore(
                "&7Get this lore entry",
                "&7as a written book item.",
                "",
                "&eClick to receive book"
            )
            .build());
        setClickHandler(48, event -> {
            var bookManager = plugin.getLoreManager().getItemManager();
            // Use the LoreBookSubCommand's book manager or create book directly
            // For now, just notify
            viewer.sendMessage(ChatColor.YELLOW + "Use /lore book give " + viewer.getName() +
                " " + entry.getId().substring(0, 8) + " to get this as a book!");
        });

        // Share button (for admins)
        if (viewer.hasPermission("rvnklore.share")) {
            inventory.setItem(50, new ItemBuilder(Material.ENDER_PEARL)
                .name("&dShare Entry")
                .lore(
                    "&7Broadcast this lore",
                    "&7entry to all players.",
                    "",
                    "&eClick to share"
                )
                .build());
            setClickHandler(50, event -> {
                plugin.getServer().broadcastMessage(ChatColor.GOLD + "✦ " + viewer.getName() +
                    " shared lore: " + ChatColor.YELLOW + entry.getName());
                viewer.sendMessage(ChatColor.GREEN + "Lore entry shared!");
            });
        }
    }

    // Helper methods from LoreCategoryMenu
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

    private String formatTypeName(LoreType type) {
        if (type == null) return "Unknown";
        String name = type.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String getTypeDescription(LoreType type) {
        if (type == null) return "No description";
        switch (type) {
            case LANDMARK: return "A notable location in the world";
            case CITY: return "A settlement or civilization";
            case PLAYER: return "A legendary character or hero";
            case FACTION: return "A group or organization";
            case ITEM: return "A legendary weapon or artifact";
            case HEAD: return "A decorative head item";
            case EVENT: return "A historical moment";
            case PATH: return "An ancient road or route";
            case QUEST: return "An adventure or journey";
            case ENCHANTMENT: return "A magical enhancement";
            case GENERIC: return "General lore entry";
            default: return "Lore entry";
        }
    }
}
