package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.gui.browse.LoreBrowseMainMenu;
import org.fourz.RVNKLore.gui.browse.LoreCategoryMenu;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for opening the lore browser GUI.
 * Usage:
 * - /lore browse - Open main browser
 * - /lore browse <type> - Open specific category
 */
public class LoreBrowseSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreBrowseSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreBrowseSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open main browser
            openMainMenu(player);
        } else {
            // Try to parse type
            String typeArg = args[0].toUpperCase();
            try {
                LoreType type = LoreType.valueOf(typeArg);
                openCategoryMenu(player, type);
            } catch (IllegalArgumentException e) {
                if (typeArg.equalsIgnoreCase("all")) {
                    openCategoryMenu(player, null);
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Unknown lore type: " + args[0]);
                    sender.sendMessage(ChatColor.GRAY + "Available types: " + getTypeList());
                }
            }
        }

        return true;
    }

    /**
     * Open the main browser menu.
     */
    private void openMainMenu(Player player) {
        LoreBrowseMainMenu menu = new LoreBrowseMainMenu(plugin, player);
        menu.open();
    }

    /**
     * Open a category menu.
     */
    private void openCategoryMenu(Player player, LoreType type) {
        LoreCategoryMenu menu = new LoreCategoryMenu(plugin, player, type);
        menu.open();
    }

    /**
     * Get comma-separated list of lore types.
     */
    private String getTypeList() {
        StringBuilder sb = new StringBuilder();
        for (LoreType type : LoreType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(type.name().toLowerCase());
        }
        return sb.toString();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add "all" option
            if ("all".startsWith(partial)) {
                completions.add("all");
            }

            // Add lore types
            for (LoreType type : LoreType.values()) {
                String typeName = type.name().toLowerCase();
                if (typeName.startsWith(partial)) {
                    completions.add(typeName);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.browse") || sender.hasPermission("rvnklore.use");
    }

    @Override
    public String getDescription() {
        return "Open the lore browser GUI";
    }
}
