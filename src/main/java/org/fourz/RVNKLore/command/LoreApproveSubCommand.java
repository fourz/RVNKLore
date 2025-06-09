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

        String idInput = args[0];
        UUID id = null;
        LoreEntry matchedEntry = null;
        
        // Try to find a matching entry by short UUID or full description
        if (idInput.length() >= 8) {
            String shortUuidPart = idInput.substring(0, Math.min(8, idInput.length()));
            
            // Search for matching entries
            for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(shortUuidPart)) {
                if (!entry.isApproved()) {
                    String entryShortId = entry.getId().toString().substring(0, 8);
                    
                    // Check if the short UUID matches exactly
                    if (entryShortId.equalsIgnoreCase(shortUuidPart)) {
                        matchedEntry = entry;
                        break;
                    }
                }
            }
        }
        
        // If no match was found with short UUID, try to find a matching full UUID
        if (matchedEntry == null) {
            try {
                id = UUID.fromString(idInput);
                matchedEntry = plugin.getLoreManager().getLoreEntry(id);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID format - continue to other matching methods
            }
        }
        
        // If still no match, check if the command included a description part
        if (matchedEntry == null && idInput.contains("-")) {
            String[] parts = idInput.split("-", 2);
            if (parts.length > 0 && parts[0].trim().length() >= 8) {
                String shortId = parts[0].trim();
                
                // Search for entries with matching short UUID
                for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(shortId)) {
                    if (!entry.isApproved()) {
                        String entryShortId = entry.getId().toString().substring(0, 8);
                        if (entryShortId.equalsIgnoreCase(shortId)) {
                            matchedEntry = entry;
                            break;
                        }
                    }
                }
            }
        }
        
        if (matchedEntry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No valid lore entry found matching: " + idInput);
            return true;
        }
        
        return processApproval(sender, matchedEntry.getUUID());
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

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Only provide completions if at least 2 characters have been typed
            if (partial.length() >= 2) {
                for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(partial)) {
                    if (!entry.isApproved()) {
                        String uuid = entry.getId().toString();
                        String shortId = uuid.substring(0, 8);
                        
                        // First level tab completion - just show the short UUID
                        if (shortId.startsWith(partial)) {
                            completions.add(shortId);
                        }
                        // Second level - if they've already typed the exact short ID, show detailed entry
                        else if (shortId.equalsIgnoreCase(partial)) {
                            String submitter = entry.getSubmittedBy() != null ? entry.getSubmittedBy() : "Unknown";
                            String description = entry.getDescription() != null && !entry.getDescription().isEmpty() 
                                    ? entry.getDescription() : "No description";
                            
                            // Format: shortId - Name - Description - Submitter
                            completions.add(shortId + " - " + entry.getName() + " - " + 
                                    description.replaceAll("\\n", " ") + " - " + submitter);
                        }
                        // Handle case where user is typing a full UUID
                        else if (uuid.startsWith(partial)) {
                            completions.add(uuid);
                        }
                    }
                }
            }
        }
        return completions;
    }
}
