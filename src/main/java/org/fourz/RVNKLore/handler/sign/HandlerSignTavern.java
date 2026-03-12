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
 * Handler for creating taverns via sign creation.
 * When a player places a sign with "[Tavern]" on the first line,
 * a new tavern lore entry is created.
 */
public class HandlerSignTavern extends DefaultLoreHandler {
    private static final String TAVERN_SIGN_HEADER = "[Tavern]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;

    public HandlerSignTavern(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignTavern");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign tavern handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(TAVERN_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.tavern")) {
            logger.debug(player.getName() + " tried to create a tavern sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Tavern]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create tavern signs.");
            return;
        }

        // Format the sign display
        event.setLine(0, ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + "Tavern" + ChatColor.DARK_AQUA + "]");

        String tavernName = event.getLine(1);
        if (tavernName == null || tavernName.trim().isEmpty()) {
            tavernName = "Unnamed Tavern";
            event.setLine(1, tavernName);
        }

        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "Tavern established by " + player.getName();
        }

        createTavernEntry(player, tavernName, description, block);
    }

    private void createTavernEntry(Player player, String name, String description, Block block) {
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.TAVERN, block.getLocation(), player);

        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");

        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("taverns.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Tavern '" + name + "' has been " +
                    (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created tavern via sign: " + name + " by " + player.getName());

            if (plugin.isDynmapAvailable()) {
                plugin.getDynmapIntegration().getMarkerManager().createOrUpdateMarker(entry);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create tavern. Please try again or contact an admin.");
            logger.warning("Failed to create tavern via sign: " + name + " by " + player.getName());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.TAVERN;
    }
}
