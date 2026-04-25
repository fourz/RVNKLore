package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /lore item tag <entry-name> — stamp the held item with a lore entry's display name,
 * description lore lines, and PDC key {@code rvnklore:entry_id}.
 */
public class LoreItemTagSubCommand implements SubCommand {

    private static final String PERMISSION = "rvnklore.item.tag";
    private final RVNKLore plugin;

    public LoreItemTagSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command requires a player (item in hand needed).");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item tag <entry-name>");
            return true;
        }

        String entryName = String.join(" ", args);

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == org.bukkit.Material.AIR) {
            player.sendMessage(ChatColor.RED + "✖ Hold the item you want to tag in your main hand.");
            return true;
        }

        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(entryName);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "✖ No approved lore entry found named: " + entryName);
            return true;
        }

        if (!entry.isApproved()) {
            player.sendMessage(ChatColor.RED + "✖ That entry has not been approved yet.");
            return true;
        }

        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "✖ This item cannot be tagged (no item meta).");
            return true;
        }

        // Display name: gold entry name
        meta.setDisplayName(ChatColor.GOLD + entry.getName());

        // Lore lines: description + entry ID footer
        List<String> loreLines = new ArrayList<>();
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            loreLines.add(ChatColor.GRAY + description);
        }
        String shortId = entry.getUUID().toString().substring(0, 8);
        loreLines.add(ChatColor.DARK_GRAY + "rvnklore: " + shortId);
        meta.setLore(loreLines);

        // PDC stamp: rvnklore:entry_id = full UUID string
        NamespacedKey entryIdKey = new NamespacedKey(plugin, "entry_id");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(entryIdKey, PersistentDataType.STRING, entry.getUUID().toString());

        held.setItemMeta(meta);
        player.sendMessage(ChatColor.GREEN + "✓ Tagged: " + ChatColor.GOLD + entry.getName()
                + ChatColor.GREEN + " (" + entry.getType() + ")");
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION) || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public String getDescription() {
        return "Tag held item with a lore entry's name, description, and entry ID";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            String prefix = String.join(" ", args).toLowerCase();
            return plugin.getLoreManager().getApprovedLoreEntriesSync().stream()
                    .map(LoreEntry::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
