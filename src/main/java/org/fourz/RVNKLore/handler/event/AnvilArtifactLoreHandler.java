package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for creating lore entries when players rename items in an anvil
 * with a configurable prefix (default: "Lore:"). The renamed item is
 * registered as a named artifact in the lore system.
 */
public class AnvilArtifactLoreHandler extends DefaultLoreHandler {

    private static final String DEFAULT_PREFIX = "Lore:";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public AnvilArtifactLoreHandler(RVNKLore plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing anvil artifact lore handler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType() == Material.AIR) return;

        ItemMeta meta = resultItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String prefix = plugin.getConfigManager().getConfig()
            .getString("artifacts.name_prefix", DEFAULT_PREFIX).trim();
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        if (!displayName.startsWith(prefix)) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!player.hasPermission("rvnklore.artifact.name")) return;

        String artifactName = displayName.substring(prefix.length()).trim();
        if (artifactName.isEmpty()) return;

        createArtifactEntry(player, resultItem, artifactName);
    }

    private void createArtifactEntry(Player player, ItemStack item, String artifactName) {
        Material material = item.getType();
        Map<Enchantment, Integer> enchantments = item.getEnchantments();

        StringBuilder description = new StringBuilder();
        description.append(formatMaterialName(material));
        if (!enchantments.isEmpty()) {
            String enchantList = enchantments.entrySet().stream()
                .map(e -> formatEnchantmentName(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining(", "));
            description.append(" enchanted with ").append(enchantList);
        }
        description.append(". Named by ").append(player.getName()).append(".");

        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.ITEM);
        entry.setName(artifactName);
        entry.setDescription(description.toString());
        entry.setLocation(player.getLocation());
        entry.setSubmittedBy(player.getName());

        entry.addMetadata("sub_type", "artifact");
        entry.addMetadata("material", material.name());
        entry.addMetadata("source", "anvil");
        entry.addMetadata("created_at", dateFormat.format(new Date()));
        entry.addMetadata("creator_uuid", player.getUniqueId().toString());
        entry.addMetadata("creator_name", player.getName());
        if (!enchantments.isEmpty()) {
            String enchantData = enchantments.entrySet().stream()
                .map(e -> e.getKey().getKey().getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
            entry.addMetadata("enchantments", enchantData);
        }

        boolean autoApprove = plugin.getConfigManager().getConfig()
            .getBoolean("artifacts.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        plugin.getLoreManager().addLoreEntry(entry).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Artifact '" + artifactName + "' has been " +
                        (entry.isApproved() ? "registered in the lore." : "submitted for approval."));
                    logger.debug("Artifact lore entry created: " + artifactName + " by " + player.getName());
                } else {
                    logger.debug("Artifact entry not saved (may already exist): " + artifactName);
                }
            });
        });
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String formatEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String name = key.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        String materialName = entry.getMetadata("material");
        Material material = Material.DIAMOND_SWORD; // fallback
        if (materialName != null) {
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException ignored) {}
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Named Artifact");

            String creator = entry.getMetadata("creator_name");
            if (creator != null) {
                lore.add(ChatColor.GRAY + "Named by: " + ChatColor.WHITE + creator);
            }

            String enchants = entry.getMetadata("enchantments");
            if (enchants != null && !enchants.isEmpty()) {
                lore.add(ChatColor.GRAY + "Enchantments: " + ChatColor.AQUA + enchants);
            }

            lore.add("");
            lore.add(ChatColor.WHITE + entry.getDescription());

            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Created at: " + ChatColor.WHITE +
                    entry.getLocation().getWorld().getName() + " " +
                    (int) entry.getLocation().getX() + ", " +
                    (int) entry.getLocation().getY() + ", " +
                    (int) entry.getLocation().getZ());
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ITEM;
    }
}
