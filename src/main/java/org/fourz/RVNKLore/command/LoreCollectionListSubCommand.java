package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.data.repository.CollectionRepository;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;
import org.fourz.RVNKLore.command.output.DisplayFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles the /lore collection list [theme] command.
 * Lists all collections, optionally filtered by theme, with progress and metadata.
 */
public class LoreCollectionListSubCommand implements org.fourz.RVNKLore.command.subcommand.SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final CollectionRepository collectionRepository;

    public LoreCollectionListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionListSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
        this.collectionRepository = databaseManager.getCollectionRepository();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "&c▶ This command can only be used by players");
            return true;
        }
        Player player = (Player) sender;
        String themeFilter = null;
        if (args.length > 0) {
            themeFilter = args[0];
        }
        final String filter = themeFilter;
        collectionRepository.getAllCollections().thenAccept(collections -> {
            List<ItemCollectionDTO> collectionsToShow = new ArrayList<>();
            if (filter != null) {
                CollectionTheme theme = CollectionTheme.fromDisplayName(filter);
                if (theme == null || theme == CollectionTheme.CUSTOM) {
                    player.sendMessage(ChatColor.RED + "&c✖ Unknown theme: " + filter);
                    listThemes(player, collections);
                    return;
                }
                for (ItemCollectionDTO collection : collections) {
                    if (theme.name().equalsIgnoreCase(collection.getThemeId())) {
                        collectionsToShow.add(collection);
                    }
                }
            } else {
                collectionsToShow.addAll(collections);
            }
            collectionsToShow.sort(Comparator.comparingLong(ItemCollectionDTO::getCreatedAt).reversed());
            DisplayFactory.displayCollectionList(player, collectionsToShow);
        }).exceptionally(e -> {
            logger.error("Error loading collections for list command", e);
            player.sendMessage(ChatColor.RED + "&c✖ Failed to load collections from database.");
            return null;
        });
        return true;
    }

    private void listThemes(Player player, List<ItemCollectionDTO> collections) {
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Available Themes");
        player.sendMessage("");
        for (CollectionTheme theme : CollectionTheme.values()) {
            int count = (int) collections.stream()
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
