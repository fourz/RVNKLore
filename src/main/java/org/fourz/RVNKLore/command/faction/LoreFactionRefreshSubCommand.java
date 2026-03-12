package org.fourz.RVNKLore.command.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.SubCommand;
import org.fourz.RVNKLore.integration.griefprevention.GriefPreventionIntegration;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Re-syncs cached territory bounds with current GriefPrevention claim data.
 * Reports missing claims and updates bounds for existing claims.
 * Usage: /lore faction refresh <faction-name>
 */
public class LoreFactionRefreshSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreFactionRefreshSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreFactionRefreshSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "\u25B6 Usage: /lore faction refresh <faction-name>");
            return false;
        }

        String factionName = args[0];

        try {
            // Resolve faction by name
            LoreEntry faction = plugin.getLoreManager().getLoreEntryByNameSync(factionName);
            if (faction == null || faction.getType() != LoreType.FACTION) {
                player.sendMessage(ChatColor.RED + "\u2716 Faction '" + factionName + "' not found.");
                return false;
            }

            // Permission check: must be founder or have manage permission
            String founderUuid = faction.getMetadata("founder_uuid");
            if (!player.hasPermission("rvnklore.faction.manage") &&
                (founderUuid == null || !founderUuid.equals(player.getUniqueId().toString()))) {
                player.sendMessage(ChatColor.RED + "\u2716 You must be the faction founder or have manage permission.");
                return false;
            }

            // GP Integration
            GriefPreventionIntegration gpIntegration = plugin.getGriefPreventionIntegration();
            if (gpIntegration == null || !gpIntegration.isEnabled()) {
                player.sendMessage(ChatColor.RED + "\u2716 GriefPrevention integration is not available.");
                return false;
            }

            String claimIdsStr = faction.getMetadata("claim_ids");
            if (claimIdsStr == null || claimIdsStr.isEmpty()) {
                player.sendMessage(ChatColor.RED + "\u2716 This faction has no territory claims to refresh.");
                return false;
            }

            String[] claimIdParts = claimIdsStr.split(",");
            JsonArray updatedTerritories = new JsonArray();
            List<String> validClaimIds = new ArrayList<>();
            int refreshed = 0;
            int missing = 0;

            for (String claimIdStr : claimIdParts) {
                long claimId;
                try {
                    claimId = Long.parseLong(claimIdStr.trim());
                } catch (NumberFormatException e) {
                    logger.debug("Invalid claim ID in faction " + factionName + ": " + claimIdStr);
                    missing++;
                    continue;
                }

                Optional<Claim> claimOpt = gpIntegration.getClaimById(claimId);
                if (claimOpt.isPresent()) {
                    Claim claim = claimOpt.get();
                    Location lesser = claim.getLesserBoundaryCorner();
                    Location greater = claim.getGreaterBoundaryCorner();

                    JsonObject territory = new JsonObject();
                    territory.addProperty("claim_id", claimId);
                    territory.addProperty("world", lesser.getWorld().getName());
                    territory.addProperty("min_x", lesser.getBlockX());
                    territory.addProperty("min_z", lesser.getBlockZ());
                    territory.addProperty("max_x", greater.getBlockX());
                    territory.addProperty("max_z", greater.getBlockZ());
                    updatedTerritories.add(territory);
                    validClaimIds.add(String.valueOf(claimId));
                    refreshed++;
                } else {
                    player.sendMessage(ChatColor.YELLOW + "\u26A0 Claim #" + claimId + " no longer exists");
                    missing++;
                }
            }

            // Update metadata with refreshed data
            faction.addMetadata("claim_ids", String.join(",", validClaimIds));
            faction.addMetadata("territory_data", updatedTerritories.toString());

            // Save to database
            boolean success = plugin.getDatabaseManager().updateLoreEntry(faction);

            if (success) {
                player.sendMessage(ChatColor.GREEN + "\u2713 Refreshed " + refreshed + " territories" +
                    (missing > 0 ? ", " + missing + " claim(s) no longer exist" : ""));
                logger.debug("Refreshed territory for faction: " + factionName +
                    " (" + refreshed + " valid, " + missing + " missing)");
            } else {
                player.sendMessage(ChatColor.RED + "\u2716 Failed to save refreshed territory data.");
                logger.debug("Failed to save refreshed territory for faction: " + factionName);
            }
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            logger.error("Error ID: " + errorId + " - Error refreshing faction territory", e);
            player.sendMessage(ChatColor.RED + "\u2716 An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> factionNames = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (LoreEntry entry : plugin.getLoreManager().getLoreEntriesByTypeSync(LoreType.FACTION)) {
                if (entry.getName().toLowerCase().startsWith(partial)) {
                    factionNames.add(entry.getName());
                }
            }
            return factionNames;
        }
        return new ArrayList<>();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.faction.manage") || sender.hasPermission("rvnklore.register.faction");
    }

    @Override
    public String getDescription() {
        return "Re-sync faction territory bounds from GriefPrevention";
    }
}
