package org.fourz.RVNKLore.handler;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.QuestLoreHandler;
import org.fourz.RVNKLore.util.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Factory for creating and managing lore handlers
 */
public class HandlerFactory {
    private final RVNKLore plugin;
    private final Debug debug;
    // Cache handlers to avoid creating new instances repeatedly
    private final Map<LoreType, LoreHandler> handlerCache = new HashMap<>();
    // Map of type names to handler classes for dynamic instantiation
    private final Map<String, Class<? extends LoreHandler>> handlerClasses = new HashMap<>();
    
    public HandlerFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "HandlerFactory", Level.FINE);
        registerDefaultHandlers();
    }
    
    /**
     * Register the default handler implementations
     */
    private void registerDefaultHandlers() {
        // Quest handler requires special registration due to package location
        handlerClasses.put("QUEST", QuestLoreHandler.class);
        
        // All unregistered types fallback to DefaultLoreHandler
        for (LoreType type : LoreType.values()) {
            if (!handlerClasses.containsKey(type.name())) {
                handlerClasses.put(type.name(), DefaultLoreHandler.class);
            }
        }
    }
    
    /**
     * Get a handler for the specified lore type
     * 
     * @param type The lore type
     * @return The appropriate handler for the type
     */
    public LoreHandler getHandler(LoreType type) {
        // Return cached handler if available to improve performance
        if (handlerCache.containsKey(type)) {
            return handlerCache.get(type);
        }
        
        // Create and cache new handler instance
        LoreHandler handler = createHandler(type);
        handlerCache.put(type, handler);
        return handler;
    }
    
    /**
     * Create a new handler for the specified type
     * 
     * @param type The lore type
     * @return A new handler instance
     */
    private LoreHandler createHandler(LoreType type) {
        debug.debug("Creating handler for lore type: " + type);
        
        try {
            Class<? extends LoreHandler> handlerClass = handlerClasses.get(type.name());
            
            // If no specific handler is registered, use the default
            if (handlerClass == null) {
                debug.debug("No specific handler found for type " + type + ", using default");
                handlerClass = DefaultLoreHandler.class;
            }
            
            // Instantiate the handler
            return handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
            
        } catch (Exception e) {
            debug.error("Failed to create handler for type " + type, e);
            // Return a default handler as fallback
            return new DefaultLoreHandler(plugin);
        }
    }
    
    /**
     * Register a custom handler for a lore type
     * 
     * @param type The lore type
     * @param handlerClass The handler class
     */
    public void registerHandler(LoreType type, Class<? extends LoreHandler> handlerClass) {
        handlerClasses.put(type.name(), handlerClass);
        // Clear from cache to ensure the new class is used
        handlerCache.remove(type);
    }
    
    /**
     * Clears the handler cache, forcing new instances to be created
     */
    public void clearCache() {
        handlerCache.clear();
    }
}
