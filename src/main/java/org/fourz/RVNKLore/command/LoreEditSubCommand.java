package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand for editing lore entry fields in-place.
 *
 * Usage: /lore edit <name|uuid> [--name <new-name>] [--description <text>] [--visibility PUBLIC|STAFF_ONLY|HIDDEN]
 *
 * Authors can edit --name and --description on their own unapproved entries.
 * Admins can edit any field on any entry. Edits do not alter approval status.
 */
public class LoreEditSubCommand implements SubCommand {
    private static final List<String> VISIBILITY_VALUES = Arrays.asList("PUBLIC", "STAFF_ONLY", "HIDDEN");

    private final RVNKLore plugin;
    private final LogManager logger;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreEditSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreEditSubCommand");
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore edit <name> [--name <new>] [--description <text>] [--visibility PUBLIC|STAFF_ONLY|HIDDEN]");
            return true;
        }

        // Split: entry name is everything up to the first "--" flag
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        String newName = extractFlag(argList, "--name");
        String newDescription = extractFlag(argList, "--description");
        String newVisibility = extractFlag(argList, "--visibility");
        String nameInput = String.join(" ", argList).trim();

        if (nameInput.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore edit <name> [--name <new>] [--description <text>] [--visibility PUBLIC|STAFF_ONLY|HIDDEN]");
            return true;
        }

        if (newName == null && newDescription == null && newVisibility == null) {
            sender.sendMessage(ChatColor.RED + "✖ No fields specified. Use --name, --description, or --visibility.");
            return true;
        }

        // Visibility validation
        if (newVisibility != null && !VISIBILITY_VALUES.contains(newVisibility.toUpperCase())) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid visibility. Supported: " + String.join(", ", VISIBILITY_VALUES));
            return true;
        }

        // Only admins can change visibility
        if (newVisibility != null && !LoreCommandUtil.isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ --visibility requires admin permission.");
            return true;
        }

        // Find entry
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(nameInput);
        if (entry == null) {
            try {
                UUID id = UUID.fromString(nameInput);
                entry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found matching: " + nameInput);
            return true;
        }

        // Permission check
        if (!LoreCommandUtil.canManage(sender, entry)) {
            sender.sendMessage(ChatColor.RED + "✖ You can only edit your own unapproved entries.");
            return true;
        }

        // Apply edits to entry
        String oldName = entry.getName();
        if (newName != null) entry.setName(newName);
        if (newDescription != null) entry.setDescription(newDescription);
        if (newVisibility != null) entry.setVisibility(newVisibility.toUpperCase());

        boolean success = plugin.getLoreManager().updateLoreEntryInPlace(entry);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry updated.");
            if (newName != null)
                sender.sendMessage(ChatColor.GRAY + "   Name: " + oldName + " → " + newName);
            if (newDescription != null)
                sender.sendMessage(ChatColor.GRAY + "   Description updated.");
            if (newVisibility != null)
                sender.sendMessage(ChatColor.GRAY + "   Visibility: " + newVisibility.toUpperCase());
            logger.info("Lore entry '" + entry.getName() + "' edited in-place by " + sender.getName() +
                    " [id=" + entry.getId() + "]");
        } else {
            // Restore original name in cache if DB write failed
            if (newName != null) entry.setName(oldName);
            sender.sendMessage(ChatColor.RED + "✖ Failed to update lore entry. Check console for errors.");
        }

        return true;
    }

    /** Extract the value following --flag from the arg list (removes both flag and value). */
    private String extractFlag(List<String> args, String flag) {
        int idx = args.indexOf(flag);
        if (idx < 0 || idx + 1 >= args.size()) {
            if (idx >= 0) args.remove(idx); // flag present but no value — remove it
            return null;
        }
        args.remove(idx); // remove flag
        return args.remove(idx); // remove and return value
    }

    @Override
    public String getDescription() {
        return "Edit lore entry fields in-place (--name, --description, --visibility)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (LoreCommandUtil.isAdmin(sender)) return true;
        return sender.hasPermission("rvnklore.add") || sender instanceof Player;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return tabCompletionUtil.completeLoreEntryNames(args[0]);
        }

        String last = args[args.length - 1];
        // Check if previous arg was --visibility
        if (args.length >= 2 && "--visibility".equals(args[args.length - 2])) {
            List<String> matches = new ArrayList<>();
            for (String v : VISIBILITY_VALUES) {
                if (v.startsWith(last.toUpperCase())) matches.add(v);
            }
            return matches;
        }

        List<String> flags = new ArrayList<>();
        if (LoreCommandUtil.isAdmin(sender)) {
            if ("--visibility".startsWith(last)) flags.add("--visibility");
        }
        if ("--name".startsWith(last)) flags.add("--name");
        if ("--description".startsWith(last)) flags.add("--description");
        return flags;
    }
}
