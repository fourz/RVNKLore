package org.fourz.RVNKLore.gui.browse;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.gui.ItemBuilder;
import org.fourz.RVNKLore.gui.MenuHolder;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Main menu for the lore browser.
 * Shows categories of lore types to browse.
 */
public class LoreBrowseMainMenu extends MenuHolder {

    private final RVNKLore plugin;

    public LoreBrowseMainMenu(RVNKLore plugin, Player viewer) {
        super(viewer, ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "Lore Browser", 54);
        this.plugin = plugin;
    }

    @Override
    protected void build() {
        inventory.clear();
        clickHandlers.clear();

        // Fill with glass pane border
        fillBorder();

        // Category items
        addCategoryItem(10, Material.GRASS_BLOCK, LoreType.LANDMARK,
            "&2Landmarks", "&7Discover notable locations", "&7across the world.");

        addCategoryItem(12, Material.BRICKS, LoreType.CITY,
            "&6Cities", "&7Explore settlements and", "&7civilizations.");

        addCategoryItem(14, Material.PLAYER_HEAD, LoreType.PLAYER,
            "&bPlayers", "&7Learn about legendary", "&7characters and heroes.");

        addCategoryItem(16, Material.PURPLE_BANNER, LoreType.FACTION,
            "&5Factions", "&7Uncover groups and", "&7organizations.");

        addCategoryItem(28, Material.DIAMOND_SWORD, LoreType.ITEM,
            "&eItems", "&7Find legendary weapons", "&7and artifacts.");

        addCategoryItem(30, Material.ZOMBIE_HEAD, LoreType.HEAD,
            "&9Heads", "&7Collect decorative", "&7head items.");

        addCategoryItem(32, Material.CLOCK, LoreType.EVENT,
            "&cEvents", "&7Witness historical", "&7moments.");

        addCategoryItem(34, Material.COMPASS, LoreType.PATH,
            "&2Paths", "&7Trace ancient roads", "&7and trade routes.");

        // Quest and Enchantment
        addCategoryItem(20, Material.WRITABLE_BOOK, LoreType.QUEST,
            "&dQuests", "&7Adventures and epic", "&7journeys.");

        addCategoryItem(24, Material.ENCHANTING_TABLE, LoreType.ENCHANTMENT,
            "&bEnchantments", "&7Magical enhancements", "&7and powers.");

        // Special center item - All lore
        inventory.setItem(22, new ItemBuilder(Material.NETHER_STAR)
            .name("&6✦ &lAll Lore")
            .lore(
                "&7Browse all lore entries",
                "",
                "&eClick to view everything"
            )
            .glow()
            .build());
        setClickHandler(22, event -> openCategoryMenu(null));

        // Search button
        inventory.setItem(40, new ItemBuilder(Material.OAK_SIGN)
            .name("&e⌕ Search")
            .lore("&7Search for specific lore", "&7entries by name or keyword")
            .build());
        setClickHandler(40, event -> {
            // TODO: Implement search via anvil GUI
            viewer.sendMessage(ChatColor.YELLOW + "Search feature coming soon!");
        });

        // Close button
        inventory.setItem(49, ItemBuilder.closeButton());
        setClickHandler(49, event -> close());

        // Stats in bottom corners
        addStatsDisplay();
    }

    /**
     * Fill border with glass panes.
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
     * Add a category item to the menu.
     */
    private void addCategoryItem(int slot, Material material, LoreType type,
                                  String name, String... loreLines) {
        // Get count for this type
        long count = plugin.getLoreManager().getAllLoreEntriesSync().stream()
            .filter(e -> e.getType() == type)
            .count();

        var builder = new ItemBuilder(material)
            .name(name)
            .lore(loreLines)
            .addLore("")
            .addLore("&7Entries: &f" + count)
            .addLore("")
            .addLore("&eClick to browse");

        if (count > 0) {
            builder.glow();
        }

        inventory.setItem(slot, builder.build());
        setClickHandler(slot, event -> openCategoryMenu(type));
    }

    /**
     * Open a category menu.
     */
    private void openCategoryMenu(LoreType type) {
        LoreCategoryMenu categoryMenu = new LoreCategoryMenu(plugin, viewer, type);
        categoryMenu.setParent(this);
        categoryMenu.open();
    }

    /**
     * Add stats display to bottom corners.
     */
    private void addStatsDisplay() {
        int totalEntries = plugin.getLoreManager().getAllLoreEntriesSync().size();
        // TODO: Get discovered count from PlayerService
        int discovered = 0;

        inventory.setItem(45, new ItemBuilder(Material.BOOK)
            .name("&eYour Progress")
            .lore(
                "&7Discovered: &f" + discovered + "/" + totalEntries,
                "&7Completion: &f" + (totalEntries > 0 ? (discovered * 100 / totalEntries) : 0) + "%"
            )
            .build());

        int achievementPoints = 0;
        if (plugin.getAchievementManager() != null) {
            achievementPoints = plugin.getAchievementManager().getPlayerPoints(viewer.getUniqueId());
        }

        inventory.setItem(53, new ItemBuilder(Material.GOLD_INGOT)
            .name("&6Achievement Points")
            .lore("&7Total: &f" + achievementPoints)
            .build());
    }
}
