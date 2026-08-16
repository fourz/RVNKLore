package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.LoreCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore collection delete command.
 *
 * The counterpart to `add`, which had none — a collection created by typo was permanent from the
 * command surface and stayed in every operator's `list` forever (#1957). Console-capable for the
 * same reason removeitem became console-capable in #1935: a headless seeding path needs a headless
 * correction path, because seeding is when mistakes get made.
 *
 * This is destructive in a way removeitem is not, so a collection holding anything requires an
 * explicit `confirm` token. An empty one deletes without ceremony — there is nothing to lose.
 */
public class LoreCollectionDeleteSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CollectionManager collectionManager;

    public LoreCollectionDeleteSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionDeleteSubCommand");
        this.collectionManager = plugin.getLoreManager().getItemManager().getCollectionManager();
    }

    @Override
    public String getDescription() {
        return "Delete a collection and its contents";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.collection.delete") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection delete <collection_id> [confirm]");
            sender.sendMessage(ChatColor.GRAY + "   Deletes the collection, its members, rewards and player progress");
            sender.sendMessage(ChatColor.GRAY + "   'confirm' is required when the collection is not empty");
            return true;
        }

        String collectionId = args[0].toLowerCase();
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");

        LoreCollection collection = collectionManager.getCollectionSync(collectionId);
        if (collection == null) {
            sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore collection list to see available IDs");
            return true;
        }

        String contents = DisplayFactory.formatCollectionCount(collection);
        boolean isEmpty = collection.getItemCount() == 0 && collection.getRequiredEntryCount() == 0;

        if (!isEmpty && !confirmed) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ '" + collection.getName() + "' holds " + contents + ".");
            sender.sendMessage(ChatColor.GRAY + "   Deleting also removes its rewards and every player's progress.");
            sender.sendMessage(ChatColor.GRAY + "   Run: " + ChatColor.WHITE + "/lore collection delete " + collectionId + " confirm");
            return true;
        }

        if (collectionManager.deleteCollectionSync(collectionId)) {
            sender.sendMessage(ChatColor.GREEN + "✓ Deleted collection: " + collection.getName() + " (" + collectionId + ")");
            sender.sendMessage(ChatColor.GRAY + "   " + (isEmpty
                    ? "It was empty; cleared any rewards and player progress"
                    : "Removed " + contents + ", plus rewards and player progress"));
            logger.info(sender.getName() + " deleted collection " + collectionId + " (held " + contents + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to delete collection: " + collectionId);
            sender.sendMessage(ChatColor.GRAY + "   Nothing was removed - see the server log for the cause");
        }
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!hasPermission(sender)) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            collectionManager.getAllCollectionsSync().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .forEach(completions::add);
        } else if (args.length == 2) {
            completions.add("confirm");
        }

        return completions;
    }
}
