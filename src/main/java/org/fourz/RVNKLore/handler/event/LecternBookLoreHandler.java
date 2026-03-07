package org.fourz.RVNKLore.handler.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handler for creating lore entries when players place written books on lecterns.
 * A signed book placed on an empty lectern is registered as a library lore entry.
 */
public class LecternBookLoreHandler extends DefaultLoreHandler {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public LecternBookLoreHandler(RVNKLore plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing lectern book lore handler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.WRITTEN_BOOK) return;

        // Only trigger when placing a book on an empty lectern
        if (block.getState() instanceof Lectern) {
            Lectern lectern = (Lectern) block.getState();
            if (lectern.getInventory().getItem(0) != null) return;
        }

        if (!player.hasPermission("rvnklore.library.submit")) return;

        BookMeta bookMeta = (BookMeta) itemInHand.getItemMeta();
        if (bookMeta == null || bookMeta.getTitle() == null) return;

        createLibraryEntry(player, block, bookMeta);
    }

    private void createLibraryEntry(Player player, Block lecternBlock, BookMeta bookMeta) {
        String bookTitle = bookMeta.getTitle();
        String bookAuthor = bookMeta.getAuthor();
        List<String> pages = bookMeta.getPages();

        // Build description from book pages
        StringBuilder description = new StringBuilder();
        for (String page : pages) {
            if (description.length() > 0) description.append(" ");
            description.append(ChatColor.stripColor(page));
            if (description.length() >= MAX_DESCRIPTION_LENGTH) break;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description.setLength(MAX_DESCRIPTION_LENGTH);
            description.append("...");
        }

        String entryName = (bookAuthor != null && !bookAuthor.isEmpty())
            ? bookTitle + " by " + bookAuthor : bookTitle;

        LoreEntry entry = LoreEntry.createLocationLore(
            entryName, description.toString(), LoreType.ITEM,
            lecternBlock.getLocation(), player
        );

        entry.addMetadata("sub_type", "library_book");
        entry.addMetadata("book_title", bookTitle);
        if (bookAuthor != null) entry.addMetadata("book_author", bookAuthor);
        entry.addMetadata("page_count", String.valueOf(pages.size()));
        entry.addMetadata("source", "lectern");
        entry.addMetadata("placed_at", dateFormat.format(new Date()));
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());

        boolean autoApprove = plugin.getConfigManager().getConfig()
            .getBoolean("library.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));

        plugin.getLoreManager().addLoreEntry(entry).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Book '" + bookTitle + "' has been " +
                        (entry.isApproved() ? "added to the lore library." : "submitted for approval."));
                    logger.info("Library lore entry created: " + entryName + " by " + player.getName());
                } else {
                    logger.debug("Library entry not saved (may already exist): " + entryName);
                }
            });
        });
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Library Entry");

            String author = entry.getMetadata("book_author");
            if (author != null) {
                lore.add(ChatColor.GRAY + "Author: " + ChatColor.WHITE + author);
            }

            String pageCount = entry.getMetadata("page_count");
            if (pageCount != null) {
                lore.add(ChatColor.GRAY + "Pages: " + ChatColor.WHITE + pageCount);
            }

            lore.add("");
            // Show first ~100 chars of description as preview
            String desc = entry.getDescription();
            if (desc.length() > 100) {
                desc = desc.substring(0, 100) + "...";
            }
            lore.add(ChatColor.WHITE + desc);

            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Lectern: " + ChatColor.WHITE +
                    entry.getLocation().getWorld().getName() + " at " +
                    (int) entry.getLocation().getX() + ", " +
                    (int) entry.getLocation().getY() + ", " +
                    (int) entry.getLocation().getZ());
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ITEM;
    }
}
