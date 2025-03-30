package org.fourz.RVNKLore.handler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Utility class to track and debug handler events
 */
public class HandlerEventLogger implements Listener {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<Class<? extends Event>, Integer> eventCounts = new ConcurrentHashMap<>();
    private boolean enabled = false;
    
    // Events to monitor
    private static final Class<?>[] MONITORED_EVENTS = {
        PlayerJoinEvent.class,
        PlayerDeathEvent.class,
        EnchantItemEvent.class
    };
    
    public HandlerEventLogger(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "HandlerEventLogger", Level.FINE);
    }
    
    /**
     * Start logging handler events
     */
    public void enable() {
        if (enabled) return;
        
        debug.debug("Enabling handler event logging");
        registerEventListeners();
        enabled = true;
    }
    
    /**
     * Stop logging handler events
     */
    public void disable() {
        if (!enabled) return;
        
        debug.debug("Disabling handler event logging");
        enabled = false;
    }
    
    /**
     * Register listeners for all relevant events
     */
    private void registerEventListeners() {
        for (Class<?> eventClass : MONITORED_EVENTS) {
            if (Event.class.isAssignableFrom(eventClass)) {
                registerEventListener((Class<? extends Event>) eventClass);
            }
        }
    }
    
    /**
     * Register a listener for a specific event class
     */
    private <T extends Event> void registerEventListener(Class<T> eventClass) {
        EventExecutor executor = (listener, event) -> {
            if (eventClass.isInstance(event) && enabled) {
                logEvent(event);
            }
        };
        
        Bukkit.getPluginManager().registerEvent(
            eventClass,
            this,
            EventPriority.MONITOR,
            executor,
            plugin,
            false
        );
        
        debug.debug("Registered event logger for: " + eventClass.getSimpleName());
    }
    
    /**
     * Log an event occurrence
     */
    private void logEvent(Event event) {
        Class<? extends Event> eventClass = event.getClass();
        eventCounts.compute(eventClass, (k, v) -> (v == null) ? 1 : v + 1);
        
        // Only log detailed info for debug level
        if (debug.isDebugLevel(Level.FINE)) {
            logDetailedEventInfo(event);
        }
    }
    
    /**
     * Log detailed information for specific event types
     */
    private void logDetailedEventInfo(Event event) {
        if (event instanceof PlayerJoinEvent) {
            PlayerJoinEvent e = (PlayerJoinEvent) event;
            debug.debug("PlayerJoinEvent: " + e.getPlayer().getName());
        } else if (event instanceof PlayerDeathEvent) {
            PlayerDeathEvent e = (PlayerDeathEvent) event;
            debug.debug("PlayerDeathEvent: " + e.getEntity().getName() + 
                      " - " + e.getDeathMessage());
        } else if (event instanceof EnchantItemEvent) {
            EnchantItemEvent e = (EnchantItemEvent) event;
            debug.debug("EnchantItemEvent: " + e.getEnchanter().getName() + 
                      " enchanted " + e.getItem().getType());
        } else {
            debug.debug("Event: " + event.getClass().getSimpleName());
        }
    }
    
    /**
     * Send event statistics to a player
     */
    public void sendStatisticsToPlayer(Player player) {
        player.sendMessage("§6§lHandler Event Statistics:");
        eventCounts.forEach((eventClass, count) -> 
            player.sendMessage("§e  " + eventClass.getSimpleName() + "§7: " + count + " events")
        );
    }
}
