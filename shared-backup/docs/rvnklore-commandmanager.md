# RVNKLore Command Manager Architecture

*Last Updated: June 25, 2025*

This document provides a detailed overview of the RVNKLore plugin's command architecture, including class relationships, implementation patterns, and best practices for extending the command system.

## Command Architecture Overview

RVNKLore implements a modular command architecture using a combination of command pattern and builder pattern, with hierarchical command structure:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  CommandManager │────▶│   LoreCommand   │────▶│   SubCommands   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Command Registry│     │  Permission     │     │ Command Logic   │
└─────────────────┘     │  Handling       │     └─────────────────┘
                        └─────────────────┘
```

## Core Components

### 1. CommandManager

The `CommandManager` is responsible for registering all plugin commands with Bukkit/Spigot and handling initial command routing.

```java
public class CommandManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, CommandExecutor> commands;
    
    public CommandManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.commands = new HashMap<>();
        registerCommands();
    }
    
    private void registerCommands() {
        // Register main /lore command
        registerCommand("lore", new LoreCommand(plugin));
    }
    
    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            commands.put(name, executor);
            logger.info("Registered command: " + name);
        } else {
            logger.warning("Failed to register command: " + name + " (not found in plugin.yml)");
        }
    }
}
```

### 2. LoreCommand 

The `LoreCommand` class serves as the primary command handler for the `/lore` command and acts as a router to various subcommands.

```java
public class LoreCommand implements CommandExecutor, TabCompleter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands;
    
    public LoreCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.subCommands = new HashMap<>();
        registerSubCommands();
    }
    
    private void registerSubCommands() {
        // Register all subcommands
        registerSubCommand("get", new LoreGetSubCommand(plugin));
        registerSubCommand("add", new LoreAddSubCommand(plugin));
        // Additional subcommands...
    }
    
    private void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            displayHelpMenu(sender);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage("&c▶ Unknown subcommand. Use /lore help for a list of commands.");
            return true;
        }
        
        // Check permissions
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage("&c✖ You don't have permission to use this command.");
            return true;
        }
        
        // Execute the subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(sender, subArgs);
    }
    
    // TabCompleter implementation and help menu methods...
}
```

### 3. SubCommand Interface/Abstract Class

The `SubCommand` interface defines the contract that all subcommands must implement:

```java
public interface SubCommand {
    /**
     * Execute the subcommand with the given arguments.
     *
     * @param sender The command sender (player or console)
     * @param args Arguments passed to the subcommand
     * @return true if the command was handled, false otherwise
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Checks if the sender has permission to use this subcommand.
     *
     * @param sender The command sender to check
     * @return true if the sender has permission, false otherwise
     */
    boolean hasPermission(CommandSender sender);
    
    /**
     * Gets tab completions for the subcommand.
     *
     * @param sender The command sender
     * @param args Current arguments
     * @return List of possible tab completions
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);
    
    /**
     * Gets the usage message for this subcommand.
     *
     * @return The usage message
     */
    String getUsage();
    
    /**
     * Gets a brief description of what this subcommand does.
     *
     * @return The subcommand description
     */
    String getDescription();
}
```

## Command Implementation Best Practices

### Standard Implementation Pattern

All commands should follow this consistent validation, execution, and feedback pattern:

1. **Permission Check**
   ```java
   if (!sender.hasPermission("rvnklore.command.subcommand")) {
       sender.sendMessage("&c✖ You don't have permission to use this command.");
       return true;
   }
   ```

2. **Argument Validation**
   ```java
   if (args.length < requiredArgsCount) {
       sender.sendMessage("&c▶ Usage: " + getUsage());
       return true;
   }
   ```

3. **Entity/Target Validation**
   ```java
   Player target = Bukkit.getPlayer(args[0]);
   if (target == null) {
       sender.sendMessage("&c✖ Player not found: " + args[0]);
       return true;
   }
   ```

4. **Operation Execution**
   ```java
   // For database operations, use asynchronous calls
   plugin.getDatabaseManager().getLoreEntry(id).thenAccept(entry -> {
       // Handle the result
   }).exceptionally(ex -> {
       logger.error("Error retrieving lore entry", ex);
       sender.sendMessage("&c✖ An error occurred while retrieving the lore entry.");
       return null;
   });
   ```

5. **User Feedback**
   ```java
   // Success message
   sender.sendMessage("&a✓ Lore entry added successfully.");
   
   // Additional information
   sender.sendMessage("&7   ID: " + entry.getId());
   ```

6. **Next-Step Guidance**
   ```java
   sender.sendMessage("&7   Use /lore get " + entry.getId() + " to view this entry.");
   ```

### Tab Completion Implementation

```java
@Override
public List<String> getTabCompletions(CommandSender sender, String[] args) {
    List<String> completions = new ArrayList<>();
    
    if (args.length == 1) {
        // First argument completions
        String arg = args[0].toLowerCase();
        for (String option : availableOptions) {
            if (option.toLowerCase().startsWith(arg)) {
                completions.add(option);
            }
        }
    } else if (args.length == 2) {
        // Second argument completions
        // ...
    }
    
    return completions;
}
```

## Adding New Commands

To add a new command to the RVNKLore plugin:

1. **Create a new subcommand class**
   - Implement the `SubCommand` interface or extend `AbstractSubCommand`
   - Implement all required methods
   - Follow the standard implementation pattern

2. **Register the subcommand**
   - Add the subcommand to the appropriate parent command's registration method
   - Update plugin.yml if adding a top-level command

3. **Add permission to plugin.yml**
   ```yaml
   permissions:
     rvnklore.command.newcommmand:
       description: Allows the use of the /lore newcommmand command
       default: op
   ```

## Command-Database Interaction Pattern

Commands should never directly access repositories or database connections. Instead:

```java
// Incorrect approach
LoreEntry entry = loreRepository.getLoreEntryById(id);

// Correct approach
CompletableFuture<LoreEntry> futureEntry = databaseManager.getLoreEntry(id);
futureEntry.thenAccept(entry -> {
    // Handle entry here (send messages to player, etc.)
}).exceptionally(ex -> {
    logger.error("Error retrieving lore entry", ex);
    sender.sendMessage("&c✖ An error occurred: " + ex.getMessage());
    return null;
});
```

## Current Command Implementation Status

| Command | Status | Description |
|---------|--------|-------------|
| /lore | Implemented | Main command with subcommands |
| /lore get | Implemented | Get lore entries |
| /lore add | Implemented | Add new lore entries |
| /lore approve | Implemented | Approve pending lore entries |
| /lore item | Implemented | Item-related subcommands |
| /lore collection | Implemented | Collection-related subcommands |
| /lore debug | Implemented | Debug commands for developers |

## Future Enhancements

- **Command Aliasing System**: Allow alternative names for commands
- **Dynamic Permission System**: Permission-based command visibility
- **Command Cooldowns**: Prevent command spam
- **Enhanced Help System**: More detailed, paginated help menus
