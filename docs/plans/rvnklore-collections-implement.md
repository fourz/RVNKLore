# CollectionManager Implementation Plan

**Status**: Draft  
**Priority**: High  
**Target**: Q3 2025  
**Last Updated**: June 5, 2025

This document outlines the implementation plan for enhancing the CollectionManager system based on the validation analysis and project roadmap requirements.

## Current State Assessment

### ✅ Completed Features
- Basic collection creation and management
- Thread-safe collection storage using `ConcurrentHashMap`
- Theme-based organization via `CollectionTheme` enum
- LogManager integration following coding standards
- Item addition/removal from collections
- Default collection initialization

### ❌ Missing Critical Features
- Database persistence for collections
- Player progress tracking system
- Collection reward management
- Event system integration
- Validation and error handling improvements

## Implementation Phases

### Phase 1: Database Integration (Week 1-2)

#### 1.1 Database Schema Updates
Create tables for collection persistence:

```sql
-- Collection storage
CREATE TABLE IF NOT EXISTS collection (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    collection_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    theme_id TEXT,
    is_active BOOLEAN DEFAULT 1,
    created_at INTEGER NOT NULL
);

-- Player progress tracking
CREATE TABLE IF NOT EXISTS player_collection_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id TEXT NOT NULL,
    collection_id TEXT NOT NULL,
    progress REAL DEFAULT 0.0,
    completed_at INTEGER,
    last_updated INTEGER NOT NULL,
    UNIQUE(player_id, collection_id)
);

-- Collection rewards
CREATE TABLE IF NOT EXISTS collection_reward (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    collection_id TEXT NOT NULL,
    reward_type TEXT NOT NULL,
    reward_data TEXT,
    is_claimed BOOLEAN DEFAULT 0
);
```

#### 1.2 Repository Layer Implementation
Enhance `ItemRepository` with collection-specific methods:

```java
/**
 * Save a collection to the database with transaction support
 * 
 * @param collection The collection to save
 * @return True if successfully saved
 */
public boolean saveCollection(ItemCollection collection) {
    String sql = "INSERT OR REPLACE INTO collection (collection_id, name, description, theme_id, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    
    try {
        return dbHelper.executeUpdate(sql, stmt -> {
            stmt.setString(1, collection.getId());
            stmt.setString(2, collection.getName());
            stmt.setString(3, collection.getDescription());
            stmt.setString(4, collection.getThemeId());
            stmt.setBoolean(5, collection.isActive());
            stmt.setLong(6, collection.getCreatedAt());
        }) > 0;
    } catch (LoreException e) {
        logger.error("Failed to save collection: " + collection.getId(), e);
        return false;
    }
}

/**
 * Load all collections from database
 * 
 * @return List of collections
 */
public List<ItemCollection> loadAllCollections() {
    String sql = "SELECT collection_id, name, description, theme_id, is_active, created_at FROM collection WHERE is_active = 1";
    
    try {
        return dbHelper.executeQuery(sql, null, rs -> {
            List<ItemCollection> collections = new ArrayList<>();
            while (rs.next()) {
                ItemCollection collection = new ItemCollection(
                    rs.getString("collection_id"),
                    rs.getString("name"), 
                    rs.getString("description")
                );
                collection.setThemeId(rs.getString("theme_id"));
                collection.setActive(rs.getBoolean("is_active"));
                collections.add(collection);
            }
            return collections;
        });
    } catch (LoreException e) {
        logger.error("Failed to load collections from database", e);
        return new ArrayList<>();
    }
}
```

#### 1.3 CollectionManager Database Integration

```java
/**
 * Initialize collections from database on startup
 */
private void loadCollectionsFromDatabase() {
    if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
        logger.warning("Database not available - using default collections only");
        return;
    }
    
    ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    List<ItemCollection> loadedCollections = repository.loadAllCollections();
    
    for (ItemCollection collection : loadedCollections) {
        collections.put(collection.getId(), collection);
        logger.info("Loaded collection from database: " + collection.getName());
    }
    
    logger.info("Loaded " + loadedCollections.size() + " collections from database");
}

/**
 * Save a collection to the database
 * 
 * @param collection The collection to persist
 * @return True if successfully saved
 */
public boolean saveCollection(ItemCollection collection) {
    if (collection == null) {
        logger.warning("Cannot save null collection");
        return false;
    }
    
    if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
        logger.warning("Database not available - collection will not be persisted");
        return false;
    }
    
    ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    boolean saved = repository.saveCollection(collection);
    
    if (saved) {
        logger.info("Successfully saved collection: " + collection.getId());
    } else {
        logger.error("Failed to save collection: " + collection.getId());
    }
    
    return saved;
}
```

### Phase 2: Player Progress Tracking (Week 3)

#### 2.1 Progress Tracking Implementation

```java
/**
 * Track player progress for a specific collection
 * 
 * @param playerId The player's UUID
 * @param collectionId The collection identifier
 * @return Progress value between 0.0 and 1.0
 */
public double getPlayerProgress(UUID playerId, String collectionId) {
    if (playerId == null || collectionId == null) {
        return 0.0;
    }
    
    ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    return repository.getPlayerCollectionProgress(playerId.toString(), collectionId);
}

/**
 * Update player progress for a collection
 * 
 * @param playerId The player's UUID
 * @param collectionId The collection identifier
 * @param progress Progress value between 0.0 and 1.0
 * @return True if successfully updated
 */
public boolean updatePlayerProgress(UUID playerId, String collectionId, double progress) {
    if (playerId == null || collectionId == null || progress < 0.0 || progress > 1.0) {
        logger.warning("Invalid parameters for progress update");
        return false;
    }
    
    ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    boolean updated = repository.updatePlayerCollectionProgress(playerId.toString(), collectionId, progress);
    
    if (updated) {
        logger.info("Updated progress for player " + playerId + " in collection " + collectionId + ": " + String.format("%.1f%%", progress * 100));
        
        // Check for completion and trigger rewards
        if (progress >= 1.0) {
            handleCollectionCompletion(playerId, collectionId);
        }
    }
    
    return updated;
}
```

#### 2.2 Collection Completion Handling

```java
/**
 * Handle collection completion events and rewards
 * 
 * @param playerId The player who completed the collection
 * @param collectionId The completed collection
 */
private void handleCollectionCompletion(UUID playerId, String collectionId) {
    ItemCollection collection = getCollection(collectionId);
    if (collection == null) {
        logger.warning("Cannot handle completion for unknown collection: " + collectionId);
        return;
    }
    
    logger.info("Player " + playerId + " completed collection: " + collection.getName());
    
    // Emit completion event for other systems to handle
    // TODO: Integrate with event system when implemented
    
    // Mark completion timestamp
    ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    repository.markCollectionCompleted(playerId.toString(), collectionId, System.currentTimeMillis());
}

/**
 * Grant collection rewards to a player
 * 
 * @param playerId The player's UUID
 * @param collectionId The collection identifier
 * @return True if rewards were successfully granted
 */
public boolean grantCollectionReward(UUID playerId, String collectionId) {
    if (getPlayerProgress(playerId, collectionId) < 1.0) {
        logger.warning("Cannot grant rewards - collection not completed by player " + playerId);
        return false;
    }
    
    ItemCollection collection = getCollection(collectionId);
    if (collection == null) {
        logger.warning("Cannot grant rewards for unknown collection: " + collectionId);
        return false;
    }
    
    // TODO: Implement reward granting logic
    // This will integrate with the reward system when implemented
    
    logger.info("Granted collection rewards to player " + playerId + " for collection: " + collectionId);
    return true;
}
```

### Phase 3: Validation and Error Handling (Week 4)

#### 3.1 Collection Validation

```java
/**
 * Validate a collection before creation or update
 * 
 * @param collection The collection to validate
 * @return True if valid, false otherwise
 */
private boolean validateCollection(ItemCollection collection) {
    if (collection == null) {
        logger.warning("Collection validation failed: null collection");
        return false;
    }
    
    if (collection.getId() == null || collection.getId().trim().isEmpty()) {
        logger.warning("Collection validation failed: missing or empty ID");
        return false;
    }
    
    if (collection.getName() == null || collection.getName().trim().isEmpty()) {
        logger.warning("Collection validation failed: missing or empty name");
        return false;
    }
    
    // Check for duplicate IDs
    if (collections.containsKey(collection.getId())) {
        logger.warning("Collection validation failed: duplicate ID - " + collection.getId());
        return false;
    }
    
    logger.debug("Collection validation passed: " + collection.getId());
    return true;
}

/**
 * Enhanced collection creation with validation
 */
@Override
public ItemCollection createCollection(String id, String name, String description) {
    ItemCollection collection = new ItemCollection(id, name, description);
    
    if (!validateCollection(collection)) {
        logger.error("Failed to create collection due to validation errors: " + id);
        return null;
    }
    
    collections.put(id, collection);
    
    // Persist to database
    saveCollection(collection);
    
    logger.info("Created and validated collection: " + name + " (" + id + ")");
    return collection;
}
```

#### 3.2 Enhanced Error Handling

```java
/**
 * Safely retrieve a collection with detailed error logging
 */
@Override
public ItemCollection getCollection(String id) {
    if (id == null || id.trim().isEmpty()) {
        logger.warning("Cannot retrieve collection: null or empty ID provided");
        return null;
    }
    
    ItemCollection collection = collections.get(id);
    if (collection == null) {
        logger.debug("Collection not found: " + id);
    }
    
    return collection;
}
```

### Phase 4: Integration and Testing (Week 5)

#### 4.1 LoreCollectionSubCommand Integration
Update the command class to use the enhanced CollectionManager:

```java
/**
 * Enhanced progress display using new tracking methods
 */
private void showProgress(Player player) {
    player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Your Collection Progress");
    player.sendMessage("");

    CollectionManager collectionManager = plugin.getItemManager().getCollectionManager();
    Map<String, ItemCollection> allCollections = collectionManager.getAllCollections();
    
    if (allCollections.isEmpty()) {
        player.sendMessage(ChatColor.YELLOW + "⚠ No collections available");
        return;
    }

    for (ItemCollection collection : allCollections.values()) {
        double progress = collectionManager.getPlayerProgress(player.getUniqueId(), collection.getId());
        double percent = progress * 100;
        
        String status = percent >= 100.0 ? ChatColor.GREEN + "✓ COMPLETE" : ChatColor.YELLOW + String.format("%.1f%%", percent);
        
        player.sendMessage(ChatColor.WHITE + collection.getName() + ": " + status);
        
        if (percent < 100.0) {
            int totalItems = collection.getItemCount();
            int ownedItems = (int) (totalItems * progress);
            player.sendMessage(ChatColor.GRAY + "   " + ownedItems + "/" + totalItems + " items collected");
        }
    }
}
```

#### 4.2 Event System Preparation
Add placeholder methods for future event integration:

```java
/**
 * Emit collection change events for other systems to listen to
 * TODO: Implement when event system is available
 * 
 * @param collection The collection that changed
 * @param changeType The type of change
 */
private void fireCollectionChangeEvent(ItemCollection collection, ChangeType changeType) {
    // Placeholder for event system integration
    logger.debug("Collection change event: " + changeType + " for " + collection.getId());
}

/**
 * Types of collection changes for event system
 */
public enum ChangeType {
    CREATED, UPDATED, DELETED, COMPLETED
}
```

## Testing Strategy

### Unit Tests
- Collection creation and validation
- Progress tracking accuracy
- Database persistence operations
- Error handling scenarios

### Integration Tests
- Command integration with enhanced CollectionManager
- Database transaction handling
- Multi-player progress tracking

### Performance Tests
- Large collection handling
- Concurrent player progress updates
- Database query optimization

## Rollout Plan

1. **Week 1-2**: Implement database integration and test with existing collections
2. **Week 3**: Add progress tracking and test with sample player data
3. **Week 4**: Implement validation and error handling improvements
4. **Week 5**: Integration testing and documentation updates
5. **Week 6**: Production deployment and monitoring

## Success Metrics

- ✅ All collections persist across server restarts
- ✅ Player progress accurately tracked and persisted
- ✅ Zero data loss during collection operations
- ✅ Performance impact < 5ms for progress updates
- ✅ Command response time < 100ms for collection operations

## Future Considerations

### Q4 2025 Integration Points
- VotingPlugin reward integration
- Event-driven collection unlocking
- Seasonal collection automation
- Advanced reward distribution system

### Extension Points
- Collection sharing between players
- Guild/team collection challenges  
- Dynamic collection generation based on server events
- Integration with external achievement systems

---

**Next Steps**: Begin Phase 1 implementation with database schema updates and repository layer enhancements.