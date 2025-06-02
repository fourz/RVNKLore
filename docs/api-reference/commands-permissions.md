# Commands & Permissions API Reference

This document provides comprehensive reference for implementing commands and permission systems in Minecraft plugin development, specifically tailored for RVNKLore project patterns.

## Table of Contents
- [Command System Overview](#command-system-overview)
- [Command Registration](#command-registration)
- [Command Execution](#command-execution)
- [Tab Completion](#tab-completion)
- [Permission System](#permission-system)
- [Command Validation](#command-validation)
- [Sub-Command Architecture](#sub-command-architecture)
- [Error Handling](#error-handling)
- [RVNKLore Integration](#rvnklore-integration)

## Command System Overview

### Basic Command Structure

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
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                           String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("rvnklore.use")) {
            MessageUtil.sendMessage(sender, "&c✖ You don't have permission to use this command");
            return true;
        }
        
        // Handle no arguments
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        // Find and execute sub-command
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            MessageUtil.sendMessage(sender, "&c✖ Unknown sub-command: " + args[0]);
            MessageUtil.sendMessage(sender, "&c▶ Use /lore help for available commands");
            return true;
        }
        
        // Execute sub-command
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(sender, subArgs);
    }
}
```

### Command Interface Design

```java
public interface SubCommand {
    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);
    String getPermission();
    String getUsage();
    String getDescription();
    boolean isPlayerOnly();
}

public abstract class BaseSubCommand implements SubCommand {
    protected final RVNKLore plugin;
    protected final LogManager logger;
    
    public BaseSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
    }
    
    protected boolean validatePlayer(CommandSender sender) {
        if (isPlayerOnly() && !(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "&c✖ This command can only be used by players");
            return false;
        }
        return true;
    }
    
    protected boolean validatePermission(CommandSender sender) {
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            MessageUtil.sendMessage(sender, "&c✖ You don't have permission to use this command");
            return false;
        }
        return true;
    }
    
    protected boolean validateArgs(CommandSender sender, String[] args, int minArgs) {
        if (args.length < minArgs) {
            MessageUtil.sendMessage(sender, "&c▶ " + getUsage());
            return false;
        }
        return true;
    }
}
```

## Command Registration

### Plugin Main Class Registration

```java
public class RVNKLore extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Register commands
        registerCommands();
    }
    
    private void registerCommands() {
        // Main lore command
        LoreCommand loreCommand = new LoreCommand(this);
        getCommand("lore").setExecutor(loreCommand);
        getCommand("lore").setTabCompleter(loreCommand);
        
        // Admin commands
        LoreAdminCommand adminCommand = new LoreAdminCommand(this);
        getCommand("loreadmin").setExecutor(adminCommand);
        getCommand("loreadmin").setTabCompleter(adminCommand);
        
        // Alias commands
        getCommand("rvnklore").setExecutor(loreCommand);
        getCommand("rvnklore").setTabCompleter(loreCommand);
        
        logger.info("Registered command handlers");
    }
}
```

### Plugin.yml Configuration

```yaml
name: RVNKLore
version: ${project.version}
main: com.rvnklore.RVNKLore
api-version: 1.21

commands:
  lore:
    description: Main lore command
    usage: /<command> [subcommand] [args...]
    permission: rvnklore.use
    aliases: [rvnklore, lores]
  loreadmin:
    description: Administrative lore commands
    usage: /<command> [subcommand] [args...]
    permission: rvnklore.admin
    aliases: [ladmin, loremanage]

permissions:
  rvnklore.*:
    description: All RVNKLore permissions
    children:
      rvnklore.use: true
      rvnklore.admin: true
      rvnklore.create: true
      rvnklore.delete: true
      rvnklore.reload: true
  rvnklore.use:
    description: Basic lore commands
    default: true
  rvnklore.admin:
    description: Administrative commands
    default: op
  rvnklore.create:
    description: Create new lore
    default: op
  rvnklore.delete:
    description: Delete existing lore
    default: op
  rvnklore.reload:
    description: Reload plugin configuration
    default: op
```

## Command Execution

### Lore View Command

```java
public class LoreViewCommand extends BaseSubCommand {
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!validatePlayer(sender) || !validatePermission(sender)) {
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Show nearby lore
            showNearbyLore(player);
            return true;
        }
        
        // Show specific lore
        String loreId = args[0];
        LoreEntry lore = LoreManager.getLoreEntry(loreId);
        
        if (lore == null) {
            MessageUtil.sendMessage(player, "&c✖ Lore not found: " + loreId);
            return true;
        }
        
        // Check if player has discovered this lore
        if (!LoreManager.hasPlayerDiscovered(player, loreId)) {
            MessageUtil.sendMessage(player, "&c✖ You haven't discovered this lore yet");
            return true;
        }
        
        // Display lore content
        LoreManager.displayLoreToPlayer(player, lore);
        MessageUtil.sendMessage(player, "&a✓ Displaying lore: " + lore.getTitle());
        
        return true;
    }
    
    private void showNearbyLore(Player player) {
        Location location = player.getLocation();
        List<LoreLocation> nearbyLore = LoreManager.getNearbyLore(location, 50.0);
        
        if (nearbyLore.isEmpty()) {
            MessageUtil.sendMessage(player, "&e⚠ No lore locations found nearby");
            return;
        }
        
        MessageUtil.sendMessage(player, "&6⚙ Nearby lore locations:");
        for (LoreLocation lore : nearbyLore) {
            double distance = location.distance(lore.getLocation());
            MessageUtil.sendMessage(player, String.format(
                "&7   &e%s &7- &f%.1f blocks away", 
                lore.getTitle(), distance
            ));
        }
    }
    
    @Override
    public String getPermission() { return "rvnklore.use"; }
    
    @Override
    public String getUsage() { return "/lore view [lore_id]"; }
    
    @Override
    public String getDescription() { return "View discovered lore"; }
    
    @Override
    public boolean isPlayerOnly() { return true; }
}
```

### Lore Create Command

```java
public class LoreCreateCommand extends BaseSubCommand {
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!validatePlayer(sender) || !validatePermission(sender)) {
            return true;
        }
        
        if (!validateArgs(sender, args, 2)) {
            return true;
        }
        
        Player player = (Player) sender;
        String loreId = args[0];
        String title = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // Check if lore ID already exists
        if (LoreManager.loreExists(loreId)) {
            MessageUtil.sendMessage(player, "&c✖ Lore with ID '" + loreId + "' already exists");
            return true;
        }
        
        // Validate lore ID format
        if (!isValidLoreId(loreId)) {
            MessageUtil.sendMessage(player, "&c✖ Invalid lore ID format");
            MessageUtil.sendMessage(player, "&7   Use alphanumeric characters and underscores only");
            return true;
        }
        
        // Create lore at player's location
        Location location = player.getLocation();
        LoreEntry newLore = new LoreEntry(loreId, title);
        newLore.setCreator(player.getUniqueId());
        newLore.setCreationDate(new Date());
        
        // Start content creation process
        startLoreCreationProcess(player, newLore, location);
        
        return true;
    }
    
    private void startLoreCreationProcess(Player player, LoreEntry lore, Location location) {
        MessageUtil.sendMessage(player, "&6⚙ Creating new lore: " + lore.getTitle());
        MessageUtil.sendMessage(player, "&7   Type the lore content in chat");
        MessageUtil.sendMessage(player, "&7   Type 'cancel' to cancel or 'done' to finish");
        
        // Store creation session
        LoreCreationSession session = new LoreCreationSession(player, lore, location);
        LoreManager.startCreationSession(session);
    }
    
    private boolean isValidLoreId(String loreId) {
        return loreId.matches("^[a-zA-Z0-9_]+$") && loreId.length() <= 50;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest lore ID format
            return Arrays.asList("new_lore_id", "ancient_tale", "mysterious_rune");
        }
        return new ArrayList<>();
    }
    
    @Override
    public String getPermission() { return "rvnklore.create"; }
    
    @Override
    public String getUsage() { return "/lore create <lore_id> <title...>"; }
    
    @Override
    public String getDescription() { return "Create new lore at your location"; }
    
    @Override
    public boolean isPlayerOnly() { return true; }
}
```

### Administrative Commands

```java
public class LoreAdminCommand implements CommandExecutor, TabCompleter {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                           String label, String[] args) {
        if (!sender.hasPermission("rvnklore.admin")) {
            MessageUtil.sendMessage(sender, "&c✖ You don't have permission to use admin commands");
            return true;
        }
        
        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender, subArgs);
            case "delete":
                return handleDelete(sender, subArgs);
            case "teleport":
                return handleTeleport(sender, subArgs);
            case "stats":
                return handleStats(sender);
            default:
                MessageUtil.sendMessage(sender, "&c✖ Unknown admin command: " + subCommand);
                sendAdminHelp(sender);
                return true;
        }
    }
    
    private boolean handleReload(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&6⚙ Reloading RVNKLore configuration...");
        
        try {
            ConfigManager.reloadConfig();
            LoreManager.reloadLoreData();
            
            MessageUtil.sendMessage(sender, "&a✓ Configuration reloaded successfully");
            logger.info("Configuration reloaded by " + sender.getName());
            
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, "&c✖ Error reloading configuration");
            logger.error("Error reloading configuration", e);
        }
        
        return true;
    }
    
    private boolean handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, "&c✖ Invalid page number");
                return true;
            }
        }
        
        List<LoreEntry> allLore = LoreManager.getAllLore();
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) allLore.size() / itemsPerPage);
        
        if (page < 1 || page > totalPages) {
            MessageUtil.sendMessage(sender, "&c✖ Invalid page number (1-" + totalPages + ")");
            return true;
        }
        
        MessageUtil.sendMessage(sender, "&6⚙ All Lore (Page " + page + "/" + totalPages + "):");
        
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allLore.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            LoreEntry lore = allLore.get(i);
            MessageUtil.sendMessage(sender, String.format(
                "&7   &e%s &7(&f%s&7) - %d discoveries", 
                lore.getTitle(), lore.getId(), lore.getDiscoveryCount()
            ));
        }
        
        if (page < totalPages) {
            MessageUtil.sendMessage(sender, "&7   Use /loreadmin list " + (page + 1) + " for next page");
        }
        
        return true;
    }
}
```

## Tab Completion

### Advanced Tab Completion

```java
@Override
public List<String> onTabComplete(CommandSender sender, Command command, 
                                 String alias, String[] args) {
    List<String> completions = new ArrayList<>();
    
    if (args.length == 1) {
        // First argument - sub-commands
        for (String subCmd : subCommands.keySet()) {
            if (sender.hasPermission(subCommands.get(subCmd).getPermission())) {
                completions.add(subCmd);
            }
        }
        return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
    }
    
    if (args.length >= 2) {
        // Delegate to sub-command
        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return subCommand.tabComplete(sender, subArgs);
        }
    }
    
    return completions;
}

// Utility methods for common completions
public static List<String> getPlayerNames() {
    return Bukkit.getOnlinePlayers().stream()
        .map(Player::getName)
        .collect(Collectors.toList());
}

public static List<String> getLoreIds() {
    return LoreManager.getAllLore().stream()
        .map(LoreEntry::getId)
        .collect(Collectors.toList());
}

public static List<String> getWorldNames() {
    return Bukkit.getWorlds().stream()
        .map(World::getName)
        .collect(Collectors.toList());
}
```

### Context-Aware Completions

```java
public class LoreEditCommand extends BaseSubCommand {
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Lore IDs that the player can edit
            Player player = (Player) sender;
            completions.addAll(LoreManager.getEditableLore(player).stream()
                .map(LoreEntry::getId)
                .collect(Collectors.toList()));
                
        } else if (args.length == 2) {
            // Edit properties
            completions.addAll(Arrays.asList("title", "content", "location", "visibility"));
            
        } else if (args.length == 3) {
            String property = args[1].toLowerCase();
            
            if ("visibility".equals(property)) {
                completions.addAll(Arrays.asList("public", "private", "guild", "admin"));
            } else if ("location".equals(property)) {
                completions.addAll(Arrays.asList("current", "spawn", "here"));
            }
        }
        
        return StringUtil.copyPartialMatches(args[args.length - 1], completions, new ArrayList<>());
    }
}
```

## Permission System

### Permission Hierarchy

```java
public class PermissionManager {
    
    // Permission constants
    public static final String BASE = "rvnklore";
    public static final String USE = BASE + ".use";
    public static final String ADMIN = BASE + ".admin";
    public static final String CREATE = BASE + ".create";
    public static final String DELETE = BASE + ".delete";
    public static final String EDIT = BASE + ".edit";
    public static final String RELOAD = BASE + ".reload";
    public static final String TELEPORT = BASE + ".teleport";
    
    // Dynamic permissions
    public static String getLoreViewPermission(String loreId) {
        return BASE + ".view." + loreId;
    }
    
    public static String getLoreEditPermission(String loreId) {
        return BASE + ".edit." + loreId;
    }
    
    public static String getWorldPermission(String worldName) {
        return BASE + ".world." + worldName;
    }
    
    // Permission checking utilities
    public static boolean canViewLore(CommandSender sender, String loreId) {
        return sender.hasPermission(USE) && 
               (sender.hasPermission(ADMIN) || 
                sender.hasPermission(getLoreViewPermission(loreId)));
    }
    
    public static boolean canEditLore(CommandSender sender, String loreId) {
        return sender.hasPermission(EDIT) && 
               (sender.hasPermission(ADMIN) || 
                sender.hasPermission(getLoreEditPermission(loreId)));
    }
    
    public static boolean canUseInWorld(CommandSender sender, World world) {
        return sender.hasPermission(USE) && 
               (sender.hasPermission(ADMIN) || 
                sender.hasPermission(getWorldPermission(world.getName())));
    }
}
```

### Runtime Permission Management

```java
public class DynamicPermissionManager {
    private final PermissionManager permissionManager;
    
    public void grantLoreAccess(Player player, String loreId) {
        String permission = PermissionManager.getLoreViewPermission(loreId);
        
        // Grant temporary permission
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission(permission, true);
        
        // Store attachment for cleanup
        storePermissionAttachment(player, loreId, attachment);
        
        MessageUtil.sendMessage(player, "&a✓ Granted access to lore: " + loreId);
        logger.info("Granted lore access to " + player.getName() + " for " + loreId);
    }
    
    public void revokeLoreAccess(Player player, String loreId) {
        PermissionAttachment attachment = getPermissionAttachment(player, loreId);
        if (attachment != null) {
            attachment.remove();
            removePermissionAttachment(player, loreId);
            
            MessageUtil.sendMessage(player, "&e⚠ Revoked access to lore: " + loreId);
            logger.info("Revoked lore access from " + player.getName() + " for " + loreId);
        }
    }
    
    public void cleanupPlayerPermissions(Player player) {
        List<PermissionAttachment> attachments = getPlayerAttachments(player);
        for (PermissionAttachment attachment : attachments) {
            attachment.remove();
        }
        clearPlayerAttachments(player);
    }
}
```

## Command Validation

### Input Validation

```java
public class CommandValidator {
    
    public static boolean validateLoreId(String loreId, CommandSender sender) {
        if (loreId == null || loreId.trim().isEmpty()) {
            MessageUtil.sendMessage(sender, "&c✖ Lore ID cannot be empty");
            return false;
        }
        
        if (!loreId.matches("^[a-zA-Z0-9_-]+$")) {
            MessageUtil.sendMessage(sender, "&c✖ Lore ID can only contain letters, numbers, underscores, and hyphens");
            return false;
        }
        
        if (loreId.length() > 50) {
            MessageUtil.sendMessage(sender, "&c✖ Lore ID must be 50 characters or less");
            return false;
        }
        
        return true;
    }
    
    public static boolean validateTitle(String title, CommandSender sender) {
        if (title == null || title.trim().isEmpty()) {
            MessageUtil.sendMessage(sender, "&c✖ Title cannot be empty");
            return false;
        }
        
        if (title.length() > 100) {
            MessageUtil.sendMessage(sender, "&c✖ Title must be 100 characters or less");
            return false;
        }
        
        // Check for inappropriate content
        if (containsInappropriateContent(title)) {
            MessageUtil.sendMessage(sender, "&c✖ Title contains inappropriate content");
            return false;
        }
        
        return true;
    }
    
    public static boolean validateContent(String content, CommandSender sender) {
        if (content == null || content.trim().isEmpty()) {
            MessageUtil.sendMessage(sender, "&c✖ Content cannot be empty");
            return false;
        }
        
        if (content.length() > 5000) {
            MessageUtil.sendMessage(sender, "&c✖ Content must be 5000 characters or less");
            return false;
        }
        
        return true;
    }
    
    private static boolean containsInappropriateContent(String text) {
        // Implement content filtering logic
        List<String> bannedWords = ConfigManager.getBannedWords();
        String lowerText = text.toLowerCase();
        
        return bannedWords.stream().anyMatch(lowerText::contains);
    }
}
```

### Argument Parsing

```java
public class ArgumentParser {
    
    public static Optional<Player> parsePlayer(String playerName, CommandSender sender) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            MessageUtil.sendMessage(sender, "&c✖ Player not found: " + playerName);
            return Optional.empty();
        }
        
        return Optional.of(target);
    }
    
    public static Optional<Integer> parseInt(String value, CommandSender sender, 
                                           String fieldName, int min, int max) {
        try {
            int parsed = Integer.parseInt(value);
            
            if (parsed < min || parsed > max) {
                MessageUtil.sendMessage(sender, String.format(
                    "&c✖ %s must be between %d and %d", fieldName, min, max));
                return Optional.empty();
            }
            
            return Optional.of(parsed);
            
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c✖ Invalid number for " + fieldName + ": " + value);
            return Optional.empty();
        }
    }
    
    public static Optional<Double> parseDouble(String value, CommandSender sender, 
                                             String fieldName, double min, double max) {
        try {
            double parsed = Double.parseDouble(value);
            
            if (parsed < min || parsed > max) {
                MessageUtil.sendMessage(sender, String.format(
                    "&c✖ %s must be between %.2f and %.2f", fieldName, min, max));
                return Optional.empty();
            }
            
            return Optional.of(parsed);
            
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c✖ Invalid decimal number for " + fieldName + ": " + value);
            return Optional.empty();
        }
    }
    
    public static Optional<Location> parseLocation(String[] coords, World world, CommandSender sender) {
        if (coords.length != 3) {
            MessageUtil.sendMessage(sender, "&c✖ Location requires 3 coordinates (x y z)");
            return Optional.empty();
        }
        
        try {
            double x = Double.parseDouble(coords[0]);
            double y = Double.parseDouble(coords[1]);
            double z = Double.parseDouble(coords[2]);
            
            return Optional.of(new Location(world, x, y, z));
            
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c✖ Invalid coordinates");
            return Optional.empty();
        }
    }
}
```

## Sub-Command Architecture

### Command Manager

```java
public class CommandManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, CommandCategory> categories;
    
    public CommandManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.categories = new HashMap<>();
        initializeCategories();
    }
    
    private void initializeCategories() {
        // User commands
        CommandCategory userCategory = new CommandCategory("user", "rvnklore.use");
        userCategory.addCommand("view", new LoreViewCommand(plugin));
        userCategory.addCommand("list", new LoreListCommand(plugin));
        userCategory.addCommand("search", new LoreSearchCommand(plugin));
        userCategory.addCommand("help", new LoreHelpCommand(plugin));
        categories.put("user", userCategory);
        
        // Creator commands
        CommandCategory creatorCategory = new CommandCategory("creator", "rvnklore.create");
        creatorCategory.addCommand("create", new LoreCreateCommand(plugin));
        creatorCategory.addCommand("edit", new LoreEditCommand(plugin));
        creatorCategory.addCommand("delete", new LoreDeleteCommand(plugin));
        categories.put("creator", creatorCategory);
        
        // Admin commands
        CommandCategory adminCategory = new CommandCategory("admin", "rvnklore.admin");
        adminCategory.addCommand("reload", new LoreReloadCommand(plugin));
        adminCategory.addCommand("stats", new LoreStatsCommand(plugin));
        adminCategory.addCommand("migrate", new LoreMigrateCommand(plugin));
        categories.put("admin", adminCategory);
    }
    
    public Optional<SubCommand> getCommand(String category, String commandName) {
        CommandCategory cat = categories.get(category);
        return cat != null ? Optional.ofNullable(cat.getCommand(commandName)) : Optional.empty();
    }
    
    public List<String> getAvailableCommands(CommandSender sender) {
        return categories.values().stream()
            .filter(category -> sender.hasPermission(category.getPermission()))
            .flatMap(category -> category.getCommandNames().stream())
            .collect(Collectors.toList());
    }
}

public class CommandCategory {
    private final String name;
    private final String permission;
    private final Map<String, SubCommand> commands;
    
    public CommandCategory(String name, String permission) {
        this.name = name;
        this.permission = permission;
        this.commands = new HashMap<>();
    }
    
    public void addCommand(String name, SubCommand command) {
        commands.put(name, command);
    }
    
    public SubCommand getCommand(String name) {
        return commands.get(name);
    }
    
    public Set<String> getCommandNames() {
        return commands.keySet();
    }
    
    public String getPermission() {
        return permission;
    }
}
```

## Error Handling

### Centralized Error Handling

```java
public class CommandErrorHandler {
    private final LogManager logger;
    
    public CommandErrorHandler(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin);
    }
    
    public void handleCommandError(CommandSender sender, String command, Exception error) {
        // Log the error
        logger.error("Error executing command: " + command, error);
        
        // Send user-friendly message
        MessageUtil.sendMessage(sender, "&c✖ An error occurred while executing the command");
        
        // Provide additional help for known errors
        if (error instanceof IllegalArgumentException) {
            MessageUtil.sendMessage(sender, "&7   Please check your command arguments");
        } else if (error instanceof SQLException) {
            MessageUtil.sendMessage(sender, "&7   Database error - please try again later");
            MessageUtil.sendMessage(sender, "&7   If the problem persists, contact an administrator");
        }
        
        // Suggest help command
        MessageUtil.sendMessage(sender, "&7   Use /lore help for command usage");
    }
    
    public void handlePermissionError(CommandSender sender, String permission) {
        MessageUtil.sendMessage(sender, "&c✖ You don't have permission to use this command");
        MessageUtil.sendMessage(sender, "&7   Required permission: " + permission);
        
        logger.warning(sender.getName() + " attempted to use command without permission: " + permission);
    }
    
    public void handleValidationError(CommandSender sender, String field, String value, String requirement) {
        MessageUtil.sendMessage(sender, String.format("&c✖ Invalid %s: %s", field, value));
        MessageUtil.sendMessage(sender, "&7   Requirement: " + requirement);
    }
}
```

## RVNKLore Integration

### Integration with Core Systems

```java
// Integration with LoreManager
public class LoreListCommand extends BaseSubCommand {
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!validatePlayer(sender) || !validatePermission(sender)) {
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get filter options
        String filter = args.length > 0 ? args[0].toLowerCase() : "all";
        List<LoreEntry> loreList;
        
        switch (filter) {
            case "discovered":
                loreList = LoreManager.getDiscoveredLore(player);
                break;
            case "nearby":
                loreList = LoreManager.getNearbyLore(player.getLocation(), 100.0)
                    .stream()
                    .map(LoreLocation::getLoreEntry)
                    .collect(Collectors.toList());
                break;
            case "created":
                loreList = LoreManager.getCreatedLore(player);
                break;
            default:
                loreList = LoreManager.getAllLore();
                break;
        }
        
        displayLoreList(player, loreList, filter);
        return true;
    }
    
    private void displayLoreList(Player player, List<LoreEntry> loreList, String filter) {
        if (loreList.isEmpty()) {
            MessageUtil.sendMessage(player, "&e⚠ No lore found for filter: " + filter);
            return;
        }
        
        MessageUtil.sendMessage(player, "&6⚙ Lore List (" + filter + "):");
        
        for (LoreEntry lore : loreList) {
            boolean discovered = LoreManager.hasPlayerDiscovered(player, lore.getId());
            String status = discovered ? "&a✓" : "&7○";
            
            MessageUtil.sendMessage(player, String.format(
                "&7   %s &e%s &7- &f%s", 
                status, lore.getTitle(), lore.getCategory()
            ));
        }
        
        MessageUtil.sendMessage(player, "&7   Use /lore view <title> to read lore content");
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("all", "discovered", "nearby", "created");
        }
        return new ArrayList<>();
    }
}

// Integration with DatabaseManager
public class LoreStatsCommand extends BaseSubCommand {
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!validatePermission(sender)) {
            return true;
        }
        
        MessageUtil.sendMessage(sender, "&6⚙ Generating lore statistics...");
        
        // Run async to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LoreStatistics stats = DatabaseManager.getInstance().getLoreStatistics();
                
                // Send results on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayStatistics(sender, stats);
                });
                
            } catch (SQLException e) {
                logger.error("Error generating lore statistics", e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(sender, "&c✖ Error generating statistics");
                });
            }
        });
        
        return true;
    }
    
    private void displayStatistics(CommandSender sender, LoreStatistics stats) {
        MessageUtil.sendMessage(sender, "&6⚙ RVNKLore Statistics:");
        MessageUtil.sendMessage(sender, "&7   Total Lore Entries: &f" + stats.getTotalLore());
        MessageUtil.sendMessage(sender, "&7   Total Discoveries: &f" + stats.getTotalDiscoveries());
        MessageUtil.sendMessage(sender, "&7   Unique Discoverers: &f" + stats.getUniqueDiscoverers());
        MessageUtil.sendMessage(sender, "&7   Average Discoveries per Player: &f" + 
            String.format("%.1f", stats.getAverageDiscoveriesPerPlayer()));
        MessageUtil.sendMessage(sender, "&7   Most Popular Lore: &e" + stats.getMostPopularLore());
        MessageUtil.sendMessage(sender, "&7   Recently Created: &f" + stats.getRecentlyCreated());
    }
}
```

## Best Practices

### 1. **Command Structure**
- Use consistent command hierarchies
- Implement proper permission checking
- Provide clear usage messages
- Handle edge cases gracefully

### 2. **User Experience**
- Provide helpful error messages
- Use tab completion effectively
- Include command help and examples
- Maintain consistent message formatting

### 3. **Performance**
- Use async operations for database queries
- Cache frequently accessed data
- Implement command cooldowns if needed
- Validate input before processing

### 4. **Security**
- Always validate user input
- Check permissions at multiple levels
- Sanitize command arguments
- Log administrative actions

This Commands & Permissions API reference provides comprehensive patterns for implementing robust command systems in the RVNKLore plugin, ensuring proper integration with existing systems while maintaining security and usability standards.
