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
    }    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore approve <id>");
            return true;
        }

        String idStr = args[0];
        UUID id;
        
        // Handle the case where the user selected a tab completion with name (UUID format)
        if (idStr.contains("(") && idStr.contains(")")) {
            // Extract the UUID from the tab completion format: "name (UUID)"
            int startPos = idStr.lastIndexOf("(") + 1;
            int endPos = idStr.lastIndexOf(")");
            
            if (startPos < endPos) {
                String shortId = idStr.substring(startPos, endPos);
                
                // Try to find the matching entry from the short ID
                for (LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
                    String entryId = entry.getId().toString();                    
                    if (entryId.startsWith(shortId) && !entry.isApproved()) {
                        
                        id = entry.getUUID();

                        // Found the entry, process approval
                        processApproval(sender, id);
                        return true;
                    }
                }
            }
            
            sender.sendMessage(ChatColor.RED + "✖ Could not find a valid lore entry from the provided identifier.");
            return true;
        }
        
        // Handle direct UUID input
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid UUID format: " + idStr);
            return true;
        }
        
        return processApproval(sender, id);
    }
    
    /**
     * Process the approval of a lore entry
     * 
     * @param sender The command sender
     * @param id The UUID of the lore entry to approve
     * @return true if the command was processed
     */
    private boolean processApproval(CommandSender sender, UUID id) {
        LoreEntry entry = plugin.getLoreManager().getLoreEntry(id);
        
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found with ID: " + id);
            return true;
        }
        
        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This lore entry is already approved.");
            return true;
        }
        
        boolean success = plugin.getLoreManager().approveLoreEntry(id);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry approved successfully!");
            
            // Log the approval
            logger.info("Lore entry " + id + " (" + entry.getName() + ") approved by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to approve lore entry. Please check console for errors.");
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
    }    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // Only provide completions for the first argument (lore entry ID)
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            
            // Only provide completions if at least 2 characters have been typed
            if (partial.length() >= 2) {
                // Get all non-approved lore entries and filter by matching UUID
                plugin.getLoreManager().getAllLoreEntries().stream()
                    .filter(entry -> !entry.isApproved())
                    .filter(entry -> entry.getId().toString().toLowerCase().startsWith(partial))
                    .forEach(entry -> {
                        // Add the UUID only - Minecraft doesn't support colors in tab completion
                        completions.add(entry.getId().toString());
                        
                        // Also add a user-friendly version with the ID and name for easy identification
                        // Format: "name (first 8 chars of UUID)"
                        String shortId = entry.getId().toString().substring(0, 8);
                        completions.add(entry.getName() + " (" + shortId + ")");
                    });
            }
        }
        
        return completions;
    }
}
