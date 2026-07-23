package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemPropertiesExtractor;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.fourz.RVNKLore.util.ForgeNaming;
import org.fourz.RVNKLore.util.ForgeSignUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes the item in a player's hand to the RVNKLore catalog when they right-click a
 * {@code [Forge]}-designated anvil (see {@link org.fourz.RVNKLore.handler.sign.HandlerSignForge}).
 *
 * <p>The vanilla anvil GUI is suppressed ({@code event.setCancelled(true)}) once the anvil
 * is confirmed to be a forge anvil and the player is eligible. Re-forging a same-base-name
 * item you already authored content-versions the existing catalog entry (#1528); a new base
 * name mints a fresh author-namespaced lineage. The held item is stamped with the lore id
 * PDC (+{@code forged_at}) so it resolves back and can be enriched by {@code /lore item text}.</p>
 */
public class ForgeAnvilLoreHandler extends DefaultLoreHandler {

    private final Map<UUID, Long> forgeCooldowns = new ConcurrentHashMap<>();

    public ForgeAnvilLoreHandler(RVNKLore plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing forge anvil lore handler");
    }

    // HIGH (not MONITOR) so setCancelled(true) actually suppresses the vanilla anvil GUI.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !ForgeSignUtil.isAnvil(block.getType())) return;
        if (!ForgeSignUtil.isForgeAnvil(block)) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        // Empty hand → let the anvil behave normally (open the vanilla GUI).
        if (held == null || held.getType() == Material.AIR) return;

        // No forge permission → don't hijack the anvil; leave it as a normal anvil.
        if (!player.hasPermission("rvnklore.forge")) return;

        // Committed to forging from here — suppress the vanilla anvil GUI.
        event.setCancelled(true);

        // Per-player cooldown.
        UUID uuid = player.getUniqueId();
        long cooldownMs = plugin.getConfig().getInt("forge.cooldown-seconds", 60) * 1000L;
        long now = System.currentTimeMillis();
        Long last = forgeCooldowns.get(uuid);
        if (last != null && now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000;
            player.sendMessage(ChatColor.YELLOW + "⚠ Please wait " + remaining + "s before forging again.");
            return;
        }
        forgeCooldowns.put(uuid, now);

        forge(player, held);
    }

    private void forge(Player player, ItemStack held) {
        ItemProperties props = ItemPropertiesExtractor.from(held);

        String display = props.getDisplayName();
        if (display == null || display.isEmpty()) {
            display = prettifyMaterial(held.getType());
            props.setDisplayName(display);
        }
        final String cleanDisplay = display;
        final String base = ForgeNaming.baseName(cleanDisplay);
        final String uuidStr = player.getUniqueId().toString();
        props.setCreatedBy(uuidStr);

        // Find the caller's existing lineage with the same base name → content-version it.
        plugin.getLoreManager().getItemManager().getItemsByCreatedBy(uuidStr).thenAccept(existing -> {
            ItemProperties match = null;
            for (ItemProperties ip : existing) {
                if (base.equalsIgnoreCase(ForgeNaming.baseName(ip.getDisplayName()))) {
                    match = ip;
                    break;
                }
            }
            if (match != null) {
                reforgeExisting(player, held, props, match, cleanDisplay);
            } else {
                mintNewLineage(player, held, props, base, uuidStr, cleanDisplay);
            }
        });
    }

    /** Re-forge: bump content_version on the caller's existing lineage (old version archived). */
    private void reforgeExisting(Player player, ItemStack held, ItemProperties props,
                                 ItemProperties existing, String cleanDisplay) {
        final int itemId = existing.getDatabaseId();
        final String entryId = existing.getLoreEntryId();
        props.setLoreEntryId(entryId);
        props.setDisplayName(cleanDisplay); // lore_item.name = clean forged display name

        plugin.getLoreManager().getItemManager().updateItemVersioned(itemId, props).thenAccept(version ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (version != null && version > 0) {
                    stampAndConfirm(player, held, itemId, entryId, cleanDisplay,
                        "published as version " + version + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "✖ Failed to re-forge " + cleanDisplay + ".");
                }
            }));
    }

    /** Mint a brand-new author-namespaced lineage (mirrors LoreApiEndpointImpl.createItem). */
    private void mintNewLineage(Player player, ItemStack held, ItemProperties props,
                                String base, String uuidStr, String cleanDisplay) {
        final String entryId = UUID.randomUUID().toString();
        final String author8 = uuidStr.substring(0, Math.min(8, uuidStr.length()));
        // lore_entry.name is author-namespaced (unique per author); lore_item.name stays clean.
        final String entryKey = base + "@" + author8;
        final Material material = props.getMaterial();
        final ItemType itemType = props.getItemType();
        final String rarity = props.getRarity();

        LoreEntry entry = new LoreEntry(entryId, entryKey, cleanDisplay, LoreType.ITEM);
        entry.setSubmittedBy(uuidStr);
        // ITEM inserts require material/type/rarity metadata (see LecternBookLoreHandler #1417).
        entry.addMetadata("material", material.name());
        entry.addMetadata("item_type", itemType != null ? itemType.name() : ItemType.STANDARD.name());
        if (rarity != null && !rarity.isBlank()) entry.addMetadata("rarity", rarity);
        final boolean approved = player.hasPermission("rvnklore.approve.own");
        entry.setApproved(approved);

        props.setLoreEntryId(entryId);
        props.setDisplayName(cleanDisplay);

        plugin.getLoreManager().addLoreEntry(entry).thenAccept(saved -> {
            if (!Boolean.TRUE.equals(saved)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "✖ Could not forge " + cleanDisplay
                        + " (it may already exist)."));
                return;
            }
            plugin.getLoreManager().getItemManager()
                .registerLoreItemForId(UUID.fromString(entryId), props).thenAccept(itemId -> {
                    if (itemId == null || itemId <= 0) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(ChatColor.RED + "✖ Failed to persist forged item " + cleanDisplay + "."));
                        return;
                    }
                    // Snapshot the created props into v1 so version history is consistent (#1528).
                    plugin.getLoreManager().getItemManager().snapshotItemVersion(itemId).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () -> stampAndConfirm(player, held, itemId, entryId,
                            cleanDisplay, approved ? "published." : "submitted for approval.")));
                });
        });
    }

    /** Stamp the held item with the lore id PDC (+forged_at) and message the player. */
    private void stampAndConfirm(Player player, ItemStack held, int itemId, String entryId,
                                 String display, String tail) {
        plugin.getLoreManager().getItemManager().stampLoreItemPdc(held, itemId, entryId, display);
        player.sendMessage(ChatColor.GREEN + "✓ Forged into the record — "
            + ChatColor.GOLD + display + ChatColor.GREEN + " " + tail);
    }

    /** Title-case a material name for items with no custom display name (e.g. STONE_SWORD → Stone Sword). */
    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ITEM;
    }
}
