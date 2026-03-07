package org.fourz.RVNKLore.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.integration.griefprevention.GriefPreventionIntegration;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Subcommand for registering faction lore entries with GriefPrevention territory integration.
 * Player must stand in a GP claim they own or manage to register a faction.
 */
public class LoreRegisterFactionSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreRegisterFactionSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreRegisterFactionSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        logger.info("[FACTION-CMD] execute() called");
        logger.info("[FACTION-CMD] sender: " + sender.getName() + " (isPlayer: " + (sender instanceof Player) + ")");
        logger.info("[FACTION-CMD] args.length: " + args.length);
        for (int i = 0; i < args.length; i++) {
            logger.info("[FACTION-CMD] args[" + i + "]: " + args[i]);
        }

        if (!(sender instanceof Player)) {
            logger.info("[FACTION-CMD] Sender is not a player!");
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        logger.info("[FACTION-CMD] Player confirmed: " + player.getName());

        boolean hasPerm = player.hasPermission("rvnklore.register.faction");
        logger.info("[FACTION-CMD] hasPermission(rvnklore.register.faction) = " + hasPerm);
        if (!hasPerm) {
            player.sendMessage(ChatColor.RED + "\u2716 You don't have permission to register factions.");
            return true;
        }

        logger.info("[FACTION-CMD] Permission check passed");
        if (args.length < 2) {
            logger.info("[FACTION-CMD] Invalid args count: " + args.length);
            player.sendMessage(ChatColor.RED + "\u25B6 Usage: /lore registerfaction <name> <member1> [member2] [member3...]");
            return false;
        }

        logger.info("[FACTION-CMD] Args validation passed");

        String factionName = args[0];
        String[] memberArgs = Arrays.copyOfRange(args, 1, args.length);
        String members = String.join(",", memberArgs);

        try {
            // Validate faction name format
            if (!factionName.matches("^[a-zA-Z0-9\\s-]{3,32}$")) {
                player.sendMessage(ChatColor.RED + "\u2716 Faction name must be 3-32 characters and contain only letters, numbers, spaces and hyphens.");
                return false;
            }

            // Check if faction already exists
            if (plugin.getLoreManager().getLoreEntryByNameSync(factionName) != null) {
                player.sendMessage(ChatColor.RED + "\u2716 A faction with that name already exists!");
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

            // Validate player owns or manages this claim
            if (!gpIntegration.ownsOrManagesClaim(player, claim)) {
                player.sendMessage(ChatColor.RED + "\u2716 You do not have permission to register a faction in this claim.");
                return false;
            }

            // Build territory data JSON
            long claimId = claim.getID();
            Location lesser = claim.getLesserBoundaryCorner();
            Location greater = claim.getGreaterBoundaryCorner();

            JsonArray territoryArray = new JsonArray();
            JsonObject territory = new JsonObject();
            territory.addProperty("claim_id", claimId);
            territory.addProperty("world", lesser.getWorld().getName());
            territory.addProperty("min_x", lesser.getBlockX());
            territory.addProperty("min_z", lesser.getBlockZ());
            territory.addProperty("max_x", greater.getBlockX());
            territory.addProperty("max_z", greater.getBlockZ());
            territoryArray.add(territory);

            // Create the lore entry
            String transactionId = java.util.UUID.randomUUID().toString();
            LoreEntry entry = new LoreEntry();
            entry.addMetadata("transaction_id", transactionId);
            logger.debug("Starting faction registration transaction: " + transactionId);

            entry.setType(LoreType.FACTION);
            entry.setName(factionName);
            entry.setDescription("Faction " + factionName + " - founded by " + player.getName());
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy(player.getName());

            // Faction metadata
            entry.addMetadata("founding_date", System.currentTimeMillis() + "");
            entry.addMetadata("founder_uuid", player.getUniqueId().toString());
            entry.addMetadata("members", members);
            entry.addMetadata("claim_ids", String.valueOf(claimId));
            entry.addMetadata("territory_data", territoryArray.toString());

            // Validate against handler before saving
            LoreHandler handler = plugin.getHandlerFactory().getHandler(LoreType.FACTION);
            if (!handler.validateEntry(entry)) {
                player.sendMessage(ChatColor.RED + "\u2716 Faction entry validation failed. Please check all requirements.");
                return false;
            }

            // Faction entries need approval
            entry.setApproved(false);

            // Save to database
            boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

            if (success) {
                int area = (greater.getBlockX() - lesser.getBlockX()) * (greater.getBlockZ() - lesser.getBlockZ());
                player.sendMessage(ChatColor.GREEN + "\u2713 Faction '" + factionName + "' has been registered!");
                player.sendMessage(ChatColor.GRAY + "  Territory claim #" + claimId + " (" + area + " blocks)");
                player.sendMessage(ChatColor.GRAY + "  Members: " + members);
                player.sendMessage(ChatColor.YELLOW + "It will be reviewed by the lore keepers before being made public.");

                // Notify admins
                plugin.getServer().broadcast(
                    ChatColor.GOLD + player.getName() + " has registered a new faction: " + factionName,
                    "rvnklore.admin"
                );

                logger.debug("Faction lore entry registered: " + factionName + " (claim #" + claimId + ")");
            } else {
                player.sendMessage(ChatColor.RED + "\u2716 Failed to register faction. Please try again later.");
                logger.debug("Failed to register faction lore entry: " + factionName);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid faction registration parameters", e);
            player.sendMessage(ChatColor.RED + "\u2716 Invalid parameters: " + e.getMessage());
            return false;
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            logger.error("Error ID: " + errorId + " - Error registering faction", e);
            player.sendMessage(ChatColor.RED + "\u2716 An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("<faction_name>");
        } else if (args.length == 2) {
            return Arrays.asList("<member1>");
        } else {
            return Arrays.asList("<member" + args.length + ">");
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.register.faction");
    }

    @Override
    public String getDescription() {
        return "Register a new faction with territory claim";
    }
}
