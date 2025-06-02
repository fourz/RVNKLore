# Entity & Mob API Reference

## Overview

This reference covers entity and mob management APIs across Bukkit, Spigot, and Paper platforms, focusing on features relevant to RVNKLore's lore discovery, interaction systems, and dynamic content generation.

## Core Entity Management

### Entity Types and Classification

```java
public class LoreEntityManager {
    private final RVNKLore plugin;
    private final Map<EntityType, LoreEntityHandler> entityHandlers = new HashMap<>();
    
    public LoreEntityManager(RVNKLore plugin) {
        this.plugin = plugin;
        initializeEntityHandlers();
    }
    
    private void initializeEntityHandlers() {
        // Register handlers for different entity categories
        entityHandlers.put(EntityType.VILLAGER, new VillagerLoreHandler(plugin));
        entityHandlers.put(EntityType.ARMOR_STAND, new ArmorStandLoreHandler(plugin));
        entityHandlers.put(EntityType.ITEM_FRAME, new ItemFrameLoreHandler(plugin));
        
        // Register mob-specific handlers
        for (EntityType type : EntityType.values()) {
            if (type.isSpawnable() && type.isAlive() && !entityHandlers.containsKey(type)) {
                entityHandlers.put(type, new GenericMobLoreHandler(plugin, type));
            }
        }
    }
    
    public void handleEntityInteraction(Player player, Entity entity, EquipmentSlot hand) {
        LoreEntityHandler handler = entityHandlers.get(entity.getType());
        if (handler != null) {
            handler.handleInteraction(player, entity, hand);
        }
    }
    
    public void registerLoreEntity(Entity entity, LoreEntry loreEntry) {
        // Use PersistentDataContainer to store lore association
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        NamespacedKey loreKey = new NamespacedKey(plugin, "lore_id");
        pdc.set(loreKey, PersistentDataType.STRING, loreEntry.getId().toString());
        
        // Add visual indicators
        addLoreIndicators(entity, loreEntry);
    }
    
    private void addLoreIndicators(Entity entity, LoreEntry loreEntry) {
        // Add glowing effect for lore entities
        entity.setGlowing(true);
        
        // Custom name with lore information
        String displayName = String.format("§6⚡ %s §7(§e%s§7)", 
            loreEntry.getName(), loreEntry.getType().getDisplayName());
        entity.setCustomName(displayName);
        entity.setCustomNameVisible(true);
    }
}
```

### Entity Event Handling

```java
public class EntityLoreEvents implements Listener {
    private final RVNKLore plugin;
    private final LoreEntityManager entityManager;
    
    public EntityLoreEvents(RVNKLore plugin) {
        this.plugin = plugin;
        this.entityManager = plugin.getLoreEntityManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        EquipmentSlot hand = event.getHand();
        
        // Check if entity has associated lore
        LoreEntry loreEntry = getLoreFromEntity(entity);
        if (loreEntry != null) {
            event.setCancelled(true);
            handleLoreEntityInteraction(player, entity, loreEntry, hand);
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        
        // Protect lore entities from damage
        if (hasAssociatedLore(entity)) {
            event.setCancelled(true);
            
            if (event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
                if (damageEvent.getDamager() instanceof Player) {
                    Player player = (Player) damageEvent.getDamager();
                    LoreEntry lore = getLoreFromEntity(entity);
                    player.sendMessage("§e⚠ This " + entity.getType().toString().toLowerCase() + 
                                     " contains lore: " + lore.getName());
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        if (killer != null && hasAssociatedLore(entity)) {
            // Handle lore discovery through mob defeat
            LoreEntry loreEntry = getLoreFromEntity(entity);
            if (loreEntry != null) {
                triggerLoreDiscovery(killer, loreEntry, entity.getLocation());
                
                // Add special drops for lore mobs
                addLoreDrops(event, loreEntry);
            }
        }
    }
    
    private void handleLoreEntityInteraction(Player player, Entity entity, 
                                           LoreEntry loreEntry, EquipmentSlot hand) {
        // Check if player has already discovered this lore
        if (plugin.getLoreManager().hasPlayerDiscovered(player, loreEntry)) {
            displayLoreDetails(player, loreEntry);
        } else {
            triggerLoreDiscovery(player, loreEntry, entity.getLocation());
        }
        
        // Play interaction effects
        playInteractionEffects(entity.getLocation());
    }
    
    private void addLoreDrops(EntityDeathEvent event, LoreEntry loreEntry) {
        // Add custom lore-related items to drops
        ItemStack loreToken = plugin.getItemManager().createLoreToken(loreEntry);
        if (loreToken != null) {
            event.getDrops().add(loreToken);
        }
        
        // Increase experience for lore mob defeats
        event.setDroppedExp(event.getDroppedExp() * 2);
    }
}
```

## Villager Integration

### Custom Villager Trades

```java
public class LoreVillagerManager {
    private final RVNKLore plugin;
    private final Map<UUID, VillagerLoreData> villagerData = new HashMap<>();
    
    public void createLoreVillager(Location location, LoreEntry loreEntry) {
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        
        // Configure villager appearance
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5);
        
        // Set custom name
        villager.setCustomName("§6Lorekeeper §7- §e" + loreEntry.getName());
        villager.setCustomNameVisible(true);
        
        // Store lore association
        plugin.getLoreEntityManager().registerLoreEntity(villager, loreEntry);
        
        // Set up custom trades
        setupLoreTrades(villager, loreEntry);
        
        // Make villager immobile and protected
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
    }
    
    private void setupLoreTrades(Villager villager, LoreEntry loreEntry) {
        List<MerchantRecipe> trades = new ArrayList<>();
        
        // Trade 1: Emeralds for lore scrolls
        ItemStack loreScroll = createLoreScroll(loreEntry);
        MerchantRecipe scrollTrade = new MerchantRecipe(loreScroll, 0, 99, true, 5, 0.1f);
        scrollTrade.addIngredient(new ItemStack(Material.EMERALD, 3));
        scrollTrade.addIngredient(new ItemStack(Material.PAPER));
        trades.add(scrollTrade);
        
        // Trade 2: Lore-related items for information
        if (loreEntry.getType() == LoreType.ITEM) {
            ItemStack relatedItem = plugin.getItemManager().createLoreItem(loreEntry);
            if (relatedItem != null) {
                MerchantRecipe itemTrade = new MerchantRecipe(relatedItem, 0, 10, true, 10, 0.2f);
                itemTrade.addIngredient(new ItemStack(Material.EMERALD, 10));
                itemTrade.addIngredient(new ItemStack(Material.DIAMOND));
                trades.add(itemTrade);
            }
        }
        
        // Trade 3: Books for knowledge
        ItemStack knowledgeBook = createKnowledgeBook(loreEntry);
        MerchantRecipe bookTrade = new MerchantRecipe(knowledgeBook, 0, 5, true, 15, 0.3f);
        bookTrade.addIngredient(new ItemStack(Material.EMERALD, 5));
        bookTrade.addIngredient(new ItemStack(Material.BOOK));
        trades.add(bookTrade);
        
        villager.setRecipes(trades);
    }
    
    private ItemStack createLoreScroll(LoreEntry loreEntry) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();
        
        meta.setDisplayName("§6Lore Scroll: §e" + loreEntry.getName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Type: §f" + loreEntry.getType().getDisplayName());
        lore.add("§7Description:");
        
        // Wrap description text
        String[] words = loreEntry.getDescription().split(" ");
        StringBuilder line = new StringBuilder("§7");
        for (String word : words) {
            if (line.length() + word.length() > 35) {
                lore.add(line.toString());
                line = new StringBuilder("§7" + word + " ");
            } else {
                line.append(word).append(" ");
            }
        }
        if (line.length() > 2) {
            lore.add(line.toString());
        }
        
        lore.add("");
        lore.add("§8Right-click to read");
        
        meta.setLore(lore);
        scroll.setItemMeta(meta);
        
        // Add NBT data for functionality
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey loreKey = new NamespacedKey(plugin, "lore_id");
        pdc.set(loreKey, PersistentDataType.STRING, loreEntry.getId().toString());
        
        return scroll;
    }
}
```

### Villager Event Handling

```java
@EventHandler
public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
    Villager villager = event.getEntity();
    
    // Prevent lore villagers from changing careers
    if (hasAssociatedLore(villager)) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
    Villager villager = event.getEntity();
    
    // Prevent lore villagers from acquiring new trades
    if (hasAssociatedLore(villager)) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onVillagerReplenishTrade(VillagerReplenishTradeEvent event) {
    Villager villager = event.getEntity();
    
    // Allow lore villagers to replenish trades normally
    if (hasAssociatedLore(villager)) {
        LoreEntry loreEntry = getLoreFromEntity(villager);
        logger.info("Lore villager " + loreEntry.getName() + " replenishing trades");
    }
}
```

## Armor Stand Displays

### Interactive Lore Displays

```java
public class ArmorStandLoreDisplay {
    private final RVNKLore plugin;
    
    public void createLoreDisplay(Location location, LoreEntry loreEntry) {
        ArmorStand armorStand = (ArmorStand) location.getWorld()
            .spawnEntity(location, EntityType.ARMOR_STAND);
        
        // Configure armor stand properties
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        armorStand.setPersistent(true);
        armorStand.setInvulnerable(true);
        
        // Set display name
        armorStand.setCustomName("§6⚡ " + loreEntry.getName());
        armorStand.setCustomNameVisible(true);
        
        // Add lore-specific equipment
        equipArmorStandForLore(armorStand, loreEntry);
        
        // Register with lore system
        plugin.getLoreEntityManager().registerLoreEntity(armorStand, loreEntry);
        
        // Add particle effects
        scheduleParticleEffects(armorStand);
    }
    
    private void equipArmorStandForLore(ArmorStand armorStand, LoreEntry loreEntry) {
        EntityEquipment equipment = armorStand.getEquipment();
        
        switch (loreEntry.getType()) {
            case WEAPON:
                ItemStack weapon = plugin.getItemManager().createLoreItem(loreEntry);
                if (weapon != null) {
                    equipment.setItemInMainHand(weapon);
                }
                break;
                
            case ARMOR:
                ItemStack armor = plugin.getItemManager().createLoreItem(loreEntry);
                if (armor != null) {
                    Material type = armor.getType();
                    if (type.toString().contains("HELMET")) {
                        equipment.setHelmet(armor);
                    } else if (type.toString().contains("CHESTPLATE")) {
                        equipment.setChestplate(armor);
                    } else if (type.toString().contains("LEGGINGS")) {
                        equipment.setLeggings(armor);
                    } else if (type.toString().contains("BOOTS")) {
                        equipment.setBoots(armor);
                    }
                }
                break;
                
            case CHARACTER:
                // Set up character representation
                equipment.setHelmet(new ItemStack(Material.PLAYER_HEAD));
                equipment.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                break;
                
            default:
                // Generic lore display
                equipment.setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK));
                break;
        }
        
        // Prevent equipment from dropping
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }
    
    private void scheduleParticleEffects(ArmorStand armorStand) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (armorStand.isValid()) {
                Location loc = armorStand.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 5, 0.3, 0.3, 0.3, 0.1);
            }
        }, 0L, 40L); // Every 2 seconds
    }
}
```

## Animal and Mob Behavior

### Lore-Influenced Mob Spawning

```java
public class LoreMobSpawner {
    private final RVNKLore plugin;
    private final Map<Location, LoreMobSpawnData> spawnPoints = new HashMap<>();
    
    public void registerLoreSpawnPoint(Location location, LoreEntry loreEntry, 
                                     EntityType mobType, SpawnConditions conditions) {
        LoreMobSpawnData spawnData = new LoreMobSpawnData(loreEntry, mobType, conditions);
        spawnPoints.put(location, spawnData);
        
        // Schedule spawning checks
        scheduleSpawnChecks(location, spawnData);
    }
    
    private void scheduleSpawnChecks(Location location, LoreMobSpawnData spawnData) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            checkAndSpawnLoreMob(location, spawnData);
        }, 0L, spawnData.getConditions().getCheckInterval());
    }
    
    private void checkAndSpawnLoreMob(Location location, LoreMobSpawnData spawnData) {
        if (!spawnData.getConditions().shouldSpawn(location)) {
            return;
        }
        
        // Check if mob already exists nearby
        if (hasLoreMobNearby(location, spawnData.getLoreEntry(), 32.0)) {
            return;
        }
        
        // Spawn the lore mob
        LivingEntity mob = (LivingEntity) location.getWorld()
            .spawnEntity(location, spawnData.getMobType());
        
        configureLoreMob(mob, spawnData.getLoreEntry());
    }
    
    private void configureLoreMob(LivingEntity mob, LoreEntry loreEntry) {
        // Set custom name
        mob.setCustomName("§6" + loreEntry.getName() + "'s Guardian");
        mob.setCustomNameVisible(true);
        
        // Register with lore system
        plugin.getLoreEntityManager().registerLoreEntity(mob, loreEntry);
        
        // Enhance mob properties
        AttributeInstance health = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * 1.5);
            mob.setHealth(health.getBaseValue());
        }
        
        // Add special equipment
        addLoreMobEquipment(mob, loreEntry);
        
        // Add special effects
        mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
    }
    
    private void addLoreMobEquipment(LivingEntity mob, LoreEntry loreEntry) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return;
        
        // Add lore-themed equipment based on entry type
        switch (loreEntry.getType()) {
            case WEAPON:
                ItemStack weapon = plugin.getItemManager().createLoreItem(loreEntry);
                if (weapon != null) {
                    equipment.setItemInMainHand(weapon);
                    equipment.setItemInMainHandDropChance(0.1f);
                }
                break;
                
            case CHARACTER:
                // Give mob character-themed equipment
                equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                break;
        }
    }
    
    private boolean hasLoreMobNearby(Location center, LoreEntry loreEntry, double radius) {
        return center.getWorld().getNearbyEntities(center, radius, radius, radius)
            .stream()
            .anyMatch(entity -> {
                LoreEntry entityLore = plugin.getLoreEntityManager().getLoreFromEntity(entity);
                return entityLore != null && entityLore.getId().equals(loreEntry.getId());
            });
    }
}

public class SpawnConditions {
    private final int minPlayers;
    private final int maxPlayers;
    private final long checkInterval;
    private final Set<Material> requiredBlocks;
    private final TimeRange timeRange;
    
    public boolean shouldSpawn(Location location) {
        // Check player count in area
        int nearbyPlayers = (int) location.getWorld()
            .getNearbyEntities(location, 50, 50, 50)
            .stream()
            .filter(entity -> entity instanceof Player)
            .count();
        
        if (nearbyPlayers < minPlayers || nearbyPlayers > maxPlayers) {
            return false;
        }
        
        // Check time conditions
        long worldTime = location.getWorld().getTime();
        if (!timeRange.contains(worldTime)) {
            return false;
        }
        
        // Check required blocks nearby
        if (!requiredBlocks.isEmpty()) {
            boolean hasRequiredBlock = false;
            for (int x = -3; x <= 3; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Block block = location.clone().add(x, y, z).getBlock();
                        if (requiredBlocks.contains(block.getType())) {
                            hasRequiredBlock = true;
                            break;
                        }
                    }
                }
            }
            if (!hasRequiredBlock) {
                return false;
            }
        }
        
        return true;
    }
    
    // Getters and constructor...
}
```

## Entity Metadata and Data Storage

### Persistent Data Management

```java
public class EntityLoreDataManager {
    private final RVNKLore plugin;
    private final NamespacedKey LORE_ID_KEY;
    private final NamespacedKey DISCOVERY_COUNT_KEY;
    private final NamespacedKey LAST_INTERACTION_KEY;
    
    public EntityLoreDataManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.LORE_ID_KEY = new NamespacedKey(plugin, "lore_id");
        this.DISCOVERY_COUNT_KEY = new NamespacedKey(plugin, "discovery_count");
        this.LAST_INTERACTION_KEY = new NamespacedKey(plugin, "last_interaction");
    }
    
    public void setEntityLoreData(Entity entity, LoreEntry loreEntry) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        
        pdc.set(LORE_ID_KEY, PersistentDataType.STRING, loreEntry.getId().toString());
        pdc.set(DISCOVERY_COUNT_KEY, PersistentDataType.INTEGER, 0);
        pdc.set(LAST_INTERACTION_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    }
    
    public LoreEntry getEntityLoreData(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        
        if (pdc.has(LORE_ID_KEY, PersistentDataType.STRING)) {
            String loreIdStr = pdc.get(LORE_ID_KEY, PersistentDataType.STRING);
            try {
                UUID loreId = UUID.fromString(loreIdStr);
                return plugin.getLoreManager().getLoreById(loreId).orElse(null);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid lore ID in entity data: " + loreIdStr);
            }
        }
        
        return null;
    }
    
    public void incrementDiscoveryCount(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        
        int currentCount = pdc.getOrDefault(DISCOVERY_COUNT_KEY, PersistentDataType.INTEGER, 0);
        pdc.set(DISCOVERY_COUNT_KEY, PersistentDataType.INTEGER, currentCount + 1);
        pdc.set(LAST_INTERACTION_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    }
    
    public int getDiscoveryCount(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.getOrDefault(DISCOVERY_COUNT_KEY, PersistentDataType.INTEGER, 0);
    }
    
    public long getLastInteraction(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.getOrDefault(LAST_INTERACTION_KEY, PersistentDataType.LONG, 0L);
    }
    
    public void cleanupOldEntityData() {
        // Clean up data from entities that no longer exist
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (hasLoreData(entity)) {
                    long lastInteraction = getLastInteraction(entity);
                    long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
                    
                    if (lastInteraction < weekAgo && getDiscoveryCount(entity) == 0) {
                        // Remove unused lore entities
                        clearEntityLoreData(entity);
                        entity.remove();
                    }
                }
            }
        }
    }
    
    private boolean hasLoreData(Entity entity) {
        return entity.getPersistentDataContainer().has(LORE_ID_KEY, PersistentDataType.STRING);
    }
    
    private void clearEntityLoreData(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.remove(LORE_ID_KEY);
        pdc.remove(DISCOVERY_COUNT_KEY);
        pdc.remove(LAST_INTERACTION_KEY);
    }
}
```

## Advanced Entity Interactions

### Multi-Entity Lore Sequences

```java
public class LoreSequenceManager {
    private final RVNKLore plugin;
    private final Map<UUID, LoreSequence> activeSequences = new HashMap<>();
    
    public void startLoreSequence(Player player, List<Entity> entities, LoreEntry mainLore) {
        UUID playerId = player.getUniqueId();
        
        // Create sequence
        LoreSequence sequence = new LoreSequence(mainLore, entities, player);
        activeSequences.put(playerId, sequence);
        
        // Start the sequence
        sequence.start();
        
        player.sendMessage("§6⚙ Lore sequence started: " + mainLore.getName());
        player.sendMessage("§7Interact with the highlighted entities in order...");
    }
    
    public boolean handleSequenceInteraction(Player player, Entity entity) {
        UUID playerId = player.getUniqueId();
        LoreSequence sequence = activeSequences.get(playerId);
        
        if (sequence == null) return false;
        
        boolean result = sequence.interact(entity);
        
        if (sequence.isComplete()) {
            completeSequence(player, sequence);
            activeSequences.remove(playerId);
        } else if (sequence.isFailed()) {
            failSequence(player, sequence);
            activeSequences.remove(playerId);
        }
        
        return result;
    }
    
    private void completeSequence(Player player, LoreSequence sequence) {
        LoreEntry mainLore = sequence.getMainLore();
        
        // Award discovery
        plugin.getLoreManager().markPlayerDiscovery(player, mainLore);
        
        // Special rewards for completing sequences
        awardSequenceRewards(player, sequence);
        
        player.sendMessage("§a✓ Lore sequence completed: " + mainLore.getName());
        player.sendMessage("§7You have unlocked the full story!");
    }
    
    private void awardSequenceRewards(Player player, LoreSequence sequence) {
        // Extra experience
        player.giveExp(100);
        
        // Special lore item
        ItemStack reward = plugin.getItemManager().createSequenceReward(sequence.getMainLore());
        if (reward != null) {
            player.getInventory().addItem(reward);
        }
        
        // Achievement progress
        plugin.getAchievementManager().progressAchievement(player, "lore_sequences", 1);
    }
}

public class LoreSequence {
    private final LoreEntry mainLore;
    private final List<Entity> entities;
    private final Player player;
    private int currentStep = 0;
    private boolean complete = false;
    private boolean failed = false;
    private long startTime;
    
    public void start() {
        startTime = System.currentTimeMillis();
        highlightNextEntity();
    }
    
    public boolean interact(Entity entity) {
        if (complete || failed) return false;
        
        if (currentStep < entities.size() && entities.get(currentStep).equals(entity)) {
            currentStep++;
            
            if (currentStep >= entities.size()) {
                complete = true;
            } else {
                highlightNextEntity();
                player.sendMessage("§a✓ Step " + currentStep + " completed");
            }
            
            return true;
        } else {
            // Wrong entity - fail the sequence
            failed = true;
            removeAllHighlights();
            return false;
        }
    }
    
    private void highlightNextEntity() {
        if (currentStep < entities.size()) {
            Entity nextEntity = entities.get(currentStep);
            nextEntity.setGlowing(true);
            
            // Send title to player
            player.sendTitle("§6Step " + (currentStep + 1), 
                           "§7Interact with the highlighted entity", 
                           10, 70, 20);
        }
    }
    
    private void removeAllHighlights() {
        for (Entity entity : entities) {
            entity.setGlowing(false);
        }
    }
    
    // Getters...
}
```

This Entity & Mob API reference provides comprehensive coverage of entity management, interaction systems, and advanced features for creating immersive lore experiences in RVNKLore. The examples demonstrate practical implementations that leverage the full capabilities of modern Minecraft server APIs.
