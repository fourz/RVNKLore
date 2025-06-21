package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand for registering city lore entries (async, DTO-based)
 */
public class LoreRegisterCitySubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private static final double MIN_CITY_DISTANCE = 100.0; // Minimum blocks between cities

    public LoreRegisterCitySubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreRegisterCitySubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "&c✖ This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(ChatColor.RED + "&c✖ You don't have permission to register city lore.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "&c▶ Usage: " + getUsage());
            return false;
        }
        String cityName = args[0];
        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));        // Validate city name format
        if (!cityName.matches("^[a-zA-Z0-9\\s-]{3,32}$")) {
            player.sendMessage(ChatColor.RED + "&c✖ City name must be 3-32 characters and contain only letters, numbers, spaces and hyphens.");
            return false;
        }
          player.sendMessage(ChatColor.YELLOW + "⚙ Checking if city exists and validating location...");
        
        // Async: Check if city already exists
        databaseManager.searchLoreEntries(cityName).thenAccept(results -> {
            // Check if any existing entry has the exact name match
            boolean nameExists = results.stream()
                .anyMatch(entry -> entry.getName().equalsIgnoreCase(cityName));
                
            if (nameExists) {
                player.sendMessage(ChatColor.RED + "✖ A city with that name already exists!");
                return;
            }
            
            // Async: Get all city entries for distance check
            databaseManager.getLoreEntriesByType(LoreType.CITY.name()).thenAccept(cityEntries -> {                Location playerLoc = player.getLocation();
                
                // Check minimum distance to other cities
                boolean tooClose = false;
                String closestCityName = null;
                double closestDistance = Double.MAX_VALUE;
                
                for (LoreEntryDTO existingCity : cityEntries) {
                    if (existingCity.getWorld() == null || !existingCity.getWorld().equals(playerLoc.getWorld().getName())) {
                        continue;
                    }
                    
                    Location cityLoc = new Location(
                        playerLoc.getWorld(),
                        existingCity.getX(),
                        existingCity.getY(),
                        existingCity.getZ()
                    );
                    
                    double distance = playerLoc.distance(cityLoc);
                    
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestCityName = existingCity.getName();
                    }
                    
                    if (distance < MIN_CITY_DISTANCE) {
                        tooClose = true;
                        break;
                    }
                }
                  if (tooClose) {
                    player.sendMessage(ChatColor.RED + "✖ Cities must be at least " + MIN_CITY_DISTANCE + 
                                      " blocks apart. Too close to " + closestCityName);
                    return;
                }
                
                // Create the lore entry DTO
                LoreEntryDTO dto = new LoreEntryDTO();
                String transactionId = UUID.randomUUID().toString();
                  // Basic fields
                dto.setUuid(UUID.randomUUID().toString());
                dto.setEntryType(LoreType.CITY.name());
                dto.setName(cityName);
                dto.setDescription(description.toString().trim());
                dto.setWorld(playerLoc.getWorld().getName());
                dto.setX(playerLoc.getX());                dto.setY(playerLoc.getY());
                dto.setZ(playerLoc.getZ());
                dto.setSubmittedBy(player.getName());
                dto.setApproved(false); // City entries need approval
                
                // Create metadata map
                Map<String, String> metadata = new HashMap<>();
                metadata.put("transaction_id", transactionId);
                metadata.put("founding_date", String.valueOf(System.currentTimeMillis()));
                metadata.put("founder_uuid", player.getUniqueId().toString());
                metadata.put("registration_time", String.valueOf(System.currentTimeMillis()));
                metadata.put("registration_version", plugin.getDescription().getVersion());
                metadata.put("validation_checks", "distance,name,description,location");
                metadata.put("nearest_city_distance", String.valueOf(closestDistance));
                dto.setMetadata(metadata);
                
                // Convert DTO to LoreEntry for validation
                LoreEntry entry = LoreEntry.fromDTO(dto);
                
                // Validate with handler
                LoreHandler handler = plugin.getHandlerFactory().getHandler(LoreType.CITY);                if (!handler.validateEntry(entry)) {
                    player.sendMessage(ChatColor.RED + "✖ City entry validation failed. Please check all requirements.");
                    return;
                }
                
                // Async: Save to database
                databaseManager.saveLoreEntry(dto).thenAccept(id -> {
                    if (id > 0) {
                        player.sendMessage(ChatColor.GREEN + "✓ City '" + cityName + "' has been registered in the lore books!");
                        player.sendMessage(ChatColor.YELLOW + "   It will be reviewed by the lore keepers before being made public.");
                        plugin.getServer().broadcast(ChatColor.GOLD + player.getName() + " has registered a new city: " + cityName, "rvnklore.admin");
                        logger.info("City lore entry registered successfully: " + cityName);
                    } else {
                        player.sendMessage(ChatColor.RED + "✖ Failed to register city. Please try again later.");
                        logger.warning("Failed to register city lore entry: " + cityName);
                    }
                }).exceptionally(e -> {
                    String errorId = UUID.randomUUID().toString();
                    logger.error("Error ID: " + errorId + " - Error registering city", e);
                    player.sendMessage(ChatColor.RED + "✖ An error occurred (ID: " + errorId + "). Please report this to an administrator.");
                    return null;
                });
            });
        });
        return true;
    }

    private double findNearestCityDistanceAsync(Location location, List<LoreEntryDTO> cityEntries) {
        return cityEntries.stream()
            .filter(entry -> entry.getLocation() != null && location.getWorld().equals(entry.getLocation().getWorld()))
            .mapToDouble(entry -> location.distance(entry.getLocation()))
            .min().orElse(-1);
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
