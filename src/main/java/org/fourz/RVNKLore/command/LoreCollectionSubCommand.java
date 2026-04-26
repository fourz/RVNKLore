package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadCollection;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sub-command for managing and viewing head collections.
 * Provides player-facing interface for browsing collections and checking progress.
 */
public class LoreCollectionSubCommand implements SubCommand {
    private final CosmeticsManager cosmeticItem;
    private final RVNKLore plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public LoreCollectionSubCommand(RVNKLore plugin) {        
        this.plugin = plugin;
        this.cosmeticItem = plugin.getLoreManager().getItemManager().getCosmeticItem();
        
        // Register sub-commands
        subCommands.put("add", new LoreCollectionAddSubCommand(plugin));
        subCommands.put("list", new LoreCollectionListSubCommand(plugin));
        subCommands.put("additem", new LoreCollectionAddItemSubCommand(plugin));
        subCommands.put("removeitem", new LoreCollectionRemoveItemSubCommand(plugin));
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Only allow players with the base collection permission or admin
        return sender.hasPermission("rvnklore.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Check if the first argument is a registered subcommand
        if (args.length > 0 && subCommands.containsKey(args[0].toLowerCase())) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand.hasPermission(sender)) {
                return subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            } else {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
                return true;
            }
        }

        // If not a subcommand, handle direct view/claim/list routing via CollectionManager
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection <view|claim|list|add|additem|removeitem> [collection_id]");
            return true;
        }

        CollectionManager cmgr = plugin.getLoreManager().getItemManager().getCollectionManager();
        String sub = args[0].toLowerCase();
        String collectionId;

        switch (sub) {
            case "view":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection view <collection_id>");
                    return true;
                }
                collectionId = args[1];
                ItemCollection itemCollection = cmgr.getCollectionSync(collectionId);
                if (itemCollection == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
                    sender.sendMessage(ChatColor.GRAY + "   Use /lore collection list to see available IDs");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Collection: " + ChatColor.YELLOW + itemCollection.getName());
                sender.sendMessage(ChatColor.GRAY + "ID: " + ChatColor.WHITE + itemCollection.getId());
                List<UUID> requiredEntries = itemCollection.getRequiredEntryIds();
                if (!requiredEntries.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Required entries (" + requiredEntries.size() + "):");
                    for (UUID entryId : requiredEntries) {
                        LoreEntry entry = plugin.getLoreManager().getLoreEntrySync(entryId);
                        String entryName = entry != null ? entry.getName() : entryId.toString().substring(0, 8) + "...";
                        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + entryName);
                    }
                    if (sender instanceof Player) {
                        int collected = cmgr.getCollectedEntryCountSync(((Player) sender).getUniqueId(), collectionId);
                        sender.sendMessage(ChatColor.GRAY + "Your progress: " + ChatColor.WHITE + collected + "/" + requiredEntries.size());
                    }
                } else {
                    sender.sendMessage(ChatColor.GRAY + "Items: " + ChatColor.WHITE + itemCollection.getItemCount());
                }
                break;

            case "claim":
                Player claimTarget;
                if (sender instanceof Player) {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection claim <collection_id>");
                        return true;
                    }
                    claimTarget = (Player) sender;
                    collectionId = args[1];
                } else {
                    if (args.length < 3) {
                        sender.sendMessage("Usage: /lore collection claim <player> <collection_id>");
                        return true;
                    }
                    claimTarget = Bukkit.getPlayerExact(args[1]);
                    if (claimTarget == null) {
                        sender.sendMessage("Player not found or not online: " + args[1]);
                        return true;
                    }
                    collectionId = args[2];
                }
                ItemCollection claimColl = cmgr.getCollectionSync(collectionId);
                if (claimColl == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
                    return true;
                }
                int required = claimColl.getRequiredEntryCount();
                if (required > 0) {
                    int collected = cmgr.getCollectedEntryCountSync(claimTarget.getUniqueId(), collectionId);
                    if (collected < required) {
                        sender.sendMessage(ChatColor.YELLOW + "⚠ " + claimTarget.getName() + " must collect all " + required + " required entries (" + collected + "/" + required + ").");
                        return true;
                    }
                }
                boolean rewarded = cmgr.grantCollectionRewardSync(claimTarget.getUniqueId(), collectionId);
                if (rewarded) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Claimed rewards for collection: " + ChatColor.YELLOW + collectionId);
                } else {
                    // Fallback: HEAD cosmetic rewards via CosmeticsManager
                    HeadCollection headColl = cosmeticItem.getCollection(collectionId);
                    if (headColl != null && headColl.getRewards().hasRewards()) {
                        cosmeticItem.awardCollectionRewards(claimTarget, headColl, headColl.getRewards());
                        sender.sendMessage(ChatColor.GREEN + "✓ Claimed rewards for collection: " + ChatColor.YELLOW + collectionId);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "⚠ No rewards available for this collection.");
                    }
                }
                break;

            case "list":
                Map<String, ItemCollection> allColls = cmgr.getAllCollectionsSync();
                if (allColls.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No collections available");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Collections (" + allColls.size() + " total):");
                for (ItemCollection coll : allColls.values()) {
                    int entryCount = coll.getRequiredEntryCount();
                    String countStr = entryCount > 0 ? entryCount + " entries" : coll.getItemCount() + " items";
                    sender.sendMessage(ChatColor.GRAY + "  - [" + ChatColor.WHITE + coll.getId() + ChatColor.GRAY + "] "
                            + ChatColor.YELLOW + coll.getName() + ChatColor.GRAY + " (" + countStr + ")");
                }
                break;

            default:
                sender.sendMessage(ChatColor.RED + "✖ Unknown subcommand: " + sub);
                sender.sendMessage(ChatColor.GRAY + "   Use /lore collection <view|claim|list|add|additem|removeitem>");
                break;
        }
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Add original commands
            completions.add("view");
            completions.add("claim");
            completions.add("list");
            // Add registered subcommands
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender)) {
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length > 1) {
            // If first arg is a registered subcommand, delegate completion
            String subCommandName = args[0].toLowerCase();
            if (subCommands.containsKey(subCommandName)) {
                SubCommand subCommand = subCommands.get(subCommandName);
                if (subCommand.hasPermission(sender)) {
                    return subCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return completions;
            }
            
            // ...existing code...
        }
        return completions;
    }

    @Override
    public String getDescription() {
        return "Manage and view lore collections";
    }

    public String getUsage() {
        return "/lore collection <view|claim|list|add|additem|removeitem> [args...]";
    }
}
