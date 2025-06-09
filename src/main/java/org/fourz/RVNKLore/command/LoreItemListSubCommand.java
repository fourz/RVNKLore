package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.List;

/**
 * Handles the /lore item list command.
 * Outputs a paginated, sorted list of items using DisplayFactory.
 */
public class LoreItemListSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final ItemManager itemManager;

    public LoreItemListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        return DisplayFactory.displayItemList(sender, itemManager, page, true);
    }

    @Override
    public String getDescription() {
        return "List all lore items, sorted from newest to oldest.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }
}
