package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand for approving or rejecting lore entries
 * Usage: /lore approve <id> [reason] or /lore reject <id> [reason]
 */
public class LoreApproveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final boolean isApproved; // true for approve, false for reject

    public LoreApproveSubCommand(RVNKLore plugin, boolean isApproved) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreApproveSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
        this.isApproved = isApproved;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore " + (isApproved ? "approve" : "reject") + " <id> [reason]");
            return true;
        }

        String idInput = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
        
        // Notify user that we're processing their request
        sender.sendMessage(ChatColor.GOLD + "⚙ Processing " + (isApproved ? "approval" : "rejection") + " request...");
        
        // Try to match by full UUID first
        if (idInput.length() == 36) {
            try {
                UUID uuid = UUID.fromString(idInput);
                return processApprovalByUuid(sender, uuid, reason);
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID, continue with other matching methods
            }
        }
        
        // If not a full UUID, try to find by partial ID
        if (idInput.length() >= 4) {
            // Search by partial ID or name
            databaseManager.getLoreEntryRepository().searchLoreEntries(idInput)
                .thenAccept(entries -> {
                    List<LoreEntryDTO> pendingEntries = new ArrayList<>();
                    for (LoreEntryDTO entry : entries) {
                        if (entry.isApproved() != isApproved) {
                            pendingEntries.add(entry);
                        }
                    }
                    if (pendingEntries.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "✖ No " + (isApproved ? "unapproved" : "approved") + 
                            " lore entries found matching: " + idInput);
                        return;
                    }
                    if (pendingEntries.size() == 1) {
                        LoreEntryDTO entry = pendingEntries.get(0);
                        processApprovalByDto(sender, entry, reason);
                        return;
                    }
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Multiple entries match your query. Please be more specific:");
                    for (LoreEntryDTO entry : pendingEntries) {
                        UUID uuid = entry.getUuid();
                        String shortId = uuid != null ? uuid.toString().substring(0, 8) : "unknown";
                        sender.sendMessage(ChatColor.WHITE + shortId + " - " +
                                ChatColor.YELLOW + entry.getName() +
                                ChatColor.GRAY + " (" + entry.getEntryType() + ")");
                    }
                })
                .exceptionally(e -> {
                    logger.error("Error searching for lore entries", e);
                    sender.sendMessage(ChatColor.RED + "✖ An error occurred while searching for lore entries");
                    return null;
                });
        } else {
            sender.sendMessage(ChatColor.RED + "▶ Please provide at least 4 characters to search for a lore entry.");
        }
        return true;
    }

    /**
     * Process the approval of a lore entry by UUID
     * 
     * @param sender The command sender
     * @param uuid The UUID of the lore entry to approve
     * @return true if the command was processed
     */
    private boolean processApprovalByUuid(CommandSender sender, UUID uuid, String reason) {
        databaseManager.getLoreEntryRepository().getLoreEntryById(uuid)
            .thenAccept(entry -> {
                if (entry == null) {
                    sender.sendMessage(ChatColor.RED + "✖ No lore entry found with ID: " + uuid);
                    return;
                }
                
                processApprovalByDto(sender, entry, reason);
            })
            .exceptionally(e -> {
                logger.error("Error fetching lore entry", e);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred while fetching the lore entry. Please check the console for details.");
                return null;
            });
        
        return true;
    }
    
    /**
     * Process the approval of a lore entry by DTO
     * 
     * @param sender The command sender
     * @param entry The lore entry DTO to approve
     */
    private void processApprovalByDto(CommandSender sender, LoreEntryDTO entry, String reason) {
        if (entry.isApproved() == isApproved) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This lore entry is already " + 
                (isApproved ? "approved" : "rejected") + ".");
            return;
        }
        
        // Update the entry approval state
        entry.setApproved(isApproved);
        
        // Save the changes
        databaseManager.getLoreEntryRepository().saveLoreEntry(entry)
            .thenAccept(id -> {
                if (id > 0) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Lore entry " + 
                        (isApproved ? "approved" : "rejected") + " successfully!" +
                        (reason != null ? "\n" + ChatColor.GRAY + "Reason: " + reason : ""));
                    
                    // Log the action
                    logger.info("Lore entry " + entry.getUuid() + " (" + entry.getName() + ") " + 
                        (isApproved ? "approved" : "rejected") + " by " + sender.getName() + 
                        (reason != null ? " with reason: " + reason : ""));
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to " + 
                        (isApproved ? "approve" : "reject") + " lore entry");
                }
            })
            .exceptionally(e -> {
                logger.error("Error processing lore entry", e);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred while processing the lore entry");
                return null;
            });
    }

    @Override
    public String getDescription() {
        return isApproved ? "Approves a lore entry for public viewing" : "Rejects a lore entry submission";
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

            // Only search if at least 3 characters provided
            if (partial.length() >= 3) {
                databaseManager.getLoreEntryRepository().searchLoreEntries(partial)
                    .thenAccept(entries -> {
                        for (LoreEntryDTO entry : entries) {
                            if (!entry.isApproved()) {                                
                                UUID uuid = entry.getUuid();
                                completions.add(uuid.toString());
                            }
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Error searching for lore entries for tab completion", e);
                        return null;
                    });
            }
        }

        return completions;
    }
}