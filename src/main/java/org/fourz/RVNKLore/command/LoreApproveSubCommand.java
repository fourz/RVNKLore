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
            sender.sendMessage(ChatColor.RED + "&c▶ Usage: /lore approve <id>");
            return true;
        }

        String idInput = args[0];
        LoreEntry matchedEntry = null;

        // Try to match by full UUID
        try {
            UUID id = UUID.fromString(idInput);
            matchedEntry = plugin.getLoreManager().getLoreEntry(id);
        } catch (IllegalArgumentException ignored) {}

        // Try to match by short UUID (first 8 chars)
        if (matchedEntry == null && idInput.length() >= 8) {
            String shortUuidPart = idInput.substring(0, 8);
            for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(shortUuidPart)) {
                if (!entry.isApproved()) {
                    String entryShortId = entry.getId().toString().substring(0, 8);
                    if (entryShortId.equalsIgnoreCase(shortUuidPart)) {
                        matchedEntry = entry;
                        break;
                    }
                }
            }
        }

        // Try to match by short UUID with details (e.g. "shortid Name ...")
        if (matchedEntry == null && idInput.length() >= 8) {
            String[] parts = idInput.split(" ", 2);
            String shortId = parts[0].trim();
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

        if (matchedEntry == null) {
            sender.sendMessage(ChatColor.RED + "&c✖ No valid unapproved lore entry found matching: " + idInput);
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
            sender.sendMessage(ChatColor.RED + "&c✖ No lore entry found with ID: " + id);
            return true;
        }
        
        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "&e⚠ This lore entry is already approved.");
            return true;
        }
        
        boolean success = plugin.getLoreManager().approveLoreEntry(id);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "&a✓ Lore entry approved successfully!");
            
            // Log the approval
            logger.info("Lore entry " + id + " (" + entry.getName() + ") approved by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "&c✖ Failed to approve lore entry. Please check console for errors.");
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
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            if (partial.length() >= 2) {
                for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(partial)) {
                    if (!entry.isApproved()) {
                        String uuid = entry.getId().toString();
                        String shortId = uuid.substring(0, 8);

                        // If typing and it matches short ID
                        if (shortId.startsWith(partial)) {
                            completions.add(shortId);
                        }
                        //

                    }
                }
            }
        } else if (args.length > 1) {
            // if a short uuid is prodided, show details
            String shortUuidPart = args[0].trim();  
            for (LoreEntry entry : plugin.getLoreManager().findLoreEntries(shortUuidPart)) {
                if (!entry.isApproved()) {
                    String entryShortId = entry.getId().toString().substring(0, 8);
                    if (entryShortId.equalsIgnoreCase(shortUuidPart)) {                        // Stage 2: If they typed a short ID, show details
                        completions.add(entry.getName() + " " + entry.getDescription() + " " + entry.getSubmittedBy() + " " + entry.getType());
                    }
                }
            }
        }

        return completions;
    }
}
