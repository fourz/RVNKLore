package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for creating lore entries when players equip named armor stands.
 * A named armor stand that receives equipment is registered as a "statue"
 * in the lore system.
 */
public class ArmorStandLoreHandler extends DefaultLoreHandler {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    // Track armor stand UUIDs that have already been registered to avoid duplicates
    private final Set<String> registeredStatues = new HashSet<>();

    public ArmorStandLoreHandler(RVNKLore plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing armor stand lore handler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();

        // Only trigger for named armor stands
        if (armorStand.getCustomName() == null || armorStand.getCustomName().trim().isEmpty()) return;

        // Only trigger when player is ADDING equipment (player item is not air, stand slot is air)
        ItemStack playerItem = event.getPlayerItem();
        ItemStack standItem = event.getArmorStandItem();
        if (playerItem == null || playerItem.getType() == Material.AIR) return;
        if (standItem != null && standItem.getType() != Material.AIR) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("rvnklore.statue.create")) return;

        // Prevent duplicate registration for the same armor stand
        String standUuid = armorStand.getUniqueId().toString();
        if (registeredStatues.contains(standUuid)) return;

        String statueName = ChatColor.stripColor(armorStand.getCustomName()).trim();

        // Schedule 1 tick later so the equipment is actually placed
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            createStatueEntry(player, armorStand, statueName, standUuid), 1L);
    }

    private void createStatueEntry(Player player, ArmorStand armorStand, String statueName, String standUuid) {
        // Build equipment description
        EntityEquipment equipment = armorStand.getEquipment();
        StringBuilder equipDesc = new StringBuilder();
        appendEquipment(equipDesc, "Head", equipment.getHelmet());
        appendEquipment(equipDesc, "Chest", equipment.getChestplate());
        appendEquipment(equipDesc, "Legs", equipment.getLeggings());
        appendEquipment(equipDesc, "Feet", equipment.getBoots());
        appendEquipment(equipDesc, "Hand", equipment.getItemInMainHand());
        appendEquipment(equipDesc, "Off-hand", equipment.getItemInOffHand());

        String description = "Statue of " + statueName + " created by " + player.getName() + ".";
        if (equipDesc.length() > 0) {
            description += "\nEquipment: " + equipDesc.toString();
        }

        LoreEntry entry = LoreEntry.createLocationLore(
            statueName + " (Statue)", description, LoreType.MONUMENT,
            armorStand.getLocation(), player
        );

        entry.addMetadata("sub_type", "statue");
        entry.addMetadata("armor_stand_uuid", standUuid);
        entry.addMetadata("source", "armor_stand");
        entry.addMetadata("created_at", dateFormat.format(new Date()));
        entry.addMetadata("creator_uuid", player.getUniqueId().toString());
        entry.addMetadata("creator_name", player.getName());

        boolean autoApprove = plugin.getConfigManager().getConfig()
            .getBoolean("statues.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        plugin.getLoreManager().addLoreEntry(entry).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    registeredStatues.add(standUuid);
                    player.sendMessage(ChatColor.GREEN + "Statue '" + statueName + "' has been " +
                        (entry.isApproved() ? "registered in the lore." : "submitted for approval."));
                    logger.debug("Statue lore entry created: " + statueName + " by " + player.getName());
                } else {
                    logger.debug("Statue entry not saved (may already exist): " + statueName);
                }
            });
        });
    }

    private void appendEquipment(StringBuilder sb, String slotName, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(slotName).append(": ").append(formatMaterialName(item.getType()));
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Statue");

            String creator = entry.getMetadata("creator_name");
            if (creator != null) {
                lore.add(ChatColor.GRAY + "Created by: " + ChatColor.WHITE + creator);
            }

            lore.add("");
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }

            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE +
                    entry.getLocation().getWorld().getName() + " at " +
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
        return LoreType.MONUMENT;
    }
}
