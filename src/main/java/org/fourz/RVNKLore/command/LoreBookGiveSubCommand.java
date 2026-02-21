package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.item.book.BookRarity;
import org.fourz.RVNKLore.lore.item.book.LoreBookManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for giving lore books to players.
 * Usage: /lore book give <player> <entry_id> [rarity]
 */
public class LoreBookGiveSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreBookManager bookManager;

    public LoreBookGiveSubCommand(RVNKLore plugin, LoreBookManager bookManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreBookGiveSubCommand");
        this.bookManager = bookManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore book give <player> <entry_id> [rarity]");
            sender.sendMessage(ChatColor.GRAY + "   Rarities: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, UNIQUE");
            return true;
        }

        String playerName = args[0];
        String entryArg = args[1];
        BookRarity rarity = args.length > 2 ? BookRarity.fromString(args[2]) : null;

        // Find target player
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player not found: " + playerName);
            return true;
        }

        // Resolve slug name to entry ID (fall back to treating arg as ID)
        LoreEntry namedEntry = plugin.getLoreManager().getLoreEntryByNameSync(entryArg);
        String entryId = (namedEntry != null) ? namedEntry.getId() : entryArg;

        sender.sendMessage(ChatColor.YELLOW + "⚙ Creating lore book...");

        // Create and give the book asynchronously
        if (rarity != null) {
            // Specific rarity requested
            bookManager.createLoreBookById(entryId).thenAccept(optBook -> {
                if (optBook.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "✖ Lore entry not found: " + entryId);
                    return;
                }

                // Create with specific rarity
                String loreEntryId = bookManager.getLoreEntryId(optBook.get());
                plugin.getLoreManager().getLoreById(loreEntryId).ifPresent(entry -> {
                    var book = bookManager.createLoreBook(entry, rarity);
                    if (book != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            target.getInventory().addItem(book);
                            sender.sendMessage(ChatColor.GREEN + "✓ Gave " + rarity.getColoredName() + ChatColor.GREEN + " lore book to " + target.getName());
                            target.sendMessage(ChatColor.GREEN + "You received a lore book: " + rarity.getColor() + entry.getName());
                        });
                    }
                });
            }).exceptionally(ex -> {
                logger.error("Error creating lore book", (Exception) ex);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred while creating the book.");
                return null;
            });
        } else {
            // Auto-determine rarity
            bookManager.giveBookToPlayer(target, entryId).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Gave lore book to " + target.getName());

                    // Get entry name for the message
                    bookManager.createLoreBookById(entryId).thenAccept(optBook -> {
                        if (optBook.isPresent()) {
                            var meta = optBook.get().getItemMeta();
                            if (meta != null && meta.hasDisplayName()) {
                                target.sendMessage(ChatColor.GREEN + "You received a lore book: " + meta.getDisplayName());
                            }
                        }
                    });
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Lore entry not found: " + entryId);
                }
            }).exceptionally(ex -> {
                logger.error("Error giving lore book", (Exception) ex);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred while giving the book.");
                return null;
            });
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Player names
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Slug names (entry names)
            String partial = args[1].toLowerCase();
            for (var entry : plugin.getLoreManager().getAllLoreEntriesSync()) {
                if (entry.getName() != null && entry.getName().toLowerCase().startsWith(partial)) {
                    completions.add(entry.getName());
                }
            }
        } else if (args.length == 3) {
            // Rarity options
            String partial = args[2].toUpperCase();
            for (BookRarity rarity : BookRarity.values()) {
                if (rarity.name().startsWith(partial)) {
                    completions.add(rarity.name().toLowerCase());
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.book.give") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public String getDescription() {
        return "Give a lore book to a player";
    }
}
