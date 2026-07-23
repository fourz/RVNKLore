package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /lore item pool add|remove|list} — author RNG item pools ({@code lore_item_rng_pool})
 * from the console (#1496). Replaces raw DB inserts, so pools can be authored on Event/prod where
 * {@code db_query} is read-only. Console-safe; narrow additive writes on the existing table (no
 * schema change).
 */
public class LoreItemPoolSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemPoolSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemPoolSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.pool");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(sender, args);
            case "remove", "rm" -> handleRemove(sender, args);
            case "list", "ls" -> handleList(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        // pool add <poolId> <itemId|name> [rarityTier] [weight]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool add <poolId> <itemId|name> [rarityTier] [weight]");
            return;
        }
        String poolId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        String rarityTier = args.length >= 4 ? args[3].toUpperCase() : "COMMON";
        int weight = args.length >= 5 ? parseIntOr(args[4], 1) : 1;
        itemManager.addPoolEntry(poolId, itemId, rarityTier, weight).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Added item " + itemId + " to pool '" + poolId
                        + "' (" + rarityTier + ", weight " + weight + ")");
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to add pool entry (does the item exist?).");
                }
            }));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        // pool remove <poolId> <itemId|name>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool remove <poolId> <itemId|name>");
            return;
        }
        String poolId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        itemManager.removePoolEntry(poolId, itemId).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Removed item " + itemId + " from pool '" + poolId + "'");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No such entry in pool '" + poolId + "' for item " + itemId);
                }
            }));
    }

    private void handleList(CommandSender sender, String[] args) {
        // pool list <poolId>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool list <poolId>");
            return;
        }
        String poolId = args[1];
        itemManager.listPoolEntries(poolId).thenAccept(rows ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rows.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Pool '" + poolId + "' is empty.");
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "===== RNG Pool: " + poolId + " (" + rows.size() + ") =====");
                for (ItemRepository.PoolEntryRow r : rows) {
                    sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + "item " + r.loreItemId()
                        + ChatColor.GRAY + " [" + r.rarityTier() + "] "
                        + ChatColor.AQUA + "w" + r.weight()
                        + (r.active() ? "" : ChatColor.DARK_GRAY + " (inactive)"));
                }
            }));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "▶ /lore item pool <add|remove|list>");
        sender.sendMessage(ChatColor.GRAY + "   add <poolId> <itemId|name> [rarityTier] [weight]");
        sender.sendMessage(ChatColor.GRAY + "   remove <poolId> <itemId|name>");
        sender.sendMessage(ChatColor.GRAY + "   list <poolId>");
    }

    private int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public String getDescription() {
        return "Author RNG item pools (add/remove/list)";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String o : Arrays.asList("add", "remove", "list")) {
                if (o.startsWith(args[0].toLowerCase())) out.add(o);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
