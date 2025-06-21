package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.repository.LoreEntryRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handler for city lore entries
 */
public class CityLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MIN_DESCRIPTION_LENGTH = 10;
    
    public CityLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CityLoreHandler");
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public void initialize() {
        logger.debug("Initializing city lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        List<String> validationErrors = new ArrayList<>();
        
        if (entry.getName() == null || entry.getName().isEmpty()) {
            validationErrors.add("Name is required");
        } else if (!entry.getName().matches("^[a-zA-Z0-9\\s-]{3,32}$")) {
            validationErrors.add("Invalid city name format");
        } else {
            // Check for duplicate names asynchronously
            try {
                LoreEntryRepository repo = databaseManager.getLoreEntryRepository();
                LoreEntryDTO existingEntry = repo.getLoreEntryByName(entry.getName())
                    .get(5, TimeUnit.SECONDS); // Short timeout for validation
                
                if (existingEntry != null && !existingEntry.getUuidString().equals(entry.getId())) {
                    validationErrors.add("City name already exists");
                }
            } catch (Exception e) {
                logger.error("Error checking for duplicate city name", e);
                validationErrors.add("Error validating city name");
            }
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            validationErrors.add("Description is required");
        } else if (entry.getDescription().length() < MIN_DESCRIPTION_LENGTH) {
            validationErrors.add("Description too short");
        }
        
        // Enhanced description validation
        if (entry.getDescription() != null) {
            if (entry.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
                validationErrors.add("Description too long (max " + MAX_DESCRIPTION_LENGTH + " characters)");
            }
            if (containsInvalidCharacters(entry.getDescription())) {
                validationErrors.add("Description contains invalid characters");
            }
        }
        
        if (entry.getLocation() == null) {
            validationErrors.add("Location is required");
        } else {
            // Validate world exists
            if (plugin.getServer().getWorld(entry.getLocation().getWorld().getName()) == null) {
                validationErrors.add("Invalid world");
            }
            // Validate coordinates are within world bounds
            if (Math.abs(entry.getLocation().getX()) > 29999984 || 
                Math.abs(entry.getLocation().getZ()) > 29999984) {
                validationErrors.add("Location coordinates out of valid range");
            }
        }
        
        // Record validation metadata
        entry.setMetadata("validation_attempt_time", String.valueOf(System.currentTimeMillis()));
        entry.setMetadata("validation_errors", String.join(";", validationErrors));
        
        // Update metadata in database asynchronously
        if (!validationErrors.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    databaseManager.getLoreEntryRepository()
                        .updateLoreEntry(entry.toDTO())
                        .get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.error("Error updating validation metadata", e);
                }
            });
            
            logger.debug("City validation failed for " + entry.getName() + 
                       " (Transaction: " + entry.getMetadata("transaction_id") + "): " + 
                       String.join(", ", validationErrors));
            return false;
        }
        
        return true;
    }

    private boolean containsInvalidCharacters(String text) {
        // Check for illegal formatting codes or characters
        return text.contains("§") || 
               text.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*");
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.CHISELED_STONE_BRICKS);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "City of " + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Settlement");
            // Format founding date if available
            if (entry.getMetadata("founding_date") != null) {
                try {
                    long foundingTimestamp = Long.parseLong(entry.getMetadata("founding_date"));
                    Date foundingDate = new Date(foundingTimestamp);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    lore.add(ChatColor.GRAY + "Founded: " + ChatColor.WHITE + sdf.format(foundingDate));
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse founding date for city: " + entry.getName());
                }
            }
            
            // Add founder if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Founder: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            // Add location
            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Location: " + 
                        ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                        (int)entry.getLocation().getX() + ", " + 
                        (int)entry.getLocation().getY() + ", " + 
                        (int)entry.getLocation().getZ());
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.AQUA + "==== City of " + entry.getName() + " ====");
        
        // Format founding date if available
        if (entry.getMetadata("founding_date") != null) {
            try {
                long foundingTimestamp = Long.parseLong(entry.getMetadata("founding_date"));
                Date foundingDate = new Date(foundingTimestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                player.sendMessage(ChatColor.GRAY + "Founded: " + ChatColor.WHITE + sdf.format(foundingDate));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse founding date for city: " + entry.getName());
            }
        }
        
        // Add founder if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Founder: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        // Add location
        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Location: " + 
                    ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.CITY;
    }
}
