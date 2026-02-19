package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.integration.preferences.PreferencesServiceLookup;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for managing player notification preferences for RVNKLore.
 *
 * <p>Delegates to PlayerPreferencesService via PreferencesServiceLookup.
 * All preference changes are persisted asynchronously.</p>
 *
 * Usage:
 *   /lore prefs
 *   /lore prefs toggle
 *   /lore prefs disable &lt;type&gt;
 *   /lore prefs enable &lt;type&gt;
 *   /lore prefs quiet &lt;startHour&gt; &lt;endHour&gt;
 *   /lore prefs quiet disable
 *   /lore prefs channel &lt;type&gt; &lt;channel&gt; &lt;on|off&gt;
 */
public class LorePrefsSubCommand implements SubCommand {

    private static final String PLUGIN_ID = "rvnklore";

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
        PlayerPreferencesService service = prefsLookup.getService();
        service.getPreferences(playerId, PLUGIN_ID)
                .thenAccept(prefs -> {
                    player.sendMessage(ChatColor.GOLD + "===== Your Lore Preferences =====");
                    String master = prefs.isMasterEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                    player.sendMessage(ChatColor.YELLOW + "Master Toggle: " + master);

                    if (prefs.getQuietHours().isEnabled()) {
                        player.sendMessage(ChatColor.YELLOW + "Quiet Hours: "
                                + prefs.getQuietHours().getStartHour() + ":00 - "
                                + prefs.getQuietHours().getEndHour() + ":00");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Quiet Hours: " + ChatColor.GRAY + "disabled");
                    }

                    player.sendMessage(ChatColor.YELLOW + "Notification Types:");
                    player.sendMessage(ChatColor.GRAY + "  discovery, achievement, collection_completion");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GRAY + "Use /lore prefs <action> to modify.");
                    player.sendMessage(ChatColor.GRAY + "Or use /pref " + PLUGIN_ID + " for full details.");
                })
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error loading preferences: " + ex.getMessage());
                    logger.warning("Error loading lore preferences", ex);
                    return null;
                });
        return true;
    }

    private boolean handleToggleMaster(Player player, UUID playerId) {
        PlayerPreferencesService service = prefsLookup.getService();
        service.isMasterEnabled(playerId, PLUGIN_ID)
                .thenCompose(currentEnabled -> {
                    boolean newEnabled = !currentEnabled;
                    return service.setMasterEnabled(playerId, PLUGIN_ID, newEnabled)
                            .thenApply(v -> newEnabled);
                })
                .thenAccept(newEnabled -> {
                    String status = newEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                    player.sendMessage(ChatColor.AQUA + "✓ Lore notifications " + status);
                })
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error toggling notifications: " + ex.getMessage());
                    logger.warning("Error toggling lore master toggle", ex);
                    return null;
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
        PlayerPreferencesService service = prefsLookup.getService();
        service.setNotificationEnabled(playerId, PLUGIN_ID, type, true)
                .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Enabled " + type + " notifications"))
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error enabling notifications: " + ex.getMessage());
                    logger.warning("Error enabling lore notification type: " + type, ex);
                    return null;
                });
        return true;
    }

    private boolean handleDisable(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs disable <type>");
            player.sendMessage(ChatColor.GRAY + "Types: discovery, achievement, collection_completion");
            return true;
        }

        String type = args[1].toLowerCase();
        PlayerPreferencesService service = prefsLookup.getService();
        service.setNotificationEnabled(playerId, PLUGIN_ID, type, false)
                .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Disabled " + type + " notifications"))
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error disabling notifications: " + ex.getMessage());
                    logger.warning("Error disabling lore notification type: " + type, ex);
                    return null;
                });
        return true;
    }

    private boolean handleQuietHours(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /lore prefs quiet <hour1> <hour2> or /lore prefs quiet disable");
            return true;
        }

        PlayerPreferencesService service = prefsLookup.getService();

        if ("disable".equalsIgnoreCase(args[1])) {
            service.setQuietHours(playerId, PLUGIN_ID, -1, -1)
                    .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Quiet hours disabled"))
                    .exceptionally(ex -> {
                        player.sendMessage(ChatColor.RED + "✖ Error disabling quiet hours: " + ex.getMessage());
                        logger.warning("Error disabling lore quiet hours", ex);
                        return null;
                    });
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

            service.setQuietHours(playerId, PLUGIN_ID, hour1, hour2)
                    .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Quiet hours set to " + hour1 + ":00 - " + hour2 + ":00"))
                    .exceptionally(ex -> {
                        player.sendMessage(ChatColor.RED + "✖ Error setting quiet hours: " + ex.getMessage());
                        logger.warning("Error setting lore quiet hours", ex);
                        return null;
                    });
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "✖ Hours must be numbers");
        }
        return true;
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

        boolean enabled = state.equals("on");
        PlayerPreferencesService service = prefsLookup.getService();
        service.setChannelEnabled(playerId, PLUGIN_ID, type, channel, enabled)
                .thenRun(() -> {
                    String status = enabled ? "enabled" : "disabled";
                    player.sendMessage(ChatColor.AQUA + "✓ Channel " + channel + " " + status + " for " + type);
                })
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error updating channel: " + ex.getMessage());
                    logger.warning("Error updating lore channel preference", ex);
                    return null;
                });
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
                completions.add("collection_completion");
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
