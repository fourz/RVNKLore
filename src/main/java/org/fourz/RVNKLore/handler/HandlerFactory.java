package org.fourz.RVNKLore.handler;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.QuestLoreHandler;
import org.fourz.RVNKLore.util.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;

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
        try {
            Map<String, Boolean> registrationStatus = new HashMap<>();
            
            // Core handlers
            handlerClasses.put("GENERIC", DefaultLoreHandler.class);
            handlerClasses.put("PLAYER", PlayerLoreHandler.class);
            handlerClasses.put("CITY", CityLoreHandler.class);
            handlerClasses.put("LANDMARK", LandmarkLoreHandler.class);
            handlerClasses.put("PATH", PathLoreHandler.class);
            handlerClasses.put("FACTION", FactionLoreHandler.class);
            
            // Event handlers
            handlerClasses.put("PLAYER_JOIN", PlayerJoinLoreHandler.class);
            handlerClasses.put("PLAYER_DEATH", PlayerDeathLoreHandler.class);
            handlerClasses.put("ENCHANTED_ITEM", EnchantedItemLoreHandler.class);
            
            // Identify missing handlers
            List<String> missingHandlers = new ArrayList<>();
            for (LoreType type : LoreType.values()) {
                if (!handlerClasses.containsKey(type.name())) {
                    debug.warning("Missing handler implementation for: " + type.name());
                    missingHandlers.add(type.name());
                    handlerClasses.put(type.name(), DefaultLoreHandler.class);
                    registrationStatus.put(type.name(), false);
                } else {
                    registrationStatus.put(type.name(), true);
                }
            }
            
            // Log handler registration summary
            debug.info("Handler registration summary:");
            registrationStatus.forEach((type, hasImpl) -> 
                debug.info(String.format("%s: %s", type, hasImpl ? "Custom Implementation" : "Default Fallback")));
            
            // Log specific warning about missing handlers
            if (!missingHandlers.isEmpty()) {
                debug.warning("Missing implementations for " + missingHandlers.size() + " handlers: " + 
                            String.join(", ", missingHandlers));
            }
            
        } catch (Exception e) {
            debug.error("Failed to register handlers", e);
            throw new RuntimeException("Handler registration failed", e);
        }
    }
    
    /**
     * Get a handler for the specified lore type
     * 
     * @param type The lore type
     * @return The appropriate handler for the type
     */
    public LoreHandler getHandler(LoreType type) {
        try {
            return handlerCache.computeIfAbsent(type, t -> {
                try {
                    LoreHandler handler = createHandler(t);
                    registerEventListener(handler);
                    return handler;
                } catch (Exception e) {
                    debug.error("Error creating handler for " + t, e);
                    return new DefaultLoreHandler(plugin);
                }
            });
        } catch (Exception e) {
            debug.error("Error getting handler for " + type, e);
            return new DefaultLoreHandler(plugin);
        }
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
            LoreHandler handler = handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
            handler.initialize();
            return handler;
            
        } catch (Exception e) {
            debug.error("Failed to create handler for type " + type, e);
            // Return a default handler as fallback
            return new DefaultLoreHandler(plugin);
        }
    }
    
    /**
     * Register a handler as an event listener
     * 
     * @param handler The handler to register
     */
    private void registerEventListener(LoreHandler handler) {
        try {
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(handler, plugin);
            debug.debug("Registered event listener for handler: " + handler.getHandlerType());
        } catch (Exception e) {
            debug.error("Failed to register event listener for handler", e);
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
        debug.debug("Registered custom handler for type: " + type);
    }
    
    /**
     * Unregister all handlers from event handling
     */
    public void unregisterAllHandlers() {
        for (LoreHandler handler : handlerCache.values()) {
            HandlerList.unregisterAll(handler);
        }
        handlerCache.clear();
        debug.debug("Unregistered all handler event listeners");
    }
    
    /**
     * Initialize all handlers for all lore types
     */
    public void initializeAllHandlers() {
        for (LoreType type : LoreType.values()) {
            try {
                getHandler(type); // This will create, cache, and register the handler
            } catch (Exception e) {
                debug.error("Failed to initialize handler for type: " + type, e);
            }
        }
        debug.debug("Initialized all handlers");
    }
}
