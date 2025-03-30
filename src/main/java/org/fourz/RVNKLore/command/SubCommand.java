package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for subcommands of the main lore command
 */
public interface SubCommand {
    /**
     * Executes the subcommand
     * 
     * @param sender The command sender
     * @param args Arguments for the subcommand
     * @return True if the command was executed successfully
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Gets a description of the subcommand
     * 
     * @return The description
     */
    String getDescription();
    
    /**
     * Checks if the sender has permission to use this subcommand
     * 
     * @param sender The command sender
     * @return True if the sender has permission
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Gets tab completions for the current arguments
     * 
     * @param sender The command sender
     * @param args Current arguments
     * @return List of tab completions
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);
}
