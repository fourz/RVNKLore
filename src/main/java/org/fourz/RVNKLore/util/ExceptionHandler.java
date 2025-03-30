package org.fourz.RVNKLore.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;

import java.util.logging.Level;

/**
 * Centralized exception handler for providing consistent error handling
 */
public class ExceptionHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public ExceptionHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "ExceptionHandler", Level.FINE);
    }
    
    /**
     * Handle an exception and notify the user
     * 
     * @param exception The exception to handle
     * @param sender The command sender to notify
     * @param context Context description of what was happening
     */
    public void handleException(Exception exception, CommandSender sender, String context) {
        // Log the exception
        debug.error("Error " + context + ": " + exception.getMessage(), exception);
        
        // Notify the user
        if (sender != null) {
            sender.sendMessage(ChatColor.RED + "An error occurred " + context + ".");
            if (sender.hasPermission("rvnklore.admin")) {
                sender.sendMessage(ChatColor.RED + "Error details: " + exception.getMessage());
            }
        }
    }
    
    /**
     * Handle a database exception
     * 
     * @param exception The exception to handle
     * @param sender The command sender to notify
     */
    public void handleDatabaseException(Exception exception, CommandSender sender) {
        handleException(exception, sender, "accessing the database");
        
        // Attempt to reconnect to the database
        boolean reconnected = plugin.getDatabaseManager().reconnect();
        
        if (reconnected && sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Database connection has been restored.");
        } else if (sender != null) {
            sender.sendMessage(ChatColor.RED + "Could not reconnect to the database. Please contact an administrator.");
        }
    }
    
    /**
     * Log an error without a full exception
     * 
     * @param errorMessage The error message
     * @param context Context description
     */
    public void logError(String errorMessage, String context) {
        debug.error("Error " + context + ": " + errorMessage, null);
    }
    
    /**
     * Send a user-friendly error message to a player
     * 
     * @param player The player to send the message to
     * @param message The error message
     */
    public void sendErrorMessage(Player player, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.RED + message);
        }
    }
}
