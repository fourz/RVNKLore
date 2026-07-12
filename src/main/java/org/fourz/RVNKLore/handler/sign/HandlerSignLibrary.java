package org.fourz.RVNKLore.handler.sign;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.LecternSignUtil;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Designates a lectern as a lore-cataloguing "library lectern".
 *
 * <p>Placing a sign with {@code [Library]} on the first line, mounted on any face of a
 * lectern, marks that lectern. Written books later placed on it are catalogued as lore by
 * {@link org.fourz.RVNKLore.handler.event.LecternBookLoreHandler} (subject to approval).
 * The physical sign IS the designation — removing it un-designates the lectern. This
 * handler only validates and formats the sign; it does not itself create a lore entry.</p>
 */
public class HandlerSignLibrary extends DefaultLoreHandler {
    private static final String LIBRARY_SIGN_HEADER = "[Library]";
    private final LogManager logger;

    public HandlerSignLibrary(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignLibrary");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing sign library handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(LIBRARY_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.library")) {
            logger.debug(player.getName() + " tried to create a library sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Library]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create library signs.");
            return;
        }

        // The sign must be mounted on a lectern for the designation to mean anything.
        Block attached = LecternSignUtil.getAttachedBlock(block);
        if (attached == null || attached.getType() != Material.LECTERN) {
            event.setLine(0, ChatColor.RED + "[Library]");
            player.sendMessage(ChatColor.RED + "A [Library] sign must be placed on a lectern.");
            return;
        }

        event.setLine(0, ChatColor.DARK_BLUE + "[" + ChatColor.BLUE + "Library" + ChatColor.DARK_BLUE + "]");
        player.sendMessage(ChatColor.GREEN
            + "This lectern now catalogs written books placed on it as lore (subject to approval).");
        logger.debug("Library lectern designated by " + player.getName() + " at " + block.getLocation());
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.GENERIC;
    }
}
