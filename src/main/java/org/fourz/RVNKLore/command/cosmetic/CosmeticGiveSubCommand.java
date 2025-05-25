package org.fourz.RVNKLore.command.cosmetic;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.SubCommand;
import org.fourz.RVNKLore.cosmetic.*;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-command for administrative management of cosmetic heads.
 * Allows staff to give heads to players and manage the cosmetic system.
 */
public class CosmeticGiveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CosmeticManager cosmeticManager;

    public CosmeticGiveSubCommand(RVNKLore plugin, CosmeticManager cosmeticManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CosmeticGiveSubCommand");
        this.cosmeticManager = cosmeticManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // TODO Auto-generated method stub
        return false;
    }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rvnklore.admin.cosmetic.give")) {
            sender.sendMessage("&c✖ You don't have permission to use this command");
            return true;
        }

        if (args.length < 2) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "head":
                if (args.length < 3) {
                    sender.sendMessage("&c▶ Usage: /lore give head <player> <head_id>");
                    return true;
                }
                giveHead(sender, args[1], args[2]);
                break;
                
            case "collection":
                if (args.length < 3) {
                    sender.sendMessage("&c▶ Usage: /lore give collection <player> <collection_id>");
                    return true;
                }
                giveCollection(sender, args[1], args[2]);
                break;
                
            case "random":
                if (args.length < 2) {
                    sender.sendMessage("&c▶ Usage: /lore give random <player> [rarity]");
                    return true;
                }
                String rarity = args.length > 2 ? args[2] : null;
                giveRandomHead(sender, args[1], rarity);
                break;
                
            default:
                showUsage(sender);
                break;
        }

        return true;
    }

    /**
     * Give a specific head variant to a player.
     * @param sender The command sender (player or console)
     * @param playerName The name of the player to grant the head to
     * @param headId The ID of the head variant to grant
     */

    /**
     * Give a specific head variant to a player.
     */
    private void giveHead(CommandSender sender, String playerName, String headId) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("&c✖ Player not found: " + playerName);
            return;
        }

        HeadVariant variant = cosmeticManager.getHeadVariant(headId);
        if (variant == null) {
            sender.sendMessage("&c✖ Head variant not found: " + headId);
            return;
        }

        if (cosmeticManager.playerOwnsHead(target, headId)) {
            sender.sendMessage("&e⚠ Player " + target.getName() + " already owns: " + variant.getName());
            return;
        }

        if (cosmeticManager.grantHeadToPlayer(target, headId)) {
            sender.sendMessage("&a✓ Granted " + variant.getRarity().getColoredDisplayName() + 
                             " &f" + variant.getName() + " &ato " + target.getName());
            target.sendMessage("&a✓ You received a new head: " + variant.getRarity().getColoredDisplayName() + 
                             " &f" + variant.getName());
                             
            logger.info("Admin " + sender.getName() + " granted head " + headId + " to " + target.getName());
        } else {
            sender.sendMessage("&c✖ Failed to grant head to player");
        }
    }

    /**
     * Give all heads from a collection to a player.
     */
    private void giveCollection(CommandSender sender, String playerName, String collectionId) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("&c✖ Player not found: " + playerName);
            return;
        }

        HeadCollection collection = cosmeticManager.getCollection(collectionId);
        if (collection == null) {
            sender.sendMessage("&c✖ Collection not found: " + collectionId);
            return;
        }

        int granted = 0;
        int skipped = 0;

        for (HeadVariant variant : collection.getAllHeads()) {
            if (!cosmeticManager.playerOwnsHead(target, variant.getId())) {
                if (cosmeticManager.grantHeadToPlayer(target, variant.getId())) {
                    granted++;
                } else {
                    skipped++;
                }
            } else {
                skipped++;
            }
        }

        sender.sendMessage("&a✓ Granted collection '" + collection.getName() + "' to " + target.getName());
        sender.sendMessage("&f   " + granted + " heads granted, " + skipped + " skipped");
        
        target.sendMessage("&a✓ You received the collection: &f" + collection.getName());
        target.sendMessage("&f   " + granted + " new heads added to your collection!");
        
        logger.info("Admin " + sender.getName() + " granted collection " + collectionId + 
                   " to " + target.getName() + " (" + granted + " heads)");
    }

    /**
     * Give a random head of specified rarity to a player.
     */
    private void giveRandomHead(CommandSender sender, String playerName, String rarityName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("&c✖ Player not found: " + playerName);
            return;
        }

        HeadRarity targetRarity = null;
        if (rarityName != null) {
            targetRarity = HeadRarity.fromDisplayName(rarityName);
            if (targetRarity == null) {
                sender.sendMessage("&c✖ Unknown rarity: " + rarityName);
                sender.sendMessage("&7   Available rarities: Common, Uncommon, Rare, Epic, Legendary, Mythic, Event");
                return;
            }
        }

        List<HeadVariant> availableHeads = new ArrayList<>();
        
        // Collect all available heads matching criteria
        for (HeadCollection collection : cosmeticManager.getAvailableCollections()) {
            for (HeadVariant variant : collection.getAllHeads()) {
                // Skip if player already owns this head
                if (cosmeticManager.playerOwnsHead(target, variant.getId())) {
                    continue;
                }
                
                // Skip if rarity doesn't match filter
                if (targetRarity != null && variant.getRarity() != targetRarity) {
                    continue;
                }
                
                // Skip admin-only heads unless sender is console
                if (variant.getRarity() == HeadRarity.ADMIN && sender instanceof Player) {
                    continue;
                }
                
                availableHeads.add(variant);
            }
        }

        if (availableHeads.isEmpty()) {
            sender.sendMessage("&e⚠ No available heads to grant" + 
                             (targetRarity != null ? " of rarity: " + targetRarity.getDisplayName() : ""));
            return;
        }

        // Select random head weighted by rarity
        HeadVariant selectedHead = selectRandomHead(availableHeads);
        
        if (cosmeticManager.grantHeadToPlayer(target, selectedHead.getId())) {
            sender.sendMessage("&a✓ Granted random head to " + target.getName() + ": " + 
                             selectedHead.getRarity().getColoredDisplayName() + " &f" + selectedHead.getName());
            target.sendMessage("&a✓ You received a random head: " + 
                             selectedHead.getRarity().getColoredDisplayName() + " &f" + selectedHead.getName());
                             
            logger.info("Admin " + sender.getName() + " granted random head " + selectedHead.getId() + 
                       " to " + target.getName());
        } else {
            sender.sendMessage("&c✖ Failed to grant random head to player");
        }
    }

    /**
     * Select a random head from the list with rarity-based weighting.
     */
    private HeadVariant selectRandomHead(List<HeadVariant> heads) {
        if (heads.isEmpty()) {
            return null;
        }
        
        if (heads.size() == 1) {
            return heads.get(0);
        }

        // Calculate total weight based on inverse rarity
        double totalWeight = 0.0;
        for (HeadVariant head : heads) {
            // Higher rarity = lower weight (harder to get)
            totalWeight += getHeadWeight(head.getRarity());
        }

        // Select random head based on weight
        double random = Math.random() * totalWeight;
        double currentWeight = 0.0;

        for (HeadVariant head : heads) {
            currentWeight += getHeadWeight(head.getRarity());
            if (random <= currentWeight) {
                return head;
            }
        }

        // Fallback to last head if somehow we didn't select one
        return heads.get(heads.size() - 1);
    }

    /**
     * Get weight for random selection based on rarity.
     */
    private double getHeadWeight(HeadRarity rarity) {
        switch (rarity) {
            case COMMON: return 10.0;
            case UNCOMMON: return 7.0;
            case RARE: return 4.0;
            case EPIC: return 2.0;
            case LEGENDARY: return 1.0;
            case MYTHIC: return 0.5;
            case EVENT: return 1.5;
            case ADMIN: return 0.1;
            default: return 5.0;
        }
    }

    /**
     * Show command usage information.
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage("&c▶ Cosmetic Give Commands:");
        sender.sendMessage("&f/lore give head <player> <head_id> &7- Give specific head");
        sender.sendMessage("&f/lore give collection <player> <collection_id> &7- Give entire collection");
        sender.sendMessage("&f/lore give random <player> [rarity] &7- Give random head");
        sender.sendMessage("");
        sender.sendMessage("&7   Requires permission: &frvnklore.admin.cosmetic.give");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("rvnklore.admin.cosmetic.give")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("head");
            completions.add("collection");
            completions.add("random");
        } else if (args.length == 2) {
            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            
            if ("head".equals(action)) {
                // Add head variant IDs
                for (HeadCollection collection : cosmeticManager.getAllCollections()) {
                    for (HeadVariant variant : collection.getAllHeads()) {
                        completions.add(variant.getId());
                    }
                }
            } else if ("collection".equals(action)) {
                // Add collection IDs
                for (HeadCollection collection : cosmeticManager.getAllCollections()) {
                    completions.add(collection.getId());
                }
            } else if ("random".equals(action)) {
                // Add rarity names
                for (HeadRarity rarity : HeadRarity.values()) {
                    completions.add(rarity.getDisplayName());
                }
            }
        }

        return completions;
    }

    @Override
    public String getDescription() {
        return "Give cosmetic heads to players (admin only)";
    }

    public String getUsage() {
        return "/lore give [head|collection|random] <player> [args...]";
    }
}
