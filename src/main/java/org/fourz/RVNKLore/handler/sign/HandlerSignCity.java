package org.fourz.RVNKLore.handler.sign;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handler for creating city markers via sign creation.
 * When a player places a sign with "[City]" on the first line,
 * a new city lore entry is created and a Dynmap marker is placed.
 */
public class HandlerSignCity extends DefaultLoreHandler {
    private static final String CITY_SIGN_HEADER = "[City]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;

    public HandlerSignCity(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignCity");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign city handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(CITY_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.city")) {
            logger.debug(player.getName() + " tried to create a city sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[City]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create city signs.");
            return;
        }

        event.setLine(0, ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "City" + ChatColor.DARK_GREEN + "]");

        String cityName = event.getLine(1);
        if (cityName == null || cityName.trim().isEmpty()) {
            cityName = "Unnamed City";
            event.setLine(1, cityName);
        }

        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "City founded by " + player.getName();
        }

        createCityEntry(player, cityName, description, block);
    }

    private void createCityEntry(Player player, String name, String description, Block block) {
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.CITY, block.getLocation(), player);

        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");

        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("cities.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "City '" + name + "' has been " +
                    (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created city via sign: " + name + " by " + player.getName());

            if (plugin.isDynmapAvailable()) {
                plugin.getDynmapIntegration().getMarkerManager().createOrUpdateMarker(entry);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create city. Please try again or contact an admin.");
            logger.warning("Failed to create city via sign: " + name + " by " + player.getName());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.CITY;
    }
}
