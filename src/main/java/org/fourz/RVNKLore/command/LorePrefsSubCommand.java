package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.EnchantChronicle;
import org.fourz.rvnkcore.RVNKCore;
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
 *   /lore prefs chronicle [on|off]
 */
public class LorePrefsSubCommand implements SubCommand {

    private static final String PLUGIN_ID = "rvnklore";

    private final RVNKLore plugin;
    private final LogManager logger;

    public LorePrefsSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LorePrefsSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (RVNKCore.getServiceSafe(PlayerPreferencesService.class) == null) {
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
            case "chronicle":
                return handleChronicle(player, playerId, args);
            default:
                showUsage(player);
                return true;
        }
    }

    private boolean showPreferences(Player player, UUID playerId) {
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
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
                    if (player.hasPermission(EnchantChronicle.PERMISSION)) {
                        String chronicle = EnchantChronicle.isOptedIn(prefs.getMetadata())
                                ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                        player.sendMessage(ChatColor.YELLOW + "Enchant Chronicle: " + chronicle);
                    }
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
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
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
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
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
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
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

        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);

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
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
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

    /**
     * Opt in or out of recording notable enchants as lore (#2099). Requires the chronicle node;
     * the listener checks the node again at enchant time, so revoking it stops recording.
     */
    private boolean handleChronicle(Player player, UUID playerId, String[] args) {
        if (!player.hasPermission(EnchantChronicle.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "✖ Enchant chronicling is not available to you.");
            return true;
        }

        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);

        if (args.length < 2) {
            service.getPreferences(playerId, PLUGIN_ID)
                    .thenAccept(prefs -> {
                        boolean on = EnchantChronicle.isOptedIn(prefs.getMetadata());
                        player.sendMessage(ChatColor.YELLOW + "Enchant Chronicle: "
                                + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                        player.sendMessage(ChatColor.GRAY + "Use /lore prefs chronicle <on|off>");
                    })
                    .exceptionally(ex -> {
                        player.sendMessage(ChatColor.RED + "✖ Error loading preferences: " + ex.getMessage());
                        logger.warning("Error loading enchant chronicle preference", ex);
                        return null;
                    });
            return true;
        }

        String state = args[1].toLowerCase();
        if (!state.equals("on") && !state.equals("off")) {
            player.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return true;
        }
        boolean enabled = state.equals("on");

        service.getPreferences(playerId, PLUGIN_ID)
                .thenCompose(prefs -> {
                    prefs.getMetadata().put(EnchantChronicle.META_KEY, String.valueOf(enabled));
                    return service.savePreferences(prefs);
                })
                .thenRun(() -> player.sendMessage(enabled
                        ? ChatColor.AQUA + "✓ Your notable enchants will now be recorded as lore."
                        : ChatColor.AQUA + "✓ Your enchants will no longer be recorded as lore."))
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error saving preference: " + ex.getMessage());
                    logger.warning("Error saving enchant chronicle preference", ex);
                    return null;
                });
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.RED + "✖ Unknown preference action");
        player.sendMessage(ChatColor.YELLOW + "Usage: /lore prefs [toggle|enable|disable|quiet|channel|chronicle]");
        player.sendMessage(ChatColor.GRAY + "Use /lore prefs for more information");
    }

    @Override
    public String getDescription() {
        return "Manage your lore notification preferences";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // Let execute() handle player-only check with proper message
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
            if (sender.hasPermission(EnchantChronicle.PERMISSION)) {
                completions.add("chronicle");
            }
        } else if (args.length == 2) {
            if ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) {
                completions.add("discovery");
                completions.add("achievement");
                completions.add("collection_completion");
            } else if ("chronicle".equalsIgnoreCase(args[0])) {
                completions.add("on");
                completions.add("off");
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

    /** Grammar and worked examples served by {@code /lore help <verb>} (#1981). */
    @Override
    public String getUsage() {
        return "/lore prefs [toggle|enable|disable|quiet|channel|chronicle]";
    }

    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/lore prefs",
                "/lore prefs enable discovery",
                "/lore prefs disable achievement",
                "/lore prefs quiet 22 7",
                "/lore prefs quiet disable",
                "/lore prefs channel discovery chat off",
                "/lore prefs chronicle on",
                "  Record your notable enchants as lore (needs rvnklore.enchant.chronicle)",
                "Types: discovery achievement collection_completion");
    }
}
