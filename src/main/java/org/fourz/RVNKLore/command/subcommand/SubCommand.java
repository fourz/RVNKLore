package org.fourz.RVNKLore.command.subcommand;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for all lore subcommands.
 * Provides contract for permission checking and command execution.
 */
public interface SubCommand {
    /**
     * Check if sender has permission to use this command
     *
     * @param sender The command sender to check permissions for
     * @return true if sender has permission, false otherwise
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Execute the subcommand
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was processed successfully
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Get command description for help
     *
     * @return The description of the command
     */
    String getDescription();
    
    /**
     * Get tab completions for command
     *
     * @param sender The command sender
     * @param args The current command arguments
     * @return A list of tab completions
     */
    default List<String> getTabCompletions(CommandSender sender, String[] args) {
        return List.of();
    }
}
