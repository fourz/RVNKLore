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
 * Handler for creating temple/shrine markers via sign creation.
 * When a player places a sign with "[Temple]" on the first line,
 * a new SHRINE lore entry is created and a Dynmap marker is placed.
 */
public class HandlerSignTemple extends DefaultLoreHandler {
    private static final String TEMPLE_SIGN_HEADER = "[Temple]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;

    public HandlerSignTemple(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignTemple");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign temple handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(TEMPLE_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.temple")) {
            logger.debug(player.getName() + " tried to create a temple sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Temple]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create temple signs.");
            return;
        }

        event.setLine(0, ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Temple" + ChatColor.DARK_PURPLE + "]");

        String templeName = event.getLine(1);
        if (templeName == null || templeName.trim().isEmpty()) {
            templeName = "Unnamed Temple";
            event.setLine(1, templeName);
        }

        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "Temple dedicated by " + player.getName();
        }

        createTempleEntry(player, templeName, description, block);
    }

    private void createTempleEntry(Player player, String name, String description, Block block) {
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.SHRINE, block.getLocation(), player);

        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");

        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("temples.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Temple '" + name + "' has been " +
                    (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created temple via sign: " + name + " by " + player.getName());

            if (plugin.isDynmapAvailable()) {
                plugin.getDynmapIntegration().getMarkerManager().createOrUpdateMarker(entry);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create temple. Please try again or contact an admin.");
            logger.warning("Failed to create temple via sign: " + name + " by " + player.getName());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.SHRINE;
    }
}
