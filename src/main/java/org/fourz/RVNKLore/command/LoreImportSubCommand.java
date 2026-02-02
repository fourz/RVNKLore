package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.io.LoreImporter;
import org.fourz.RVNKLore.data.io.LoreImporter.ImportResult;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Import subcommand for loading lore entries from files.
 * Usage:
 *   /lore import <filename> [--preview]
 *   /lore import lore_export_all_2026-02-01.json
 *   /lore import backup.yaml --preview
 *
 * Features:
 *   - Supports JSON and YAML formats
 *   - Duplicate detection (skips entries with existing IDs)
 *   - Preview mode for validation without importing
 *   - Detailed error reporting
 */
public class LoreImportSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreImporter importer;

    public LoreImportSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreImportSubCommand");
        this.importer = new LoreImporter(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore import <filename> [--preview]");
            sender.sendMessage(ChatColor.GRAY + "   Import lore entries from JSON or YAML file");
            sender.sendMessage(ChatColor.GRAY + "   Files must be in: plugins/RVNKLore/imports/");
            sender.sendMessage(ChatColor.GRAY + "   Use --preview to validate without importing");
            return false;
        }

        // Parse arguments
        String filename = args[0];
        boolean preview = args.length > 1 && args[1].equalsIgnoreCase("--preview");

        // Check file
        File importDir = new File(plugin.getDataFolder(), "imports");
        if (!importDir.exists()) {
            importDir.mkdirs();
            sender.sendMessage(ChatColor.YELLOW + "⚠ Created imports directory: plugins/RVNKLore/imports/");
            sender.sendMessage(ChatColor.GRAY + "   Place your import files there and try again.");
            return false;
        }

        File importFile = new File(importDir, filename);
        if (!importFile.exists()) {
            sender.sendMessage(ChatColor.RED + "✖ File not found: " + filename);
            sender.sendMessage(ChatColor.GRAY + "   Location: plugins/RVNKLore/imports/");

            // List available files
            File[] files = importDir.listFiles((dir, name) ->
                name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml"));

            if (files != null && files.length > 0) {
                sender.sendMessage(ChatColor.GRAY + "   Available files:");
                for (File file : files) {
                    sender.sendMessage(ChatColor.GRAY + "   - " + file.getName());
                }
            }
            return false;
        }

        // Execute import
        if (preview) {
            sender.sendMessage(ChatColor.YELLOW + "⚙ Previewing import from " + filename + "...");
            sender.sendMessage(ChatColor.GRAY + "   (No changes will be made to the database)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚙ Importing from " + filename + "...");
        }

        ImportResult result = importer.importFromFile(importFile, preview);

        // Display results
        displayResults(sender, result, preview);

        return result.isSuccess() || result.getSuccessful() > 0;
    }

    /**
     * Display import results to the sender.
     */
    private void displayResults(CommandSender sender, ImportResult result, boolean preview) {
        if (result.getFailed() == 0 && result.getSuccessful() > 0) {
            sender.sendMessage(ChatColor.GREEN + "✓ " + (preview ? "Preview" : "Import") + " successful!");
        } else if (result.getSuccessful() > 0) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ " + (preview ? "Preview" : "Import") + " completed with warnings");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ " + (preview ? "Preview" : "Import") + " failed");
        }

        // Summary
        sender.sendMessage(ChatColor.GRAY + "   " + result.getSummary());

        // Show successful count
        if (result.getSuccessful() > 0) {
            String action = preview ? "Would import" : "Imported";
            sender.sendMessage(ChatColor.GREEN + "   " + action + ": " + result.getSuccessful() + " entries");
        }

        // Show skipped entries
        if (result.getSkipped() > 0) {
            sender.sendMessage(ChatColor.YELLOW + "   Skipped: " + result.getSkipped() + " duplicates");
        }

        // Show warnings (limit to first 5)
        List<String> warnings = result.getWarnings();
        if (!warnings.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "   Warnings:");
            int count = Math.min(5, warnings.size());
            for (int i = 0; i < count; i++) {
                sender.sendMessage(ChatColor.GRAY + "   - " + warnings.get(i));
            }
            if (warnings.size() > 5) {
                sender.sendMessage(ChatColor.GRAY + "   ... and " + (warnings.size() - 5) + " more (check console)");
            }
        }

        // Show errors (limit to first 5)
        List<String> errors = result.getErrors();
        if (!errors.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "   Errors:");
            int count = Math.min(5, errors.size());
            for (int i = 0; i < count; i++) {
                sender.sendMessage(ChatColor.GRAY + "   - " + errors.get(i));
            }
            if (errors.size() > 5) {
                sender.sendMessage(ChatColor.GRAY + "   ... and " + (errors.size() - 5) + " more (check console)");
            }
        }

        // Preview tip
        if (preview && result.getSuccessful() > 0) {
            sender.sendMessage(ChatColor.GRAY + "   Run without --preview to actually import");
        }

        // Log full details
        if (!warnings.isEmpty() || !errors.isEmpty()) {
            logger.info("Import complete. Warnings: " + warnings.size() + ", Errors: " + errors.size());
            warnings.forEach(w -> logger.warning("Import warning: " + w));
            errors.forEach(e -> logger.error("Import error: " + e));
        }
    }

    @Override
    public String getDescription() {
        return "Import lore entries from JSON or YAML file";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete filenames from imports directory
            File importDir = new File(plugin.getDataFolder(), "imports");
            if (importDir.exists() && importDir.isDirectory()) {
                File[] files = importDir.listFiles((dir, name) ->
                    name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml"));

                if (files != null) {
                    String partial = args[0].toLowerCase();
                    for (File file : files) {
                        if (file.getName().toLowerCase().startsWith(partial)) {
                            completions.add(file.getName());
                        }
                    }
                }
            }
        } else if (args.length == 2) {
            // Complete --preview flag
            if ("--preview".startsWith(args[1].toLowerCase())) {
                completions.add("--preview");
            }
        }

        return completions;
    }
}
