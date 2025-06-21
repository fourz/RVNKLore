package org.fourz.RVNKLore.command.output;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Factory class for standardized command output formatting.
 * Provides consistent display patterns for various lore-related data types.
 */
public class DisplayFactory {
    // Static constants for configuration
    private static final int ITEMS_PER_PAGE = 10;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    /**
     * Display a paginated list of items
     *
     * @param sender The command sender
     * @param itemManager The item manager containing item data
     * @param page The page number to display (1-based)
     * @param newestFirst Sort by newest first if true, oldest first if false
     * @return true if the display was successful
     */
    public static boolean displayItemList(CommandSender sender, ItemManager itemManager, int page, boolean newestFirst) {
        // Get all items with their properties
        List<ItemProperties> items = itemManager.getAllItemsWithProperties();
        
        // Sort items by creation date
        if (newestFirst) {
            items.sort(Comparator.comparing(ItemProperties::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        } else {
            items.sort(Comparator.comparing(ItemProperties::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        
        // Calculate pagination
        int totalItems = items.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages)); // Ensure page is in valid range
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        
        // Display header
        sender.sendMessage(ChatColor.GOLD + "===== Item List (Page " + page + "/" + Math.max(1, totalPages) + ") =====");
        
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No items found");
            return true;
        }
        
        // Display items for current page
        for (int i = startIndex; i < endIndex; i++) {
            ItemProperties item = items.get(i);
            String dateStr = item.getCreatedAt() != null ? 
                DATE_FORMAT.format(new Date(item.getCreatedAt())) : "Unknown";
            
            sender.sendMessage(
                ChatColor.WHITE + item.getDisplayName() + 
                ChatColor.GRAY + " (" + item.getItemType() + ") - " + 
                ChatColor.YELLOW + dateStr
            );
        }
        
        // Display navigation help
        if (totalPages > 1) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + 
                "/lore item list <page>" + ChatColor.GRAY + " to navigate pages");
        }
        
        return true;
    }
    
    /**
     * Display detailed information about a lore entry
     *
     * @param sender The command sender
     * @param entry The lore entry to display
     * @return true if the display was successful
     */
    public static boolean displayLoreEntry(CommandSender sender, LoreEntry entry) {
        sender.sendMessage(ChatColor.GOLD + "===== Lore Info: " + entry.getName() + " =====");
        sender.sendMessage(ChatColor.YELLOW + "Type: " + (entry.getType() != null ? entry.getType().name() : "Unknown"));
        sender.sendMessage(ChatColor.YELLOW + "ID: " + entry.getNumericId() + " (UUID: " + entry.getId() + ")");
        
        if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Description: " + entry.getDescription());
        }
        
        if (entry.getSubmittedBy() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Submitted by: " + entry.getSubmittedBy());
        }
        
        if (entry.hasMetadata()) {
            sender.sendMessage(ChatColor.YELLOW + "Metadata:");
            entry.getAllMetadata().forEach((k, v) ->
                sender.sendMessage(ChatColor.GRAY + "  " + k + ": " + v)
            );
        }
        
        return true;
    }
    
    /**
     * Display detailed information about a lore entry using DTO
     *
     * @param sender The command sender
     * @param dto The lore entry DTO to display
     * @return true if the display was successful
     */
    public static boolean displayLoreEntryDTO(CommandSender sender, LoreEntryDTO dto) {
        if (dto == null) {
            sender.sendMessage(ChatColor.RED + "✖ Lore entry not found");
            return false;
        }
        
        LoreEntry entry = LoreEntry.fromDTO(dto);
        return displayLoreEntry(sender, entry);
    }
      /**
     * Display detailed information about an item
     *
     * @param sender The command sender
     * @param item The item to display
     * @param itemName The name of the item for display purposes
     * @return true if the display was successful
     */
    public static boolean displayItem(CommandSender sender, ItemStack item, String itemName) {
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + itemName);
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        sender.sendMessage(ChatColor.GOLD + "===== Item Info: " + itemName + " =====");
        sender.sendMessage(ChatColor.YELLOW + "Material: " + item.getType());
        
        if (meta != null) {
            if (meta.hasDisplayName()) {
                sender.sendMessage(ChatColor.YELLOW + "Display Name: " + meta.getDisplayName());
            }
            
            if (meta.hasLore()) {
                sender.sendMessage(ChatColor.YELLOW + "Lore:");
                for (String line : meta.getLore()) {
                    sender.sendMessage(ChatColor.GRAY + "  " + line);
                }
            }
            
            if (meta.hasCustomModelData()) {
                sender.sendMessage(ChatColor.YELLOW + "Custom Model Data: " + meta.getCustomModelData());
            }
            
            if (meta.hasEnchants()) {
                sender.sendMessage(ChatColor.YELLOW + "Enchantments:");
                for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    String enchantName = entry.getKey().toString().replace("Enchantment[", "").replace("]", "");
                    sender.sendMessage(ChatColor.GRAY + "  " + formatEnchantmentName(enchantName) + " " + entry.getValue());
                }
            }
        }
        
        return true;
    }
      /**
     * Display a paginated list of collections
     *
     * @param sender The command sender
     * @param collections The list of collections to display
     * @return true if the display was successful
     */
    public static boolean displayCollectionList(CommandSender sender, List<ItemCollection> collections) {
        sender.sendMessage(ChatColor.GOLD + "===== Collections (Newest First) =====");
        if (collections == null || collections.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No collections found");
            return true;
        }
        for (ItemCollection collection : collections) {
            String dateStr = collection.getCreatedAt() > 0 ? 
                DATE_FORMAT.format(new Date(collection.getCreatedAt())) : "Unknown";
            
            sender.sendMessage(ChatColor.WHITE + collection.getName() + ChatColor.GRAY + " (" + collection.getId() + ")"
                    + ChatColor.YELLOW + " - " + dateStr);
            
            if (collection.getDescription() != null && !collection.getDescription().isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "   " + collection.getDescription());
            }
            
            sender.sendMessage(ChatColor.GRAY + "   " + collection.getItemCount() + " items • " +
                    (collection.getThemeId() != null ? collection.getThemeId() : "custom"));
        }
        sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection view <id> " + ChatColor.GRAY + "for details");
        return true;
    }

    /**
     * Display a list of collections using DTOs
     *
     * @param sender The command sender
     * @param collections The list of collection DTOs to display
     * @return true if the display was successful
     */
    public static boolean displayCollectionListDTO(CommandSender sender, List<ItemCollectionDTO> collections) {
        sender.sendMessage(ChatColor.GOLD + "===== Collections (Newest First) =====");
        if (collections == null || collections.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No collections found");
            return true;
        }
        
        for (ItemCollectionDTO collection : collections) {
            String dateStr = collection.getCreatedAt() > 0 ? 
                DATE_FORMAT.format(new Date(collection.getCreatedAt())) : "Unknown";
            
            sender.sendMessage(ChatColor.WHITE + collection.getName() + ChatColor.GRAY + " (" + collection.getId() + ")"
                    + ChatColor.YELLOW + " - " + dateStr);
            
            if (collection.getDescription() != null && !collection.getDescription().isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "   " + collection.getDescription());
            }
            
            // Get item count from serialized items or direct count
            int itemCount = collection.getSerializedItems() != null ? collection.getSerializedItems().size() : 0;
            
            sender.sendMessage(ChatColor.GRAY + "   " + itemCount + " items • " +
                    (collection.getThemeId() != null ? collection.getThemeId() : "custom"));
            
            // Display progress if available
            if (collection.getProgress() > 0) {
                String progressText = String.format("%.1f%%", collection.getProgress() * 100);
                sender.sendMessage(ChatColor.GRAY + "   Progress: " + ChatColor.YELLOW + progressText);
                
                if (collection.getCompletionTime() > 0) {
                    String completionDate = DATE_FORMAT.format(new Date(collection.getCompletionTime()));
                    sender.sendMessage(ChatColor.GRAY + "   Completed: " + ChatColor.GREEN + completionDate);
                }
            }
        }
        sender.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection view <id> " + ChatColor.GRAY + "for details");
        return true;
    }    /**
     * Convert a list of DTOs to domain objects for display
     * 
     * @param dtos The list of collection DTOs
     * @return A list of ItemCollection domain objects
     */
    private static List<ItemCollection> convertDTOsToCollections(List<ItemCollectionDTO> dtos) {
        List<ItemCollection> collections = new ArrayList<>();
        if (dtos == null || dtos.isEmpty()) {
            return collections;
        }
        
        for (ItemCollectionDTO dto : dtos) {
            if (dto != null) {
                collections.add(dto.toCollection());
            }
        }
        
        return collections;
    }
    
    /**
     * Display a list of collections using DTOs by converting to domain objects
     *
     * @param sender The command sender
     * @param collections The list of collection DTOs to display
     * @return true if the display was successful
     */
    public static boolean displayCollectionListFromDTOs(CommandSender sender, List<ItemCollectionDTO> collections) {
        return displayCollectionList(sender, convertDTOsToCollections(collections));
    }

    /**
     * Format enchantment name for user-friendly display
     * Converts snake_case to Title Case
     * 
     * @param enchantName Raw enchantment name
     * @return Formatted enchantment name
     */
    private static String formatEnchantmentName(String enchantName) {
        if (enchantName == null || enchantName.isEmpty()) {
            return "";
        }
        
        String[] parts = enchantName.split("_");
        StringBuilder formatted = new StringBuilder();
        
        for (String part : parts) {
            if (part.length() > 0) {
                formatted.append(part.substring(0, 1).toUpperCase())
                         .append(part.substring(1).toLowerCase())
                         .append(" ");
            }
        }
        
        return formatted.toString().trim();
    }
    
    /**
     * Display a paginated list with standard formatting
     * 
     * @param sender The command sender
     * @param title The title for the list
     * @param items The list of items to display
     * @param page The page number (1-based)
     * @param itemsPerPage Number of items per page
     * @param formatter Function to format each item as a string
     * @param <T> The type of items in the list
     * @return true if the display was successful
     */
    public static <T> boolean displayPaginatedList(CommandSender sender, String title, List<T> items, int page, int itemsPerPage, Function<T, String> formatter) {
        if (items == null) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No items found");
            return true;
        }
        
        int totalItems = items.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        // Validate page number and provide feedback if needed
        if (page < 1) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Page number must be positive. Showing first page.");
            page = 1;
        } else if (page > totalPages && totalPages > 0) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Page number exceeds max pages. Showing last page.");
            page = totalPages;
        }

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        sender.sendMessage(ChatColor.GOLD + "===== " + title + " (Page " + page + "/" + Math.max(1, totalPages) + ") =====");
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ No items found");
            return true;
        }
        
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(formatter.apply(items.get(i)));
        }
        
        if (totalPages > 1) {
            sender.sendMessage(ChatColor.GRAY + "Use /lore item list <page> to navigate pages");
        }
        
        return true;
    }
    
    /**
     * Display paginated list of LoreEntries
     * 
     * @param sender The command sender
     * @param entries The list of lore entries to display
     * @param page The page number (1-based)
     * @param title The title for the list
     * @return true if the display was successful
     */
    public static boolean displayLoreEntryList(CommandSender sender, List<LoreEntry> entries, int page, String title) {
        return displayPaginatedList(
            sender,
            title != null ? title : "Lore Entries",
            entries,
            page,
            ITEMS_PER_PAGE,
            entry -> ChatColor.WHITE + entry.getName() + 
                     ChatColor.GRAY + " (" + entry.getType() + ") - " + 
                     ChatColor.YELLOW + (entry.getCreatedAt() != null ? 
                         DATE_FORMAT.format(new Date(entry.getCreatedAt().getTime())) : "Unknown")
        );
    }
    
    /**
     * Display paginated list of LoreEntry DTOs
     * 
     * @param sender The command sender
     * @param dtos The list of lore entry DTOs to display
     * @param page The page number (1-based)
     * @param title The title for the list
     * @return true if the display was successful
     */
    public static boolean displayLoreEntryDTOList(CommandSender sender, List<LoreEntryDTO> dtos, int page, String title) {
        List<LoreEntry> entries = new ArrayList<>();
        if (dtos != null) {
            for (LoreEntryDTO dto : dtos) {
                if (dto != null) {
                    entries.add(LoreEntry.fromDTO(dto));
                }
            }
        }
        
        return displayLoreEntryList(sender, entries, page, title);
    }
}
