package org.fourz.RVNKLore.handler;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages lore handler registrations and provides access to them
 */
public class LoreHandlerManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<LoreType, LoreHandler> handlers = new HashMap<>();
    private final HandlerFactory factory;
    
    public LoreHandlerManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreHandlerManager", Level.FINE);
        this.factory = new HandlerFactory(plugin);
        initializeHandlers();
    }
    
    /**
     * Initialize all handlers for supported lore types
     */
    private void initializeHandlers() {
        debug.debug("Initializing lore handlers");
        
        try {
            // Register all lore type handlers
            for (LoreType type : LoreType.values()) {
                LoreHandler handler = factory.getHandler(type);
                registerHandler(type, handler);
            }
            
            debug.debug("All lore handlers initialized successfully");
        } catch (Exception e) {
            debug.error("Error initializing lore handlers", e);
            throw new RuntimeException("Failed to initialize lore handlers", e);
        }
    }
    
    /**
     * Register a handler for a specific lore type
     * 
     * @param type The lore type
     * @param handler The handler instance
     */
    public void registerHandler(LoreType type, LoreHandler handler) {
        handlers.put(type, handler);
        
        try {
            // Register the handler as an event listener
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(handler, plugin);
            debug.debug("Registered handler for lore type: " + type);
        } catch (Exception e) {
            debug.error("Failed to register handler for type: " + type, e);
        }
    }
    
    /**
     * Get a handler for a specific lore type
     * 
     * @param type The lore type
     * @return The handler for the type
     */
    public LoreHandler getHandler(LoreType type) {
        LoreHandler handler = handlers.get(type);
        
        // If no handler exists, create and register a default one
        if (handler == null) {
            debug.debug("No handler found for type " + type + ", creating default");
            handler = new DefaultLoreHandler(plugin);
            registerHandler(type, handler);
        }
        
        return handler;
    }
    
    /**
     * Unregister all event handlers
     */
    public void unregisterAllHandlers() {
        debug.debug("Unregistering all lore handlers");
        
        for (LoreHandler handler : handlers.values()) {
            HandlerList.unregisterAll(handler);
        }
        
        handlers.clear();
    }
    
    /**
     * Reload all handlers
     */
    public void reloadHandlers() {
        debug.debug("Reloading lore handlers");
        unregisterAllHandlers();
        initializeHandlers();
    }
    
    /**
     * Get all registered handlers
     * 
     * @return Map of lore types to their handlers
     */
    public Map<LoreType, LoreHandler> getAllHandlers() {
        return new HashMap<>(handlers);
    }
}
