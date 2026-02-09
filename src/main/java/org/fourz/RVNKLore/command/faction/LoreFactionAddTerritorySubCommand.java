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
 * Adds the GP claim at the player's current location to an existing faction's territory.
 * Usage: /lore faction addterritory <faction-name>
 */
public class LoreFactionAddTerritorySubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreFactionAddTerritorySubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreFactionAddTerritorySubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "\u25B6 Usage: /lore faction addterritory <faction-name>");
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

            // GP Integration: Get claim at player location
            GriefPreventionIntegration gpIntegration = plugin.getGriefPreventionIntegration();
            if (gpIntegration == null || !gpIntegration.isEnabled()) {
                player.sendMessage(ChatColor.RED + "\u2716 GriefPrevention integration is not available.");
                return false;
            }

            Optional<Claim> claimOpt = gpIntegration.getClaimAt(player.getLocation());
            if (!claimOpt.isPresent()) {
                player.sendMessage(ChatColor.RED + "\u2716 You must be standing in a claim you own or manage.");
                return false;
            }

            Claim claim = claimOpt.get();

            if (!gpIntegration.ownsOrManagesClaim(player, claim)) {
                player.sendMessage(ChatColor.RED + "\u2716 You do not have permission to add this claim as territory.");
                return false;
            }

            long claimId = claim.getID();

            // Check if claim already in faction territory
            String existingClaimIds = faction.getMetadata("claim_ids");
            if (existingClaimIds != null) {
                for (String id : existingClaimIds.split(",")) {
                    if (id.trim().equals(String.valueOf(claimId))) {
                        player.sendMessage(ChatColor.RED + "\u2716 Claim #" + claimId + " is already part of this faction's territory.");
                        return false;
                    }
                }
            }

            // Append new claim_id
            String newClaimIds = (existingClaimIds != null && !existingClaimIds.isEmpty())
                ? existingClaimIds + "," + claimId
                : String.valueOf(claimId);

            // Build updated territory data
            String existingTerritoryData = faction.getMetadata("territory_data");
            JsonArray territoryArray;
            if (existingTerritoryData != null && !existingTerritoryData.isEmpty()) {
                territoryArray = JsonParser.parseString(existingTerritoryData).getAsJsonArray();
            } else {
                territoryArray = new JsonArray();
            }

            Location lesser = claim.getLesserBoundaryCorner();
            Location greater = claim.getGreaterBoundaryCorner();

            JsonObject territory = new JsonObject();
            territory.addProperty("claim_id", claimId);
            territory.addProperty("world", lesser.getWorld().getName());
            territory.addProperty("min_x", lesser.getBlockX());
            territory.addProperty("min_z", lesser.getBlockZ());
            territory.addProperty("max_x", greater.getBlockX());
            territory.addProperty("max_z", greater.getBlockZ());
            territoryArray.add(territory);

            // Update metadata
            faction.addMetadata("claim_ids", newClaimIds);
            faction.addMetadata("territory_data", territoryArray.toString());

            // Save to database
            boolean success = plugin.getDatabaseManager().updateLoreEntry(faction);

            if (success) {
                int area = (greater.getBlockX() - lesser.getBlockX()) * (greater.getBlockZ() - lesser.getBlockZ());
                player.sendMessage(ChatColor.GREEN + "\u2713 Added claim #" + claimId + " to " + factionName + " territories (" + area + " blocks)");
                logger.debug("Added territory claim #" + claimId + " to faction: " + factionName);
            } else {
                player.sendMessage(ChatColor.RED + "\u2716 Failed to update faction territory. Please try again later.");
                logger.debug("Failed to add territory to faction: " + factionName);
            }
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            logger.error("Error ID: " + errorId + " - Error adding territory", e);
            player.sendMessage(ChatColor.RED + "\u2716 An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest faction names
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
        return "Add current claim to faction territory";
    }
}
