# Minecraft Java Edition 1.21.x API Reference

This document covers the latest Minecraft Java Edition features and changes relevant to RVNKLore development.

## Current Version: 1.21.4 "The Garden Awakens"

### Release Summary
- **1.21**: "Tricky Trials" - Major update with Trial Chambers, new mobs, and items
- **1.21.4**: "The Garden Awakens" - Creaking mob, Pale Garden biome, new wood type

## New Blocks (1.21+)

### Trial Chambers & Combat (1.21)
```java
// Trial Spawner blocks - unique spawning mechanics
Material.TRIAL_SPAWNER
Material.VAULT

// New copper variants
Material.COPPER_BULB
Material.EXPOSED_COPPER_BULB
Material.WEATHERED_COPPER_BULB
Material.OXIDIZED_COPPER_BULB

// Copper doors and trapdoors
Material.COPPER_DOOR
Material.EXPOSED_COPPER_DOOR
Material.WEATHERED_COPPER_DOOR
Material.OXIDIZED_COPPER_DOOR

Material.COPPER_TRAPDOOR
Material.EXPOSED_COPPER_TRAPDOOR
Material.WEATHERED_COPPER_TRAPDOOR
Material.OXIDIZED_COPPER_TRAPDOOR

// Copper grates
Material.COPPER_GRATE
Material.EXPOSED_COPPER_GRATE
Material.WEATHERED_COPPER_GRATE
Material.OXIDIZED_COPPER_GRATE

// Heavy Core - crafting material
Material.HEAVY_CORE

// Tuff variants
Material.TUFF_BRICKS
Material.TUFF_BRICK_STAIRS
Material.TUFF_BRICK_SLAB
Material.TUFF_BRICK_WALL
Material.CHISELED_TUFF_BRICKS
Material.POLISHED_TUFF
Material.POLISHED_TUFF_STAIRS
Material.POLISHED_TUFF_SLAB
Material.POLISHED_TUFF_WALL

// Automatic crafter
Material.CRAFTER
```

### Pale Garden Biome (1.21.4)
```java
// Creaking Heart - core block for Creaking mobs
Material.CREAKING_HEART

// Pale Oak wood set
Material.PALE_OAK_LOG
Material.PALE_OAK_WOOD
Material.STRIPPED_PALE_OAK_LOG
Material.STRIPPED_PALE_OAK_WOOD
Material.PALE_OAK_PLANKS
Material.PALE_OAK_STAIRS
Material.PALE_OAK_SLAB
Material.PALE_OAK_FENCE
Material.PALE_OAK_FENCE_GATE
Material.PALE_OAK_DOOR
Material.PALE_OAK_TRAPDOOR
Material.PALE_OAK_PRESSURE_PLATE
Material.PALE_OAK_BUTTON
Material.PALE_OAK_SIGN
Material.PALE_OAK_WALL_SIGN
Material.PALE_OAK_HANGING_SIGN
Material.PALE_OAK_WALL_HANGING_SIGN

// Pale oak leaves
Material.PALE_OAK_LEAVES

// Eyeblossom flowers
Material.EYEBLOSSOM
Material.POTTED_EYEBLOSSOM

// Resin-based blocks
Material.RESIN_BRICK
Material.RESIN_BRICK_STAIRS
Material.RESIN_BRICK_SLAB
Material.RESIN_BRICK_WALL
Material.CHISELED_RESIN_BRICKS
```

## New Items (1.21+)

### Weapons & Combat (1.21)
```java
// Mace - new weapon type
Material.MACE

// Wind Charge - projectile item
Material.WIND_CHARGE

// Breeze Rod - crafting material
Material.BREEZE_ROD
```

### Pottery & Decoration (1.21)
```java
// New pottery sherds
Material.FLOW_POTTERY_SHERD
Material.GUSTER_POTTERY_SHERD
Material.SCRAPE_POTTERY_SHERD

// Decorated pots with these sherds
Material.DECORATED_POT

// New armor trims
Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE
Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE

// Music discs
Material.MUSIC_DISC_CREATOR
Material.MUSIC_DISC_CREATOR_MUSIC_BOX
Material.MUSIC_DISC_PRECIPICE
```

### Pale Garden Items (1.21.4)
```java
// Resin items
Material.RESIN_CLUMP

// Pale oak items (see wood set above)
Material.PALE_OAK_BOAT
Material.PALE_OAK_CHEST_BOAT
```

## New Mobs & Entities

### Breeze (1.21)
```java
EntityType.BREEZE
```
- **Type**: Hostile mob
- **Behavior**: Wind-based attacks, jumps to avoid attacks
- **Drops**: Breeze Rod
- **Spawning**: Trial Chambers only
- **Special**: Creates wind charges, immune to projectiles

### Bogged (1.21)
```java
EntityType.BOGGED
```
- **Type**: Variant of skeleton
- **Behavior**: Shoots poisonous arrows
- **Spawning**: Swamps and mangrove swamps
- **Special**: Arrows inflict poison effect

### Creaking (1.21.4)
```java
EntityType.CREAKING
```
- **Type**: Unique mob connected to Creaking Heart
- **Behavior**: Only moves when not observed, invulnerable unless heart is broken
- **Spawning**: Pale Garden biome at night
- **Special**: Connected to Creaking Heart block, plays unique sound effects

## New Enchantments (1.21)

### Mace-Exclusive Enchantments
```java
Enchantment.DENSITY    // Increases damage based on fall distance
Enchantment.BREACH     // Reduces effectiveness of armor
Enchantment.WIND_BURST // Launches entities upward on critical hits
```

## New Effects (1.21)

```java
PotionEffectType.INFESTED    // Spawns silverfish when damaged
PotionEffectType.OOZING      // Spawns slimes when killed
PotionEffectType.WEAVING     // Spawns cobwebs when damaged
PotionEffectType.WIND_CHARGED // Launches wind burst when damaged
```

## New Structures

### Trial Chambers (1.21)
- **Generation**: Underground structures in specific biomes
- **Features**: Trial Spawners, Vaults, unique loot
- **Mechanics**: Adaptive spawning based on nearby players

### Pale Garden (1.21.4)
- **Type**: Biome variant
- **Features**: Pale Oak trees, Eyeblossom flowers, Creaking spawning
- **Generation**: Appears as Dark Forest variant

## Biomes

### New/Updated Biomes (1.21.4)
```java
Biome.PALE_GARDEN  // New biome with unique characteristics
```

## Data Components & Technical Changes

### Item Data Components (1.21.4)
The item system has been significantly updated with data components:

```java
// Example: Working with custom model data
ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
ItemMeta meta = item.getItemMeta();
if (meta != null) {
    meta.setCustomModelData(12345);
    item.setItemMeta(meta);
}
```

### Rendering System Updates
- Model rendering has been optimized
- Better support for custom resource packs
- Improved performance for complex models

## API Implications for RVNKLore

### New Lore Opportunities
1. **Trial Chambers**: Create lore entries for discovered chambers
2. **Creaking Hearts**: Track and document found hearts
3. **Pale Garden**: Generate location lore for rare biome discoveries
4. **New Mobs**: Automatic lore generation for Breeze/Creaking encounters

### Item System Enhancements
1. **Mace Weapons**: Support for new weapon type in item generation
2. **Custom Model Data**: Better resource pack integration
3. **New Materials**: Extended material support for all new blocks/items

### Enhanced Features
```java
// Example: Detecting new biomes for automatic lore generation
public void onPlayerEnterBiome(Player player, Biome biome) {
    if (biome == Biome.PALE_GARDEN) {
        // Generate automatic lore entry for rare biome discovery
        generateLocationLore(player, biome, player.getLocation());
    }
}

// Example: Working with new enchantments
public void applyMaceEnchantments(ItemStack mace) {
    if (mace.getType() == Material.MACE) {
        mace.addEnchantment(Enchantment.DENSITY, 3);
        mace.addEnchantment(Enchantment.BREACH, 2);
    }
}
```

## Compatibility Notes

### Version Support
- **Minimum**: Minecraft 1.17+ (as specified in RVNKLore)
- **Recommended**: 1.21+ for full feature support
- **Latest**: 1.21.4 for newest content

### API Stability
- Block/Item materials are stable across patch versions
- Entity types require version checks for backwards compatibility
- Enchantments need version detection for availability

### Resource Pack Considerations
- New blocks require updated textures
- Custom model data system is more robust in 1.21+
- Sound effects for new mobs need resource pack support

## Performance Considerations

### New Features Impact
1. **Trial Spawners**: Complex spawning logic may impact performance
2. **Creaking Mechanics**: Line-of-sight calculations for behavior
3. **Pale Garden**: Additional biome generation overhead

### Optimization Tips
1. Cache biome checks for frequent operations
2. Limit Trial Chamber scanning frequency
3. Use efficient storage for new item types
4. Batch entity type checks for new mobs

## Migration Guide

### Updating from Pre-1.21
1. Add new material constants to item databases
2. Update entity type handling for new mobs
3. Extend enchantment support for mace enchantments
4. Add biome recognition for Pale Garden

### Code Example: Version-Safe Implementation
```java
public class VersionSafeFeatures {
    private static final boolean HAS_MACE;
    private static final boolean HAS_CREAKING;
    
    static {
        HAS_MACE = Arrays.stream(Material.values())
            .anyMatch(m -> m.name().equals("MACE"));
        HAS_CREAKING = Arrays.stream(EntityType.values())
            .anyMatch(e -> e.name().equals("CREAKING"));
    }
    
    public static boolean supportsMace() {
        return HAS_MACE;
    }
    
    public static boolean supportsCreaking() {
        return HAS_CREAKING;
    }
}
```
