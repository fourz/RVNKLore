package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item give <item_name> <player> command.
 * Looks up items by name from ItemManager and gives them to the specified player.
 * Supports all item types registered in the system.
 */
public class LoreItemGiveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemGiveSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.itemManager = plugin.getLoreManager().getItemManager();
    }

    /**
     * Handles the /lore item give <item_name> <player> command.
     * Looks up items by name from ItemManager and gives them to the specified player.
     * Supports all item types registered in the system.
     */
    public LoreItemGiveSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give");
    }    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item give <item_name> <player>");
            sender.sendMessage(ChatColor.GRAY + "   Give any registered lore item to a player");
            return true;
        }
        String playerName = args[args.length - 1];
        String itemName = stripQuotes(String.join(" ", java.util.Arrays.copyOfRange(args, 0, args.length - 1)));
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player '" + playerName + "' not found or not online.");
            return true;
        }
        if (itemManager == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to give item: " + itemName, null);
            return true;
        }
        // Accept a numeric database id as well as a display name (#1887). This branch was missing
        // entirely: give always went down the name-only path, so `give 159 <player>` looked up an
        // item literally named "159", found nothing, and reported "Item not found: 159" — while
        // `info 159` and `spawn 159` resolved the same item fine. Mirrors LoreItemSpawnSubCommand
        // so the two commands agree on what an argument means.
        if (isNumericId(itemName)) {
            int itemId = Integer.parseInt(itemName.trim());
            itemManager.getItemPropertiesById(itemId).thenAccept(optProps -> {
                String resolvedName = optProps.map(p -> p.getDisplayName()).orElse(itemName);
                itemManager.createLoreItem(itemId).thenAccept(opt ->
                    Bukkit.getScheduler().runTask(plugin, () -> deliver(sender, opt, resolvedName, target)));
            });
            return true;
        }

        // Name path stays async too — createLoreItemSync blocks the main thread on a DB read when
        // the name is not cached, which on the cross-host MySQL is a real stall (#1856).
        itemManager.createLoreItem(itemName).thenAccept(opt ->
            Bukkit.getScheduler().runTask(plugin, () -> deliver(sender, opt, itemName, target)));
        return true;
    }

    private void deliver(CommandSender sender, java.util.Optional<org.bukkit.inventory.ItemStack> opt,
                         String resolvedName, Player target) {
        if (opt == null || opt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + resolvedName);
            return;
        }
        target.getInventory().addItem(opt.get());
        sender.sendMessage(ChatColor.GREEN + "✓ Gave " + resolvedName + " to " + target.getName());
    }

    private boolean isNumericId(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Give any lore item by name to a player.";
    }

    private String stripQuotes(String value) {
        if (value != null && value.length() > 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNamesForCommands());
        } else if (args.length == 2) {
            // Suggest online player names for the second argument
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        return completions;
    }
}
