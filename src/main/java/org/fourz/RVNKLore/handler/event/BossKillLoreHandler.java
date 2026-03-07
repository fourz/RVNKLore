package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for creating lore entries when boss mobs are killed.
 * Listens for Ender Dragon and Wither deaths, records all nearby participants.
 */
public class BossKillLoreHandler extends DefaultLoreHandler {

    private static final Set<EntityType> BOSS_TYPES = EnumSet.of(
        EntityType.ENDER_DRAGON,
        EntityType.WITHER
    );

    private static final double PARTICIPANT_RADIUS = 100.0;

    public BossKillLoreHandler(RVNKLore plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing boss kill lore handler");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (!BOSS_TYPES.contains(entity.getType())) {
                return;
            }

            createBossKillEntry(entity);
        } catch (Exception e) {
            logger.error("Error processing boss kill event", e);
        }
    }

    private void createBossKillEntry(LivingEntity boss) {
        String bossName = getBossDisplayName(boss.getType());
        Location location = boss.getLocation();
        Player killer = boss.getKiller();

        // Find all players within radius as participants
        List<Player> participants = location.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(location) <= PARTICIPANT_RADIUS * PARTICIPANT_RADIUS)
            .collect(Collectors.toList());

        SimpleDateFormat nameFormatter = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat descFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date now = new Date();
        String nameDate = nameFormatter.format(now);
        String descDate = descFormatter.format(now);

        StringBuilder description = new StringBuilder();
        description.append("On ").append(descDate).append(", the ").append(bossName)
            .append(" was slain");

        if (location.getWorld() != null) {
            description.append(" in ").append(location.getWorld().getName());
        }
        description.append(".");

        if (killer != null) {
            description.append("\nFinal blow dealt by ").append(killer.getName()).append(".");
        }

        if (!participants.isEmpty()) {
            String participantNames = participants.stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
            description.append("\nParticipants: ").append(participantNames).append(".");
        }

        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.EVENT);
        entry.setName(bossName + " Slain (" + nameDate + ")");
        entry.setDescription(description.toString());
        entry.setLocation(location);
        entry.setSubmittedBy("Server");
        entry.setApproved(true);

        entry.addMetadata("boss_type", boss.getType().name());
        entry.addMetadata("event_type", "boss_kill");
        entry.addMetadata("kill_date", String.valueOf(System.currentTimeMillis()));
        if (killer != null) {
            entry.addMetadata("killer_uuid", killer.getUniqueId().toString());
            entry.addMetadata("killer_name", killer.getName());
        }
        entry.addMetadata("participant_count", String.valueOf(participants.size()));

        plugin.getLoreManager().addLoreEntry(entry).thenAccept(success -> {
            if (success) {
                logger.info("Boss kill lore entry created: " + entry.getName());
                Bukkit.getScheduler().runTask(plugin, () ->
                    announceBossKill(bossName, killer, participants));
            } else {
                logger.warning("Failed to save boss kill lore entry: " + entry.getName());
            }
        });
    }

    private void announceBossKill(String bossName, Player killer, List<Player> participants) {
        String message = ChatColor.GOLD + "[Lore] " + ChatColor.WHITE + "The " + bossName + " has been slain!";
        if (killer != null) {
            message += ChatColor.GRAY + " Final blow: " + ChatColor.YELLOW + killer.getName();
        }
        if (participants.size() > 1) {
            message += ChatColor.GRAY + " (" + participants.size() + " participants)";
        }
        Bukkit.broadcastMessage(message);
    }

    private String getBossDisplayName(EntityType type) {
        switch (type) {
            case ENDER_DRAGON: return "Ender Dragon";
            case WITHER: return "Wither";
            default: return type.name();
        }
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        Material material = Material.DRAGON_EGG;
        if ("WITHER".equals(entry.getMetadata("boss_type"))) {
            material = Material.NETHER_STAR;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.RED + "Legendary Event");

            if (entry.getMetadata("killer_name") != null) {
                lore.add(ChatColor.GRAY + "Final blow: " + ChatColor.YELLOW + entry.getMetadata("killer_name"));
            }
            if (entry.getMetadata("participant_count") != null) {
                lore.add(ChatColor.GRAY + "Participants: " + ChatColor.WHITE + entry.getMetadata("participant_count"));
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
        return LoreType.EVENT;
    }
}
