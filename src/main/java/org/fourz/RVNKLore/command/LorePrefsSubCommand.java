package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.integration.preferences.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for managing player notification preferences.
 * Usage: /lore prefs
 *        /lore prefs toggle
 *        /lore prefs disable <type>
 *        /lore prefs enable <type>
 *        /lore prefs quiet <startHour> <endHour>
 *        /lore prefs quiet disable
 *        /lore prefs channel <type> <channel> <on|off>
 */
public class LorePrefsSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final PreferencesServiceLookup prefsLookup;

    public LorePrefsSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LorePrefsSubCommand");
        this.prefsLookup = new PreferencesServiceLookup(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (!prefsLookup.isAvailable()) {
            player.sendMessage(ChatColor.RED + "✖ PlayerPreferencesService is not available.");
            return true;
        }

        if (args.length == 0) {
            return showPreferences(player, playerId);
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "toggle":
                return handleToggleMaster(player, playerId);
            case "enable":
                return handleEnable(player, playerId, args);
            case "disable":
                return handleDisable(player, playerId, args);
            case "quiet":
                return handleQuietHours(player, playerId, args);
            case "channel":
                return handleChannel(player, playerId, args);
            default:
                showUsage(player);
                return true;
        }
    }

    private boolean showPreferences(Player player, UUID playerId) {
        player.sendMessage(ChatColor.GOLD + "===== Your Lore Preferences =====");
        player.sendMessage(ChatColor.YELLOW + "Use /lore prefs <action> to modify:");
        player.sendMessage(ChatColor.GRAY + "  toggle - Toggle all notifications on/off");
        player.sendMessage(ChatColor.GRAY + "  enable <type> - Enable notification type");
        player.sendMessage(ChatColor.GRAY + "  disable <type> - Disable notification type");
        player.sendMessage(ChatColor.GRAY + "  quiet <hour1> <hour2> - Set quiet hours (24h format)");
        player.sendMessage(ChatColor.GRAY + "  quiet disable - Disable quiet hours");
        player.sendMessage(ChatColor.GRAY + "  channel <type> <channel> <on|off> - Toggle channel");
        player.sendMessage(ChatColor.YELLOW + "Notification Types: discovery, achievement, collection_completion");
        player.sendMessage(ChatColor.YELLOW + "Channels: TITLE, ACTION_BAR, CHAT, SOUND");
        return true;
    }

    private boolean handleToggleMaster(Player player, UUID playerId) {
        var prefsService = prefsLookup.getService();
        prefsService.isNotificationEnabled(playerId, "rvnklore", "discovery")
            .thenAccept(enabled -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (enabled) {
                        player.sendMessage(ChatColor.GREEN + "✓ Lore notifications are currently " + ChatColor.YELLOW + "enabled");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "✓ Lore notifications are currently " + ChatColor.YELLOW + "disabled");
                    }
                    player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
                });
            });
        return true;
    }

    private boolean handleEnable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs enable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: discovery, achievement, collection_completion");
            return true;
        }

        String type = args[1].toLowerCase();
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Enable notifications for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private boolean handleDisable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs disable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: discovery, achievement, collection_completion");
            return true;
        }

        String type = args[1].toLowerCase();
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Disable notifications for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private boolean handleQuietHours(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs quiet <hour1> <hour2> or /lore prefs quiet disable");
            return true;
        }

        if ("disable".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.GREEN + "✓ Quiet hours disabled");
            player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs quiet <hour1> <hour2>");
            return true;
        }

        try {
            int hour1 = Integer.parseInt(args[1]);
            int hour2 = Integer.parseInt(args[2]);

            if (hour1 < 0 || hour1 > 23 || hour2 < 0 || hour2 > 23) {
                player.sendMessage(ChatColor.RED + "✖ Hours must be between 0-23");
                return true;
            }

            player.sendMessage(ChatColor.GREEN + "✓ Quiet hours set to " + hour1 + ":00 - " + hour2 + ":00");
            player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "✖ Hours must be numbers");
            return true;
        }
    }

    private boolean handleChannel(Player player, UUID playerId, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs channel <type> <channel> <on|off>");
            player.sendMessage(ChatColor.GRAY + "Channels: TITLE, ACTION_BAR, CHAT, SOUND");
            return true;
        }

        String type = args[1].toLowerCase();
        String channel = args[2].toUpperCase();
        String state = args[3].toLowerCase();

        if (!state.equals("on") && !state.equals("off")) {
            player.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return true;
        }

        String status = state.equals("on") ? "enabled" : "disabled";
        player.sendMessage(ChatColor.GREEN + "✓ Queued: Channel " + channel + " " + status + " for " + type);
        player.sendMessage(ChatColor.GRAY + "Note: Preference persistence coming in future update");
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.RED + "✖ Unknown preference action");
        player.sendMessage(ChatColor.YELLOW + "Usage: /lore prefs [toggle|enable|disable|quiet|channel]");
        player.sendMessage(ChatColor.GRAY + "Use /lore prefs for more information");
    }

    @Override
    public String getDescription() {
        return "Manage your lore notification preferences";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        return sender.hasPermission("rvnklore.prefs") || sender.hasPermission("rvnklore.*");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("toggle");
            completions.add("enable");
            completions.add("disable");
            completions.add("quiet");
            completions.add("channel");
        } else if (args.length == 2) {
            if ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) {
                completions.add("discovery");
                completions.add("achievement");
                completions.add("collection_completion");
            } else if ("quiet".equalsIgnoreCase(args[0])) {
                completions.add("disable");
                completions.add("0");
                completions.add("22");
            } else if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("discovery");
                completions.add("achievement");
            }
        } else if (args.length == 3) {
            if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("TITLE");
                completions.add("ACTION_BAR");
                completions.add("CHAT");
                completions.add("SOUND");
            } else if ("quiet".equalsIgnoreCase(args[0])) {
                completions.add("8");
            }
        } else if (args.length == 4) {
            if ("channel".equalsIgnoreCase(args[0])) {
                completions.add("on");
                completions.add("off");
            }
        }

        return completions;
    }
}
