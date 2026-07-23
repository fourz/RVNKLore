package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /lore item preset bind|unbind|list} — author quest item presets
 * ({@code quest_item_presets}) from the console (#1496). Companion to the read-only
 * {@code /lore item presets <questId>}; console-safe, narrow additive writes on the existing table
 * (no schema change).
 */
public class LoreItemPresetSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemPresetSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemPresetSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.preset");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "bind" -> handleBind(sender, args);
            case "unbind", "rm" -> handleUnbind(sender, args);
            case "list", "ls" -> handleList(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void handleBind(CommandSender sender, String[] args) {
        // preset bind <questId> <itemId|name> [label...]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item preset bind <questId> <itemId|name> [label]");
            return;
        }
        String questId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        String label = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        itemManager.addPreset(questId, itemId, label).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Bound item " + itemId + " to quest '" + questId + "'"
                        + (label != null ? " (label: " + label + ")" : ""));
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to bind preset (does the item exist?).");
                }
            }));
    }

    private void handleUnbind(CommandSender sender, String[] args) {
        // preset unbind <questId> <itemId|name>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item preset unbind <questId> <itemId|name>");
            return;
        }
        String questId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        itemManager.removePreset(questId, itemId).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Unbound item " + itemId + " from quest '" + questId + "'");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No preset binding for item " + itemId + " on quest '" + questId + "'");
                }
            }));
    }

    private void handleList(CommandSender sender, String[] args) {
        // preset list <questId>  (same read as /lore item presets)
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item preset list <questId>");
            return;
        }
        String questId = args[1];
        itemManager.getPresetsForQuest(questId).thenAccept(presets ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (presets.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No preset items bound to quest: " + questId);
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "===== Preset Items: " + questId + " (" + presets.size() + ") =====");
                for (ItemProperties props : presets) {
                    String material = props.getMaterial() != null ? props.getMaterial().name() : "?";
                    sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + props.getDisplayName()
                        + ChatColor.GRAY + " [" + material + "]"
                        + ChatColor.DARK_GRAY + " (id=" + props.getDatabaseId() + ")");
                }
            }));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "▶ /lore item preset <bind|unbind|list>");
        sender.sendMessage(ChatColor.GRAY + "   bind <questId> <itemId|name> [label]");
        sender.sendMessage(ChatColor.GRAY + "   unbind <questId> <itemId|name>");
        sender.sendMessage(ChatColor.GRAY + "   list <questId>");
    }

    @Override
    public String getDescription() {
        return "Author quest item presets (bind/unbind/list)";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String o : Arrays.asList("bind", "unbind", "list")) {
                if (o.startsWith(args[0].toLowerCase())) out.add(o);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
