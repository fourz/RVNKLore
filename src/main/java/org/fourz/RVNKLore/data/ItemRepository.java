package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import java.util.concurrent.CompletableFuture;

/**
 * Repository class for database operations related to lore items.
 * Provides methods to create, read, update, and delete item records.
 * 
 * This class handles:
 * - Basic item CRUD operations for lore items
 * - Collection management
 * - Item metadata storage and retrieval
 * - JSON property serialization/deserialization
 */
public class ItemRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    /**
     * Create a new ItemRepository instance
     * 
     * @param plugin The RVNKLore plugin instance
     * @param databaseManager The new async DatabaseManager
     */
    public ItemRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemRepository");
        this.databaseManager = databaseManager;
    }

    /**
     * Get an item by ID (async).
     */
    public CompletableFuture<ItemPropertiesDTO> getItemById(int id) {
        return databaseManager.getItem(id);
    }

    /**
     * Get an item by lore entry ID (async).
     */
    public CompletableFuture<ItemPropertiesDTO> getItemByLoreEntry(int loreEntryId) {
        return databaseManager.getItemByLoreEntry(loreEntryId);
    }

    /**
     * Get items by type (async).
     */
    public CompletableFuture<java.util.List<ItemPropertiesDTO>> getItemsByType(String type) {
        return databaseManager.getItemsByType(type);
    }

    /**
     * Save an item (async).
     */
    public CompletableFuture<Integer> saveItem(ItemPropertiesDTO dto) {
        return databaseManager.saveItem(dto);
    }

    /**
     * Delete an item (async).
     */
    public CompletableFuture<Boolean> deleteItem(int id) {
        return databaseManager.deleteItem(id);
    }

    // Add other methods as needed, always delegating to databaseManager's async API
}
