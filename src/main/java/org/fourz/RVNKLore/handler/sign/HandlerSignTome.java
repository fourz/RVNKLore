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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
     * Permission to take a sign-governed lectern apart (#2022).
     *
     * <p>Creation was gated from the start; destruction was not gated at all, so any player with
     * build rights could delete a published quest-giver and its book with one swing. #1881 is the
     * precedent: Chapter 1's start lectern vanished from Event with no record of how it had been
     * built.</p>
     */
    private static final String PERMISSION_DESTROY = "rvnklore.sign.destroy";

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

        // #2021: the name may span lines 2-4 on the sign, because one line cannot hold a name
        // like "RAVENFORGE WAYBILL". Read all three the same way the interact path does.
        String itemName = LecternSignUtil.joinNameLines(event.getLines());
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
            String[] wrapped = LecternSignUtil.wrapNameOntoLines(itemName);
            for (int i = 0; i < wrapped.length; i++) {
                event.setLine(LecternSignUtil.NAME_FIRST_LINE + i, wrapped[i]);
            }
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

        String itemName = LecternSignUtil.readNameFromTaggedSide(sign, LecternSignUtil.TOME_TAG);
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

        String itemName = LecternSignUtil.readNameFromTaggedSide(sign, LecternSignUtil.TOME_TAG);
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

    // ---------------------------------------------------------------------------------------
    // Protection for sign-governed lecterns (#2022)
    //
    // Lives here rather than in its own handler because handlers are keyed by LoreType and this
    // needs no type of its own. It is deliberately TAG-AGNOSTIC: it guards [Library] lecterns as
    // well as [Tome] ones, since both are published content that a pickaxe should not be able to
    // delete. HandlerSignLibrary reads no payload line and needs no changes.
    //
    // Protection is derived from the blocks themselves — no registry, nothing to persist, nothing
    // to fall out of sync across a restart or a chunk reload.
    //
    // KNOWN LIMIT, stated rather than implied: /setblock, /fill and WorldEdit do not fire
    // BlockBreakEvent and will still destroy the pair. This stops players, not operators.
    // ---------------------------------------------------------------------------------------

    /** @return the tag governing this lectern, or null when it is an ordinary lectern. */
    private String governingTag(Block block) {
        if (block == null || block.getType() != Material.LECTERN) return null;
        if (LecternSignUtil.findTomeSign(block) != null) return LecternSignUtil.TOME_TAG;
        if (LecternSignUtil.findLibrarySign(block) != null) return LecternSignUtil.LIBRARY_TAG;
        return null;
    }

    /** @return the tag on this sign if it governs the lectern it is mounted on, else null. */
    private String governingSignTag(Block block) {
        if (block == null || !(block.getState() instanceof Sign)) return null;
        Block attached = LecternSignUtil.getAttachedBlock(block);
        if (attached == null || attached.getType() != Material.LECTERN) return null;
        String tag = governingTag(attached);
        // Only protect the sign that is actually doing the designating.
        return (tag != null && block.equals(LecternSignUtil.findTaggedSign(attached, tag))) ? tag : null;
    }

    /** @return true when breaking this block would take a designated lectern apart. */
    private boolean isProtected(Block block) {
        return governingTag(block) != null || governingSignTag(block) != null;
    }

    /**
     * Refuses to let a player break a designated lectern or its sign.
     *
     * <p>The refusal explains itself. A silent cancel reads as a broken server, and the player has
     * no way to learn that the block is content rather than scenery — the same reasoning as the
     * out-of-order feedback in RVNKQuests.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String tag = governingTag(block);
        if (tag == null) tag = governingSignTag(block);
        if (tag == null) return;

        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION_DESTROY)) {
            logger.info("Sign-governed lectern " + tag + " at " + block.getLocation()
                + " broken by " + player.getName() + " (has " + PERMISSION_DESTROY + ")");
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "That lectern is a lore " + tag + " - it hands out a book.");
        player.sendMessage(ChatColor.GRAY + "   Clear the tag from its sign to retire it. "
            + "You do not have permission to do that.");
        logger.debug("Blocked break of " + tag + " lectern at " + block.getLocation()
            + " by " + player.getName());
    }

    /**
     * Keeps explosions from doing what a pickaxe may not.
     *
     * <p>Without this the protection is theatre: a creeper, a bed, or one TNT block deletes a
     * quest-giver that a player was explicitly refused. Both explosion events are handled because
     * they have separate sources — entities and blocks — and neither implies the other.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    /** Block-sourced explosions (beds, respawn anchors, TNT blocks). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    /**
     * Stops a piston shoving a designated lectern or its sign out of position.
     *
     * <p>Moving either block silently un-designates the pair — the sign is the designation, and it
     * only designates the lectern it is mounted on.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) event.setCancelled(true);
    }

    /** Sticky pistons pulling the pair apart. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) event.setCancelled(true);
    }

    /**
     * Gates un-designation, which is the sanctioned way to remove one of these (#2022).
     *
     * <p>Runs at LOW so it settles before the designating handler at default priority sees the
     * event. Retiring a lectern is a deliberate act performed through the sign; breaking it is
     * not. An unauthorised player editing the tag away is refused and the sign is left alone.</p>
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignUndesignate(SignChangeEvent event) {
        Block signBlock = event.getBlock();
        String tag = governingSignTag(signBlock);
        if (tag == null) return;

        // Still carrying its tag after the edit? Then this is a rename, not a retirement.
        String newLine0 = event.getLine(0) == null ? "" : ChatColor.stripColor(event.getLine(0)).trim();
        if (newLine0.equalsIgnoreCase(tag)) return;

        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION_DESTROY)) {
            logger.info("Lectern " + tag + " at " + signBlock.getLocation() + " retired by "
                + player.getName());
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You do not have permission to retire a lore " + tag + ".");
        logger.debug("Blocked un-designation of " + tag + " at " + signBlock.getLocation()
            + " by " + player.getName());
    }

    /**
     * Refuses to wax a designating sign.
     *
     * <p>A waxed sign cannot be edited, and editing the sign is the only sanctioned removal path.
     * Allowing the wax would make the pair permanently unbreakable by anyone, including an
     * operator holding {@code rvnklore.sign.destroy} — a protection with no way out is a bug, not
     * a stronger protection.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaxAttempt(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null || event.getItem().getType() != Material.HONEYCOMB) return;
        if (governingSignTag(event.getClickedBlock()) == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED
            + "Waxing this sign would seal the lectern permanently - refused.");
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.GENERIC;
    }
}
