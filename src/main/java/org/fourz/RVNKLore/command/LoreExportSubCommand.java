package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.io.LoreExporter;
import org.fourz.RVNKLore.data.io.LoreExporter.ExportFormat;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced export subcommand supporting multiple formats and filtering.
 * Usage:
 *   /lore export [json|yaml] [type]
 *   /lore export json          - Export all entries to JSON
 *   /lore export yaml          - Export all entries to YAML
 *   /lore export json landmark - Export only landmarks to JSON
 *   /lore export yaml city     - Export only cities to YAML
 */
public class LoreExportSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreExporter exporter;

    public LoreExportSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreExportSubCommand");
        this.exporter = new LoreExporter(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Parse format argument
        ExportFormat format = ExportFormat.JSON; // Default
        if (args.length > 0) {
            format = ExportFormat.fromString(args[0]);
        }

        // Check if type filter is specified
        if (args.length > 1) {
            String typeStr = args[1].toUpperCase();
            try {
                LoreType type = LoreType.valueOf(typeStr);
                return exportByType(sender, type, format);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "✖ Invalid lore type: " + args[1]);
                sender.sendMessage(ChatColor.GRAY + "   Valid types: " + getValidTypes());
                return false;
            }
        }

        // Export all entries
        return exportAll(sender, format);
    }

    /**
     * Export all lore entries.
     */
    private boolean exportAll(CommandSender sender, ExportFormat format) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ Exporting all lore entries to " + format.name() + "...");

        File exportFile = exporter.exportAll(format);

        if (exportFile == null) {
            sender.sendMessage(ChatColor.RED + "✖ Export failed. Check console for errors.");
            return false;
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Export successful!");
        sender.sendMessage(ChatColor.GRAY + "   File: " + ChatColor.WHITE + exportFile.getName());
        sender.sendMessage(ChatColor.GRAY + "   Location: plugins/RVNKLore/exports/");
        logger.info("Exported all lore entries to " + exportFile.getName());

        return true;
    }

    /**
     * Export lore entries of a specific type.
     */
    private boolean exportByType(CommandSender sender, LoreType type, ExportFormat format) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ Exporting " + type.name() + " entries to " + format.name() + "...");

        File exportFile = exporter.exportByType(type, format);

        if (exportFile == null) {
            sender.sendMessage(ChatColor.RED + "✖ Export failed. Check console for errors.");
            return false;
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Export successful!");
        sender.sendMessage(ChatColor.GRAY + "   File: " + ChatColor.WHITE + exportFile.getName());
        sender.sendMessage(ChatColor.GRAY + "   Location: plugins/RVNKLore/exports/");
        sender.sendMessage(ChatColor.GRAY + "   Type: " + type.name());
        logger.info("Exported " + type.name() + " entries to " + exportFile.getName());

        return true;
    }

    /**
     * Get a comma-separated list of valid lore types.
     */
    private String getValidTypes() {
        return Arrays.stream(LoreType.values())
            .map(type -> type.name().toLowerCase())
            .collect(Collectors.joining(", "));
    }

    @Override
    public String getDescription() {
        return "Export lore data to JSON or YAML format";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Format completion
            String partial = args[0].toLowerCase();
            for (ExportFormat format : ExportFormat.values()) {
                if (format.name().toLowerCase().startsWith(partial)) {
                    completions.add(format.name().toLowerCase());
                }
            }
        } else if (args.length == 2) {
            // Type completion
            String partial = args[1].toLowerCase();
            for (LoreType type : LoreType.values()) {
                if (type.name().toLowerCase().startsWith(partial)) {
                    completions.add(type.name().toLowerCase());
                }
            }
        }

        return completions;
    }
}
