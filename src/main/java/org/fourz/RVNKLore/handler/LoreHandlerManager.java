package org.fourz.RVNKLore.handler;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages lore handler registrations and provides access to them
 */
public class LoreHandlerManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<LoreType, LoreHandler> handlers = new EnumMap<>(LoreType.class);
    private final HandlerFactory factory;
    
    public LoreHandlerManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreHandlerManager");
        this.factory = new HandlerFactory(plugin);
        initializeHandlers();
    }
    
    /**
     * Initialize all handlers for supported lore types
     */
    private void initializeHandlers() {
        logger.info("Initializing lore handlers");
        try {
            // Initialize only the actively used lore types
            LoreType[] activeTypes = {
                LoreType.GENERIC, LoreType.PLAYER, LoreType.CITY, 
                LoreType.LANDMARK, LoreType.FACTION, LoreType.PATH
            };
            for (LoreType type : activeTypes) {
                getHandler(type); // This will create and cache the handler
            }
            logger.info("All core lore handlers initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing lore handlers", e);
        }
    }
    
    /**
     * Get a handler for a specific lore type
     * 
     * @param type The lore type
     * @return The handler for the type
     */
    public LoreHandler getHandler(LoreType type) {
        // Use computeIfAbsent to avoid redundant handler creation
        LoreHandler handler = handlers.computeIfAbsent(type, t -> {
            LoreHandler h = factory.getHandler(t);
            logger.info("Retrieved handler for lore type: " + t);
            return h;
        });
        return handler;
    }
    
    /**
     * Unregister all event handlers
     */
    public void unregisterAllHandlers() {
        logger.info("Unregistering all lore handlers");
        factory.unregisterAllHandlers();
        handlers.clear();
    }
    
    /**
     * Reload all handlers
     */
    public void reloadHandlers() {
        logger.info("Reloading lore handlers");
        unregisterAllHandlers();
        initializeHandlers();
    }
    
    /**
     * Get all registered handlers
     * 
     * @return Map of lore types to their handlers
     */
    public Map<LoreType, LoreHandler> getAllHandlers() {
        return new EnumMap<>(handlers);
    }
}
