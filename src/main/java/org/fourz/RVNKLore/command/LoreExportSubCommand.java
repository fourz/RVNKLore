package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Subcommand for exporting lore data to a JSON file
 * Usage: /lore export [filename]
 */
public class LoreExportSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreManager loreManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public LoreExportSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreExportSubCommand");
        this.loreManager = LoreManager.getInstance(plugin);
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

        sender.sendMessage(ChatColor.YELLOW + "⚙ Exporting lore data to " + filename + "...");

        final String exportFilename = filename;
        loreManager.getAllEntriesAsync().thenAccept(entries -> {
            List<Object> jsonList = new ArrayList<>();
            for (LoreEntry entry : entries) {
                jsonList.add(entry.toJson());
            }
            String jsonData = gson.toJson(jsonList);
            File exportFile = new File(plugin.getDataFolder(), exportFilename);
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                try (FileWriter writer = new FileWriter(exportFile)) {
                    writer.write(jsonData);
                }
                sender.sendMessage(ChatColor.GREEN + "&a✓ Lore data exported successfully to: " + exportFile.getAbsolutePath());
                logger.info("Lore data exported to: " + exportFile.getAbsolutePath());
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "&c✖ Failed to export lore data: " + e.getMessage());
                logger.error("Failed to export lore data", e);
            }
        }).exceptionally(e -> {
            logger.error("Error exporting lore data", e);
            sender.sendMessage(ChatColor.RED + "&c✖ Error exporting lore data.");
            return null;
        });
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
