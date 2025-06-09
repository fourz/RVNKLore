package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.command.output.DisplayFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Handles the /lore collection list [theme] command.
 * Lists all collections, optionally filtered by theme, with progress and metadata.
 */
public class LoreCollectionListSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CollectionManager collectionManager;

    public LoreCollectionListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionListSubCommand");
        this.collectionManager = plugin.getItemManager().getCollectionManager();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "▶ This command can only be used by players");
            return true;
        }
        Player player = (Player) sender;

        String themeFilter = null;
        if (args.length > 0) {
            themeFilter = args[0];
        }

        List<ItemCollection> collectionsToShow = new ArrayList<>();
        if (themeFilter != null) {
            CollectionTheme theme = CollectionTheme.fromDisplayName(themeFilter);
            if (theme == null || theme == CollectionTheme.CUSTOM) {
                player.sendMessage(ChatColor.RED + "✖ Unknown theme: " + themeFilter);
                listThemes(player);
                return true;
            }
            for (ItemCollection collection : collectionManager.getAllCollections().values()) {
                if (theme.name().equalsIgnoreCase(collection.getThemeId())) {
                    collectionsToShow.add(collection);
                }
            }
        } else {
            collectionsToShow.addAll(collectionManager.getAllCollections().values());
        }

        // Sort newest to oldest
        collectionsToShow.sort(Comparator.comparingLong(ItemCollection::getCreatedAt).reversed());

        // Output via DisplayFactory
        DisplayFactory.displayCollectionList(player, collectionsToShow);

        return true;
    }

    private void listThemes(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Available Themes");
        player.sendMessage("");
        for (CollectionTheme theme : CollectionTheme.values()) {
            int count = (int) collectionManager.getAllCollections().values().stream()
                    .filter(c -> theme.name().equalsIgnoreCase(c.getThemeId()))
                    .count();
            if (count > 0) {
                player.sendMessage(ChatColor.WHITE + theme.getDisplayName() + " " + ChatColor.GRAY + "(" + count + " collections)");
                player.sendMessage(ChatColor.GRAY + "   " + theme.getDescription());
            }
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection list <theme> " + ChatColor.GRAY + "to view theme collections");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!hasPermission(sender)) return completions;
        if (args.length == 1) {
            for (CollectionTheme theme : CollectionTheme.values()) {
                completions.add(theme.getDisplayName());
            }
        }
        return completions;
    }

    @Override
    public String getDescription() {
        return "List all item collections, optionally filtered by theme.";
    }
}
