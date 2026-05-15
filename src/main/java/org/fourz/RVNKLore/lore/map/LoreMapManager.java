package org.fourz.RVNKLore.lore.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.Bukkit;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.repository.MapRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Optional;

/**
 * Manages lore map creation, storage, and retrieval.
 *
 * Maps are stored in lore_map and can be reconstructed on any server sharing
 * the same database. The pixel_data column holds a base64-encoded 128×128 ARGB
 * byte array for full visual fidelity. Coordinate metadata (center_x/z, scale,
 * world_name, dimension) is always stored even when pixel_data is absent, so
 * maps can be regenerated in-server from live world data.
 *
 * Subtype mapping:
 *   TREASURE → QUEST entries       (partial/hand-drawn style, X marks the spot)
 *   ATLAS    → CITY/LANDMARK/FACTION (overview map centred on entry location)
 *   PIXEL    → standalone maps     (no linked entry, full pixel_data required)
 */
public class LoreMapManager {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreMapManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreMapManager");
    }

    // ==================== Repository shorthand ====================

    private MapRepository repo() {
        return plugin.getDatabaseManager().getMapRepository();
    }

    // ==================== Query ====================

    public Optional<LoreMap> getMapById(int mapId) {
        return repo().findById(mapId).join();
    }

    public List<LoreMap> getMapsForEntry(String entryId) {
        return repo().findByEntryId(entryId).join();
    }

    public List<LoreMap> getMapsBySubtype(MapSubtype subtype) {
        return repo().findBySubtype(subtype).join();
    }

    // ==================== Creation ====================

    /**
     * Register a new lore map record derived from a lore entry.
     * The subtype is inferred from the entry type.
     *
     * @param entry  the lore entry to create a map for
     * @param player the player requesting the map (provides position/world context)
     * @return the saved LoreMap, or empty if creation failed
     */
    public Optional<LoreMap> createMapForEntry(LoreEntry entry, Player player) {
        if (entry == null) return Optional.empty();

        MapSubtype subtype = subtypeFor(entry.getType());
        Location loc = entry.getLocation() != null ? entry.getLocation() : player.getLocation();

        LoreMap map = new LoreMap(
            entry.getId(),
            subtype,
            loc.getBlockX(),
            loc.getBlockZ(),
            (byte) 0,
            loc.getWorld() != null ? loc.getWorld().getName() : "world",
            "NORMAL",
            player.getName()
        );

        Optional<LoreMap> saved = repo().save(map).join();
        saved.ifPresent(m -> logger.info("Created " + subtype.name() + " map #" + m.getId() +
                " for entry '" + entry.getName() + "'"));
        return saved;
    }

    /**
     * Register a standalone PIXEL map (no linked lore entry).
     */
    public Optional<LoreMap> createPixelMap(int centerX, int centerZ, String worldName,
                                             String base64PixelData, String createdBy) {
        LoreMap map = new LoreMap(null, MapSubtype.PIXEL, centerX, centerZ, (byte) 0,
                worldName, "NORMAL", createdBy);
        map.setPixelData(base64PixelData);
        Optional<LoreMap> saved = repo().save(map).join();
        saved.ifPresent(m -> logger.info("Created PIXEL map #" + m.getId() + " by " + createdBy));
        return saved;
    }

    // ==================== Pixel data ====================

    public boolean storePixelData(int mapId, String base64Data) {
        return repo().updatePixelData(mapId, base64Data).join();
    }

    // ==================== Item generation ====================

    /**
     * Give the player a Minecraft filled_map item linked to a new MapView.
     * The map is centred on the LoreMap's stored coordinates.
     *
     * If the server's Dynmap or actual world data is unavailable, the map item
     * is still created — it will render as blank until explored in-game.
     *
     * @param player   recipient
     * @param loreMap  stored map record
     * @return true if the item was placed in inventory (or dropped at feet)
     */
    public boolean giveMapItem(Player player, LoreMap loreMap) {
        if (loreMap.getWorldName() == null) return false;

        org.bukkit.World world = Bukkit.getWorld(loreMap.getWorldName());
        MapView view;
        if (world != null) {
            view = Bukkit.createMap(world);
            view.setCenterX(loreMap.getCenterX());
            view.setCenterZ(loreMap.getCenterZ());
            view.setScale(MapView.Scale.values()[Math.max(0, Math.min(loreMap.getScale(), 4))]);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(true);
        } else {
            // World not loaded on this server — give a blank map with metadata for future use
            logger.debug("World '" + loreMap.getWorldName() + "' not loaded; issuing blank map item");
            ItemStack blank = new ItemStack(Material.MAP);
            giveOrDrop(player, blank);
            return true;
        }

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.setDisplayName("§6Lore Map #" + loreMap.getId() +
                    (loreMap.getSubtype() != null ? " [" + loreMap.getSubtype().name() + "]" : ""));
            item.setItemMeta(meta);
        }

        giveOrDrop(player, item);
        logger.info("Gave map #" + loreMap.getId() + " to " + player.getName());
        return true;
    }

    // ==================== Deletion ====================

    public boolean deleteMap(int mapId) {
        return repo().deleteById(mapId).join();
    }

    public boolean deleteMapsForEntry(String entryId) {
        return repo().deleteByEntryId(entryId).join();
    }

    // ==================== Helpers ====================

    private MapSubtype subtypeFor(LoreType type) {
        if (type == null) return MapSubtype.ATLAS;
        return switch (type) {
            case QUEST -> MapSubtype.TREASURE;
            case CITY, LANDMARK, FACTION, MONUMENT, GUILD, SHRINE, TAVERN -> MapSubtype.ATLAS;
            default -> MapSubtype.ATLAS;
        };
    }

    private void giveOrDrop(Player player, ItemStack item) {
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }
}
