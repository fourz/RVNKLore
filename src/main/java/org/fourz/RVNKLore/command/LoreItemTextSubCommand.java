package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /lore item text <text…>} — enrich a freshly-forged lore item with an RVNKLore
 * descriptor line (extra flavor a vanilla anvil cannot produce), XP-gated and time-limited.
 *
 * <p>Guards: player-only; item in main hand; the held item is a published lore item
 * (resolved via the {@code rvnklore:lore_item_id} PDC); recently forged — {@code forged_at}
 * PDC within a 1-hour window (anchored to the original forge; text edits do NOT extend it);
 * and enough XP levels ({@code forge.text-xp-cost-levels}). On success the descriptor is
 * appended to {@code item_properties.lore_text} as a new content version (#1528), the held
 * item's lore is re-rendered, and the XP is deducted.</p>
 */
public class LoreItemTextSubCommand implements SubCommand {

    private static final String PERMISSION = "rvnklore.forge";
    private static final long EDIT_WINDOW_MS = 3_600_000L; // 1 hour

    private final RVNKLore plugin;
    private final ItemManager itemManager;

    public LoreItemTextSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command requires a player (item in hand needed).");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "▶ Usage: /lore item text <text...>");
            return true;
        }
        final String text = String.join(" ", args).trim();
        if (text.isEmpty()) {
            player.sendMessage(ChatColor.RED + "▶ Usage: /lore item text <text...>");
            return true;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "✖ Hold the forged item you want to enrich in your main hand.");
            return true;
        }

        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "✖ This item cannot carry a descriptor (no item meta).");
            return true;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer itemId = pdc.get(new NamespacedKey(plugin, "lore_item_id"), PersistentDataType.INTEGER);
        if (itemId == null || itemId <= 0) {
            player.sendMessage(ChatColor.RED + "✖ That item is not a forged lore item.");
            return true;
        }

        Long forgedAt = pdc.get(new NamespacedKey(plugin, "forged_at"), PersistentDataType.LONG);
        if (forgedAt == null) {
            player.sendMessage(ChatColor.RED + "✖ That item is not a freshly forged item.");
            return true;
        }
        if (System.currentTimeMillis() - forgedAt >= EDIT_WINDOW_MS) {
            player.sendMessage(ChatColor.RED + "✖ This item is no longer editable (the forge window has closed).");
            return true;
        }

        final int cost = plugin.getConfig().getInt("forge.text-xp-cost-levels", 3);
        if (player.getLevel() < cost) {
            player.sendMessage(ChatColor.RED + "✖ You need " + cost + " XP levels to add a descriptor (you have "
                + player.getLevel() + ").");
            return true;
        }

        final int resolvedId = itemId;
        itemManager.getItemPropertiesById(resolvedId).thenAccept(opt ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt == null || !opt.isPresent()) {
                    player.sendMessage(ChatColor.RED + "✖ That item was not found in the lore catalog.");
                    return;
                }
                // Re-check XP on the main thread (player state may have changed while async ran).
                if (player.getLevel() < cost) {
                    player.sendMessage(ChatColor.RED + "✖ You no longer have enough XP levels ("
                        + cost + " required).");
                    return;
                }

                ItemProperties props = opt.get();
                List<String> lore = props.getLore() != null ? new ArrayList<>(props.getLore()) : new ArrayList<>();
                lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + text);
                props.setLore(lore);

                final List<String> renderedLore = lore;
                itemManager.updateItemVersioned(resolvedId, props).thenAccept(version ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (version == null || version <= 0) {
                            player.sendMessage(ChatColor.RED + "✖ Failed to add the descriptor. No XP was charged.");
                            return;
                        }
                        // Deduct XP only after the versioned update succeeded.
                        player.giveExpLevels(-cost);

                        // Re-render the in-hand item's lore. getItemMeta()/setItemMeta() preserves the
                        // existing PDC — including forged_at, which is intentionally NOT reset here so
                        // text edits do not extend the enrichment window.
                        ItemMeta m = held.getItemMeta();
                        if (m != null) {
                            m.setLore(renderedLore);
                            held.setItemMeta(m);
                        }
                        player.sendMessage(ChatColor.GREEN + "✓ Descriptor added - cost " + cost + " levels.");
                    }));
            }));
        return true;
    }

    @Override
    public String getDescription() {
        return "Add a lore descriptor line to a freshly-forged item (XP-gated)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION) || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
