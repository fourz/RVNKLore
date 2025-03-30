package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Subcommand for adding new lore entries
 * Usage: /lore add <type> <name> <description>
 */
public class LoreAddSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final Debug debug;

    public LoreAddSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreAddCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /lore add <type> <name> <description>");
            return true;
        }

        String typeStr = args[0].toUpperCase();
        String name = args[1];
        
        // Combine remaining args into description
        StringBuilder description = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) description.append(" ");
            description.append(args[i]);
        }

        // Get lore type
        LoreType type;
        try {
            type = LoreType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid lore type: " + typeStr);
            player.sendMessage(ChatColor.RED + "Valid types: " + 
                    Arrays.stream(LoreType.values()).map(LoreType::name).collect(Collectors.joining(", ")));
            return true;
        }

        // Create lore entry
        
        LoreEntry entry = new LoreEntry(name, description.toString(), type, player);
        
        // For location-based lore types, add player's current location
        if (type == LoreType.LANDMARK || type == LoreType.CITY || type == LoreType.PATH) {
            entry.setLocation(player.getLocation());
        }
        
        // If player has item in hand and type is HEAD or HAT, get NBT data
        if ((type == LoreType.HEAD || type == LoreType.HAT) && player.getInventory().getItemInMainHand() != null) {
            // In a real implementation, you would use an NBT API to extract NBT data
            // For this example, we'll use a placeholder
            entry.setNbtData("{}");
        }

        // Add the entry
        boolean success = plugin.getLoreManager().addLoreEntry(entry);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Lore entry added successfully! ID: " + entry.getId());
            player.sendMessage(ChatColor.YELLOW + "Your submission will be reviewed by a staff member.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to add lore entry. Please check console for errors.");
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Adds a new lore entry";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.add") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toUpperCase();
            return Arrays.stream(LoreType.values())
                    .map(LoreType::name)
                    .filter(type -> type.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
