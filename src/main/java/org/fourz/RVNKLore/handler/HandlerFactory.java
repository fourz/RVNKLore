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
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating and managing lore handlers
 * Combines functionality from LoreHandlerManager to prevent duplicate initialization
 */
public class HandlerFactory {
    private final RVNKLore plugin;
    private final Debug debug;
    // Cache handlers to avoid creating new instances repeatedly - use EnumMap for better performance
    private final Map<LoreType, LoreHandler> handlerCache = new EnumMap<>(LoreType.class);
    // Map of type names to handler classes for dynamic instantiation
    private final Map<String, Class<? extends LoreHandler>> handlerClasses = new HashMap<>();
    // Track registered event listeners
    private final Set<LoreHandler> registeredListeners = new HashSet<>();
    // Track initialization state
    private boolean initialized = false;
    
    public HandlerFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "HandlerFactory", Level.FINE);
    }
    
    /**
     * Initialize the factory and register default handlers
     * This method is idempotent - safe to call multiple times
     */
    public void initialize() {
        if (initialized) {
            debug.debug("HandlerFactory already initialized, skipping");
            return;
        }
        
        try {
            debug.debug("Initializing HandlerFactory");
            registerDefaultHandlers();
            initializeDefaultHandlers();
            initialized = true;
        } catch (Exception e) {
            debug.error("Error initializing HandlerFactory", e);
        }
    }
    
    /**
     * Register the default handler implementations
     */
    private void registerDefaultHandlers() {
        if (!handlerClasses.isEmpty()) {
            debug.debug("Handler classes already registered, skipping registration");
            return;
        }
        
        try {
            // Core handlers - only register the ones we have actual implementations for
            handlerClasses.put("GENERIC", DefaultLoreHandler.class);
            handlerClasses.put("PLAYER", PlayerLoreHandler.class);
            handlerClasses.put("CITY", CityLoreHandler.class);
            handlerClasses.put("LANDMARK", LandmarkLoreHandler.class);
            handlerClasses.put("PATH", PathLoreHandler.class);
            handlerClasses.put("FACTION", FactionLoreHandler.class);
            handlerClasses.put("ITEM", ItemLoreHandler.class);
            
            // Replace individual head handlers with the unified CommonHeadHandler
            handlerClasses.put("HEAD", CommonHeadHandler.class);
            
            // Event-specific handlers
            handlerClasses.put("PLAYER_JOIN", PlayerJoinLoreHandler.class);
            handlerClasses.put("PLAYER_DEATH", PlayerDeathLoreHandler.class);
            handlerClasses.put("ENCHANTED_ITEM", EnchantedItemLoreHandler.class);
            
            // Check for missing handlers but don't log warnings yet - will use default
            for (LoreType type : LoreType.values()) {
                if (!handlerClasses.containsKey(type.name())) {
                    handlerClasses.put(type.name(), DefaultLoreHandler.class);
                }
            }
            
            debug.info("Registered " + handlerClasses.size() + " handler classes");
        } catch (Exception e) {
            debug.error("Failed to register handlers", e);
        }
    }
    
    /**
     * Initialize only the most commonly used handlers to avoid excessive initialization
     */
    private void initializeDefaultHandlers() {
        debug.debug("Pre-initializing core handlers");
        // Only initialize the most commonly used handler types to reduce startup overhead
        LoreType[] coreTypes = {
            LoreType.GENERIC, LoreType.PLAYER, LoreType.CITY, 
            LoreType.LANDMARK, LoreType.FACTION
        };
        
        for (LoreType type : coreTypes) {
            getHandler(type);
        }
    }
    
    /**
     * Get a handler for the specified lore type
     * 
     * @param type The lore type
     * @return The appropriate handler for the type
     */
    public LoreHandler getHandler(LoreType type) {
        if (!initialized) {
            initialize();
        }
        
        try {
            return handlerCache.computeIfAbsent(type, t -> {
                try {
                    debug.debug("Creating handler for type: " + t);
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
     */
    private LoreHandler createHandler(LoreType type) {
        Class<? extends LoreHandler> handlerClass = handlerClasses.get(type.name());
        
        if (handlerClass == null) {
            debug.warning("No handler class registered for type: " + type + ", using default");
            return new DefaultLoreHandler(plugin);
        }
        
        try {
            LoreHandler handler = handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
            try {
                handler.initialize();
            } catch (Exception e) {
                debug.error("Handler initialization failed for type: " + type, e);
                // Continue with the handler even if initialization failed
            }
            debug.debug("Created handler for lore type: " + type + " using " + handlerClass.getSimpleName());
            return handler;
        } catch (Exception e) {
            debug.error("Failed to create handler for type " + type, e);
            return new DefaultLoreHandler(plugin);
        }
    }
    
    /**
     * Register a handler as an event listener only if it hasn't been registered already
     */
    private void registerEventListener(LoreHandler handler) {
        if (registeredListeners.contains(handler)) {
            return;
        }
        
        try {
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(handler, plugin);
            registeredListeners.add(handler);
            debug.debug("Registered event listener for handler: " + handler.getHandlerType());
        } catch (Exception e) {
            debug.error("Failed to register event listener for handler", e);
        }
    }
    
    /**
     * Unregister all handlers from event handling
     */
    public void unregisterAllHandlers() {
        for (LoreHandler handler : registeredListeners) {
            HandlerList.unregisterAll(handler);
        }
        registeredListeners.clear();
        handlerCache.clear();
        initialized = false;
        debug.debug("Unregistered all handler event listeners");
    }
    
    /**
     * Get all registered handlers
     * 
     * @return Map of lore types to their handlers
     */
    public Map<LoreType, LoreHandler> getAllHandlers() {
        return new EnumMap<>(handlerCache);
    }
    
    /**
     * Reload all handlers
     */
    public void reloadHandlers() {
        debug.debug("Reloading lore handlers");
        unregisterAllHandlers();
        initialize();
    }
}
