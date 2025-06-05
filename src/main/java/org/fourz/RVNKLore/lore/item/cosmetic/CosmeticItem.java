package org.fourz.RVNKLore.lore.item.cosmetic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.util.HeadUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central management system for head collections and cosmetic items.
 * Handles registration, retrieval, and player progress tracking for all head variants.
 * Integrates with the broader ItemManager system for unified item creation.
 */
public class CosmeticItem {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, HeadCollection> collections;
    private final Map<String, HeadVariant> headVariants;
    private final Map<UUID, Set<String>> playerOwnedHeads;
    private final Map<CollectionTheme, List<HeadCollection>> themeIndex;

    public CosmeticItem(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CosmeticItem");
        this.collections = new ConcurrentHashMap<>();
        this.headVariants = new ConcurrentHashMap<>();
        this.playerOwnedHeads = new ConcurrentHashMap<>();
        this.themeIndex = new EnumMap<>(CollectionTheme.class);
        
        // Initialize theme index
        for (CollectionTheme theme : CollectionTheme.values()) {
            themeIndex.put(theme, new ArrayList<>());
        }
    }
    
    /**
     * Initialize the cosmetic manager and load default collections.
     */
    public void initialize() {
        logger.info("Initializing cosmetic manager");
        loadDefaultCollections();
        logger.info("Loaded " + collections.size() + " head collections with " + 
                   headVariants.size() + " total variants");
    }
      /**
     * Create a cosmetic item based on provided properties.
     *
     * @param properties Item properties containing cosmetic specifications
     * @return ItemStack representing the cosmetic item
     */
    public ItemStack createCosmeticItem(ItemProperties properties) {
        String cosmeticType = properties.getMetadata("cosmetic_type");
        
        if ("head".equals(cosmeticType)) {
            String variantId = properties.getMetadata("head_variant");
            if (variantId != null) {
                HeadVariant variant = getHeadVariant(variantId);
                if (variant != null) {
                    return createHeadItem(variant);
                }
            }
        }
        
        // Check if we have a direct head variant
        if (properties.getHeadVariant() != null) {
            return createHeadItem(properties.getHeadVariant());
        }
        
        // Fallback: create basic cosmetic item
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        if (properties.getDisplayName() != null) {
            item.getItemMeta().setDisplayName(properties.getDisplayName());
        }
        
        return item;
    }
    
    /**
     * Register a new head collection.
     *
     * @param collection The collection to register
     * @return True if successfully registered, false if ID already exists
     */
    public boolean registerCollection(HeadCollection collection) {
        if (collections.containsKey(collection.getId())) {
            logger.warning("Attempted to register collection with duplicate ID: " + collection.getId());
            return false;
        }
        
        collections.put(collection.getId(), collection);
        themeIndex.get(collection.getTheme()).add(collection);
        
        // Register all head variants from this collection
        for (HeadVariant variant : collection.getAllHeads()) {
            headVariants.put(variant.getId(), variant);
        }
        
        logger.info("Registered collection: " + collection.getName() + " (" + collection.getId() + ")");
        return true;
    }
    
    /**
     * Unregister a head collection.
     *
     * @param collectionId ID of the collection to remove
     * @return True if successfully removed
     */
    public boolean unregisterCollection(String collectionId) {
        HeadCollection collection = collections.remove(collectionId);
        if (collection != null) {
            themeIndex.get(collection.getTheme()).remove(collection);
            
            // Remove head variants
            for (HeadVariant variant : collection.getAllHeads()) {
                headVariants.remove(variant.getId());
            }
            
            logger.info("Unregistered collection: " + collection.getName());
            return true;
        }
        return false;
    }
    
    /**
     * Get a head collection by ID.
     *
     * @param collectionId The collection ID
     * @return The collection or null if not found
     */
    public HeadCollection getCollection(String collectionId) {
        return collections.get(collectionId);
    }


    
    /**
     * Get all registered collections.
     *
     * @return Unmodifiable collection of all head collections
     */
    public Collection<HeadCollection> getAllCollections() {
        return Collections.unmodifiableCollection(collections.values());
    }
    
    /**
     * Get collections by theme.
     *
     * @param theme The theme to filter by
     * @return List of collections matching the theme
     */
    public List<HeadCollection> getCollectionsByTheme(CollectionTheme theme) {
        return new ArrayList<>(themeIndex.get(theme));
    }
    
    /**
     * Get collections available for a player (considering seasonal restrictions).
     *
     * @return List of currently available collections
     */
    public List<HeadCollection> getAvailableCollections() {
        return collections.values().stream()
            .filter(HeadCollection::isAvailable)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a head variant by ID.
     *
     * @param variantId The variant ID
     * @return The head variant or null if not found
     */
    public HeadVariant getHeadVariant(String variantId) {
        return headVariants.get(variantId);
    }
    
    /**
     * Generate an ItemStack for a head variant.
     *
     * @param variant The head variant to create an item for
     * @return ItemStack representing the head
     */
    public ItemStack createHeadItem(HeadVariant variant) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta == null) {
            logger.warning("Failed to get skull meta for head variant: " + variant.getId());
            return item;
        }
        
        // Set display name with rarity color
        meta.setDisplayName(variant.getRarity().getColor() + variant.getName());
        
        // Set lore
        List<String> lore = new ArrayList<>();
        lore.add("§7" + variant.getDescription());
        lore.add("");
        lore.add(variant.getRarity().getColoredDisplayName());
        
        if (variant.getType() == HeadType.ANIMATED) {
            lore.add("§e⚡ Animated (" + variant.getAnimationFrameCount() + " frames)");
        }
        
        meta.setLore(lore);
          // Apply texture based on head type
        switch (variant.getType()) {            case PLAYER:
                if (variant.getOwnerName() != null) {
                    // Use meta.setOwner for player heads (deprecated but functional)
                    meta.setOwner(variant.getOwnerName());
                }
                break;
                
            case MOB:
                if (variant.getMobType() != null) {
                    HeadUtil.applyMobTexture(meta, variant.getMobType());
                }
                break;
                
            case CUSTOM:
            case ANIMATED:
            case HAT:
                if (variant.getTextureData() != null) {
                    HeadUtil.applyTextureData(meta, variant.getTextureData());
                }
                break;
        }
        
        // Apply custom model data if available
        if (variant.getCustomModelData() != null) {
            meta.setCustomModelData(variant.getCustomModelData());
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Grant a head variant to a player.
     *
     * @param player The player to grant the head to
     * @param variantId The head variant ID
     * @return True if successfully granted
     */
    public boolean grantHeadToPlayer(Player player, String variantId) {
        HeadVariant variant = headVariants.get(variantId);
        if (variant == null) {
            logger.warning("Attempted to grant unknown head variant: " + variantId);
            return false;
        }
        
        // Check permissions if required
        if (variant.requiresPermission()) {
            String permission = variant.getRequiredPermission();
            if (permission != null && !player.hasPermission(permission)) {
                logger.info("Player " + player.getName() + " lacks permission for head: " + variantId);
                return false;
            }
        }
        
        Set<String> ownedHeads = playerOwnedHeads.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        
        if (ownedHeads.contains(variantId)) {
            return false; // Already owns this head
        }
        
        ownedHeads.add(variantId);
        
        // Give the physical item to the player
        ItemStack headItem = createHeadItem(variant);
        player.getInventory().addItem(headItem);
        
        // Check for completed collections
        checkCollectionCompletion(player);
        
        logger.info("Granted head variant " + variantId + " to player " + player.getName());
        return true;
    }
    
    /**
     * Check if a player owns a specific head variant.
     *
     * @param player The player to check
     * @param variantId The head variant ID
     * @return True if the player owns the head
     */
    public boolean playerOwnsHead(Player player, String variantId) {
        Set<String> ownedHeads = playerOwnedHeads.get(player.getUniqueId());
        return ownedHeads != null && ownedHeads.contains(variantId);
    }
    
    /**
     * Get all head variants owned by a player.
     *
     * @param player The player
     * @return Set of owned head variant IDs
     */
    public Set<String> getPlayerOwnedHeads(Player player) {
        return new HashSet<>(playerOwnedHeads.getOrDefault(player.getUniqueId(), new HashSet<>()));
    }
    
    /**
     * Get completion status for all collections for a player.
     *
     * @param player The player
     * @return Map of collection ID to completion percentage
     */
    public Map<String, Double> getPlayerCollectionProgress(Player player) {
        Set<String> ownedHeads = getPlayerOwnedHeads(player);
        Map<String, Double> progress = new HashMap<>();
        
        for (HeadCollection collection : collections.values()) {
            if (collection.isAvailable()) {
                double percentage = collection.getCompletionPercentage(ownedHeads);
                progress.put(collection.getId(), percentage);
            }
        }
        
        return progress;
    }
    
    /**
     * Get available cosmetic types.
     *
     * @return List of available cosmetic types
     */
    public List<String> getAvailableCosmeticTypes() {
        return Arrays.asList("head", "hat", "accessory", "decoration");
    }
    
    /**
     * Check for newly completed collections and award rewards.
     *
     * @param player The player to check
     */
    private void checkCollectionCompletion(Player player) {
        Set<String> ownedHeads = getPlayerOwnedHeads(player);
        
        for (HeadCollection collection : collections.values()) {
            if (collection.isAvailable() && collection.isComplete(ownedHeads)) {
                // Award collection completion rewards
                CollectionRewards rewards = collection.getRewards();
                if (rewards.hasRewards()) {
                    awardCollectionRewards(player, collection, rewards);
                }
            }
        }
    }
    
    /**
     * Allow players to claim rewards for a completed collection.
     */
    public void awardCollectionRewards(Player player, HeadCollection collection, CollectionRewards rewards) {
        // Give item rewards
        for (org.bukkit.inventory.ItemStack item : rewards.getItems()) {
            player.getInventory().addItem(item);
        }
        // Execute command rewards
        for (String command : rewards.getCommands()) {
            String processedCommand = command.replace("{player}", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processedCommand);
        }
        // Give experience
        if (rewards.getExperiencePoints() > 0) {
            player.giveExp(rewards.getExperiencePoints());
        }
        // Send completion message
        if (rewards.getCompletionMessage() != null) {
            player.sendMessage("§a✓ " + rewards.getCompletionMessage());
        } else {
            player.sendMessage("§a✓ Completed collection: " + collection.getName());
        }
        logger.info("Awarded collection completion rewards to " + player.getName() +
                   " for collection: " + collection.getName());
    }
    
    /**
     * Load default head collections for common themes.
     */
    private void loadDefaultCollections() {
        // Create a basic mob collection
        HeadCollection mobCollection = new HeadCollection(
            "basic_mobs", 
            "Basic Mobs", 
            "Common creature heads from the overworld",
            CollectionTheme.ANIMALS
        );
        
        // Add some basic mob heads
        mobCollection.addHead(new HeadVariant(
            "zombie_head", "Zombie Head", "A decaying zombie head", 
            org.bukkit.entity.EntityType.ZOMBIE, HeadRarity.COMMON
        ));
        
        mobCollection.addHead(new HeadVariant(
            "skeleton_head", "Skeleton Head", "A bleached skeleton skull", 
            org.bukkit.entity.EntityType.SKELETON, HeadRarity.COMMON
        ));
        
        mobCollection.addHead(new HeadVariant(
            "creeper_head", "Creeper Head", "An explosive creeper head", 
            org.bukkit.entity.EntityType.CREEPER, HeadRarity.UNCOMMON
        ));
        
        registerCollection(mobCollection);
        
        logger.info("Loaded default collections");
    }
    
    /**
     * Cleanup and shutdown the cosmetic manager.
     */
    public void shutdown() {
        logger.info("Shutting down cosmetic manager");
        // Here you would save player data to database
        // For now, just log the shutdown
    }
}
