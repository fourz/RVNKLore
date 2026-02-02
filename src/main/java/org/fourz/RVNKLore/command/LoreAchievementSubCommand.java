package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.Achievement;
import org.fourz.RVNKLore.achievement.AchievementManager;
import org.fourz.RVNKLore.achievement.AchievementProgress;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Subcommand for achievement-related operations.
 * Usage:
 * - /lore achievement list [page]
 * - /lore achievement progress [player]
 * - /lore achievement grant <player> <achievement_id>
 * - /lore achievement revoke <player> <achievement_id>
 */
public class LoreAchievementSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final AchievementManager achievementManager;

    public LoreAchievementSubCommand(RVNKLore plugin, AchievementManager achievementManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreAchievementSubCommand");
        this.achievementManager = achievementManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "list":
                return handleList(sender, args);
            case "progress":
                return handleProgress(sender, args);
            case "grant":
                return handleGrant(sender, args);
            case "revoke":
                return handleRevoke(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "✖ Unknown action: " + action);
                showUsage(sender);
                return true;
        }
    }

    private boolean handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        Collection<Achievement> achievements = achievementManager.getAllAchievements();
        List<String> formatted = new ArrayList<>();

        for (Achievement achievement : achievements) {
            if (achievement.isHidden() && !sender.hasPermission("rvnklore.achievement.seehidden")) {
                continue;
            }

            String line = ChatColor.GOLD + achievement.getName() +
                ChatColor.GRAY + " [" + achievement.getType().getDisplayName() + "] " +
                ChatColor.DARK_GRAY + "(" + achievement.getPoints() + " pts)";
            formatted.add(line);
        }

        if (formatted.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No achievements available.");
            return true;
        }

        DisplayFactory.displayPaginatedList(
            sender,
            "Achievements",
            formatted,
            page,
            10,
            entry -> ChatColor.GRAY + "• " + entry
        );

        return true;
    }

    private boolean handleProgress(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "✖ Player not found: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Specify a player: /lore achievement progress <player>");
            return true;
        }

        UUID playerId = target.getUniqueId();
        int totalPoints = achievementManager.getPlayerPoints(playerId);
        List<Achievement> completed = achievementManager.getCompletedAchievements(playerId);

        sender.sendMessage(ChatColor.GOLD + "===== " + target.getName() + "'s Achievements =====");
        sender.sendMessage(ChatColor.YELLOW + "Total Points: " + ChatColor.WHITE + totalPoints);
        sender.sendMessage(ChatColor.YELLOW + "Completed: " + ChatColor.WHITE + completed.size() +
            "/" + achievementManager.getAllAchievements().size());
        sender.sendMessage("");

        if (completed.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No achievements completed yet.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Completed Achievements:");
            for (Achievement achievement : completed) {
                sender.sendMessage(ChatColor.GREEN + "  ✓ " + ChatColor.WHITE + achievement.getName() +
                    ChatColor.DARK_GRAY + " (" + achievement.getPoints() + " pts)");
            }
        }

        // Show in-progress achievements
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "In Progress:");
        boolean hasInProgress = false;
        for (Achievement achievement : achievementManager.getAllAchievements()) {
            AchievementProgress progress = achievementManager.getProgress(playerId, achievement.getId());
            if (!progress.isCompleted() && progress.getCurrentProgress() > 0) {
                hasInProgress = true;
                int percent = progress.getProgressDisplayPercent();
                sender.sendMessage(ChatColor.GRAY + "  ○ " + ChatColor.WHITE + achievement.getName() +
                    ChatColor.GRAY + " [" + percent + "%] " +
                    ChatColor.DARK_GRAY + "(" + progress.getCurrentProgress() + "/" + progress.getTargetProgress() + ")");
            }
        }
        if (!hasInProgress) {
            sender.sendMessage(ChatColor.GRAY + "  No achievements in progress.");
        }

        return true;
    }

    private boolean handleGrant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rvnklore.achievement.grant")) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to grant achievements.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✖ Usage: /lore achievement grant <player> <achievement_id>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player not found: " + args[1]);
            return true;
        }

        String achievementId = args[2];
        Optional<Achievement> achievement = achievementManager.getAchievement(achievementId);
        if (achievement.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Achievement not found: " + achievementId);
            return true;
        }

        if (achievementManager.grantAchievement(target, achievementId)) {
            sender.sendMessage(ChatColor.GREEN + "✓ Granted achievement '" + achievement.get().getName() +
                "' to " + target.getName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Player already has this achievement.");
        }

        return true;
    }

    private boolean handleRevoke(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rvnklore.achievement.revoke")) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to revoke achievements.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✖ Usage: /lore achievement revoke <player> <achievement_id>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player not found: " + args[1]);
            return true;
        }

        String achievementId = args[2];
        if (achievementManager.revokeAchievement(target.getUniqueId(), achievementId)) {
            sender.sendMessage(ChatColor.GREEN + "✓ Revoked achievement '" + achievementId + "' from " + target.getName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Player doesn't have this achievement.");
        }

        return true;
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Achievement Commands =====");
        sender.sendMessage(ChatColor.YELLOW + "/lore achievement list [page]" + ChatColor.GRAY + " - List all achievements");
        sender.sendMessage(ChatColor.YELLOW + "/lore achievement progress [player]" + ChatColor.GRAY + " - View progress");
        if (sender.hasPermission("rvnklore.achievement.grant")) {
            sender.sendMessage(ChatColor.YELLOW + "/lore achievement grant <player> <id>" + ChatColor.GRAY + " - Grant achievement");
        }
        if (sender.hasPermission("rvnklore.achievement.revoke")) {
            sender.sendMessage(ChatColor.YELLOW + "/lore achievement revoke <player> <id>" + ChatColor.GRAY + " - Revoke achievement");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : Arrays.asList("list", "progress", "grant", "revoke")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if ("list".equals(action)) {
                // Page numbers
                for (int i = 1; i <= 5; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ("progress".equals(action) || "grant".equals(action) || "revoke".equals(action)) {
                // Player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            if ("grant".equals(action) || "revoke".equals(action)) {
                // Achievement IDs
                String partial = args[2].toLowerCase();
                for (Achievement achievement : achievementManager.getAllAchievements()) {
                    if (achievement.getId().toLowerCase().startsWith(partial)) {
                        completions.add(achievement.getId());
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.achievement") || sender.hasPermission("rvnklore.use");
    }

    @Override
    public String getDescription() {
        return "View and manage achievements";
    }
}
