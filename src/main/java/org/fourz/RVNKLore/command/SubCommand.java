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

    /**
     * One-line grammar for this subcommand, shown by {@code /lore help <verb>}.
     *
     * <p>Default is empty, meaning "not stated" — the help then falls back to the description
     * alone. Prefer filling this in over restating the grammar inside {@link #getExamples()}.</p>
     *
     * @return a usage line such as {@code /lore get <name|id>}, or empty
     */
    default String getUsage() {
        return "";
    }

    /**
     * Worked examples for this subcommand, served by {@code /lore help <verb>}.
     *
     * <p>Return concrete, runnable lines with real-looking arguments. A line beginning with two
     * spaces renders as an indented note under the example above it.</p>
     *
     * <p><b>Why the examples live in the jar.</b> Restated in {@code docs/plugins/commands/}, they
     * must be read whole to answer a question about one verb, and they drift from the build with
     * nothing to catch it. Shipping them here makes them per-verb and unable to drift. The doc
     * pages keep what no command can print: sequence diagrams, backend class references and
     * changelogs. (#1981)</p>
     *
     * <p>Default is empty, so existing subcommands need no change, and {@code /lore help} marks
     * which verbs carry examples.</p>
     *
     * @return example lines, or an empty list
     */
    default List<String> getExamples() {
        return List.of();
    }
}
