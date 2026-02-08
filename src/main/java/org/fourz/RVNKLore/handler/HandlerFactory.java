package org.fourz.RVNKLore.handler;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.event.AnvilArtifactLoreHandler;
import org.fourz.RVNKLore.handler.event.ArmorStandLoreHandler;
import org.fourz.RVNKLore.handler.event.BossKillLoreHandler;
import org.fourz.RVNKLore.handler.event.LecternBookLoreHandler;
import org.fourz.RVNKLore.handler.event.PlayerDeathLoreHandler;
import org.fourz.RVNKLore.handler.PlayerLoreHandler;


import org.fourz.RVNKLore.handler.event.PlayerJoinLoreHandler;

import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.QuestLoreHandler;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating and managing lore handlers
 * Combines functionality from LoreHandlerManager to prevent duplicate initialization
 */
public class HandlerFactory {
    private final RVNKLore plugin;
    private final LogManager logger;
    // Cache handlers to avoid creating new instances repeatedly - use EnumMap for better performance
    private final Map<LoreType, LoreHandler> handlerCache = new EnumMap<>(LoreType.class);
    // Map of type names to handler classes for dynamic instantiation
    private final Map<String, Class<? extends LoreHandler>> handlerClasses = new HashMap<>();
    // Track registered event listeners
    private final Set<LoreHandler> registeredListeners = new HashSet<>();
    // Track initialization state
    private boolean initialized = false;
    private boolean initializing = false; // Add flag to prevent recursion

    public HandlerFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "HandlerFactory");
    }
    
    /**
     * Initialize the factory and register default handlers
     * This method is idempotent - safe to call multiple times
     */
    public void initialize() {
        // Both flags prevent reentry 
        if (initialized) {
            logger.debug("HandlerFactory already initialized, skipping");
            return;
        }
        
        if (initializing) {
            logger.debug("HandlerFactory initialization already in progress, skipping recursive call");
            return;
        }
        
        try {
            initializing = true;
            logger.debug("Initializing HandlerFactory");
            
            // First register handler classes - this doesn't create instances yet
            registerDefaultHandlers();
            
            // Then pre-create the core handler instances without involving LoreManager
            preCreateCoreHandlers();
            
            // Mark as initialized BEFORE we leave the method to prevent reentry
            initialized = true;
            logger.debug("HandlerFactory initialization complete");
        } catch (Exception e) {
            logger.error("Error initializing HandlerFactory", e);
        } finally {
            initializing = false;
        }
    }
    
    /**
     * Register the default handler implementations
     */
    private void registerDefaultHandlers() {
        if (!handlerClasses.isEmpty()) {
            logger.debug("Handler classes already registered, skipping registration");
            return;
        }
        
        try {
            // Event-specific handlers - register these first to ensure they take precedence
            handlerClasses.put("PLAYER_JOIN", PlayerJoinLoreHandler.class);
            handlerClasses.put("PLAYER_DEATH", PlayerDeathLoreHandler.class);
            handlerClasses.put("ANVIL_ARTIFACT", AnvilArtifactLoreHandler.class);
            handlerClasses.put("ARMOR_STAND", ArmorStandLoreHandler.class);
            handlerClasses.put("BOSS_KILL", BossKillLoreHandler.class);
            handlerClasses.put("LECTERN_BOOK", LecternBookLoreHandler.class);
            
            // Core handlers - only register the ones we have actual implementations for
            handlerClasses.put("GENERIC", DefaultLoreHandler.class);
            handlerClasses.put("PLAYER", PlayerLoreHandler.class);
            handlerClasses.put("CITY", CityLoreHandler.class);
            handlerClasses.put("LANDMARK", LandmarkLoreHandler.class);
            handlerClasses.put("MONUMENT", MonumentLoreHandler.class);
            handlerClasses.put("PATH", PathLoreHandler.class);
            handlerClasses.put("FACTION", FactionLoreHandler.class);
            handlerClasses.put("ITEM", ItemLoreHandler.class);
            handlerClasses.put("EVENT", EventLoreHandler.class);
            handlerClasses.put("ENCHANTED_ITEM", EnchantedItemLoreHandler.class);
            
            // Replace individual head handlers with the unified CommonHeadHandler
            handlerClasses.put("HEAD", CommonHeadHandler.class);
            
            // Sign handlers
            handlerClasses.put("SIGN_LANDMARK", org.fourz.RVNKLore.handler.sign.HandlerSignLandmark.class);
            handlerClasses.put("SIGN_MONUMENT", org.fourz.RVNKLore.handler.sign.HandlerSignMonument.class);
            
            // Check for missing handlers but don't log warnings yet - will use default
            for (LoreType type : LoreType.values()) {
                if (!handlerClasses.containsKey(type.name())) {
                    handlerClasses.put(type.name(), DefaultLoreHandler.class);
                }
            }
            
            logger.info("Registered " + handlerClasses.size() + " handler classes");
        } catch (Exception e) {
            logger.error("Failed to register handlers", e);
        }
    }
      /**
     * Pre-create essential handlers without triggering initialization chains
     */
    private void preCreateCoreHandlers() {
        logger.debug("Pre-creating core handlers");
        LoreType[] coreTypes = {
            LoreType.GENERIC, LoreType.CITY, 
            LoreType.LANDMARK, LoreType.FACTION, LoreType.EVENT
        };
        
        for (LoreType type : coreTypes) {
            if (!handlerCache.containsKey(type)) {
                try {
                    Class<? extends LoreHandler> handlerClass = handlerClasses.get(type.name());
                    if (handlerClass == null) {
                        logger.debug("No handler class for " + type + ", using default");
                        handlerClass = DefaultLoreHandler.class;
                    }

                    // Direct instantiation without initialization to avoid circular dependencies
                    LoreHandler handler = handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
                    handlerCache.put(type, handler);

                    // Register as listener but don't initialize yet
                    if (!registeredListeners.contains(handler)) {
                        PluginManager pm = plugin.getServer().getPluginManager();
                        pm.registerEvents(handler, plugin);
                        registeredListeners.add(handler);
                    }
                } catch (Exception e) {
                    logger.error("Failed to pre-create handler for " + type, e);
                }
            }
        }

        // Create event-driven handlers that need listener registration
        // These aren't tied to a LoreType but must receive Bukkit events
        String[] eventHandlerKeys = {"ANVIL_ARTIFACT", "ARMOR_STAND", "BOSS_KILL", "LECTERN_BOOK", "PLAYER_DEATH", "PLAYER_JOIN"};
        for (String key : eventHandlerKeys) {
            try {
                Class<? extends LoreHandler> handlerClass = handlerClasses.get(key);
                if (handlerClass != null) {
                    LoreHandler handler = handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
                    PluginManager pm = plugin.getServer().getPluginManager();
                    pm.registerEvents(handler, plugin);
                    registeredListeners.add(handler);
                    logger.debug("Registered event handler: " + key);
                }
            } catch (Exception e) {
                logger.error("Failed to create event handler: " + key, e);
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
        // Protection against recursion
        if (!initialized && !initializing) {
            // Only log this once
            logger.debug("Initializing HandlerFactory on demand");
            initialize();
        }
        
        // Fast path if already cached
        if (handlerCache.containsKey(type)) {
            return handlerCache.get(type);
        }
        
        // Create and cache the handler if not found
        try {
            logger.debug("Creating handler for type: " + type);
            LoreHandler handler = createHandler(type);
            handlerCache.put(type, handler);
            registerEventListener(handler);
            return handler;
        } catch (Exception e) {
            logger.error("Error creating handler for " + type, e);
            // Always return something usable
            DefaultLoreHandler defaultHandler = new DefaultLoreHandler(plugin);
            handlerCache.put(type, defaultHandler);
            return defaultHandler;
        }
    }
    
    /**
     * Create a new handler for the specified type
     */
    private LoreHandler createHandler(LoreType type) {
        Class<? extends LoreHandler> handlerClass = handlerClasses.get(type.name());
        
        if (handlerClass == null) {
            logger.warning("No handler class registered for type: " + type + ", using default");
            return new DefaultLoreHandler(plugin);
        }
        
        try {
            LoreHandler handler = handlerClass.getConstructor(RVNKLore.class).newInstance(plugin);
            try {
                handler.initialize();
            } catch (Exception e) {
                logger.error("Handler initialization failed for type: " + type, e);
                // Continue with the handler even if initialization failed
            }
            logger.debug("Created handler for lore type: " + type + " using " + handlerClass.getSimpleName());
            return handler;
        } catch (Exception e) {
            logger.error("Failed to create handler for type " + type, e);
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
            logger.debug("Registered event listener for handler: " + handler.getHandlerType());
        } catch (Exception e) {
            logger.error("Failed to register event listener for handler", e);
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
        logger.debug("Unregistered all handler event listeners");
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
        logger.debug("Reloading lore handlers");
        unregisterAllHandlers();
        initialize();
    }
}
