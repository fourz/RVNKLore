package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for deleting lore entries.
 *
 * Default (authors of unapproved entries + admins): soft-delete — archives the entry.
 *   /lore delete <name>
 *
 * Hard-delete (admin only): two-step confirmation via --purge flag.
 *   /lore delete <name> --purge          → shows confirmation prompt
 *   /lore delete <name> --purge confirm  → permanently deletes
 */
public class LoreDeleteSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreDeleteSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreDeleteSubCommand");
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore delete <name> [--purge [confirm]]");
            return true;
        }

        List<String> argList = new ArrayList<>(Arrays.asList(args));
        boolean purge = argList.remove("--purge");
        boolean confirm = argList.remove("confirm");

        String nameInput = String.join(" ", argList).trim();
        if (nameInput.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore delete <name> [--purge [confirm]]");
            return true;
        }

        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(nameInput);
        if (entry == null) {
            try {
                UUID id = UUID.fromString(nameInput);
                entry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found matching: " + nameInput);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore search or /lore list to find entries");
            return true;
        }

        boolean isAdmin = isAdmin(sender);

        if (purge) {
            if (!isAdmin) {
                sender.sendMessage(ChatColor.RED + "✖ --purge requires admin permission");
                return true;
            }
            if (!confirm) {
                showPurgePreview(sender, entry);
                return true;
            }
            return executeHardDelete(sender, entry);
        }

        // Soft-delete path
        if (!isAdmin && !isAuthorOfUnapproved(sender, entry)) {
            sender.sendMessage(ChatColor.RED + "✖ You can only delete your own unapproved entries.");
            return true;
        }

        return executeSoftDelete(sender, entry);
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.delete")
                || sender.hasPermission("rvnklore.admin")
                || sender.isOp();
    }

    private boolean isAuthorOfUnapproved(CommandSender sender, LoreEntry entry) {
        if (entry.isApproved()) return false;
        if (!(sender instanceof Player)) return false;
        String submittedBy = entry.getSubmittedBy();
        if (submittedBy == null) return false;
        Player player = (Player) sender;
        // submittedBy may be UUID string or player name
        return submittedBy.equals(player.getUniqueId().toString())
                || submittedBy.equalsIgnoreCase(player.getName());
    }

    private boolean executeSoftDelete(CommandSender sender, LoreEntry entry) {
        String entryName = entry.getName();
        UUID entryUUID = entry.getUUID();

        boolean success = plugin.getDatabaseManager().softDeleteLoreEntry(entryUUID);

        if (success) {
            // Remove from in-memory cache so it stops appearing in lists/dynmap
            plugin.getLoreManager().removeLoreEntry(entry);
            if (plugin.isDynmapAvailable()) {
                try {
                    plugin.getDynmapIntegration().getMarkerManager().deleteMarker(entry.getId());
                } catch (Exception e) {
                    logger.debug("Failed to delete Dynmap marker on soft-delete: " + e.getMessage());
                }
            }
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry '" + entryName + "' archived.");
            sender.sendMessage(ChatColor.GRAY + "   Admins can view archived entries with /lore list --archived");
            logger.info("Lore entry '" + entryName + "' (" + entry.getType() + ") soft-deleted by " +
                    sender.getName() + " [id=" + entry.getId() + "]");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to archive lore entry. Check console for errors.");
        }

        return true;
    }

    private boolean executeHardDelete(CommandSender sender, LoreEntry entry) {
        String entryName = entry.getName();
        UUID entryUUID = entry.getUUID();

        if (plugin.isDynmapAvailable()) {
            try {
                plugin.getDynmapIntegration().getMarkerManager().deleteMarker(entry.getId());
            } catch (Exception e) {
                logger.debug("Failed to delete Dynmap marker on hard-delete: " + e.getMessage());
            }
        }

        boolean success = plugin.getDatabaseManager().deleteLoreEntry(entryUUID);

        if (success) {
            plugin.getLoreManager().removeLoreEntry(entry);
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry '" + entryName + "' permanently deleted.");
            logger.info("Lore entry '" + entryName + "' (" + entry.getType() + ") HARD deleted by " +
                    sender.getName() + " [id=" + entry.getId() + "]");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to delete lore entry. Check console for errors.");
        }

        return true;
    }

    private void showPurgePreview(CommandSender sender, LoreEntry entry) {
        sender.sendMessage(ChatColor.GOLD + "===== Purge Lore Entry =====");
        sender.sendMessage(ChatColor.WHITE + "Name: " + ChatColor.YELLOW + entry.getName());
        sender.sendMessage(ChatColor.WHITE + "Type: " + ChatColor.YELLOW + entry.getType());
        Location loc = entry.getLocation();
        if (loc != null && loc.getWorld() != null) {
            sender.sendMessage(ChatColor.WHITE + "Location: " + ChatColor.YELLOW +
                    loc.getWorld().getName() + " " +
                    String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
        }
        sender.sendMessage(ChatColor.WHITE + "Creator: " + ChatColor.YELLOW + entry.getSubmittedBy());
        sender.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + entry.getId());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "⚠ This PERMANENTLY deletes this entry and cannot be undone.");
        sender.sendMessage(ChatColor.GRAY + "   Run: /lore delete " + entry.getName() + " --purge confirm");
    }

    @Override
    public String getDescription() {
        return "Archive a lore entry (--purge to permanently delete, admin only)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Authors can delete their own unapproved entries; admins can delete anything
        if (isAdmin(sender)) return true;
        // Non-admins can still reach execute() where ownership is checked
        return sender.hasPermission("rvnklore.add") || sender instanceof Player;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        boolean hasPurge = argList.contains("--purge");

        if (args.length == 1) {
            return tabCompletionUtil.completeLoreEntryNames(args[0]);
        }

        if (!hasPurge) {
            String partial = args[args.length - 1].toLowerCase();
            if ("--purge".startsWith(partial) && isAdmin(sender)) {
                return Collections.singletonList("--purge");
            }
        } else if (!argList.contains("confirm")) {
            String partial = args[args.length - 1].toLowerCase();
            if ("confirm".startsWith(partial)) {
                return Collections.singletonList("confirm");
            }
        }

        return new ArrayList<>();
    }
}
