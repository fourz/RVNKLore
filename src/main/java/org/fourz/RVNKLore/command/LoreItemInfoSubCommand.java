package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item info <item_name> command.
 * Displays detailed information about a registered lore item using DisplayFactory.
 * Also supports lookup by UUID or short UUID.
 */
public class LoreItemInfoSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemInfoSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemInfoSubCommand");
        this.itemManager = plugin.getLoreManager().getItemManager();
    }

    public LoreItemInfoSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemInfoSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give") || sender.hasPermission("rvnklore.collection");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (itemManager == null) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item system is not available. Please try again later.");
                logger.error("ItemManager is null when trying to list items", null);
                return true;
            }
            List<String> allItems = itemManager.getAllItemNamesForCommands();
            DisplayFactory.displayPaginatedList(sender, "Available Items", allItems, 1, 50, s -> org.bukkit.ChatColor.YELLOW + " - " + s);
            return true;
        }
        String itemNameOrId = stripQuotes(String.join(" ", args));
        if (itemManager == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to get item info: " + itemNameOrId, null);
            return true;
        }

        // Numeric lore_item_id lookup (G1, #1680) — the id the pool/PDC/loot system speaks.
        // `/lore item info 2` previously failed because only name/UUID were matched.
        if (itemNameOrId.matches("\\d+")) {
            int itemId = Integer.parseInt(itemNameOrId);
            itemManager.getItemPropertiesById(itemId).thenAccept(opt ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (opt.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "✖ No lore item with id " + itemId + ".");
                        return;
                    }
                    displayItemDiagnostics(sender, itemId, opt.get());
                }));
            return true;
        }

        // Try to match by UUID or short UUID for LoreEntry
       LoreEntry matched = null;
        for (LoreEntry entry : plugin.getLoreManager().getAllLoreEntriesSync()) {
            String uuid = entry.getId().toString();
            if (uuid.equalsIgnoreCase(itemNameOrId) || uuid.substring(0, 8).equalsIgnoreCase(itemNameOrId)) {
                matched = entry;
                break;
            }
        }
        if (matched != null) {
            return DisplayFactory.displayLoreEntry(sender, matched);
        }

        // Fallback: try to display as item by name
        org.bukkit.inventory.ItemStack item = itemManager.createLoreItemSync(itemNameOrId);
        return DisplayFactory.displayItem(sender, item, itemNameOrId);
    }

    /**
     * Content diagnostics for a lore item (G3, #1680) — material, custom-model-data, rarity, lore
     * lines, and for WRITTEN_BOOK the page count. Answers "does this book have pages?" from console
     * (the question that forced reading chest NBT during #1675 QA).
     */
    private void displayItemDiagnostics(CommandSender sender, int itemId, ItemProperties p) {
        sender.sendMessage(ChatColor.GOLD + "===== Lore Item #" + itemId + ": "
            + ChatColor.WHITE + p.getDisplayName() + ChatColor.GOLD + " =====");
        Material mat = p.getMaterial();
        sender.sendMessage(ChatColor.GRAY + "Material: " + ChatColor.WHITE + (mat != null ? mat.getKey().toString() : "<null>"));
        int cmd = p.getCustomModelData();
        sender.sendMessage(ChatColor.GRAY + "CustomModelData: " + ChatColor.WHITE + (cmd > 0 ? String.valueOf(cmd) : "none"));
        sender.sendMessage(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE
            + (p.getRarity() != null ? p.getRarity() : (p.getRarityLevel() != null ? p.getRarityLevel() : "none")));
        int loreLines = p.getLore() != null ? p.getLore().size() : 0;
        sender.sendMessage(ChatColor.GRAY + "Lore lines: " + ChatColor.WHITE + loreLines);
        if (mat == Material.WRITTEN_BOOK || mat == Material.WRITABLE_BOOK) {
            int pages = p.getPages() != null ? p.getPages().size() : 0;
            sender.sendMessage(ChatColor.GRAY + "Pages: " + (pages > 0
                ? ChatColor.WHITE + String.valueOf(pages)
                : ChatColor.RED + "0 — book has NO page content"));
        }
        if (p.getLoreEntryId() != null) {
            sender.sendMessage(ChatColor.GRAY + "Lore entry: " + ChatColor.WHITE + p.getLoreEntryId());
        }
    }

    @Override
    public String getDescription() {
        return "Get information about a lore item by name, numeric id, or UUID.";
    }

    private String stripQuotes(String value) {
        if (value != null && value.length() > 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNamesForCommands());
        }
        return completions;
    }
}
