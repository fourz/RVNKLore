package org.fourz.RVNKLore.handler.sign;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.LecternSignUtil;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Designates a lectern as a book-dispensing "quest-giver" lectern (#1888).
 *
 * <p>A sign reading {@code [Tome]} on line 1 and a lore item name on line 2, mounted on any face of
 * a lectern, marks that lectern. Right-clicking it hands the player their own copy of that book,
 * once.</p>
 *
 * <h3>The inverse of {@code [Library]}</h3>
 * {@link HandlerSignLibrary} is an <b>ingestion</b> point — books placed on it are catalogued as
 * lore. This is the other direction. They share {@link LecternSignUtil}'s scanning, which is
 * tag-agnostic.
 *
 * <h3>Why a sign rather than block NBT</h3>
 * Quest-giver lecterns were previously hand-built by writing block NBT. One of them
 * (Chapter 1's start lectern, {@code alphac -315,118,446}) vanished from Event with no record of how
 * it had been made, leaving the chain unstartable — #1881. A sign is placeable, visible in-world,
 * says which book it gives, and is rebuilt with two blocks. The physical sign IS the designation;
 * remove it and the lectern is ordinary again.
 *
 * <h3>One per player, and the limit of that</h3>
 * The guard is a plain inventory check: already holding a copy means no second one. That stops
 * spam-clicking, double-gives and accidents — the failure modes that actually occur. A player who
 * chests or drops the book <b>can</b> take another. This is deliberately <i>take-one</i>, not
 * <i>claim-once</i>; the book is a reference copy, not a reward. A hard claim-once would belong in
 * {@code lore_discovery}, which already carries {@code UNIQUE(player_uuid, entry_id)}.
 *
 * @since 1.0.89
 */
public class HandlerSignTome extends DefaultLoreHandler {

    private static final String PERMISSION_CREATE = "rvnklore.sign.tome";

    private final LogManager logger;

    public HandlerSignTome(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignTome");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing tome (quest-giver) lectern handler");
    }

    /**
     * Validates and formats a {@code [Tome]} sign as it is written.
     *
     * <p>Refuses the designation unless the sign is on a lectern <b>and</b> line 2 resolves to a
     * real lore item. Creating a sign that points at nothing would produce a lectern that looks like
     * a quest-giver and silently does nothing — the failure is much cheaper here, at placement,
     * where the builder is standing right there.</p>
     *
     * @param event the sign change event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String header = event.getLine(0);
        if (header == null || !header.trim().equalsIgnoreCase(LecternSignUtil.TOME_TAG)) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission(PERMISSION_CREATE)) {
            event.setLine(0, ChatColor.RED + LecternSignUtil.TOME_TAG);
            player.sendMessage(ChatColor.RED + "You don't have permission to create tome lecterns.");
            return;
        }

        Block attached = LecternSignUtil.getAttachedBlock(event.getBlock());
        if (attached == null || attached.getType() != Material.LECTERN) {
            event.setLine(0, ChatColor.RED + LecternSignUtil.TOME_TAG);
            player.sendMessage(ChatColor.RED + "A " + LecternSignUtil.TOME_TAG
                + " sign must be placed on a lectern.");
            return;
        }

        String itemName = event.getLine(1) == null ? "" : event.getLine(1).trim();
        if (itemName.isEmpty()) {
            event.setLine(0, ChatColor.RED + LecternSignUtil.TOME_TAG);
            player.sendMessage(ChatColor.RED + "Line 2 must be the lore item name to dispense.");
            return;
        }

        // Resolve by NAME, never by id: a freshly minted item is unreachable by id (#1887), and a
        // name on the sign is readable by whoever walks past it.
        if (!loreItemExists(itemName)) {
            event.setLine(0, ChatColor.RED + LecternSignUtil.TOME_TAG);
            player.sendMessage(ChatColor.RED + "No lore item named '" + itemName + "'.");
            player.sendMessage(ChatColor.GRAY + "   Mint it first, then place this sign.");
            return;
        }

        event.setLine(0, ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + "Tome" + ChatColor.DARK_AQUA + "]");
        player.sendMessage(ChatColor.GREEN + "This lectern now hands out '" + itemName + "'.");
        logger.debug("Tome lectern designated by " + player.getName() + " at "
            + event.getBlock().getLocation() + " -> " + itemName);
    }

    /**
     * Dispenses the named book on right-click.
     *
     * <p>Cancels the interaction unconditionally on a designated lectern. That is deliberate: a
     * right-click on a lectern <i>while holding a book</i> vanilla-places that book onto it, which
     * would collide head-on with take-a-copy — and on a lectern that also carried a
     * {@code [Library]} sign the player would be <i>submitting</i> a book while trying to take
     * one.</p>
     *
     * @param event the interact event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Off-hand fires a second event for the same click; ignore it or the player gets two.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) return;

        Block signBlock = LecternSignUtil.findTomeSign(block);
        if (signBlock == null) return;
        if (!(signBlock.getState() instanceof Sign sign)) return;

        String itemName = LecternSignUtil.readLineFromTaggedSide(sign, LecternSignUtil.TOME_TAG, 1);

        // Past this point the lectern is ours — never let vanilla place a held book onto it.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (itemName == null || itemName.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This tome lectern has no book set on line 2.");
            return;
        }

        if (hasCopy(player, itemName)) {
            player.sendMessage(ChatColor.YELLOW + "You already carry '" + itemName + "'.");
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }

        giveLoreItem(itemName, player);
        logger.debug("Tome lectern dispensed '" + itemName + "' to " + player.getName());
    }

    /**
     * Whether the player already carries this book.
     *
     * <p>Matches on the item's display name with colour stripped, so a renamed-by-colour copy still
     * counts and an unrelated written book does not block the dispense.</p>
     */
    private boolean hasCopy(Player player, String itemName) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) continue;
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name != null && name.trim().equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a lore item by name through ItemManager.
     *
     * @return true when an item with this name exists in the catalog
     */
    private boolean loreItemExists(String itemName) {
        try {
            org.fourz.RVNKLore.lore.item.ItemManager items = items();
            return items != null && items.createLoreItemSync(itemName) != null;
        } catch (Exception e) {
            logger.debug("Lore item lookup failed for '" + itemName + "': " + e.getMessage());
            return false;
        }
    }

    /** Hands the named lore item to the player, by name (see #1887). */
    private void giveLoreItem(String itemName, Player player) {
        try {
            items().giveItemToPlayer(itemName, player)
                .thenAccept(ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        logger.warning("Tome lectern could not give '" + itemName
                            + "' to " + player.getName());
                    }
                });
        } catch (Exception e) {
            logger.error("Tome lectern give failed for '" + itemName + "'", e);
            player.sendMessage(ChatColor.RED + "Could not hand you that book — see console.");
        }
    }

    /**
     * Resolves ItemManager through LoreManager.
     *
     * <p>Looked up per call rather than cached at construction: sign handlers are created during
     * HandlerFactory init, which can run before LoreManager has finished wiring its managers.</p>
     */
    private org.fourz.RVNKLore.lore.item.ItemManager items() {
        return plugin.getLoreManager() == null ? null : plugin.getLoreManager().getItemManager();
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.GENERIC;
    }
}
