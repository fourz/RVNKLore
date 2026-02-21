package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.search.LoreSearchService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for adding new lore entries.
 *
 * <p>Player usage: /lore add &lt;type&gt; &lt;name&gt;</p>
 * <p>Console usage: /lore add &lt;type&gt; &lt;name&gt; [--desc &lt;description&gt;] [--at &lt;world&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;]</p>
 *
 * <p>Console support enables test data creation and automation scripts.
 * ITEM and HEAD types require a player (need item in hand).</p>
 */
public class LoreAddSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreAddSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isPlayer = sender instanceof Player;

        // Require at least type and name
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore add <type> <name>" +
                    (isPlayer ? "" : " [--desc <description>] [--at <world> <x> <y> <z>]"));
            return true;
        }

        String typeStr = args[0].toUpperCase();

        LoreType type;
        try {
            type = LoreType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid lore type: " + typeStr);
            sender.sendMessage(ChatColor.RED + "▶ Valid types: " +
                    Arrays.stream(LoreType.values()).map(LoreType::name).collect(Collectors.joining(", ")));
            return true;
        }

        // ITEM and HEAD types require a player (need item in hand)
        if (!isPlayer && (type == LoreType.ITEM || type == LoreType.HEAD)) {
            sender.sendMessage(ChatColor.RED + "✖ " + type.name() + " type requires a player (item in hand needed).");
            return true;
        }

        // Parse flags: --at <world> <x> <y> <z>, --desc <text...>
        Location parsedLocation = null;
        List<String> nameArgs = new ArrayList<>();
        List<String> descArgs = new ArrayList<>();
        boolean parsingDesc = false;
        for (int i = 1; i < args.length; i++) {
            if ("--at".equalsIgnoreCase(args[i])) {
                parsingDesc = false;
                // Expect: --at <world> <x> <y> <z>
                if (i + 4 >= args.length) {
                    sender.sendMessage(ChatColor.RED + "✖ --at requires: <world> <x> <y> <z>");
                    return true;
                }
                String worldName = args[i + 1];
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Unknown world: " + worldName);
                    return true;
                }
                try {
                    double x = Double.parseDouble(args[i + 2]);
                    double y = Double.parseDouble(args[i + 3]);
                    double z = Double.parseDouble(args[i + 4]);
                    parsedLocation = new Location(world, x, y, z);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "✖ Invalid coordinates. Expected numbers for x, y, z.");
                    return true;
                }
                i += 4; // skip the 4 --at arguments
                continue;
            }
            if ("--desc".equalsIgnoreCase(args[i])) {
                parsingDesc = true;
                continue;
            }
            if (parsingDesc) {
                descArgs.add(args[i]);
            } else {
                nameArgs.add(args[i]);
            }
        }

        if (nameArgs.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Name is required.");
            return true;
        }

        String name = String.join(" ", nameArgs);
        String description = descArgs.isEmpty() ? null : String.join(" ", descArgs);
        Player player = isPlayer ? (Player) sender : null;

        // For ITEM type, require material in hand (player-only, checked above)
        if (type == LoreType.ITEM && player != null) {
            if (player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.AIR) {
                player.sendMessage(ChatColor.RED + "✖ You must be holding the item you want to register as lore in your main hand.");
                player.sendMessage(ChatColor.RED + "▶ Hold the item and run the command again.");
                return true;
            }
        }

        // Create lore entry
        LoreEntry entry;
        if (player != null) {
            entry = new LoreEntry(name, description, type, player);
        } else {
            entry = new LoreEntry(name, description, type, (String) null);
        }

        // For ITEM type, set material from hand
        if (type == LoreType.ITEM && player != null) {
            org.bukkit.Material mat = player.getInventory().getItemInMainHand().getType();
            entry.addMetadata("material", mat.name());
        }

        // Set location: --at flag takes priority, then player location for spatial types
        if (parsedLocation != null) {
            entry.setLocation(parsedLocation);
        } else if (player != null && type.isLocationCapable()) {
            entry.setLocation(player.getLocation());
        }

        // If player has item in hand and type is HEAD, get NBT data
        if (type == LoreType.HEAD && player != null &&
            player.getInventory().getItemInMainHand() != null) {
            entry.setNbtData("{}");
        }

        // Add the entry
        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry added: " + entry.getName() + " (" + entry.getType() + ")");
            if (isPlayer) {
                sender.sendMessage(ChatColor.YELLOW + "   Your submission will be reviewed by a staff member.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to add lore entry.");
            // Surface validation errors to the user
            String validationErrors = entry.getMetadata("validation_errors");
            if (validationErrors != null && !validationErrors.isEmpty()) {
                for (String error : validationErrors.split(";")) {
                    sender.sendMessage(ChatColor.RED + "   - " + error.trim());
                }
            }
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
            // First arg: lore type
            return tabCompletionUtil.completeEnum(LoreType.class, args[0]);
        }
        if (args.length >= 3) {
            String lastArg = args[args.length - 1].toLowerCase();
            // Suggest flags after name
            if (lastArg.startsWith("-")) {
                List<String> flags = new ArrayList<>();
                if ("--at".startsWith(lastArg)) flags.add("--at");
                if ("--desc".startsWith(lastArg)) flags.add("--desc");
                return flags;
            }
            // After --at, suggest world names
            for (int i = 0; i < args.length; i++) {
                if ("--at".equalsIgnoreCase(args[i])) {
                    int posAfterAt = args.length - i - 1;
                    if (posAfterAt == 1) {
                        return Bukkit.getWorlds().stream()
                                .map(World::getName)
                                .filter(w -> w.toLowerCase().startsWith(lastArg))
                                .collect(Collectors.toList());
                    }
                    break;
                }
            }
        }
        return new ArrayList<>();
    }
}
