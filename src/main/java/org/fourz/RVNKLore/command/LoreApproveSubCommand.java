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
 * Subcommand for approving lore entries
 * Usage: /lore approve <id>
 */
public class LoreApproveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public LoreApproveSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreApproveSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore approve <id>");
            return true;
        }

        String idInput = args[0];
        
        // Notify user that we're processing their request
        sender.sendMessage(ChatColor.GOLD + "⚙ Processing approval request...");
        
        // Try to match by full UUID first
        if (idInput.length() == 36) {
            try {
                UUID uuid = UUID.fromString(idInput);
                return processApprovalByUuid(sender, uuid);
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID, continue with other matching methods
            }
        }
        
        // If not a full UUID, try to find by partial ID
        if (idInput.length() >= 4) {
            // Search by partial ID or name
            databaseManager.searchLoreEntries(idInput)
                .thenAccept(entries -> {
                    List<LoreEntryDTO> unapprovedEntries = new ArrayList<>();
                    for (LoreEntryDTO entry : entries) {
                        if (!entry.isApproved()) {
                            unapprovedEntries.add(entry);
                        }
                    }
                    if (unapprovedEntries.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "✖ No unapproved lore entries found matching: " + idInput);
                        return;
                    }
                    if (unapprovedEntries.size() == 1) {
                        LoreEntryDTO entry = unapprovedEntries.get(0);
                        processApprovalByDto(sender, entry);
                        return;
                    }
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Multiple unapproved entries match your query. Please be more specific:");
                    for (LoreEntryDTO entry : unapprovedEntries) {
                        UUID uuid = entry.getUuid();
                        String shortId = uuid != null ? uuid.toString().substring(0, 8) : "unknown";
                        sender.sendMessage(ChatColor.WHITE + shortId + " - " +
                                ChatColor.YELLOW + entry.getName() +
                                ChatColor.GRAY + " (" + entry.getEntryType() + ")");
                    }
                })
                .exceptionally(e -> {
                    logger.error("Error searching for lore entries", e);
                    sender.sendMessage(ChatColor.RED + "✖ An error occurred while searching for lore entries. Please check the console for details.");
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
    private boolean processApprovalByUuid(CommandSender sender, UUID uuid) {
        databaseManager.getLoreEntryById(uuid)
            .thenAccept(entry -> {
                if (entry == null) {
                    sender.sendMessage(ChatColor.RED + "✖ No lore entry found with ID: " + uuid);
                    return;
                }
                
                processApprovalByDto(sender, entry);
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
    private void processApprovalByDto(CommandSender sender, LoreEntryDTO entry) {
        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This lore entry is already approved.");
            return;
        }
        
        // Update the entry to approved state
        entry.setApproved(true);
        
        // Save the changes
        databaseManager.saveLoreEntry(entry)
            .thenAccept(id -> {
                if (id > 0) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Lore entry approved successfully!");
                    
                    // Log the approval
                    logger.info("Lore entry " + entry.getUuid() + " (" + entry.getName() + ") approved by " + sender.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to approve lore entry. Please check console for errors.");
                }
            })
            .exceptionally(e -> {
                logger.error("Error approving lore entry", e);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred while approving the lore entry. Please check the console for details.");
                return null;
            });
    }    @Override
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

            // Only search if at least 3 characters provided
            if (partial.length() >= 3) {
                databaseManager.searchLoreEntries(partial)
                    .thenAccept(entries -> {
                        for (LoreEntryDTO entry : entries) {
                            if (!entry.isApproved()) {                                UUID uuid = entry.getUuid();
                                if (uuid != null) {
                                    String uuidStr = uuid.toString();
                                    String shortId = uuidStr.length() >= 8 ? uuidStr.substring(0, 8) : uuidStr;
                                    completions.add(shortId);
                                }
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
