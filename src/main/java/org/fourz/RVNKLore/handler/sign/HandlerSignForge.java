package org.fourz.RVNKLore.handler.sign;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.ForgeSignUtil;
import org.fourz.RVNKLore.util.LecternSignUtil;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Designates an anvil as a lore-forging "forge anvil".
 *
 * <p>Placing a sign with {@code [Forge]} on the first line, mounted on any face of an
 * anvil, marks that anvil. A player right-clicking it with an item in hand publishes the
 * held item to the RVNKLore catalog via
 * {@link org.fourz.RVNKLore.handler.event.ForgeAnvilLoreHandler} (subject to approval).
 * The physical sign IS the designation — removing it un-designates the anvil. This handler
 * only validates and formats the sign; it does not itself create a lore entry.</p>
 */
public class HandlerSignForge extends DefaultLoreHandler {
    private static final String FORGE_SIGN_HEADER = "[Forge]";
    private final LogManager forgeLogger;

    public HandlerSignForge(RVNKLore plugin) {
        super(plugin);
        this.forgeLogger = LogManager.getInstance(plugin, "HandlerSignForge");
    }

    @Override
    public void initialize() {
        forgeLogger.debug("Initializing sign forge handler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase(FORGE_SIGN_HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("rvnklore.sign.forge")) {
            forgeLogger.debug(player.getName() + " tried to create a forge sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Forge]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create forge signs.");
            return;
        }

        // The sign must be mounted on an anvil for the designation to mean anything.
        Block attached = LecternSignUtil.getAttachedBlock(block);
        if (attached == null || !ForgeSignUtil.isAnvil(attached.getType())) {
            event.setLine(0, ChatColor.RED + "[Forge]");
            player.sendMessage(ChatColor.RED + "A [Forge] sign must be placed on an anvil.");
            return;
        }

        event.setLine(0, ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "Forge" + ChatColor.DARK_GREEN + "]");
        player.sendMessage(ChatColor.GREEN
            + "This anvil now forges the item in your hand into the lore catalog (subject to approval).");
        forgeLogger.debug("Forge anvil designated by " + player.getName() + " at " + block.getLocation());
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.GENERIC;
    }
}
