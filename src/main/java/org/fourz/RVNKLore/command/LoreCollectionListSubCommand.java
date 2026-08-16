package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;
import org.fourz.RVNKLore.lore.item.collection.LoreCollection;
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
        this.collectionManager = plugin.getLoreManager().getItemManager().getCollectionManager();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String themeFilter = null;
        if (args.length > 0) {
            themeFilter = args[0];
        }

        List<LoreCollection> collectionsToShow = new ArrayList<>();
        if (themeFilter != null) {
            // parse(), not fromDisplayName(): the latter returns CUSTOM when nothing matches, so it
            // cannot tell garbage input from a real request for the CUSTOM theme (#1955).
            CollectionTheme theme = CollectionTheme.parse(themeFilter);
            if (theme == null) {
                sender.sendMessage(ChatColor.RED + "Unknown theme: " + themeFilter);
                listThemes(sender);
                return true;
            }
            for (LoreCollection collection : collectionManager.getAllCollectionsSync().values()) {
                if (theme.name().equalsIgnoreCase(collection.getThemeId())) {
                    collectionsToShow.add(collection);
                }
            }
        } else {
            collectionsToShow.addAll(collectionManager.getAllCollectionsSync().values());
        }

        // Sort newest to oldest
        collectionsToShow.sort(Comparator.comparingLong(LoreCollection::getCreatedAt).reversed());

        // Output via DisplayFactory (player only) or console output
        if (sender instanceof Player) {
            DisplayFactory.displayCollectionList((Player) sender, collectionsToShow);
        } else {
            // Console output: simple list format
            sender.sendMessage(ChatColor.YELLOW + "Collections (" + collectionsToShow.size() + " total):");
            for (LoreCollection collection : collectionsToShow) {
                sender.sendMessage("  - [" + collection.getId() + "] " + collection.getName()
                        + " (" + DisplayFactory.formatCollectionCount(collection) + ")");
            }
        }

        return true;
    }

    /**
     * List the themes that currently hold collections.
     *
     * Takes a CommandSender rather than a Player so the console gets the same help. It previously
     * required a Player purely to call sendMessage, which left a console operator with a bare
     * "Unknown theme" and no way to discover valid values (#1955).
     */
    private void listThemes(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Available Themes");
        sender.sendMessage("");
        // Every theme is listed, not only the populated ones. This is reached from the error path
        // for an unrecognised theme, where the question being answered is "what may I type?" —
        // hiding the empty ones answers a different question and leaves the list blank on a server
        // whose collections are all untagged.
        for (CollectionTheme theme : CollectionTheme.values()) {
            int count = (int) collectionManager.getAllCollectionsSync().values().stream()
                    .filter(c -> theme.name().equalsIgnoreCase(c.getThemeId()))
                    .count();
            String suffix = count > 0 ? ChatColor.GRAY + " (" + count + " collections)" : "";
            sender.sendMessage(ChatColor.WHITE + theme.getDisplayName() + suffix);
            sender.sendMessage(ChatColor.GRAY + "   " + theme.getDescription());
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection list <theme> " + ChatColor.GRAY + "to view theme collections");
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
