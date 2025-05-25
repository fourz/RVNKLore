package org.fourz.RVNKLore.handler.event;

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
import org.fourz.RVNKLore.debug.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to track and debug handler events
 */
public class HandlerEventLogger implements Listener {
    private final RVNKLore plugin;
    private final LogManager logger;
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
        this.logger = LogManager.getInstance(plugin, "HandlerEventLogger");
    }
    
    /**
     * Start logging handler events
     */
    public void enable() {
        if (enabled) return;
        logger.debug("Enabling handler event logging");
        registerEventListeners();
        enabled = true;
    }
    
    /**
     * Stop logging handler events
     */
    public void disable() {
        if (!enabled) return;
        logger.debug("Disabling handler event logging");
        enabled = false;
    }
    
    /**
     * Register listeners for all relevant events
     */
    @SuppressWarnings("unchecked")
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
        logger.debug("Registered event logger for: " + eventClass.getSimpleName());
    }
    
    /**
     * Log an event occurrence
     */
    private void logEvent(Event event) {
        Class<? extends Event> eventClass = event.getClass();
        eventCounts.compute(eventClass, (k, v) -> (v == null) ? 1 : v + 1);
        // Only log detailed info for debug level
        if (logger.getLogLevel().intValue() <= java.util.logging.Level.FINE.intValue()) {
            logDetailedEventInfo(event);
        }
    }
    
    /**
     * Log detailed information for specific event types
     */
    private void logDetailedEventInfo(Event event) {
        if (event instanceof PlayerJoinEvent) {
            PlayerJoinEvent e = (PlayerJoinEvent) event;
            logger.debug("PlayerJoinEvent: " + e.getPlayer().getName());
        } else if (event instanceof PlayerDeathEvent) {
            PlayerDeathEvent e = (PlayerDeathEvent) event;
            logger.debug("PlayerDeathEvent: " + e.getEntity().getName() +
                      " - " + e.getDeathMessage());
        } else if (event instanceof EnchantItemEvent) {
            EnchantItemEvent e = (EnchantItemEvent) event;
            logger.debug("EnchantItemEvent: " + e.getEnchanter().getName() +
                      " enchanted " + e.getItem().getType());
        } else {
            logger.debug("Event: " + event.getClass().getSimpleName());
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
