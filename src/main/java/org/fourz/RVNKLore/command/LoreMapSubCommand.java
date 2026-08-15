package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.map.LoreMap;
import org.fourz.RVNKLore.lore.map.LoreMapManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Subcommand for lore map operations.
 *
 * Usage:
 *   /lore map give <entry-name>       — create and give a map item linked to the entry
 *   /lore map info <entry-name>       — show stored map records for the entry
 *   /lore map delete <map-id>         — delete a stored map record (admin only)
 */
public class LoreMapSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreMapSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreMapSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "give" -> executeGive(sender, rest);
            case "info" -> executeInfo(sender, rest);
            case "delete" -> executeDelete(sender, rest);
            default -> showUsage(sender);
        }
        return true;
    }

    private void executeGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "▶ /lore map give must be run by a player.");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore map give <entry-name>");
            return;
        }

        String name = String.join(" ", args);
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(name);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found: " + name);
            return;
        }

        LoreMapManager mapManager = plugin.getLoreMapManager();
        Optional<LoreMap> saved = mapManager.createMapForEntry(entry, player);
        if (saved.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Failed to create map record for: " + name);
            return;
        }

        LoreMap loreMap = saved.get();
        boolean given = mapManager.giveMapItem(player, loreMap);
        if (given) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore map #" + loreMap.getId() + " created and given for: " + entry.getName());
            logger.info("Map #" + loreMap.getId() + " given to " + player.getName() + " for entry '" + entry.getName() + "'");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Map record saved (id=" + loreMap.getId() + ") but item could not be issued — world not loaded.");
        }
    }

    private void executeInfo(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore map info <entry-name>");
            return;
        }

        String name = String.join(" ", args);
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(name);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found: " + name);
            return;
        }

        List<LoreMap> maps = plugin.getLoreMapManager().getMapsForEntry(entry.getId());
        if (maps.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No maps stored for: " + entry.getName());
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Maps for: " + entry.getName() + " ===");
        for (LoreMap m : maps) {
            sender.sendMessage(ChatColor.YELLOW + " #" + m.getId() +
                    ChatColor.WHITE + " [" + m.getSubtype().name() + "]" +
                    " World=" + m.getWorldName() +
                    " X=" + m.getCenterX() + " Z=" + m.getCenterZ() +
                    " Scale=" + m.getScale() +
                    (m.getPixelData() != null ? " [pixel]" : ""));
        }
    }

    private void executeDelete(CommandSender sender, String[] args) {
        if (!LoreCommandUtil.isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to delete maps.");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore map delete <map-id>");
            return;
        }
        int mapId;
        try {
            mapId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "▶ Map ID must be a number.");
            return;
        }

        boolean deleted = plugin.getLoreMapManager().deleteMap(mapId);
        if (deleted) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore map #" + mapId + " deleted.");
            logger.info("Map #" + mapId + " deleted by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "✖ No map found with id: " + mapId);
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== /lore map ===");
        sender.sendMessage(ChatColor.YELLOW + "/lore map give <entry-name>" + ChatColor.WHITE + " — create and give a map item");
        sender.sendMessage(ChatColor.YELLOW + "/lore map info <entry-name>" + ChatColor.WHITE + " — show stored map records");
        if (LoreCommandUtil.isAdmin(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "/lore map delete <map-id>" + ChatColor.WHITE + " — delete a map record");
        }
    }

    @Override
    public String getDescription() {
        return "Create and manage lore maps (give, info, delete)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.map") || LoreCommandUtil.isAdmin(sender);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> actions = LoreCommandUtil.isAdmin(sender)
                    ? Arrays.asList("give", "info", "delete")
                    : Arrays.asList("give", "info");
            return actions.stream()
                    .filter(a -> a.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2) {
            String action = args[0].toLowerCase();
            if ("give".equals(action) || "info".equals(action)) {
                String partial = args[args.length - 1].toLowerCase();
                return plugin.getLoreManager().getAllLoreEntriesSync().stream()
                        .map(LoreEntry::getName)
                        .filter(n -> n != null && n.toLowerCase().startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .limit(5)
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }

    /** Grammar and worked examples served by {@code /lore help <verb>} (#1981). */
    @Override
    public String getUsage() {
        return "/lore map <give|info|delete>";
    }

    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/lore map give Sol Sanctum",
                "  player only - it puts a filled map in your hand",
                "/lore map info Sol Sanctum",
                "/lore map delete 42");
    }
}
