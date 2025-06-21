package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreType;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Subcommand for adding new lore entries
 * Usage: /lore add <type> <name> <description>
 */
public class LoreAddSubCommand implements org.fourz.RVNKLore.command.subcommand.SubCommand {
    private final RVNKLore plugin;
    private final DatabaseManager databaseManager;

    public LoreAddSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "&c✖ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Require at least type and name
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "&c▶ Usage: /lore add <type> <name>");
            return true;
        }

        String typeStr = args[0].toUpperCase();

        // Accept all remaining args as the name (to allow spaces)
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        // Only allow ITEM type for this syntax
        LoreType type;
        try {
            type = LoreType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "&c✖ Invalid lore type: " + typeStr);
            player.sendMessage(ChatColor.RED + "&c▶ Valid types: " +
                    Arrays.stream(LoreType.values()).map(LoreType::name).collect(Collectors.joining(", ")));
            return true;
        }

        // For ITEM type, require material in hand
        if (type == LoreType.ITEM) {
            if (player.getInventory().getItemInMainHand() == null ||
                player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.AIR) {
                player.sendMessage(ChatColor.RED + "&c✖ You must be holding the item you want to register as lore in your main hand.");
                player.sendMessage(ChatColor.RED + "&c▶ Hold the item and run the command again.");
                return true;
            }
        }

        // Build LoreEntryDTO
        LoreEntryDTO dto = new LoreEntryDTO();
        dto.setName(name);
        dto.setEntryType(type.name());
        dto.setSubmittedBy(player.getName());
        dto.setApproved(false);
        dto.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        dto.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        // For ITEM type, set material from hand
        if (type == LoreType.ITEM) {
            org.bukkit.Material mat = player.getInventory().getItemInMainHand().getType();
            dto.getMetadata().put("material", mat.name());
        }
        // For location-based lore types, add player's current location
        if (type == LoreType.LANDMARK || type == LoreType.CITY || type == LoreType.PATH) {
            dto.setLocation(player.getLocation());
        }
        // If player has item in hand and type is a head/hat type, get NBT data
        if ((type == LoreType.HEAD) && 
            player.getInventory().getItemInMainHand() != null) {
            // In a real implementation, you would use an NBT API to extract NBT data
            // For this example, we'll use a placeholder
            dto.setNbtData("{}");
        }
        // Async save
        databaseManager.saveLoreEntry(dto).thenAccept(id -> {
            if (id > 0) {
                player.sendMessage(ChatColor.GREEN + "&a✓ Lore entry added successfully! ID: " + id);
                player.sendMessage(ChatColor.YELLOW + "&7   Your submission will be reviewed by a staff member.");
            } else {
                player.sendMessage(ChatColor.RED + "&c✖ Failed to add lore entry. Please check console for errors.");
            }
        }).exceptionally(e -> {
            player.sendMessage(ChatColor.RED + "&c✖ An error occurred while adding lore entry. Please check console for details.");
            return null;
        });
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
