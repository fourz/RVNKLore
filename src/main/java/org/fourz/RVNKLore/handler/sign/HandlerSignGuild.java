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
 * Handler for creating guilds via sign creation.
 * When a player places a sign with "[Guild]" on the first line,
 * a new guild lore entry is created.
 */
public class HandlerSignGuild extends DefaultLoreHandler {
    private static final String GUILD_SIGN_HEADER = "[Guild]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;

    public HandlerSignGuild(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignGuild");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign guild handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(GUILD_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.guild")) {
            logger.debug(player.getName() + " tried to create a guild sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Guild]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create guild signs.");
            return;
        }

        // Format the sign display
        event.setLine(0, ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "Guild" + ChatColor.DARK_GREEN + "]");

        String guildName = event.getLine(1);
        if (guildName == null || guildName.trim().isEmpty()) {
            guildName = "Unnamed Guild";
            event.setLine(1, guildName);
        }

        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "Guild hall founded by " + player.getName();
        }

        createGuildEntry(player, guildName, description, block);
    }

    private void createGuildEntry(Player player, String name, String description, Block block) {
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.GUILD, block.getLocation(), player);

        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");

        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("guilds.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        boolean success = plugin.getLoreManager().addLoreEntrySync(entry);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Guild '" + name + "' has been " +
                    (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created guild via sign: " + name + " by " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create guild. Please try again or contact an admin.");
            logger.warning("Failed to create guild via sign: " + name + " by " + player.getName());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.GUILD;
    }
}
