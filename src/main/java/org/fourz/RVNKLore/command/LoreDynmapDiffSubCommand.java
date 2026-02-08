package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
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
 * Compares live dynmap markers against the lore database and reports discrepancies.
 *
 * Usage:
 * - /lore dynmap diff           - Diff all mapped marker sets
 * - /lore dynmap diff Cities    - Diff only the Cities marker set
 */
public class LoreDynmapDiffSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreDynmapDiffSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreDynmapDiffSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String markerSetId = args.length > 0 ? args[0] : null;

        DynmapMarkerReader reader = plugin.getDynmapIntegration().getMarkerReader();
        if (reader == null) {
            sender.sendMessage(ChatColor.RED + "✖ Dynmap marker reader not initialized.");
            return true;
        }

        // Validate marker set if specified
        if (markerSetId != null) {
            List<String> available = reader.getMarkerSetIds();
            if (!available.contains(markerSetId)) {
                sender.sendMessage(ChatColor.RED + "✖ Unknown marker set: " + markerSetId);
                sender.sendMessage(ChatColor.GRAY + "   Available: " + String.join(", ", available));
                return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "⚙ Comparing dynmap markers against lore database...");

        DynmapLoreDiffService diffService = new DynmapLoreDiffService(reader, plugin.getLoreManager());
        DiffResult result = diffService.diff(markerSetId);

        displayResult(sender, result);
        return true;
    }

    private void displayResult(CommandSender sender, DiffResult result) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "===== Dynmap \u2194 Lore Diff (" + result.getScope() + ") =====");

        int matchedCount = result.getMatched().size();
        int missingLore = result.getMissingFromLore().size();
        int missingDynmap = result.getMissingFromDynmap().size();

        // Summary line
        sender.sendMessage(ChatColor.GREEN + "\u2713 Matched: " + matchedCount + "/" + result.getTotalMarkers());

        // Missing from lore
        if (missingLore > 0) {
            sender.sendMessage(ChatColor.RED + "\u2717 Missing from Lore: " + missingLore);
            int shown = 0;
            for (DynmapMarkerDTO marker : result.getMissingFromLore()) {
                if (shown >= 10) {
                    sender.sendMessage(ChatColor.GRAY + "   ... and " + (missingLore - 10) + " more");
                    break;
                }
                LoreType type = plugin.getDynmapIntegration().getMarkerReader()
                    .getLoreTypeForMarkerSet(marker.markerSetId());
                String typeLabel = type != null ? type.name() : marker.markerSetId();
                sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + "[" + typeLabel + "] "
                    + marker.label() + ChatColor.DARK_GRAY + " (" + marker.world()
                    + " " + (int) marker.x() + ", " + (int) marker.y() + ", " + (int) marker.z() + ")");
                shown++;
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + "\u2713 All dynmap markers have lore entries");
        }

        // Missing from dynmap
        if (missingDynmap > 0) {
            sender.sendMessage(ChatColor.YELLOW + "\u26A0 Missing from Dynmap: " + missingDynmap);
            int shown = 0;
            for (LoreEntry entry : result.getMissingFromDynmap()) {
                if (shown >= 10) {
                    sender.sendMessage(ChatColor.GRAY + "   ... and " + (missingDynmap - 10) + " more");
                    break;
                }
                String coords = "";
                if (entry.getLocation() != null) {
                    coords = " (" + (int) entry.getLocation().getX()
                        + ", " + (int) entry.getLocation().getY()
                        + ", " + (int) entry.getLocation().getZ() + ")";
                }
                sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + "[" + entry.getType().name() + "] "
                    + entry.getName() + ChatColor.DARK_GRAY + coords);
                shown++;
            }
        }

        sender.sendMessage("");
        if (missingLore > 0) {
            sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore dynmap import"
                + ChatColor.GRAY + " to import missing markers.");
        }
    }

    @Override
    public String getDescription() {
        return "Compare dynmap markers against lore database";
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
        }
        return completions;
    }
}
