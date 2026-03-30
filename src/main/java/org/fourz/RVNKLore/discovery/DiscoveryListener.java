package org.fourz.RVNKLore.discovery;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for various game events that can trigger lore discoveries.
 *
 * <h2>Supported Triggers:</h2>
 * <ul>
 *   <li>Sign interaction - Reading lore signs</li>
 *   <li>Block break - Discovering ancient artifacts</li>
 *   <li>Mob death - Rare lore drops from named mobs</li>
 *   <li>Chest open - Finding lore items in containers</li>
 *   <li>Location proximity - Entering specific coordinates</li>
 * </ul>
 */
public class DiscoveryListener implements Listener {

    private final RVNKLore plugin;
    private final DiscoveryManager discoveryManager;
    private final LoreManager loreManager;
    private final LogManager logger;

    // PersistentDataContainer key for lore entry ID (matches LoreBookManager)
    private final NamespacedKey loreEntryIdKey;

    // Location-based discovery tracking (to prevent spam)
    private final Map<UUID, Set<String>> recentLocationDiscoveries = new ConcurrentHashMap<>();

    // Cache of locations with lore entries
    private final Map<String, LoreEntry> locationLoreCache = new ConcurrentHashMap<>();

    public DiscoveryListener(RVNKLore plugin, DiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.discoveryManager = discoveryManager;
        this.loreManager = plugin.getLoreManager();
        this.logger = LogManager.getInstance(plugin, "DiscoveryListener");
        this.loreEntryIdKey = new NamespacedKey(plugin, "lore_entry_id");

        // Build location cache
        buildLocationCache();
    }

    /**
     * Builds a cache of lore entries with location data.
     */
    private void buildLocationCache() {
        locationLoreCache.clear();

        List<LoreEntry> entries = loreManager.getAllLoreEntriesSync();
        for (LoreEntry entry : entries) {
            if (entry.getLocation() != null) {
                String locationKey = getLocationKey(entry.getLocation());
                locationLoreCache.put(locationKey, entry);
            }
        }

        logger.debug("Built location cache with " + locationLoreCache.size() + " entries");
    }

    /**
     * Handles sign interaction for lore discovery.
     * Signs can contain lore entry references.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if it's a sign
        if (!(block.getState() instanceof Sign)) return;

        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();

        // Check for lore sign marker
        // Format: [LORE] on first line, entry ID on second line
        if (lines.length < 2) return;
        if (!lines[0].equalsIgnoreCase("[LORE]") && !lines[0].equalsIgnoreCase("[RVNKLore]")) return;

        String entryRef = lines[1].trim();
        if (entryRef.isEmpty()) return;

        Player player = event.getPlayer();

        // Find the lore entry
        findLoreEntry(entryRef).ifPresent(entry -> {
            discoveryManager.triggerDiscovery(
                player, entry,
                DiscoveryTriggerType.BLOCK_INTERACT,
                block.getLocation()
            );
        });
    }

    /**
     * Handles block break for discovering ancient artifacts.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        // Check for blocks that might contain lore
        if (!isLoreBlock(type)) return;

        Player player = event.getPlayer();
        Location location = block.getLocation();

        // Check if there's a lore entry at this location
        String locationKey = getLocationKey(location);
        LoreEntry entry = locationLoreCache.get(locationKey);

        if (entry != null && entry.getType() == LoreType.ITEM) {
            discoveryManager.triggerDiscovery(
                player, entry,
                DiscoveryTriggerType.BLOCK_BREAK,
                location
            );
        }
    }

    /**
     * Handles mob death for rare lore drops from named entities.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Check for named mob with potential lore drop
        if (entity.getCustomName() == null) return;

        String mobName = entity.getCustomName();

        // Search for a lore entry matching this mob
        loreManager.findLoreEntries(mobName).thenAccept(entries -> {
            for (LoreEntry entry : entries) {
                // Check for mob-related lore (could be GENERIC type with mob metadata)
                if (entry.getName().equalsIgnoreCase(mobName) ||
                    (entry.getType() == LoreType.EVENT && entry.getName().contains(mobName))) {

                    discoveryManager.triggerDiscovery(
                        killer, entry,
                        DiscoveryTriggerType.MOB_KILL,
                        entity.getLocation()
                    );
                    break;
                }
            }
        });
    }

    /**
     * Handles chest/container opening for lore item discovery.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Only check container blocks (chests, barrels, etc.)
        if (holder == null) return;

        // Make final for lambda usage
        final Location containerLocation = (holder instanceof Block)
            ? ((Block) holder).getLocation()
            : null;

        // Check inventory contents for lore items
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (!item.hasItemMeta()) continue;
            if (!item.getItemMeta().hasLore()) continue;

            List<String> lore = item.getItemMeta().getLore();
            if (lore == null) continue;

            // Check for lore entry ID in item lore
            // Format: "§8Lore ID: <id>" or similar
            for (String line : lore) {
                String stripped = line.replaceAll("§.", "").trim();
                if (stripped.startsWith("Lore ID:")) {
                    String entryId = stripped.substring(8).trim();
                    findLoreEntry(entryId).ifPresent(entry -> {
                        discoveryManager.triggerDiscovery(
                            player, entry,
                            DiscoveryTriggerType.CHEST_LOOT,
                            containerLocation
                        );
                    });
                }
            }
        }
    }

    /**
     * Handles player movement for location-based discovery.
     * Uses chunking to reduce performance impact.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check on block change
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Check for nearby lore entries
        for (Map.Entry<String, LoreEntry> mapEntry : locationLoreCache.entrySet()) {
            LoreEntry entry = mapEntry.getValue();
            Location entryLoc = entry.getLocation();

            if (entryLoc == null) continue;
            if (!entryLoc.getWorld().equals(to.getWorld())) continue;

            double distance = to.distance(entryLoc);
            if (distance > plugin.getConfigManager().getNearbyRadius()) continue;

            // Check if recently discovered at this location
            Set<String> recentLocations = recentLocationDiscoveries.computeIfAbsent(
                playerUuid, k -> ConcurrentHashMap.newKeySet()
            );

            String locationKey = getLocationKey(entryLoc);
            if (recentLocations.contains(locationKey)) continue;

            // Mark as recently discovered
            recentLocations.add(locationKey);

            // Trigger discovery
            discoveryManager.triggerDiscovery(
                player, entry,
                DiscoveryTriggerType.LOCATION_ENTER,
                to
            );

            // Schedule removal from recent list after cooldown
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recentLocations.remove(locationKey);
            }, 20 * 60 * 5); // 5 minute cooldown
        }
    }

    /**
     * Handles item use (right-click) for lore discovery.
     * Checks held items for lore entry references via PersistentDataContainer
     * or item lore text, then triggers ITEM_USE discovery.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Skip sign interactions (handled by onSignInteract)
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getState() instanceof Sign) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();

        // Primary: check PersistentDataContainer for lore entry ID (lore books + tagged items)
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(loreEntryIdKey, PersistentDataType.STRING)) {
            String entryId = pdc.get(loreEntryIdKey, PersistentDataType.STRING);
            if (entryId != null && !entryId.isEmpty()) {
                findLoreEntry(entryId).ifPresent(entry ->
                    discoveryManager.triggerDiscovery(
                        player, entry,
                        DiscoveryTriggerType.ITEM_USE,
                        player.getLocation()
                    )
                );
                return;
            }
        }

        // Fallback: check item lore text for "Lore ID: <id>"
        if (!meta.hasLore()) return;
        List<String> lore = meta.getLore();
        if (lore == null) return;

        for (String line : lore) {
            String stripped = line.replaceAll("§.", "").trim();
            if (stripped.startsWith("Lore ID:")) {
                String entryId = stripped.substring(8).trim();
                findLoreEntry(entryId).ifPresent(entry ->
                    discoveryManager.triggerDiscovery(
                        player, entry,
                        DiscoveryTriggerType.ITEM_USE,
                        player.getLocation()
                    )
                );
                return;
            }
        }
    }

    /**
     * Handles first-join discovery granting.
     * On a player's first join, grants all lore entries tagged with
     * metadata "discovery_trigger" = "FIRST_JOIN" after a short delay.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;

        // Delay so the player loads in and gets oriented (5 seconds = 100 ticks)
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();

            List<LoreEntry> starterEntries = new ArrayList<>();
            for (LoreEntry entry : allEntries) {
                if (!entry.isApproved()) continue;
                String trigger = entry.getMetadata("discovery_trigger");
                if ("FIRST_JOIN".equalsIgnoreCase(trigger)) {
                    starterEntries.add(entry);
                }
            }

            if (starterEntries.isEmpty()) {
                logger.debug("No FIRST_JOIN starter lore entries configured");
                return;
            }

            logger.debug("Granting " + starterEntries.size() + " starter discoveries to " + player.getName());

            for (LoreEntry entry : starterEntries) {
                discoveryManager.triggerDiscovery(
                    player, entry,
                    DiscoveryTriggerType.FIRST_JOIN,
                    player.getLocation()
                );
            }
        }, 100L);
    }

    /**
     * Bridges lore discovery events to the achievement system.
     * Only processes first-time discoveries for the player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoreDiscovery(LoreDiscoveryEvent event) {
        if (!event.isFirstForPlayer()) return;

        AchievementManager achievementManager = plugin.getAchievementManager();
        if (achievementManager != null) {
            achievementManager.onLoreDiscovery(event.getPlayer(), event.getLoreEntry());
        }
    }

    /**
     * Cleans up player tracking data on quit to prevent memory leaks.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        clearPlayerData(playerUuid);
        discoveryManager.clearCooldowns(playerUuid);
    }

    /**
     * Checks if a block type can contain lore.
     */
    private boolean isLoreBlock(Material type) {
        switch (type) {
            case CHISELED_STONE_BRICKS:
            case CRACKED_STONE_BRICKS:
            case MOSSY_STONE_BRICKS:
            case ANCIENT_DEBRIS:
            case SPAWNER:
            case BOOKSHELF:
            case LECTERN:
            case BARREL:
            case CHEST:
            case TRAPPED_CHEST:
            case ENDER_CHEST:
                return true;
            default:
                return false;
        }
    }

    /**
     * Finds a lore entry by ID or name.
     */
    private Optional<LoreEntry> findLoreEntry(String reference) {
        // Try by ID first
        try {
            UUID uuid = UUID.fromString(reference);
            return loreManager.getLoreEntry(uuid).join();
        } catch (IllegalArgumentException e) {
            // Not a UUID, try by name or partial ID
        }

        // Try by partial ID
        Optional<LoreEntry> byId = loreManager.getLoreById(reference);
        if (byId.isPresent()) return byId;

        // Try by name
        try {
            return loreManager.getLoreEntryByName(reference).join();
        } catch (Exception e) {
            logger.debug("Could not find lore entry: " + reference);
        }

        return Optional.empty();
    }

    /**
     * Creates a unique key for a location (rounded to block).
     */
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" +
               location.getBlockX() + ":" +
               location.getBlockY() + ":" +
               location.getBlockZ();
    }

    /**
     * Refreshes the location cache (call after lore entries are modified).
     */
    public void refreshLocationCache() {
        buildLocationCache();
    }

    /**
     * Clears tracking data for a player (call on player quit).
     */
    public void clearPlayerData(UUID playerUuid) {
        recentLocationDiscoveries.remove(playerUuid);
    }
}
