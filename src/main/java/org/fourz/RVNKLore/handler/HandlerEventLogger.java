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
import java.util.logging.Level;

/**
 * Utility class to track and debug handler events
 */
public class HandlerEventLogger implements Listener {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<Class<? extends Event>, Integer> eventCounts = new HashMap<>();
    private boolean enabled = false;
    
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
        registerEventListener(PlayerJoinEvent.class);
        registerEventListener(PlayerDeathEvent.class);
        registerEventListener(EnchantItemEvent.class);
        // Add more events as needed
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
        int count = eventCounts.getOrDefault(eventClass, 0) + 1;
        eventCounts.put(eventClass, count);
        
        // Log detailed information based on event type
        if (event instanceof PlayerJoinEvent) {
            PlayerJoinEvent e = (PlayerJoinEvent) event;
            debug.debug("PlayerJoinEvent detected: " + e.getPlayer().getName());
        } else if (event instanceof PlayerDeathEvent) {
            PlayerDeathEvent e = (PlayerDeathEvent) event;
            debug.debug("PlayerDeathEvent detected: " + e.getEntity().getName() + 
                       " - Death message: " + e.getDeathMessage());
        } else if (event instanceof EnchantItemEvent) {
            EnchantItemEvent e = (EnchantItemEvent) event;
            debug.debug("EnchantItemEvent detected: " + e.getEnchanter().getName() + 
                       " enchanted " + e.getItem().getType() + 
                       " with " + e.getEnchantsToAdd().size() + " enchantments");
        } else {
            debug.debug("Event detected: " + eventClass.getSimpleName());
        }
    }
    
    /**
     * Get event statistics
     */
    public void printEventStatistics() {
        debug.debug("Handler Event Statistics:");
        for (Map.Entry<Class<? extends Event>, Integer> entry : eventCounts.entrySet()) {
            debug.debug("  " + entry.getKey().getSimpleName() + ": " + entry.getValue() + " events");
        }
    }
    
    /**
     * Reset event counters
     */
    public void resetCounters() {
        eventCounts.clear();
    }
    
    /**
     * Send event statistics to a player
     */
    public void sendStatisticsToPlayer(Player player) {
        player.sendMessage("§6§lHandler Event Statistics:");
        for (Map.Entry<Class<? extends Event>, Integer> entry : eventCounts.entrySet()) {
            player.sendMessage("§e  " + entry.getKey().getSimpleName() + "§7: " + 
                              entry.getValue() + " events");
        }
    }
}
