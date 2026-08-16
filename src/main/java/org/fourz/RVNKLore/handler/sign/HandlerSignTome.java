package org.fourz.RVNKLore.handler.sign;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.LecternSignUtil;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Designates a lectern as a self-restocking "quest-giver" lectern (#1888).
 *
 * <p>A {@code [Tome]} sign mounted on a lectern turns it into a dispenser for one lore book. The
 * lectern is <b>always visibly stocked</b>: a player opens it, takes the book, and the lectern
 * refills a moment later. The refill is on a short delay on purpose so the player <i>sees</i> it —
 * that is what reads as "this is a dispenser" rather than "you just took the only copy".</p>
 *
 * <h3>Two ways to build one</h3>
 * <ol>
 *   <li><b>Sign first.</b> Write {@code [Tome]} with the book name on line 2. The lectern is stocked
 *       automatically as part of the designation.</li>
 *   <li><b>Book first.</b> Put a minted lore book on the lectern, then write {@code [Tome]} with
 *       <b>no name</b> — the name is read off the book already there and written onto the sign, so
 *       the lectern still declares what it gives.</li>
 * </ol>
 *
 * <h3>The inverse of {@code [Library]}</h3>
 * {@link HandlerSignLibrary} is an <b>ingestion</b> point — books placed on it are catalogued as
 * lore. This hands books out. They share {@link LecternSignUtil}'s tag-agnostic scanning.
 *
 * <h3>Why a sign rather than block NBT</h3>
 * Quest-giver lecterns used to be hand-built by writing block NBT. Chapter 1's start lectern
 * (#1881) vanished from Event with no record of how it was made — and its book had never been
 * minted, so there was nothing to rebuild it from. A sign is placeable, visible, names its own
 * book, and refuses a book that does not exist in the catalog.
 *
 * @since 1.0.89
 */
public class HandlerSignTome extends DefaultLoreHandler {

    private static final String PERMISSION_CREATE = "rvnklore.sign.tome";

    /**
     * Line-0 text written when a {@code [Tome]} sign is refused.
     *
     * <p>Must NOT strip down to {@link LecternSignUtil#TOME_TAG}. Marking a rejection with
     * {@code RED + "[Tome]"} looks refused but still reads as the tag once colour is stripped, so
     * the lectern gets designated anyway. Observed on Event: a sign refused for naming a
     * nonexistent book still dispensed, failing at the give instead of never being a tome lectern.
     * {@code HandlerSignLibrary} carried the identical flaw.</p>
     */
    private static final String REJECTED_TAG = ChatColor.RED + "[!Tome]";

    /**
     * Ticks between a player taking the book and the lectern refilling.
     *
     * <p>Not zero, on purpose. An instant refill is indistinguishable from the take having failed;
     * a visible beat reads as the lectern restocking itself. Also necessary mechanically — the
     * take event fires <i>before</i> the book leaves the lectern, so an immediate write would be
     * overwritten a tick later.</p>
     */
    private static final long RESTOCK_DELAY_TICKS = 15L;

    /**
     * How many times the post-take refill will wait and look again.
     *
     * <p>The take event fires before the transfer completes, so the first look can still see the
     * outgoing book. Treating "slot occupied" as "already stocked" and giving up makes a failed
     * refill indistinguishable from a successful one — which is exactly how the original defect
     * hid. Retrying instead turns that ambiguity into either a refill or a log line.</p>
     */
    private static final int MAX_RESTOCK_ATTEMPTS = 3;

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
     * Validates a {@code [Tome]} sign, resolves its book, and stocks the lectern.
     *
     * <p>Line 2 is optional when the lectern already holds a book — that is the book-first build.</p>
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
            event.setLine(0, REJECTED_TAG);
            player.sendMessage(ChatColor.RED + "You don't have permission to create tome lecterns.");
            return;
        }

        Block lectern = LecternSignUtil.getAttachedBlock(event.getBlock());
        if (lectern == null || lectern.getType() != Material.LECTERN) {
            event.setLine(0, REJECTED_TAG);
            player.sendMessage(ChatColor.RED + "A " + LecternSignUtil.TOME_TAG
                + " sign must be placed on a lectern.");
            return;
        }

        String itemName = event.getLine(1) == null ? "" : event.getLine(1).trim();
        ItemStack onLectern = bookOn(lectern);

        // Book-first build: no name given, so read it off the book already on the lectern.
        if (itemName.isEmpty()) {
            itemName = displayName(onLectern);
            if (itemName == null) {
                event.setLine(0, REJECTED_TAG);
                player.sendMessage(ChatColor.RED
                    + "Put a lore book on the lectern first, or name one on line 2.");
                return;
            }
            event.setLine(1, itemName);
        }

        // Resolve by NAME, never by id — a freshly minted item is unreachable by id (#1887), and a
        // name on the sign is readable by whoever walks past it.
        if (!loreItemExists(itemName)) {
            event.setLine(0, REJECTED_TAG);
            player.sendMessage(ChatColor.RED + "No lore item named '" + itemName + "'.");
            player.sendMessage(ChatColor.GRAY + "   Mint it first, then place this sign.");
            return;
        }

        event.setLine(0, ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + "Tome"
            + ChatColor.DARK_AQUA + "]");
        player.sendMessage(ChatColor.GREEN + "This lectern now hands out '" + itemName + "'.");

        // Sign-first build: stock it now, so a tome lectern is never an empty-looking quest-giver.
        // Next tick, because the sign's own lines are not committed until this event resolves.
        if (onLectern == null) {
            final String book = itemName;
            Bukkit.getScheduler().runTask(plugin, () -> stock(lectern, book));
        }
        logger.debug("Tome lectern designated by " + player.getName() + " at "
            + lectern.getLocation() + " -> " + itemName);
    }

    /**
     * Refills an <b>empty</b> tome lectern when a player interacts with it.
     *
     * <p>This — not the post-take refill — is what keeps a tome lectern working. A lectern can be
     * emptied by a great many things other than a player taking the book: a server restart, a chunk
     * reload, a hopper, a block break, an admin {@code fill}. A scheduled refill hung off the take
     * event heals exactly one of those. Refilling on interact heals all of them, needs no
     * bookkeeping, and costs nothing until somebody actually clicks.</p>
     *
     * <p><b>Nothing is ever handed straight to the player.</b> The lectern is always the
     * intermediary: click one refills it, and the player then takes the book through the lectern's
     * own control exactly as they would from a lectern that was already stocked. Auto-giving on the
     * second click was considered and rejected — it would make a just-refilled lectern behave
     * differently from an already-stocked one for no gain.</p>
     *
     * <p>The event is cancelled so the empty-lectern GUI does not open on the same click. That is
     * what makes it read as "the lectern refilled itself" rather than "an empty lectern opened".
     * A side effect worth knowing: an admin can no longer hand-place a <i>different</i> book on a
     * designated tome lectern, because the click refills it with the signed book instead. That is
     * the intended reading — the sign declares what the lectern hands out.</p>
     *
     * @param event the interact event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Off-hand fires a second event for the same click; without this the refill runs twice.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) return;

        // A stocked lectern must behave exactly like vanilla — open, read, take.
        if (bookOn(block) != null) return;

        Block signBlock = LecternSignUtil.findTomeSign(block);
        if (signBlock == null) return;
        if (!(signBlock.getState() instanceof Sign sign)) return;

        String itemName = LecternSignUtil.readLineFromTaggedSide(sign, LecternSignUtil.TOME_TAG, 1);
        if (itemName == null || itemName.isEmpty()) return;

        event.setCancelled(true);
        if (stock(block, itemName)) {
            logger.debug("Tome lectern refilled on interact by " + event.getPlayer().getName()
                + " at " + block.getLocation() + " -> " + itemName);
        }
    }

    /**
     * Lets a player take the book, then refills the lectern.
     *
     * <p>The take itself stays vanilla — the player uses the lectern's own Take Book control, so
     * the transfer and its animation are the ones they already know. This adds the refill and the
     * one-per-player guard, nothing else.</p>
     *
     * <p>Since {@link #onInteract} landed this refill is no longer load-bearing — an empty lectern
     * heals on the next click regardless. What it still provides is the <i>visible beat</i>: the
     * lectern refilling a moment after the take, so the restock reads as deliberate rather than as
     * "you took the last copy".</p>
     *
     * @param event the take-book event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeBook(PlayerTakeLecternBookEvent event) {
        Lectern lectern = event.getLectern();
        if (lectern == null) return;

        Block block = lectern.getBlock();
        Block signBlock = LecternSignUtil.findTomeSign(block);
        // Logged either way: this fires for every lectern on the server, so a null sign is the
        // normal case and cannot be a warning. It is also the leading suspect for the refill not
        // firing — findTomeSign walks lectern -> sign, a direction designation never exercises.
        logger.debug("Lectern take at " + block.getLocation() + "; tome sign "
            + (signBlock == null ? "not found" : "found"));
        if (signBlock == null) return;
        if (!(signBlock.getState() instanceof Sign sign)) return;

        String itemName = LecternSignUtil.readLineFromTaggedSide(sign, LecternSignUtil.TOME_TAG, 1);
        if (itemName == null || itemName.isEmpty()) return;

        Player player = event.getPlayer();

        // One per player. An inventory check, so this is take-one rather than claim-once: a player
        // who chests the book can take another. Deliberate — the book is a reference copy, not a
        // reward. A hard claim-once belongs in lore_discovery, which already carries
        // UNIQUE(player_uuid, entry_id).
        if (hasCopy(player, itemName)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "You already carry '" + itemName + "'.");
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }

        final String book = itemName;
        scheduleRefill(block, book, 1);
        logger.debug("Tome lectern gave '" + itemName + "' to " + player.getName() + "; restocking");
    }

    /**
     * Waits a beat, then refills the lectern — retrying while the outgoing book is still in the slot.
     *
     * @param lectern  the lectern block
     * @param itemName the lore item name
     * @param attempt  1-based attempt counter
     */
    private void scheduleRefill(Block lectern, String itemName, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (lectern.getType() != Material.LECTERN) return;    // broken since we scheduled

            if (bookOn(lectern) != null) {
                // Still occupied. Either the transfer has not completed yet, or somebody restocked
                // it in the meantime — indistinguishable from here, so look again rather than
                // assume the happy one.
                if (attempt < MAX_RESTOCK_ATTEMPTS) {
                    scheduleRefill(lectern, itemName, attempt + 1);
                } else {
                    logger.debug("Tome lectern at " + lectern.getLocation() + " still holds a book"
                        + " after " + attempt + " attempts; leaving it alone");
                }
                return;
            }
            stock(lectern, itemName);
        }, RESTOCK_DELAY_TICKS);
    }

    /**
     * Puts a fresh copy of the named book on the lectern.
     *
     * @param lectern  the lectern block
     * @param itemName the lore item name
     * @return true when the book was placed
     */
    private boolean stock(Block lectern, String itemName) {
        ItemStack book = createBook(itemName);
        if (book == null) {
            logger.warning("Tome lectern could not stock '" + itemName + "' at "
                + lectern.getLocation() + " - item did not resolve");
            return false;
        }

        // Putting a stack in slot 0 is only half of stocking a lectern. The block carries a
        // has_book property and while it is false the block holds nothing — the book is not
        // rendered, not serialised into the block's NBT, and not returned by a later read. Every
        // symptom of #1902 came from this: stock() reported success, the block's NBT stayed
        // "components: {}", the next read saw an empty lectern, and the refill fired again. It
        // looked like the designation had been lost across restarts when no book was ever written.
        //
        // The property has a getter but no setter — vanilla only ever sets it via placeBook — so it
        // is flipped by rebuilding the block data from its own string form, which preserves facing
        // and powered without having to enumerate them.
        BlockData data = lectern.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Lectern ld && !ld.hasBook()) {
            lectern.setBlockData(
                Bukkit.createBlockData(data.getAsString().replace("has_book=false", "has_book=true")),
                false);   // no physics: nothing neighbouring needs to react
        }

        // Re-read the state: the block data write above invalidates any handle taken before it.
        if (!(lectern.getState() instanceof Lectern state)) return false;

        // Two writes, because CraftBukkit's behaviour here depends on whether getInventory() hands
        // back the live tile-entity inventory or a snapshot, and getting that wrong is what made
        // the previous two attempts look correct while changing nothing.
        //
        // For a PLACED lectern getInventory() is live, so this write lands immediately and calling
        // update() afterwards would apply the snapshot captured before it and undo the write. If it
        // is instead a snapshot, this write goes nowhere until update() is called. Rather than bet
        // on one, write, check, and only then reach for the other path.
        state.getInventory().setItem(0, book);

        if (bookOn(lectern) == null) {
            if (lectern.getState() instanceof Lectern snapshot) {
                snapshot.getInventory().setItem(0, book);
                snapshot.update(true, false);
            }
        }

        // Read it back through the same accessor the rest of this class uses. The old version
        // returned true on the strength of having called the setters, which is precisely how a
        // no-op reported success for two builds and sent the diagnosis after the wrong component.
        if (bookOn(lectern) == null) {
            logger.warning("Tome lectern at " + lectern.getLocation() + " did not accept '"
                + itemName + "' - slot still empty after write");
            return false;
        }
        return true;
    }

    /** @return the book currently on the lectern, or null */
    private ItemStack bookOn(Block lectern) {
        if (!(lectern.getState() instanceof Lectern state)) return null;
        return state.getInventory().getItem(0);
    }

    /** @return the stack's display name with colour stripped, or null */
    private String displayName(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String name = ChatColor.stripColor(meta.getDisplayName());
        return (name == null || name.isBlank()) ? null : name.trim();
    }

    /** @return true when the player already carries this book */
    private boolean hasCopy(Player player, String itemName) {
        for (ItemStack stack : player.getInventory().getContents()) {
            String name = displayName(stack);
            if (name != null && name.equalsIgnoreCase(itemName)) return true;
        }
        return false;
    }

    /** @return a fresh copy of the named lore item, or null when it does not resolve */
    private ItemStack createBook(String itemName) {
        try {
            org.fourz.RVNKLore.lore.item.ItemManager items = items();
            return items == null ? null : items.createLoreItemSync(itemName);
        } catch (Exception e) {
            logger.debug("Lore item build failed for '" + itemName + "': " + e.getMessage());
            return null;
        }
    }

    /** @return true when a lore item with this name exists in the catalog */
    private boolean loreItemExists(String itemName) {
        return createBook(itemName) != null;
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
