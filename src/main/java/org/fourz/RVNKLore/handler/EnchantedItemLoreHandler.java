package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handler for creating lore entries for enchanted items
 */
public class EnchantedItemLoreHandler extends DefaultLoreHandler {

    private static final String PREFS_PLUGIN_ID = "rvnklore";
    private static final DateTimeFormatter NAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public EnchantedItemLoreHandler(RVNKLore plugin) {
        super(plugin);
    }
    
    @Override
    public void initialize() {
    }

    /**
     * Listen for enchant item events to create lore entries for special enchantments.
     * Recorded only when the enchanter holds {@link EnchantChronicle#PERMISSION} and has opted in.
     */
    @EventHandler
    public void onItemEnchant(EnchantItemEvent event) {
        if (!isNotableEnchantment(event.getEnchantsToAdd())) {
            return;
        }
        Player enchanter = event.getEnchanter();
        if (!enchanter.hasPermission(EnchantChronicle.PERMISSION)) {
            return;
        }
        createEnchantmentLoreEntry(event);
    }
    
    /**
     * Determine if this enchantment combination is significant enough to record
     */
    private boolean isNotableEnchantment(Map<Enchantment, Integer> enchants) {
        return enchants.values().stream().anyMatch(level -> level >= 4) || 
               enchants.size() >= 3;
    }
    
    /**
     * Create a lore entry for a significant enchanted item, if the enchanter opted in.
     * The entry is built here on the main thread; only the preference read and the save are async.
     */
    private void createEnchantmentLoreEntry(EnchantItemEvent event) {
        Player enchanter = event.getEnchanter();
        Map<String, Integer> enchants = new LinkedHashMap<>();
        event.getEnchantsToAdd().forEach((enchant, level) -> enchants.put(enchant.getKey().getKey(), level));

        LoreEntry entry = buildEnchantmentEntry(
                enchanter.getName(),
                enchanter.getUniqueId(),
                event.getItem().getType(),
                enchants,
                event.getExpLevelCost(),
                event.getEnchantBlock().getLocation(),
                LocalDateTime.now(),
                enchanter.hasPermission("rvnklore.approve.own"));

        // Fail closed: without the preference service the opt-in cannot be read, so nothing records.
        PlayerPreferencesService prefs = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
        if (prefs == null) {
            logger.debug("Enchant chronicle skipped for " + enchanter.getName() + " - preferences unavailable");
            return;
        }

        prefs.getPreferences(enchanter.getUniqueId(), PREFS_PLUGIN_ID)
            .thenCompose(dto -> {
                if (!EnchantChronicle.isOptedIn(dto.getMetadata())) {
                    logger.debug("Enchant chronicle skipped for " + enchanter.getName() + " - not opted in");
                    return CompletableFuture.<Boolean>completedFuture(null);
                }
                return getPlugin().getLoreManager().addLoreEntry(entry);
            })
            .thenAccept(success -> {
                if (success == null) {
                    return;
                }
                if (success) {
                    logger.debug("Enchanted item lore entry saved: " + entry.getName());
                } else {
                    logger.warning("Enchanted item lore entry not saved: " + entry.getName());
                }
            })
            .exceptionally(ex -> {
                logger.warning("Enchant chronicle failed for " + enchanter.getName() + ": " + ex.getMessage());
                return null;
            });
    }

    /**
     * Build the lore entry for one enchanting-table event.
     *
     * <p>Typed ENCHANTMENT so it reaches the Enchanting Table browse category and the gameplay
     * collection, and validates through this handler instead of ItemLoreHandler. lore_entry is
     * UNIQUE (name, entry_type) and a collision is only logged at DEBUG, so the name carries the
     * enchanter and a timestamp - a per-material name would keep the first record and silently
     * drop every later one. The timestamp runs to MILLISECONDS: at second granularity two enchants
     * of the same material by the same player inside one second collide and the later one is
     * dropped silently (PR #21 review).
     */
    static LoreEntry buildEnchantmentEntry(String playerName, UUID playerUuid, Material material,
                                           Map<String, Integer> enchants, int expCost,
                                           Location location, LocalDateTime when, boolean approved) {
        String itemName = prettyName(material.name());
        String enchantList = enchants.entrySet().stream()
                .map(e -> prettyName(e.getKey()) + " " + roman(e.getValue()))
                .collect(Collectors.joining(", "));

        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.ENCHANTMENT);
        entry.setName(playerName + "'s " + itemName + " (" + when.format(NAME_TIMESTAMP) + ")");
        entry.setDescription(playerName + " enchanted a " + itemName + " with " + enchantList
                + " for " + expCost + " levels.");
        entry.setLocation(location);
        // submitter_uuid holds a UUID string - LoreEntryRepository:29-31 calls name-strings here a
        // legacy shape that new rows must not create, and it is what makes attribution survive a
        // rename. The readable name stays in the description and metadata (PR #21 review).
        entry.setSubmittedBy(playerUuid.toString());

        entry.addMetadata("material", material.name());
        entry.addMetadata("enchanter_uuid", playerUuid.toString());
        entry.addMetadata("enchanter_name", playerName);
        entry.addMetadata("exp_cost", String.valueOf(expCost));
        entry.addMetadata("enchantments", enchants.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(",")));

        // Matches the other event-triggered recorders: auto-approve only with rvnklore.approve.own
        entry.setApproved(approved);
        return entry;
    }

    /** DIAMOND_PICKAXE / fire_aspect -> Diamond Pickaxe / Fire Aspect */
    static String prettyName(String raw) {
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).split("_"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level < numerals.length ? numerals[level] : String.valueOf(level);
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Enchanted Item");
            lore.add("");
            lore.add(ChatColor.WHITE + entry.getDescription());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ENCHANTMENT;
    }
}
