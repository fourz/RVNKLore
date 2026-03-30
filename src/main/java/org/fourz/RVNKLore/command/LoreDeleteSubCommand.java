package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for deleting lore entries.
 * Usage: /lore delete <name> [confirm]
 *
 * Two-step confirmation: first run shows entry details and warning,
 * second run with "confirm" arg executes the deletion.
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
            sender.sendMessage(ChatColor.RED + "\u25b6 Usage: /lore delete <name> [confirm]");
            return true;
        }

        // Check if last arg is "confirm"
        boolean confirm = args.length >= 2 && args[args.length - 1].equalsIgnoreCase("confirm");

        // Build name from args (excluding "confirm" if present)
        int nameEndIndex = confirm ? args.length - 1 : args.length;
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < nameEndIndex; i++) {
            if (i > 0) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String nameInput = nameBuilder.toString();

        // Lookup entry by name (case-insensitive)
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(nameInput);

        // Fallback: try UUID
        if (entry == null) {
            try {
                UUID id = UUID.fromString(nameInput);
                entry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "\u2716 No lore entry found matching: " + nameInput);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore search or /lore list to find entries");
            return true;
        }

        if (!confirm) {
            showPreview(sender, entry);
            return true;
        }

        return executeDelete(sender, entry);
    }

    private void showPreview(CommandSender sender, LoreEntry entry) {
        sender.sendMessage(ChatColor.GOLD + "===== Delete Lore Entry =====");
        sender.sendMessage(ChatColor.WHITE + "Name: " + ChatColor.YELLOW + entry.getName());
        sender.sendMessage(ChatColor.WHITE + "Type: " + ChatColor.YELLOW + entry.getType());

        Location loc = entry.getLocation();
        if (loc != null && loc.getWorld() != null) {
            sender.sendMessage(ChatColor.WHITE + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
            sender.sendMessage(ChatColor.WHITE + "Location: " + ChatColor.YELLOW +
                    String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
        }

        String creator = entry.getSubmittedBy();
        if (creator != null) {
            sender.sendMessage(ChatColor.WHITE + "Creator: " + ChatColor.YELLOW + creator);
        }

        sender.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + entry.getId());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "\u26a0 This will permanently delete this entry and its Dynmap marker.");
        sender.sendMessage(ChatColor.GRAY + "   Run /lore delete " + entry.getName() + " confirm to proceed");
    }

    private boolean executeDelete(CommandSender sender, LoreEntry entry) {
        String entryName = entry.getName();
        UUID entryUUID = entry.getUUID();

        // Delete Dynmap marker
        if (plugin.isDynmapAvailable()) {
            try {
                plugin.getDynmapIntegration().getMarkerManager().deleteMarker(entry.getId());
            } catch (Exception e) {
                logger.debug("Failed to delete Dynmap marker: " + e.getMessage());
            }
        }

        // Delete from database (cascades to lore_submission, lore_item via FK)
        boolean success = plugin.getDatabaseManager().deleteLoreEntry(entryUUID);

        if (success) {
            // Remove from in-memory cache
            plugin.getLoreManager().removeLoreEntry(entry);

            sender.sendMessage(ChatColor.GREEN + "\u2713 Lore entry '" + entryName + "' deleted");
            logger.info("Lore entry '" + entryName + "' (" + entry.getType() + ") deleted by " +
                    sender.getName() + " [id=" + entry.getId() + "]");
        } else {
            sender.sendMessage(ChatColor.RED + "\u2716 Failed to delete lore entry. Check console for errors.");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Delete a lore entry permanently";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.delete") || sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return tabCompletionUtil.completeLoreEntryNames(args[0]);
        }

        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            if ("confirm".startsWith(partial)) {
                return Collections.singletonList("confirm");
            }
        }

        return new ArrayList<>();
    }
}
