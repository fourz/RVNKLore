package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /lore item pool add|remove|list} — author RNG item pools ({@code lore_item_rng_pool})
 * from the console (#1496). Replaces raw DB inserts, so pools can be authored on Event/prod where
 * {@code db_query} is read-only. Console-safe; narrow additive writes on the existing table (no
 * schema change).
 */
public class LoreItemPoolSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemPoolSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemPoolSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.pool");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(sender, args);
            case "remove", "rm" -> handleRemove(sender, args);
            case "list", "ls" -> handleList(sender, args);
            case "preview" -> handlePreview(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        // pool add <poolId> <itemId|name> [rarityTier] [weight]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool add <poolId> <itemId|name> [rarityTier] [weight]");
            return;
        }
        String poolId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        String rarityTier = args.length >= 4 ? args[3].toUpperCase() : "COMMON";
        int weight = args.length >= 5 ? parseIntOr(args[4], 1) : 1;
        final int resolvedId = itemId;
        itemManager.addPoolEntry(poolId, itemId, rarityTier, weight).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Added item " + resolvedId + " to pool '" + poolId
                        + "' (" + rarityTier + ", weight " + weight + ")");
                    warnIfUntexturedHead(sender, resolvedId);
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to add pool entry (does the item exist?).");
                }
            }));
    }

    /**
     * Flag a player head pooled with no stored texture (#1920).
     *
     * <p>A {@code PLAYER_HEAD} with no {@code skull_texture} rolls and bakes as an anonymous Steve.
     * That is consistent across both lanes so it is not a parity break — but in a reward chest it
     * looks like a texture that failed to load, and the author almost certainly meant to set one.
     * Catching it here beats discovering it in-game.</p>
     *
     * <p>Warn, never refuse: an intentionally anonymous head is legitimate, and the pool entry has
     * already been written by the time this runs. Fires after the success line so the warning reads
     * as a follow-up to a completed action rather than a failure.</p>
     */
    private void warnIfUntexturedHead(CommandSender sender, int itemId) {
        itemManager.getItemPropertiesById(itemId).thenAccept(opt ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (opt == null || opt.isEmpty()) {
                    return;
                }
                ItemProperties p = opt.get();
                org.bukkit.Material mat = p.getMaterial();
                boolean playerHead = mat == org.bukkit.Material.PLAYER_HEAD
                    || mat == org.bukkit.Material.PLAYER_WALL_HEAD;
                if (playerHead && (p.getSkullTexture() == null || p.getSkullTexture().isEmpty())) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Item " + itemId + " is a player head with no"
                        + " stored texture - it will roll and bake as a blank (Steve) head.");
                    sender.sendMessage(ChatColor.GRAY + "   Set one with /lore item texture " + itemId
                        + " <base64>, or ignore this if an anonymous head is intended.");
                }
            }));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        // pool remove <poolId> <itemId|name>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool remove <poolId> <itemId|name>");
            return;
        }
        String poolId = args[1];
        int itemId = itemManager.resolveDatabaseId(args[2]);
        if (itemId <= 0) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + args[2]);
            return;
        }
        itemManager.removePoolEntry(poolId, itemId).thenAccept(ok ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Removed item " + itemId + " from pool '" + poolId + "'");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No such entry in pool '" + poolId + "' for item " + itemId);
                }
            }));
    }

    private void handleList(CommandSender sender, String[] args) {
        // pool list <poolId>
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool list <poolId>");
            return;
        }
        String poolId = args[1];
        itemManager.listPoolEntries(poolId).thenAccept(rows -> {
            if (rows.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Pool '" + poolId + "' is empty."));
                return;
            }
            // Resolve item names so the pool is readable by name, not just numeric id (G2, #1681).
            Map<Integer, String> names = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> lookups = new ArrayList<>();
            for (ItemRepository.PoolEntryRow r : rows) {
                int id = r.loreItemId();
                if (names.containsKey(id)) {
                    continue;
                }
                names.put(id, "?"); // placeholder guards against duplicate lookups for the same id
                lookups.add(itemManager.getItemPropertiesById(id).thenAccept(opt ->
                    names.put(id, opt.map(ItemProperties::getDisplayName).orElse("<missing>"))));
            }
            CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0])).whenComplete((v, t) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "===== RNG Pool: " + poolId + " (" + rows.size() + ") =====");
                    for (ItemRepository.PoolEntryRow r : rows) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + "item " + r.loreItemId()
                            + ChatColor.GREEN + " " + names.getOrDefault(r.loreItemId(), "?")
                            + ChatColor.GRAY + " [" + r.rarityTier() + "] "
                            + ChatColor.AQUA + "w" + r.weight()
                            + (r.active() ? "" : ChatColor.DARK_GRAY + " (inactive)"));
                    }
                }));
        });
    }

    private void handlePreview(CommandSender sender, String[] args) {
        // pool preview <poolId> [rarityTier] — dump the generated loot-table JSON without baking a
        // datapack. The console view of what `loot bake` would emit (G4, #1679) — surfaces identity
        // gaps like #1677 without opening an in-game chest.
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item pool preview <poolId> [rarityTier]");
            return;
        }
        if (plugin.getRngItemService() == null) {
            sender.sendMessage(ChatColor.RED + "✖ RNG item service is not available.");
            return;
        }
        String poolId = args[1];
        String tier = args.length >= 3 ? args[2] : null;
        String label = poolId + (tier != null ? " [" + tier + "]" : "");
        plugin.getRngItemService().poolToLootTableJson(poolId, tier).thenAccept(opt ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Pool '" + label
                        + "' is empty or unavailable - nothing to preview.");
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "===== Baked loot table (preview): " + label + " =====");
                for (String line : opt.get().split("\n")) {
                    sender.sendMessage(ChatColor.GRAY + line);
                }
                sender.sendMessage(ChatColor.DARK_GRAY
                    + "(dry-run - no datapack written; commit with /world structure loot bake " + poolId
                    + (tier != null ? " " + tier : "") + ")");
            }));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "▶ /lore item pool <add|remove|list|preview>");
        sender.sendMessage(ChatColor.GRAY + "   add <poolId> <itemId|name> [rarityTier] [weight]");
        sender.sendMessage(ChatColor.GRAY + "   remove <poolId> <itemId|name>");
        sender.sendMessage(ChatColor.GRAY + "   list <poolId>");
        sender.sendMessage(ChatColor.GRAY + "   preview <poolId> [rarityTier]  (dry-run loot JSON)");
    }

    private int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public String getDescription() {
        return "Author RNG item pools (add/remove/list)";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String o : Arrays.asList("add", "remove", "list", "preview")) {
                if (o.startsWith(args[0].toLowerCase())) out.add(o);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
