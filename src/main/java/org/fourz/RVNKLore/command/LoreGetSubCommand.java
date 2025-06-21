package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.command.subcommand.SubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for getting lore entries by ID
 * Usage: /lore get <id>
 */
public class LoreGetSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LoreManager loreManager;

    public LoreGetSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.loreManager = LoreManager.getInstance(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "&c▶ Usage: /lore get <id>");
            return true;
        }

        String idStr = args[0];
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "&c✖ Invalid UUID format: " + idStr);
            return true;
        }

        loreManager.getEntryByIdAsync(id).thenAccept(entry -> {
            if (entry == null) {
                sender.sendMessage(ChatColor.RED + "&c✖ No lore entry found with ID: " + idStr);
                return;
            }
            // If the entry is not approved and the sender is not an admin, deny access
            if (!entry.isApproved() && !sender.hasPermission("rvnklore.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "&e⚠ That lore entry has not been approved yet.");
                return;
            }
            // Display the lore to the player
            if (sender instanceof Player) {
                Player player = (Player) sender;
                LoreHandler handler = plugin.getHandlerFactory().getHandler(entry.getType());
                if (handler != null) {
                    handler.displayLore(entry, player);
                } else {
                    // Fallback display if no handler exists
                    player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
                    player.sendMessage(ChatColor.GRAY + "&7   Type: " + ChatColor.YELLOW + entry.getType());
                    player.sendMessage(ChatColor.WHITE + entry.getDescription());
                }
                // If requested as an item and the player has permission
                if (args.length > 1 && args[1].equalsIgnoreCase("item") &&
                        player.hasPermission("rvnklore.command.getitem")) {
                    if (handler != null) {
                        player.getInventory().addItem(handler.createLoreItem(entry));
                        player.sendMessage(ChatColor.GREEN + "&a✓ Added lore item to your inventory.");
                    } else {
                        player.sendMessage(ChatColor.RED + "&c✖ Cannot create item for this lore type.");
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
        }).exceptionally(e -> {
            sender.sendMessage(ChatColor.RED + "&c✖ Error retrieving lore entry.");
            return null;
        });
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
