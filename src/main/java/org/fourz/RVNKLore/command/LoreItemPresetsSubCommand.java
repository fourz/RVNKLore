package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /lore item presets <quest_id>.
 * Lists all lore items bound as presets to a given quest ID.
 */
public class LoreItemPresetsSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemPresetsSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemPresetsSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.presets");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item presets <quest_id>");
            sender.sendMessage(ChatColor.GRAY + "   List all preset items bound to a quest.");
            return true;
        }

        String questId = args[0];

        itemManager.getPresetsForQuest(questId).thenAccept(presets -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (presets.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No preset items found for quest: " + questId);
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "===== Preset Items: " + questId + " (" + presets.size() + ") =====");
                for (ItemProperties props : presets) {
                    String rarity = props.getRarity() != null ? props.getRarity() : "?";
                    String material = props.getMaterial() != null ? props.getMaterial().name() : "?";
                    sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + props.getDisplayName()
                            + ChatColor.GRAY + " [" + material + "] "
                            + ChatColor.AQUA + rarity
                            + ChatColor.DARK_GRAY + " (id=" + props.getDatabaseId() + ")");
                }
            });
        }).exceptionally(ex -> {
            logger.error("Error fetching presets for quest: " + questId, ex);
            sender.sendMessage(ChatColor.RED + "✖ Failed to load presets for quest: " + questId);
            return null;
        });

        return true;
    }

    @Override
    public String getDescription() {
        return "List all preset lore items bound to a quest ID.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
