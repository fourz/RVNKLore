package org.fourz.RVNKLore.lore.player;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.service.IPlayerLoreService;
import org.fourz.rvnkcore.util.PlayerLookup;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for player-related lore operations
 *
 * This class centralizes player lore management including:
 * - First join lore creation
 * - Player name change detection and recording
 * - Player lore entry lookup and retrieval
 *
 * <p>Implements IPlayerLoreService for cross-plugin access via RVNKCore ServiceRegistry.</p>
 */
public class PlayerManager implements IPlayerLoreService {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final IPlayerRepository playerRepository;
    private PlayerLookup playerLookup;

    /**
     * Create PlayerManager with default PlayerRepository implementation.
     */
    public PlayerManager(RVNKLore plugin) {
        this(plugin, new PlayerRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection()));
    }

    /**
     * Create PlayerManager with injected repository (for testing/DI).
     *
     * @param plugin The plugin instance
     * @param playerRepository The player repository implementation
     */
    public PlayerManager(RVNKLore plugin, IPlayerRepository playerRepository) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerManager");
        this.playerRepository = playerRepository;
    }

    /**
     * Initialize the player manager
     */
    public void initialize() {
        logger.info("Initializing PlayerManager");
    }

    /**
     * Set the PlayerLookup instance for RVNKCore name resolution.
     *
     * @param playerLookup The PlayerLookup utility, or null to disable
     */
    public void setPlayerLookup(PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
        if (playerLookup != null) {
            logger.info("PlayerLookup integration configured (RVNKCore: " +
                playerLookup.isRVNKCoreEnabled() + ")");
        }
    }

    // ============================================
    // IPlayerLoreService Implementation (Async API)
    // ============================================

    /**
     * Check if a player already has a lore entry in the system.
     *
     * @param playerId The UUID of the player to check
     * @return Future containing true if the player has a lore entry, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> hasPlayer(UUID playerId) {
        return playerRepository.playerExists(playerId);
    }

    /**
     * Get the current player name. Delegates to PlayerLookup (RVNKCore) first,
     * then falls back to the lore repository's stored name.
     *
     * @param playerId The UUID of the player
     * @return Future containing Optional with the player name, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<String>> getPlayerName(UUID playerId) {
        // Try PlayerLookup (RVNKCore → Bukkit) first for authoritative name
        if (playerLookup != null) {
            String name = playerLookup.getPlayerName(playerId);
            if (name != null && !name.equals(playerId.toString().substring(0, 8))) {
                return CompletableFuture.completedFuture(Optional.of(name));
            }
        }
        // Fall back to lore repository's stored name
        return playerRepository.getStoredPlayerName(playerId);
    }

    /**
     * Get the history of name changes for a player.
     *
     * @param playerId The UUID of the player
     * @return Future containing list of name change records, from oldest to newest
     */
    @Override
    public CompletableFuture<List<NameChangeRecord>> getNameChangeHistory(UUID playerId) {
        return playerRepository.getNameChangeHistory(playerId);
    }

    /**
     * Get all lore entry IDs associated with a player.
     *
     * @param playerId The UUID of the player
     * @return Future containing list of lore entry IDs
     */
    @Override
    public CompletableFuture<List<String>> getPlayerLoreEntryIds(UUID playerId) {
        return playerRepository.getPlayerLoreEntryIds(playerId);
    }

    /**
     * Get player lore entries filtered by type.
     *
     * @param playerId The UUID of the player
     * @param entryType The type of entry to filter by (e.g., "first_join", "name_change", "player_character")
     * @return Future containing list of entry IDs matching the type
     */
    @Override
    public CompletableFuture<List<String>> getPlayerLoreEntriesByType(UUID playerId, String entryType) {
        return playerRepository.getPlayerLoreEntriesByType(playerId, entryType);
    }

    /**
     * Check if the service is in fallback mode due to database connectivity issues.
     *
     * @return true if operating in degraded mode, false otherwise
     */
    @Override
    public boolean isInFallbackMode() {
        return playerRepository.isInFallbackMode();
    }

    /**
     * Record that a player has discovered a lore entry.
     *
     * @param playerId The UUID of the player
     * @param entryId The ID of the lore entry discovered
     * @return Future containing true if the discovery was recorded, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> recordLoreDiscovery(UUID playerId, String entryId) {
        logger.debug("Recording lore discovery: player=" + playerId + ", entry=" + entryId);
        return playerRepository.recordLoreDiscovery(playerId, entryId);
    }

    /**
     * Check if a player has discovered a specific lore entry.
     *
     * @param playerId The UUID of the player
     * @param entryId The ID of the lore entry
     * @return Future containing true if the player has discovered this entry
     */
    @Override
    public CompletableFuture<Boolean> hasDiscoveredEntry(UUID playerId, String entryId) {
        return getPlayerLoreEntryIds(playerId)
            .thenApply(entries -> entries.contains(entryId));
    }

    // ============================================
    // Legacy Synchronous API (Internal Use)
    // ============================================

    /**
     * Check if a player already has a lore entry (synchronous version for backwards compatibility)
     *
     * @param playerUuid The UUID of the player to check
     * @return true if the player has a lore entry, false otherwise
     * @deprecated Use async {@link #hasPlayer(UUID)} instead
     */
    @Deprecated
    public boolean playerExists(UUID playerUuid) {
        return playerRepository.playerExists(playerUuid).join();
    }

    /**
     * Get the player's current name stored in the lore system (synchronous version)
     *
     * @param playerUuid The UUID of the player
     * @return The player's name, or null if not found
     * @deprecated Use async {@link #getPlayerName(UUID)} instead
     */
    @Deprecated
    public String getStoredPlayerName(UUID playerUuid) {
        return playerRepository.getStoredPlayerName(playerUuid).join().orElse(null);
    }

    /**
     * Process a player join event
     * - Creates a first join entry if the player is new
     * - Checks for and records name changes if the player exists
     *
     * @param player The player who joined
     * @return true if any action was taken, false otherwise
     */
    public boolean processPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        String currentName = player.getName();

        try {
            logger.debug("Processing player join: " + currentName + " (" + playerUuid + ")");
            
            // Check for existence in a thread-safe manner with synchronization
            // to avoid race conditions with multiple handlers
            synchronized (this) {
                boolean exists = playerExists(playerUuid);
                logger.debug("Player exists check for " + currentName + ": " + exists);
                
                if (!exists) {
                    // New player, create first join entry
                    logger.info("Creating first join entry for new player: " + currentName);
                    return createPlayerLoreEntry(player);
                } else {
                    // Existing player, check for name change
                    String storedName = getStoredPlayerName(playerUuid);
                    logger.debug("Stored name for " + currentName + ": " + storedName);
                    
                    if (storedName != null && !storedName.equals(currentName)) {
                        logger.info("Detected name change for player: " + storedName + " → " + currentName);
                        return createNameChangeLoreEntry(player, storedName);
                    } else {
                        logger.debug("No action needed for existing player: " + currentName);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing player join: " + player.getName(), e);
        }

        return false;
    }

    /**
     * Process a new player joining for the first time
     *
     * @param player The player who joined
     * @return true if the entry was created, false otherwise
     */
    public boolean processFirstTimeJoin(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (!playerExists(playerUuid)) {
            return createFirstJoinLoreEntry(player);
        }

        return false;
    }

    /**
     * Create a lore entry for a player's character
     *
     * @param player The player to create an entry for
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createPlayerLoreEntry(Player player) {
        logger.info("Creating player lore entry for: " + player.getName());

        try {
            // Create a guaranteed unique entry name with UUID
            String uniqueName = "Player_" + player.getUniqueId().toString();

            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);
            entry.setDescription("A player who joined the realm on " +
                              java.time.LocalDate.now().toString());
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");

            // Add metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "player_character");

            // Save to database - automatically approved since this is server-generated
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

            if (success) {
                logger.info("Player lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create player lore entry for: " + player.getName());
            }

            return success;
        } catch (Exception e) {
            logger.error("Error creating player lore entry", e);
            return false;
        }
    }

    /**
     * Create a lore entry for a player's first join
     *
     * @param player The player who joined for the first time
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createFirstJoinLoreEntry(Player player) {
        logger.info("Creating first join lore entry for: " + player.getName());

        try {
            // Create a guaranteed unique entry name with UUID
            String uniqueName = "FirstJoin_" + player.getUniqueId().toString();

            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);

            // Format date using SimpleDateFormat for consistent display
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String dateString = dateFormat.format(new Date());

            entry.setDescription(player.getName() + " first set foot in our world on " + dateString + ".\n" +
                              "Welcome to a new adventurer in our realm!");
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");

            // Add essential metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "first_join");

            // Location coordinates as metadata
            entry.addMetadata("join_location", formatLocation(player.getLocation()));

            // Auto-approve server-generated entries
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

            if (success) {
                // Notify the player
                player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
                logger.info("First join lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create first join lore entry for: " + player.getName());
            }

            return success;
        } catch (Exception e) {
            logger.error("Error creating first join lore entry", e);
            return false;
        }
    }

    /**
     * Create a lore entry for a player name change
     *
     * @param player The player who changed their name
     * @param oldName The previous name of the player
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createNameChangeLoreEntry(Player player, String oldName) {
        logger.info("Creating name change lore entry: " + oldName + " → " + player.getName());

        try {
            // Create a guaranteed unique entry name with UUID and timestamp
            String uniqueName = "NameChange_" + player.getUniqueId().toString() + "_" + System.currentTimeMillis();

            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);

            // Format date using SimpleDateFormat for consistent display
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String dateString = dateFormat.format(new Date());

            entry.setDescription("The adventurer known as " + oldName + " shall henceforth be known as " +
                              player.getName() + ".\nName changed on " + dateString + ".");
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");

            // Add essential metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("previous_name", oldName);
            entry.addMetadata("name_change_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "name_change");

            // Auto-approve server-generated entries
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

            if (success) {
                // Notify the player
                player.sendMessage(ChatColor.GOLD + "Your name change has been recorded in the annals of history!");
                logger.info("Name change lore entry created: " + oldName + " → " + player.getName());
            } else {
                logger.warning("Failed to create name change lore entry: " + oldName + " → " + player.getName());
            }

            return success;
        } catch (Exception e) {
            logger.error("Error creating name change lore entry", e);
            return false;
        }
    }

    /**
     * Format a location as a string
     *
     * @param location The location to format
     * @return Formatted location string
     */
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }

        return String.format("%s,%d,%d,%d",
            location.getWorld().getName(),
            (int)location.getX(),
            (int)location.getY(),
            (int)location.getZ());
    }
}



