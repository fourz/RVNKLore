package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.integration.dynmap.DynmapLoreDiffService;
import org.fourz.RVNKLore.integration.dynmap.DynmapLoreDiffService.DiffResult;
import org.fourz.RVNKLore.integration.dynmap.DynmapMarkerDTO;
import org.fourz.RVNKLore.integration.dynmap.DynmapMarkerReader;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Imports dynmap markers that are missing from the lore database.
 *
 * Usage:
 * - /lore dynmap import                     - Preview all missing markers
 * - /lore dynmap import Cities              - Preview missing markers from Cities set
 * - /lore dynmap import Cities --all        - Import all missing from Cities
 * - /lore dynmap import Cities "Port Name"  - Import a specific marker by name
 */
public class LoreDynmapImportSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreDynmapImportSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreDynmapImportSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        DynmapMarkerReader reader = plugin.getDynmapIntegration().getMarkerReader();
        if (reader == null) {
            sender.sendMessage(ChatColor.RED + "✖ Dynmap marker reader not initialized.");
            return true;
        }

        String markerSetId = null;
        boolean importAll = false;
        String specificName = null;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--all".equalsIgnoreCase(args[i])) {
                importAll = true;
            } else if (markerSetId == null) {
                markerSetId = args[i];
            } else if (specificName == null) {
                specificName = args[i];
            }
        }

        // Get diff to find missing markers
        DynmapLoreDiffService diffService = new DynmapLoreDiffService(reader, plugin.getLoreManager());
        DiffResult result = diffService.diff(markerSetId);

        List<DynmapMarkerDTO> toImport = result.getMissingFromLore();

        if (toImport.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "✓ No missing markers to import"
                + (markerSetId != null ? " from " + markerSetId : "") + ".");
            return true;
        }

        // Filter by specific name if provided
        if (specificName != null) {
            String nameFilter = specificName;
            toImport = toImport.stream()
                .filter(m -> m.label().equalsIgnoreCase(nameFilter))
                .collect(java.util.stream.Collectors.toList());

            if (toImport.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "✖ No missing marker found with name: " + nameFilter);
                return true;
            }
        }

        // Preview mode (no --all and no specific name)
        if (!importAll && specificName == null) {
            showPreview(sender, toImport, markerSetId);
            return true;
        }

        // Execute import
        sender.sendMessage(ChatColor.YELLOW + "⚙ Importing " + toImport.size() + " marker(s)...");
        int success = 0;
        int failed = 0;

        for (DynmapMarkerDTO marker : toImport) {
            if (importMarker(marker, reader)) {
                success++;
            } else {
                failed++;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Import complete: " + success + " imported"
            + (failed > 0 ? ChatColor.RED + ", " + failed + " failed" : ""));

        if (success > 0) {
            logger.info("Imported " + success + " dynmap markers as lore entries"
                + (markerSetId != null ? " from " + markerSetId : ""));
        }

        return true;
    }

    private void showPreview(CommandSender sender, List<DynmapMarkerDTO> markers, String scope) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "===== Dynmap Import Preview"
            + (scope != null ? " (" + scope + ")" : "") + " =====");
        sender.sendMessage(ChatColor.GRAY + "Found " + markers.size() + " marker(s) not in lore database:");
        sender.sendMessage("");

        int shown = 0;
        for (DynmapMarkerDTO marker : markers) {
            if (shown >= 15) {
                sender.sendMessage(ChatColor.GRAY + "  ... and " + (markers.size() - 15) + " more");
                break;
            }
            LoreType type = plugin.getDynmapIntegration().getMarkerReader()
                .getLoreTypeForMarkerSet(marker.markerSetId());
            String typeLabel = type != null ? type.name() : "UNKNOWN";
            sender.sendMessage(ChatColor.GRAY + "  " + (shown + 1) + ". " + ChatColor.WHITE
                + marker.label() + ChatColor.DARK_GRAY + " [" + typeLabel + "] "
                + marker.world() + " " + (int) marker.x() + ", " + (int) marker.y() + ", " + (int) marker.z());
            shown++;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "   To import all: " + ChatColor.WHITE
            + "/lore dynmap import" + (scope != null ? " " + scope : "") + " --all");
        sender.sendMessage(ChatColor.GRAY + "   To import one: " + ChatColor.WHITE
            + "/lore dynmap import" + (scope != null ? " " + scope : "") + " \"<marker name>\"");
    }

    private boolean importMarker(DynmapMarkerDTO marker, DynmapMarkerReader reader) {
        LoreType type = reader.getLoreTypeForMarkerSet(marker.markerSetId());
        if (type == null) {
            logger.warning("No LoreType mapping for marker set: " + marker.markerSetId());
            return false;
        }

        World world = Bukkit.getWorld(marker.world());
        if (world == null) {
            logger.warning("World not loaded for marker: " + marker.label() + " (world: " + marker.world() + ")");
            return false;
        }

        Location location = new Location(world, marker.x(), marker.y(), marker.z());

        String description = type.name().substring(0, 1) + type.name().substring(1).toLowerCase()
            + " imported from dynmap markers";
        LoreEntry entry = new LoreEntry(marker.label(), description, type, "Dynmap");
        entry.setLocation(location);
        entry.setApproved(true);
        entry.addMetadata("dynmap_marker_set", marker.markerSetId());
        entry.addMetadata("dynmap_marker_id", marker.markerId());
        entry.addMetadata("dynmap_icon", marker.icon());

        return plugin.getLoreManager().addLoreEntrySync(entry);
    }

    @Override
    public String getDescription() {
        return "Import missing dynmap markers as lore entries";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.dynmap") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && plugin.isDynmapAvailable()) {
            String partial = args[0].toLowerCase();
            DynmapMarkerReader reader = plugin.getDynmapIntegration().getMarkerReader();
            if (reader != null) {
                for (String setId : reader.getMarkerSetIds()) {
                    if (setId.toLowerCase().startsWith(partial)) {
                        completions.add(setId);
                    }
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            if ("--all".startsWith(partial)) {
                completions.add("--all");
            }
        }

        return completions;
    }
}
