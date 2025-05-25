package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages command registration and execution for the RVNKLore plugin.
 */
public class CommandManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, CommandExecutor> commands = new HashMap<>();

    public CommandManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CommandManager");
        registerCommands();
    }

    /**
     * Registers all commands for the plugin
     */    private void registerCommands() {
        logger.debug("Registering commands...");
        registerCommand("lore", new LoreCommand(plugin));
        logger.debug("Commands registered successfully");
    }

    /**
     * Registers a command with the server
     * 
     * @param commandName The name of the command
     * @param executor The executor for the command
     */    private void registerCommand(String commandName, CommandExecutor executor) {
        logger.debug("Registering command: " + commandName);
        PluginCommand command = plugin.getCommand(commandName);
        
        if (command == null) {
            logger.warning("Failed to register command: " + commandName + " (not found in plugin.yml)");
            return;
        }
        
        command.setExecutor(executor);
        
        // If the executor also implements TabCompleter, register it
        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
            logger.debug("Tab completer registered for command: " + commandName);
        }        
        commands.put(commandName, executor);
        logger.debug("Command registered: " + commandName);
    }

    /**
     * Gets a registered command executor
     * 
     * @param commandName The name of the command
     * @return The command executor, or null if not found
     */
    public CommandExecutor getCommand(String commandName) {
        return commands.get(commandName);
    }
}