package org.fourz.RVNKLore.discovery;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Listens for cartography table interactions to auto-discover lore locations
 * that fall within a newly created or zoomed map's coverage area.
 *
 * <p>When a player takes a map from a cartography table's result slot, this listener
 * determines the map's center and scale, then triggers CARTOGRAPHY discoveries
 * for any location-based lore entries within the map's coverage radius.</p>
 */
public class CartographyDiscoveryListener implements Listener {

    private final RVNKLore plugin;
    private final DiscoveryManager discoveryManager;
    private final LoreManager loreManager;
    private final LogManager logger;

    /** Lore types that represent physical locations discoverable via maps. */
    private static final Set<LoreType> LOCATION_TYPES = EnumSet.of(
        LoreType.LANDMARK, LoreType.MONUMENT, LoreType.CITY,
        LoreType.TAVERN, LoreType.GUILD, LoreType.SHRINE,
        LoreType.PATH, LoreType.FACTION
    );

    /** Map scale to block coverage radius (half-width): level 0=64, 1=128, 2=256, 3=512, 4=1024 */
    private static final int[] SCALE_RADIUS = {64, 128, 256, 512, 1024};

    public CartographyDiscoveryListener(RVNKLore plugin, DiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.discoveryManager = discoveryManager;
        this.loreManager = plugin.getLoreManager();
        this.logger = LogManager.getInstance(plugin, "CartographyDiscoveryListener");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCartographyClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.CARTOGRAPHY) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Only trigger when taking the result item (slot 2 in cartography table)
        if (event.getRawSlot() != 2) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() != Material.FILLED_MAP) return;
        if (!(result.getItemMeta() instanceof MapMeta)) return;

        MapMeta mapMeta = (MapMeta) result.getItemMeta();
        if (!mapMeta.hasMapView()) return;

        MapView mapView = mapMeta.getMapView();
        if (mapView == null || mapView.getWorld() == null) return;

        Player player = (Player) event.getWhoClicked();
        int centerX = mapView.getCenterX();
        int centerZ = mapView.getCenterZ();
        String worldName = mapView.getWorld().getName();
        int scale = mapView.getScale().ordinal();
        int radius = scale < SCALE_RADIUS.length ? SCALE_RADIUS[scale] : SCALE_RADIUS[SCALE_RADIUS.length - 1];

        logger.debug("Cartography table: " + player.getName() + " created map at "
            + worldName + " " + centerX + "," + centerZ + " scale=" + scale + " radius=" + radius);

        // Run async to avoid blocking the inventory click
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            discoverLoreInMapArea(player, worldName, centerX, centerZ, radius);
        });
    }

    /**
     * Discovers all location-based lore entries within the map's coverage area.
     */
    private void discoverLoreInMapArea(Player player, String worldName, int centerX, int centerZ, int radius) {
        List<LoreEntry> entries = loreManager.getAllLoreEntriesSync();
        int discovered = 0;

        for (LoreEntry entry : entries) {
            if (!entry.isApproved()) continue;
            if (!LOCATION_TYPES.contains(entry.getType())) continue;

            Location loc = entry.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().getName().equals(worldName)) continue;

            // Check if entry is within the map's coverage area
            double dx = Math.abs(loc.getX() - centerX);
            double dz = Math.abs(loc.getZ() - centerZ);

            if (dx <= radius && dz <= radius) {
                discoveryManager.triggerDiscovery(
                    player, entry,
                    DiscoveryTriggerType.CARTOGRAPHY,
                    player.getLocation()
                );
                discovered++;
            }
        }

        if (discovered > 0) {
            logger.debug("Cartography discovery: " + discovered + " entries found in map area for " + player.getName());
        }
    }
}
