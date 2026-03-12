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
 * Handler for creating monuments via sign creation.
 * When a player places a sign with "[Monument]" on the first line,
 * a new monument lore entry is created.
 */
public class HandlerSignMonument extends DefaultLoreHandler {
    private static final String MONUMENT_SIGN_HEADER = "[Monument]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;

    public HandlerSignMonument(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignMonument");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign monument handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(MONUMENT_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.monument")) {
            logger.debug(player.getName() + " tried to create a monument sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Monument]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create monument signs.");
            return;
        }

        // Format the sign display
        event.setLine(0, ChatColor.DARK_RED + "[" + ChatColor.GOLD + "Monument" + ChatColor.DARK_RED + "]");

        String monumentName = event.getLine(1);
        if (monumentName == null || monumentName.trim().isEmpty()) {
            monumentName = "Unnamed Monument";
            event.setLine(1, monumentName);
        }

        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "Monument erected by " + player.getName();
        }

        createMonumentEntry(player, monumentName, description, block);
    }

    private void createMonumentEntry(Player player, String name, String description, Block block) {
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.MONUMENT, block.getLocation(), player);

        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");

        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("monuments.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Monument '" + name + "' has been " +
                    (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created monument via sign: " + name + " by " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create monument. Please try again or contact an admin.");
            logger.warning("Failed to create monument via sign: " + name + " by " + player.getName());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.MONUMENT;
    }
}
