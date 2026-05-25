package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles /lore item spawn <name|id> <player>.
 * Looks up lore items by display name or numeric database ID.
 * Console-safe: sender can be any CommandSender.
 */
public class LoreItemSpawnSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemSpawnSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemSpawnSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.spawn");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item spawn <name|id> <player>");
            sender.sendMessage(ChatColor.GRAY + "   Spawn a lore item by name or numeric ID into a player's inventory");
            return true;
        }

        String playerName = args[args.length - 1];
        String nameOrId = String.join(" ", java.util.Arrays.copyOfRange(args, 0, args.length - 1));

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player '" + playerName + "' not found or not online.");
            return true;
        }

        if (itemManager == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item system is not available.");
            logger.error("ItemManager is null during spawn attempt for: " + nameOrId, null);
            return true;
        }

        // Detect numeric ID vs name
        if (isNumericId(nameOrId)) {
            int itemId = Integer.parseInt(nameOrId.trim());
            itemManager.createLoreItem(itemId).thenAccept(opt -> {
                Bukkit.getScheduler().runTask(plugin, () -> deliverResult(sender, opt, nameOrId, target));
            });
        } else {
            itemManager.createLoreItem(nameOrId).thenAccept(opt -> {
                Bukkit.getScheduler().runTask(plugin, () -> deliverResult(sender, opt, nameOrId, target));
            });
        }

        return true;
    }

    private void deliverResult(CommandSender sender, Optional<ItemStack> opt, String key, Player target) {
        if (opt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + key);
            return;
        }
        target.getInventory().addItem(opt.get());
        sender.sendMessage(ChatColor.GREEN + "✓ Spawned '" + key + "' for " + target.getName());
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
        return "Spawn a lore item by name or database ID into a player's inventory.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNamesForCommands());
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        return completions;
    }
}
