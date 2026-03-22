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
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for adding new lore entries.
 *
 * <p>Player usage: /lore add &lt;type&gt; &lt;name&gt; [--desc &lt;text&gt;] [--approve]</p>
 * <p>Console usage: /lore add &lt;type&gt; &lt;name&gt; [--desc &lt;text&gt;] [--at &lt;x&gt; &lt;y&gt; &lt;z&gt;] [--world &lt;name&gt;] [--approve]</p>
 *
 * <p>--at accepts 3-arg (x y z) or 4-arg (world x y z) forms.
 * Players infer world from current location; console requires --world or 4-arg --at.</p>
 * <p>--approve auto-approves the entry (requires rvnklore.admin).</p>
 * <p>ITEM and HEAD types require a player (need item in hand).</p>
 */
public class LoreAddSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreAddSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreAddSubCommand");
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isPlayer = sender instanceof Player;

        // Require at least type and name
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "\u25b6 Usage: /lore add <type> <name> [--desc <text>] [--at <x> <y> <z>] [--world <name>] [--approve]");
            return true;
        }

        String typeStr = args[0].toUpperCase();

        LoreType type;
        try {
            type = LoreType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "\u2716 Invalid lore type: " + typeStr);
            sender.sendMessage(ChatColor.RED + "\u25b6 Valid types: " +
                    Arrays.stream(LoreType.values()).map(LoreType::name).collect(Collectors.joining(", ")));
            return true;
        }

        // ITEM and HEAD types require a player (need item in hand)
        if (!isPlayer && (type == LoreType.ITEM || type == LoreType.HEAD)) {
            sender.sendMessage(ChatColor.RED + "\u2716 " + type.name() + " type requires a player (item in hand needed).");
            return true;
        }

        // Parse flags: --at, --world, --desc, --approve
        double[] coords = null;
        String atWorldName = null;    // world from 4-arg --at form
        String flagWorldName = null;  // world from --world flag
        List<String> nameArgs = new ArrayList<>();
        List<String> descArgs = new ArrayList<>();
        boolean parsingDesc = false;
        boolean autoApprove = false;

        for (int i = 1; i < args.length; i++) {
            if ("--at".equalsIgnoreCase(args[i])) {
                parsingDesc = false;
                // Detect 3-arg (x y z) vs 4-arg (world x y z) form
                // Try parsing next arg as a number to distinguish
                if (i + 3 >= args.length) {
                    sender.sendMessage(ChatColor.RED + "\u2716 --at requires at least: <x> <y> <z>");
                    return true;
                }

                boolean nextIsNumber;
                try {
                    Double.parseDouble(args[i + 1]);
                    nextIsNumber = true;
                } catch (NumberFormatException e) {
                    nextIsNumber = false;
                }

                if (nextIsNumber) {
                    // 3-arg form: --at <x> <y> <z>
                    try {
                        coords = new double[] {
                            Double.parseDouble(args[i + 1]),
                            Double.parseDouble(args[i + 2]),
                            Double.parseDouble(args[i + 3])
                        };
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "\u2716 Invalid coordinates. Expected numbers for x, y, z.");
                        return true;
                    }
                    i += 3;
                } else {
                    // 4-arg form: --at <world> <x> <y> <z>
                    if (i + 4 >= args.length) {
                        sender.sendMessage(ChatColor.RED + "\u2716 --at requires: <world> <x> <y> <z> or <x> <y> <z>");
                        return true;
                    }
                    atWorldName = args[i + 1];
                    try {
                        coords = new double[] {
                            Double.parseDouble(args[i + 2]),
                            Double.parseDouble(args[i + 3]),
                            Double.parseDouble(args[i + 4])
                        };
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "\u2716 Invalid coordinates. Expected numbers for x, y, z.");
                        return true;
                    }
                    i += 4;
                }
                continue;
            }
            if ("--world".equalsIgnoreCase(args[i])) {
                parsingDesc = false;
                if (i + 1 >= args.length) {
                    sender.sendMessage(ChatColor.RED + "\u2716 --world requires a world name.");
                    return true;
                }
                flagWorldName = args[i + 1];
                i += 1;
                continue;
            }
            if ("--desc".equalsIgnoreCase(args[i])) {
                parsingDesc = true;
                continue;
            }
            if ("--approve".equalsIgnoreCase(args[i])) {
                parsingDesc = false;
                autoApprove = true;
                continue;
            }
            if (parsingDesc) {
                descArgs.add(args[i]);
            } else {
                nameArgs.add(args[i]);
            }
        }

        if (nameArgs.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "\u2716 Name is required.");
            return true;
        }

        // Check --approve permission
        if (autoApprove && !sender.hasPermission("rvnklore.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "\u2716 --approve requires admin permission.");
            autoApprove = false;
        }

        // Resolve world for --at coordinates
        Location parsedLocation = null;
        if (coords != null) {
            // --world flag overrides 4-arg --at world name
            String worldName = flagWorldName != null ? flagWorldName : atWorldName;

            if (worldName == null) {
                // No explicit world — infer from player or error for console
                if (isPlayer) {
                    worldName = ((Player) sender).getWorld().getName();
                } else {
                    sender.sendMessage(ChatColor.RED + "\u2716 Console requires --world <name> or 4-arg --at <world> <x> <y> <z>");
                    return true;
                }
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "\u2716 Unknown world: " + worldName);
                return true;
            }
            parsedLocation = new Location(world, coords[0], coords[1], coords[2]);
        }

        String name = String.join(" ", nameArgs);
        String description = descArgs.isEmpty() ? null : String.join(" ", descArgs);
        Player player = isPlayer ? (Player) sender : null;

        // For ITEM type, require material in hand (player-only, checked above)
        if (type == LoreType.ITEM && player != null) {
            if (player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.AIR) {
                player.sendMessage(ChatColor.RED + "\u2716 You must be holding the item you want to register as lore in your main hand.");
                player.sendMessage(ChatColor.RED + "\u25b6 Hold the item and run the command again.");
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
            sender.sendMessage(ChatColor.GREEN + "\u2713 Lore entry added: " + entry.getName() + " (" + entry.getType() + ")");

            // Auto-approve via DB after successful add
            if (autoApprove) {
                boolean approved = plugin.getLoreManager().approveLoreEntrySync(entry.getUUID());
                if (approved) {
                    sender.sendMessage(ChatColor.GREEN + "   Auto-approved and published.");
                    logger.info("Lore entry '" + name + "' (" + type + ") added and auto-approved by " + sender.getName());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "   \u26a0 Entry added but auto-approve failed. Use /lore approve " + name);
                }
            } else if (isPlayer) {
                sender.sendMessage(ChatColor.YELLOW + "   Your submission will be reviewed by a staff member.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "\u2716 Failed to add lore entry.");
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
                if ("--world".startsWith(lastArg)) flags.add("--world");
                if ("--approve".startsWith(lastArg)) flags.add("--approve");
                return flags;
            }
            // After --at or --world, suggest world names
            for (int i = 0; i < args.length; i++) {
                if ("--at".equalsIgnoreCase(args[i])) {
                    int posAfterAt = args.length - i - 1;
                    if (posAfterAt == 1) {
                        // Could be world name (4-arg) or x coord (3-arg) — suggest worlds
                        return Bukkit.getWorlds().stream()
                                .map(World::getName)
                                .filter(w -> w.toLowerCase().startsWith(lastArg))
                                .collect(Collectors.toList());
                    }
                    break;
                }
                if ("--world".equalsIgnoreCase(args[i])) {
                    int posAfterWorld = args.length - i - 1;
                    if (posAfterWorld == 1) {
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
