package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for creating lore entries when players die.
 *
 * Behavior is controlled by lore.playerDeath.mode:
 *   none        - handler is never registered (see HandlerFactory)
 *   significant - only deaths flagged by the significance evaluator
 *   all         - every death creates an entry
 *
 * Entries are always created unapproved and enter the approval queue.
 */
public class PlayerDeathLoreHandler extends DefaultLoreHandler {

    /** Kill credits that are embarrassing rather than dangerous. */
    private static final Set<EntityType> HARMLESS_KILLERS = EnumSet.of(
        EntityType.BEE,
        EntityType.GOAT,
        EntityType.LLAMA,
        EntityType.TRADER_LLAMA,
        EntityType.PUFFERFISH,
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.SNOW_GOLEM
    );

    /** Mundane hazards claiming a player reads as ironic. */
    private static final Set<DamageCause> IRONIC_CAUSES = EnumSet.of(
        DamageCause.CONTACT,        // cactus, berry bush, stalagmite
        DamageCause.FALLING_BLOCK,  // anvil, falling stalactite
        DamageCause.LIGHTNING
    );

    /** Causes implying a rare or epic encounter. */
    private static final Set<DamageCause> RARE_CAUSES = EnumSet.of(
        DamageCause.SONIC_BOOM,     // warden
        DamageCause.DRAGON_BREATH
    );

    public PlayerDeathLoreHandler(RVNKLore plugin) {
        super(plugin);
        // logger is already initialized in DefaultLoreHandler; do not reassign
    }

    @Override
    public void initialize() {
        logger.debug("Initializing player death lore handler (mode: "
            + plugin.getConfigManager().getPlayerDeathLoreMode() + ")");
    }

    /**
     * Listen for player death events and create lore entries per configured mode
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        try {
            String mode = plugin.getConfigManager().getPlayerDeathLoreMode();
            if ("none".equals(mode)) {
                return;
            }

            Player player = event.getEntity();
            String deathMessage = event.getDeathMessage();

            if ("all".equals(mode)) {
                createDeathLoreEntry(player, deathMessage, "all_deaths_mode");
                return;
            }

            // significant mode: any single criterion flags the death
            String reason = evaluateSignificance(player);
            if (reason != null) {
                createDeathLoreEntry(player, deathMessage, reason);
                return;
            }

            checkLoreSiteProximity(player, deathMessage);
        } catch (Exception e) {
            logger.error("Error processing player death event", e);
        }
    }

    /**
     * Synchronous significance criteria. Returns a reason tag when the death
     * is significant, or null when nothing matched.
     */
    private String evaluateSignificance(Player player) {
        // Curated path: staff-designated notable players
        if (player.hasPermission("rvnklore.notable")) {
            return "notable_player";
        }

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) {
            return null;
        }

        DamageCause cause = lastDamage.getCause();
        if (IRONIC_CAUSES.contains(cause)) {
            return "ironic_cause:" + cause.name();
        }
        if (RARE_CAUSES.contains(cause)) {
            return "rare_cause:" + cause.name();
        }
        if (cause == DamageCause.FLY_INTO_WALL) {
            return "self_inflicted:FLY_INTO_WALL";
        }

        if (lastDamage instanceof EntityDamageByEntityEvent) {
            Entity damager = resolveDamager(((EntityDamageByEntityEvent) lastDamage).getDamager());

            if (damager != null) {
                if (damager.equals(player)) {
                    return "self_inflicted:" + cause.name();
                }
                if (HARMLESS_KILLERS.contains(damager.getType())) {
                    return "harmless_mob:" + damager.getType().name();
                }
                // Betrayed by their own tamed animal (wolf teleport into a fight, etc.)
                if (damager instanceof Tameable) {
                    Tameable pet = (Tameable) damager;
                    if (pet.isTamed() && player.equals(pet.getOwner())) {
                        return "own_pet:" + damager.getType().name();
                    }
                }
                if (damager.getType() == EntityType.END_CRYSTAL
                        || damager.getType() == EntityType.WARDEN) {
                    return "rare_cause:" + damager.getType().name();
                }
            }
        }

        return null;
    }

    /**
     * Resolve a damager to the entity that gets narrative credit:
     * projectiles and primed TNT are traced back to their source.
     */
    private Entity resolveDamager(Entity damager) {
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
            return damager;
        }
        if (damager instanceof TNTPrimed) {
            Entity source = ((TNTPrimed) damager).getSource();
            if (source != null) {
                return source;
            }
        }
        return damager;
    }

    /**
     * Async location-juxtaposition criterion: a death within nearbyRadius of an
     * existing lore location ties into established lore.
     */
    private void checkLoreSiteProximity(Player player, String deathMessage) {
        Location deathLocation = player.getLocation().clone();
        double radius = plugin.getConfigManager().getNearbyRadius();

        plugin.getLoreManager().findNearbyLoreEntries(deathLocation, radius).thenAccept(entries -> {
            if (entries == null || entries.isEmpty()) {
                return;
            }
            String siteName = entries.get(0).getName();
            Bukkit.getScheduler().runTask(plugin, () ->
                createDeathLoreEntry(player, deathMessage, "near_lore_site:" + siteName));
        });
    }

    /**
     * Create a lore entry for a player death
     */
    private void createDeathLoreEntry(Player player, String deathMessage, String significance) {
        logger.debug("Creating death lore entry for: " + player.getName()
            + " (significance: " + significance + ")");

        String dateString = DATE_FMT.format(LocalDate.now(ZoneId.systemDefault()));

        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.EVENT);
        entry.setName("Death of " + player.getName());

        // Create a descriptive death entry
        String description = "On " + dateString + ", " + player.getName() + " met their demise.";
        if (deathMessage != null) {
            description += "\n" + deathMessage;
        }
        entry.setDescription(description);

        entry.setLocation(player.getLocation());
        entry.setSubmittedBy("Server");

        // Add metadata
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("death_date", System.currentTimeMillis() + "");
        entry.addMetadata("death_message", deathMessage);
        entry.addMetadata("death_significance", significance);

        // Always pending: the approval queue decides what becomes real lore
        entry.setApproved(false);
        plugin.getLoreManager().addLoreEntry(entry).thenAccept(success -> {
            if (success) {
                logger.debug("Death lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to save death lore entry for: " + player.getName());
            }
        });
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.RED + "Notable Death");

            // Death date if available
            if (entry.getMetadata("death_date") != null) {
                try {
                    long deathTimestamp = Long.parseLong(entry.getMetadata("death_date"));
                    String formatted = DATE_FMT.format(
                            Instant.ofEpochMilli(deathTimestamp).atZone(ZoneId.systemDefault()).toLocalDate());
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + formatted);
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse death date");
                }
            }

            // Split description into lines
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }

            if (entry.getLocation() != null) {
                lore.add(ChatColor.GRAY + "Location: " +
                        ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " +
                        (int)entry.getLocation().getX() + ", " +
                        (int)entry.getLocation().getY() + ", " +
                        (int)entry.getLocation().getZ());
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.PLAYER;
    }
}
