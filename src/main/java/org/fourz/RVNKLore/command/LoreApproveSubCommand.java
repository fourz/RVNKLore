package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for approving lore entries
 * Usage: /lore approve <id>
 */
public class LoreApproveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreApproveSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreApproveSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /lore approve <id>");
            return true;
        }

        String idStr = args[0];
        UUID id;
        
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid UUID format: " + idStr);
            return true;
        }
        
        LoreEntry entry = plugin.getLoreManager().getLoreEntry(id);
        
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "No lore entry found with ID: " + idStr);
            return true;
        }
        
        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "This lore entry is already approved.");
            return true;
        }
        
        boolean success = plugin.getLoreManager().approveLoreEntry(id);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Lore entry approved successfully!");
            
            // Log the approval
            logger.info("Lore entry " + id + " (" + entry.getName() + ") approved by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to approve lore entry. Please check console for errors.");
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Approves a lore entry for public viewing";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No tab completions for UUIDs
        return new ArrayList<>();
    }
}
