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
import org.fourz.RVNKLore.lore.item.collection.LoreCollection;

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
                LoreCollection itemCollection = cmgr.getCollectionSync(collectionId);
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
                LoreCollection claimColl = cmgr.getCollectionSync(collectionId);
                if (claimColl == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
                    return true;
                }

                // Double-claim guard
                if (cmgr.getPlayerProgressSync(claimTarget.getUniqueId(), collectionId) >= 1.0) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ " + claimTarget.getName() + " has already claimed this collection.");
                    return true;
                }

                // PDC inventory scan for required entries
                java.util.List<UUID> claimRequired = claimColl.getRequiredEntryIds();
                if (!claimRequired.isEmpty()) {
                    java.util.Set<UUID> inventoryEntries = cmgr.scanInventoryForEntryIds(claimTarget);
                    java.util.List<UUID> missing = new ArrayList<>();
                    for (UUID reqId : claimRequired) {
                        if (!inventoryEntries.contains(reqId)) {
                            missing.add(reqId);
                        }
                    }
                    if (!missing.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "⚠ Missing " + missing.size() + " item(s) from your inventory:");
                        for (UUID missingId : missing) {
                            LoreEntry missingEntry = plugin.getLoreManager().getLoreEntrySync(missingId);
                            String entryName = missingEntry != null ? missingEntry.getName() : missingId.toString().substring(0, 8) + "...";
                            sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + entryName);
                        }
                        return true;
                    }
                    // All items present — mark complete
                    cmgr.updatePlayerProgressSync(claimTarget.getUniqueId(), collectionId, 1.0);
                }

                boolean rewarded = cmgr.grantCollectionRewardSync(claimTarget.getUniqueId(), collectionId);
                if (rewarded) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Claimed rewards for collection: " + ChatColor.YELLOW + claimColl.getName());
                } else {
                    // Fallback: HEAD cosmetic rewards via CosmeticsManager
                    HeadCollection headColl = cosmeticItem.getCollection(collectionId);
                    if (headColl != null && headColl.getRewards().hasRewards()) {
                        cosmeticItem.awardCollectionRewards(claimTarget, headColl, headColl.getRewards());
                        sender.sendMessage(ChatColor.GREEN + "✓ Claimed rewards for collection: " + ChatColor.YELLOW + claimColl.getName());
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "⚠ No rewards defined for this collection.");
                    }
                }
                break;

            // NOTE: there is no "list" case here. "list" is a registered subcommand (see the
            // constructor), so the dispatch at the top of execute() always routes it to
            // LoreCollectionListSubCommand and any case here would be unreachable. One did exist
            // and had the *correct* entry-aware count while the reachable implementation did not,
            // which is part of how #1935 stayed hidden. Keep the count logic in
            // DisplayFactory.formatCollectionCount() so there is only one of it.

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
