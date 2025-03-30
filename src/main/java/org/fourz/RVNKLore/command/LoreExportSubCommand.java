package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Subcommand for exporting lore data to a JSON file
 * Usage: /lore export [filename]
 */
public class LoreExportSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final Debug debug;

    public LoreExportSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreExportCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String filename;
        if (args.length > 0) {
            filename = args[0];
            if (!filename.endsWith(".json")) {
                filename += ".json";
            }
        } else {
            // Create a default filename with timestamp
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            filename = "lore_export_" + format.format(new Date()) + ".json";
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Exporting lore data to " + filename + "...");
        
        // Get JSON data
        String jsonData = plugin.getLoreManager().exportToJson();
        
        // Save to file
        File exportFile = new File(plugin.getDataFolder(), filename);
        
        try {
            // Create parent directories if they don't exist
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(jsonData);
            }
            
            sender.sendMessage(ChatColor.GREEN + "Lore data exported successfully to: " + exportFile.getAbsolutePath());
            debug.info("Lore data exported to: " + exportFile.getAbsolutePath());
            
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to export lore data: " + e.getMessage());
            debug.error("Failed to export lore data", e);
            return false;
        }
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Exports all lore data to a JSON file";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No tab completions for export filename
        return new ArrayList<>();
    }
}
