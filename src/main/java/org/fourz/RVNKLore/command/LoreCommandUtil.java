package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.lore.LoreEntry;

/**
 * Shared permission helpers for lore subcommands.
 */
public final class LoreCommandUtil {

    private LoreCommandUtil() {}

    /**
     * Returns true if the sender holds admin-level lore permissions or is op.
     * Console senders are treated as admin.
     */
    public static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.delete")
                || sender.hasPermission("rvnklore.admin")
                || sender.isOp();
    }

    /**
     * Returns true if the sender is the author of the given entry.
     * Console senders always return false (they have no UUID).
     * Matches by UUID string or player name, since submittedBy may be either.
     */
    public static boolean isOwner(CommandSender sender, LoreEntry entry) {
        if (!(sender instanceof Player)) return false;
        String submittedBy = entry.getSubmittedBy();
        if (submittedBy == null) return false;
        Player player = (Player) sender;
        return submittedBy.equals(player.getUniqueId().toString())
                || submittedBy.equalsIgnoreCase(player.getName());
    }

    /**
     * Returns true if the sender can manage (edit/delete) the given entry.
     * Admins can manage any entry.
     * Authors can manage their own unapproved entries only.
     */
    public static boolean canManage(CommandSender sender, LoreEntry entry) {
        if (isAdmin(sender)) return true;
        return isOwner(sender, entry) && !entry.isApproved();
    }

    /**
     * Returns true if the sender can see the entry given its visibility setting.
     * HIDDEN and STAFF_ONLY are restricted to admins; authors always see their own.
     */
    public static boolean canSeeEntry(CommandSender sender, LoreEntry entry) {
        String vis = entry.getVisibility();
        if ("PUBLIC".equalsIgnoreCase(vis)) return true;
        if (isAdmin(sender)) return true;
        return isOwner(sender, entry);
    }
}
