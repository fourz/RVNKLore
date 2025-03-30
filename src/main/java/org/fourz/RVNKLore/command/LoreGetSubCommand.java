package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreHandler;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Subcommand for getting lore entries by ID
 * Usage: /lore get <id>
 */
public class LoreGetSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final Debug debug;

    public LoreGetSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreGetCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /lore get <id>");
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
        
        // If the entry is not approved and the sender is not an admin, deny access
        if (!entry.isApproved() && !sender.hasPermission("rvnklore.admin")) {
            sender.sendMessage(ChatColor.RED + "That lore entry has not been approved yet.");
            return true;
        }
        
        // Display the lore to the player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            LoreHandler handler = plugin.getLoreManager().getHandler(entry.getType());
            
            if (handler != null) {
                handler.displayLore(entry, player);
            } else {
                // Fallback display if no handler exists
                player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + entry.getType());
                player.sendMessage(ChatColor.WHITE + entry.getDescription());
            }
            
            // If requested as an item and the player has permission
            if (args.length > 1 && args[1].equalsIgnoreCase("item") && 
                    player.hasPermission("rvnklore.command.getitem")) {
                
                if (handler != null) {
                    player.getInventory().addItem(handler.createLoreItem(entry));
                    player.sendMessage(ChatColor.GREEN + "Added lore item to your inventory.");
                } else {
                    player.sendMessage(ChatColor.RED + "Cannot create item for this lore type.");
                }
            }
        } else {
            // Console display
            sender.sendMessage("=== " + entry.getName() + " ===");
            sender.sendMessage("Type: " + entry.getType());
            sender.sendMessage("Description: " + entry.getDescription());
            sender.sendMessage("Submitted by: " + entry.getSubmittedBy());
            sender.sendMessage("Approved: " + entry.isApproved());
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Gets a lore entry by ID";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.get") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No tab completions for UUIDs
        return new ArrayList<>();
    }
}
