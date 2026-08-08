package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.util.HeadUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /lore item texture <id|name> <base64|clear>} — set or clear a lore item's head texture
 * ({@code item_properties.skull_texture}).
 *
 * <p>Closes the authoring gap found in #1914. The consumer side of {@code skull_texture} was wired
 * up in {@code ItemManager.createLoreItemInternal}, and the value has always persisted and
 * round-tripped through REST — but nothing could actually <em>set</em> it. The REST mint and PUT
 * parse name, material, enchantments, rarity, lore, pages, glow and customModelData and nothing
 * else, and no console command touched it, so the only way to populate the field was a direct
 * database write. That is not an authoring path.</p>
 *
 * <p>Deliberately <b>console-safe</b> (no held item, no player) so head items can be seeded by
 * automation and on Event/prod where {@code db_query} writes are not the intended tool. Writes
 * through {@link ItemManager#updateItemVersioned} so the change lands as a new content version
 * (#1528) rather than silently mutating the current row.</p>
 *
 * <p>The value is the same base64 blob a head site hands out — it decodes to
 * {@code {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/…"}}}}. It is validated
 * before the write, so a malformed blob is rejected here instead of surfacing later as a blank
 * head that looks like a texture-load failure.</p>
 */
public class LoreItemTextureSubCommand implements SubCommand {

    private static final String PERMISSION = "rvnklore.admin.item.texture";

    private final RVNKLore plugin;
    private final ItemManager itemManager;

    public LoreItemTextureSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION) || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item texture <id|name> <base64|clear>");
            sender.sendMessage(ChatColor.GRAY + "   base64 decodes to {\"textures\":{\"SKIN\":{\"url\":\"…\"}}}");
            sender.sendMessage(ChatColor.GRAY + "   clear  removes the stored texture");
            return true;
        }

        int itemId = itemManager.resolveDatabaseId(args[0]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[0]);
            return true;
        }

        final String raw = args[1];
        final boolean clearing = "clear".equalsIgnoreCase(raw) || "none".equalsIgnoreCase(raw);

        if (!clearing && !HeadUtil.isValidTextureData(raw)) {
            sender.sendMessage(ChatColor.RED + "✖ That does not look like head texture data.");
            sender.sendMessage(ChatColor.GRAY + "   Expected a long base64 blob with no spaces.");
            return true;
        }
        // Reject a blob that parses as base64 but carries no skin URL. Without this the write
        // succeeds and the head still renders blank — the silent failure #1914 was about.
        if (!clearing && !HeadUtil.hasExtractableTextureUrl(raw)) {
            sender.sendMessage(ChatColor.RED + "✖ No skin URL could be decoded from that texture data.");
            sender.sendMessage(ChatColor.GRAY + "   It must decode to {\"textures\":{\"SKIN\":{\"url\":\"…\"}}}");
            return true;
        }

        itemManager.getItemPropertiesById(itemId).thenAccept(opt ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt == null || opt.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "✖ Item " + itemId + " is not in the lore catalog.");
                    return;
                }
                ItemProperties props = opt.get();

                // Warn rather than refuse: the texture is stored either way, and an item can be
                // retargeted to PLAYER_HEAD later. Silently accepting it on, say, a sword would
                // just hide the mistake until someone wondered why nothing changed.
                Material mat = props.getMaterial();
                if (mat != Material.PLAYER_HEAD && mat != Material.PLAYER_WALL_HEAD) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Item " + itemId + " is " + mat
                        + ", not a player head — the texture is stored but will not render.");
                }

                props.setSkullTexture(clearing ? null : raw);

                itemManager.updateItemVersioned(itemId, props).thenAccept(version ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (version == null || version <= 0) {
                            sender.sendMessage(ChatColor.RED + "✖ Failed to save the texture for item " + itemId + ".");
                            return;
                        }
                        if (clearing) {
                            sender.sendMessage(ChatColor.GREEN + "✓ Cleared head texture on item " + itemId
                                + " (version " + version + ")");
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "✓ Set head texture on item " + itemId
                                + " (version " + version + ")");
                            sender.sendMessage(ChatColor.GRAY + "   Run /lore item list to refresh the name cache"
                                + " before looking the item up by name (#1917).");
                        }
                    }));
            }));
        return true;
    }

    @Override
    public String getDescription() {
        return "Set or clear a lore item's head texture (base64)";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> out = new ArrayList<>(Arrays.asList("clear"));
            return out;
        }
        return new ArrayList<>();
    }
}
