package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.handler.LoreHandler;


import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Subcommand for registering city lore entries
 */
public class LoreRegisterCitySubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final Debug debug;
    private static final double MIN_CITY_DISTANCE = 100.0; // Minimum blocks between cities
    
    public LoreRegisterCitySubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "CitySubCommand", Level.FINE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("rvnklore.register.city")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to register city lore.");
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /lore registercity <name> <description>");
            return false;
        }
        
        String cityName = args[0];
        
        // Combine remaining args for description
        StringBuilder description = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            description.append(args[i]).append(" ");
        }
        
        try {
            // Validate city name format
            if (!cityName.matches("^[a-zA-Z0-9\\s-]{3,32}$")) {
                player.sendMessage(ChatColor.RED + "City name must be 3-32 characters and contain only letters, numbers, spaces and hyphens.");
                return false;
            }
            
            // Check if city already exists
            if (plugin.getLoreManager().getLoreEntryByName(cityName) != null) {
                player.sendMessage(ChatColor.RED + "A city with that name already exists!");
                return false;
            }

            // Get existing city entries only once for efficiency
            List<LoreEntry> cityEntries = plugin.getLoreManager().getLoreEntriesByType(LoreType.CITY);
            
            // Check distance to other cities
            for (LoreEntry existingEntry : cityEntries) {
                double distance = player.getLocation().distance(existingEntry.getLocation());
                if (distance < MIN_CITY_DISTANCE) {
                    player.sendMessage(ChatColor.RED + "Cities must be at least " + MIN_CITY_DISTANCE + 
                                      " blocks apart. Too close to " + existingEntry.getName());
                    return false;
                }
            }

            // Create the lore entry with transaction ID
            String transactionId = java.util.UUID.randomUUID().toString();
            LoreEntry entry = new LoreEntry();
            entry.addMetadata("transaction_id", transactionId);
            debug.debug("Starting city registration transaction: " + transactionId);

            entry.setType(LoreType.CITY);
            entry.setName(cityName);
            entry.setDescription(description.toString().trim());
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy(player.getName());
            
            // Add founding date metadata
            entry.addMetadata("founding_date", System.currentTimeMillis() + "");
            entry.addMetadata("founder_uuid", player.getUniqueId().toString());
            
            // Add additional city metadata
            entry.addMetadata("world", player.getWorld().getName());
            entry.addMetadata("coordinates", 
                String.format("%.1f,%.1f,%.1f", 
                    player.getLocation().getX(), 
                    player.getLocation().getY(), 
                    player.getLocation().getZ()));
            
            // Enhanced metadata
            entry.addMetadata("registration_time", System.currentTimeMillis() + "");
            entry.addMetadata("registration_version", plugin.getDescription().getVersion());
            
            // Add validation metadata
            entry.addMetadata("validation_checks", "distance,name,description,location");
            entry.addMetadata("nearest_city_distance", findNearestCityDistance(player.getLocation()) + "");
            
            // Validate against handler before saving
            LoreHandler handler = plugin.getHandlerFactory().getHandler(LoreType.CITY);
            if (!handler.validateEntry(entry)) {
                player.sendMessage(ChatColor.RED + "City entry validation failed. Please check all requirements.");
                return false;
            }
            
            // City entries need approval
            entry.setApproved(false);
            
            // Save to database
            boolean success = plugin.getLoreManager().addLoreEntry(entry);
            
            if (success) {
                player.sendMessage(ChatColor.GREEN + "City '" + cityName + "' has been registered in the lore books!");
                player.sendMessage(ChatColor.YELLOW + "It will be reviewed by the lore keepers before being made public.");
                
                // Notify admins
                plugin.getServer().broadcast(
                    ChatColor.GOLD + player.getName() + " has registered a new city: " + cityName,
                    "rvnklore.admin"
                );
                
                debug.debug("City lore entry registered successfully: " + cityName);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to register city. Please try again later.");
                debug.debug("Failed to register city lore entry: " + cityName);
            }
        } catch (IllegalArgumentException e) {
            debug.error("Invalid city registration parameters", e);
            player.sendMessage(ChatColor.RED + "Invalid parameters: " + e.getMessage());
            return false;
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            debug.error("Error ID: " + errorId + " - Error registering city", e);
            player.sendMessage(ChatColor.RED + "An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }
        
        return true;
    }

    private double findNearestCityDistance(Location location) {
        double nearest = Double.MAX_VALUE;
        for (LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
            if (entry.getType() == LoreType.CITY) {
                double distance = location.distance(entry.getLocation());
                nearest = Math.min(nearest, distance);
            }
        }
        return nearest == Double.MAX_VALUE ? -1 : nearest;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("<city_name>");
        } else if (args.length == 2) {
            return Arrays.asList("<description>");
        }
        return null;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    
    public String getPermission() {
        return "rvnklore.register.city";
    }

    
    public String getUsage() {
        return "/lore registercity <name> <description>";
    }

    @Override
    public String getDescription() {
        return "Register a new city in the lore books";
    }
}
