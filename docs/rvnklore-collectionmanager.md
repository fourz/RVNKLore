# RVNKLore CollectionManager

The `CollectionManager` is responsible for managing item collections in the RVNKLore plugin. It organizes items into thematic groups, tracks player progress, and handles collection rewards.

## Responsibilities
- Define and manage item collections (`ItemCollection`)
- Track player progress and completion
- Handle collection themes, metadata, and rewards
- Integrate with the plugin's logging and item systems
- Support seasonal and event-based collections

## Key Methods
- `getCollection(String)`: Retrieve a collection by name
- `addCollection(ItemCollection)`: Register a new collection
- `getPlayerProgress(UUID, String)`: Track player progress
- `grantCollectionReward(UUID, String)`: Grant rewards for completion
- `shutdown()`: Cleanup

## Example Usage
```java
CollectionManager collectionManager = itemManager.getCollectionManager();
ItemCollection collection = collectionManager.getCollection("Winter Wonders");
```

## Design Notes
- Collections are extensible and support metadata
- Rewards are managed via `CollectionRewards`
- All actions are logged via `LogManager`
