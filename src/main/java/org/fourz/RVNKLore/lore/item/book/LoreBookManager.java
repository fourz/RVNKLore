package org.fourz.RVNKLore.lore.item.book;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the creation and handling of lore books.
 * Lore books are written books that contain lore entry content.
 */
public class LoreBookManager {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreManager loreManager;

    // NamespacedKeys for persistent data
    private final NamespacedKey loreEntryIdKey;
    private final NamespacedKey bookRarityKey;
    private final NamespacedKey signedByKey;
    private final NamespacedKey createdAtKey;

    // Cache of generated books
    private final Map<String, ItemStack> bookCache = new ConcurrentHashMap<>();

    // Configuration
    private static final int BASE_CUSTOM_MODEL_DATA = 7000;
    private static final int MAX_CHARS_PER_PAGE = 256;
    private static final int MAX_PAGES = 100;

    public LoreBookManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreBookManager");
        this.loreManager = plugin.getLoreManager();

        // Initialize namespaced keys
        this.loreEntryIdKey = new NamespacedKey(plugin, "lore_entry_id");
        this.bookRarityKey = new NamespacedKey(plugin, "book_rarity");
        this.signedByKey = new NamespacedKey(plugin, "signed_by");
        this.createdAtKey = new NamespacedKey(plugin, "created_at");

        logger.info("LoreBookManager initialized");
    }

    /**
     * Create a lore book from a lore entry.
     *
     * @param entry The lore entry to create a book from
     * @param rarity The rarity of the book
     * @return The created book ItemStack
     */
    public ItemStack createLoreBook(LoreEntry entry, BookRarity rarity) {
        if (entry == null) {
            logger.warning("Cannot create book from null entry");
            return null;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta == null) {
            logger.error("Failed to get BookMeta for written book", null);
            return null;
        }

        // Set book title with rarity color
        String title = rarity.getColor() + entry.getName();
        if (title.length() > 32) {
            title = title.substring(0, 29) + "...";
        }
        meta.setTitle(title);

        // Set author
        String author = entry.getSubmittedBy() != null ? entry.getSubmittedBy() : "Unknown";
        meta.setAuthor(author);

        // Set generation (original, copy, etc.)
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        // Build book pages
        List<String> pages = buildBookPages(entry, rarity);
        meta.setPages(pages);

        // Set display name with rarity
        meta.setDisplayName(rarity.getColor() + "" + ChatColor.BOLD + entry.getName());

        // Set item lore (the description shown in inventory)
        List<String> itemLore = buildItemLore(entry, rarity);
        meta.setLore(itemLore);

        // Set custom model data for resource packs
        meta.setCustomModelData(BASE_CUSTOM_MODEL_DATA + rarity.getCustomModelDataOffset());

        // Add enchantment glint for higher rarities
        if (rarity.hasEnchantmentGlint()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide additional flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Set persistent data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(loreEntryIdKey, PersistentDataType.STRING, entry.getId());
        pdc.set(bookRarityKey, PersistentDataType.STRING, rarity.name());
        pdc.set(createdAtKey, PersistentDataType.LONG, System.currentTimeMillis());

        book.setItemMeta(meta);

        // Cache the book
        bookCache.put(entry.getId(), book.clone());

        logger.debug("Created lore book: " + entry.getName() + " [" + rarity.name() + "]");
        return book;
    }

    /**
     * Create a lore book with automatic rarity determination.
     *
     * @param entry The lore entry
     * @return The created book
     */
    public ItemStack createLoreBook(LoreEntry entry) {
        BookRarity rarity = determineRarity(entry);
        return createLoreBook(entry, rarity);
    }

    /**
     * Create a lore book asynchronously by entry ID.
     *
     * @param entryId The lore entry ID (full or partial)
     * @return CompletableFuture containing the book, or empty if not found
     */
    public CompletableFuture<Optional<ItemStack>> createLoreBookById(String entryId) {
        // Check cache first
        if (bookCache.containsKey(entryId)) {
            return CompletableFuture.completedFuture(Optional.of(bookCache.get(entryId).clone()));
        }

        return CompletableFuture.supplyAsync(() -> {
            // Try to get by full UUID
            try {
                UUID uuid = UUID.fromString(entryId);
                Optional<LoreEntry> entry = loreManager.getLoreEntry(uuid).join();
                if (entry.isPresent()) {
                    return entry.map(this::createLoreBook);
                }
            } catch (IllegalArgumentException e) {
                // Not a valid UUID, try prefix match
            }

            // Try exact ID match
            Optional<LoreEntry> entry = loreManager.getLoreById(entryId);
            if (entry.isPresent()) {
                return entry.map(this::createLoreBook);
            }

            // Try short ID prefix match (tab completion provides 8-char short IDs)
            String prefix = entryId.toLowerCase();
            List<LoreEntry> matches = loreManager.findLoreEntriesSync(prefix);
            List<LoreEntry> idMatches = matches.stream()
                    .filter(e -> e.getId().toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());

            if (idMatches.size() == 1) {
                return Optional.of(createLoreBook(idMatches.get(0)));
            }

            return Optional.empty();
        });
    }

    /**
     * Give a lore book to a player.
     *
     * @param player The player to give the book to
     * @param entryId The lore entry ID
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> giveBookToPlayer(Player player, String entryId) {
        return createLoreBookById(entryId).thenApply(optBook -> {
            if (optBook.isEmpty()) {
                return false;
            }

            ItemStack book = optBook.get();

            // Add to player inventory on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(book);
                if (!overflow.isEmpty()) {
                    // Drop at player's feet if inventory is full
                    player.getWorld().dropItemNaturally(player.getLocation(), book);
                    player.sendMessage(ChatColor.YELLOW + "Your inventory was full. The book was dropped at your feet.");
                }
            });

            return true;
        });
    }

    /**
     * Sign a lore book with the given player as the author.
     *
     * @param book The book to sign
     * @param signer The player signing the book
     * @return The signed book, or null if invalid
     */
    public ItemStack signBook(ItemStack book, Player signer) {
        if (!isLoreBook(book)) {
            return null;
        }

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return null;

        // Update author
        meta.setAuthor(signer.getName());

        // Add signed_by to persistent data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(signedByKey, PersistentDataType.STRING, signer.getUniqueId().toString());

        book.setItemMeta(meta);
        return book;
    }

    /**
     * Check if an ItemStack is a lore book.
     *
     * @param item The item to check
     * @return true if it's a lore book
     */
    public boolean isLoreBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return false;
        }

        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(loreEntryIdKey, PersistentDataType.STRING);
    }

    /**
     * Get the lore entry ID from a lore book.
     *
     * @param book The lore book
     * @return The entry ID, or null if not a lore book
     */
    public String getLoreEntryId(ItemStack book) {
        if (!isLoreBook(book)) return null;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(loreEntryIdKey, PersistentDataType.STRING);
    }

    /**
     * Get the rarity of a lore book.
     *
     * @param book The lore book
     * @return The BookRarity, or COMMON if not found
     */
    public BookRarity getBookRarity(ItemStack book) {
        if (!isLoreBook(book)) return BookRarity.COMMON;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return BookRarity.COMMON;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String rarityStr = pdc.get(bookRarityKey, PersistentDataType.STRING);
        return BookRarity.fromString(rarityStr);
    }

    /**
     * Get all obtainable lore books (entries that are approved).
     *
     * @return CompletableFuture with list of available entry IDs and names
     */
    public CompletableFuture<List<BookListEntry>> getObtainableBooks() {
        return CompletableFuture.supplyAsync(() -> {
            List<BookListEntry> books = new ArrayList<>();
            List<LoreEntry> entries = loreManager.getAllLoreEntriesSync();

            for (LoreEntry entry : entries) {
                if (entry.isApproved()) {
                    BookRarity rarity = determineRarity(entry);
                    books.add(new BookListEntry(
                        entry.getId(),
                        entry.getName(),
                        entry.getType(),
                        rarity
                    ));
                }
            }

            // Sort by type then name
            books.sort(Comparator
                .comparing((BookListEntry b) -> b.type().name())
                .thenComparing(BookListEntry::name));

            return books;
        });
    }

    /**
     * Record for book list display.
     */
    public record BookListEntry(String id, String name, LoreType type, BookRarity rarity) {
        public String getShortId() {
            return id.length() >= 8 ? id.substring(0, 8) : id;
        }
    }

    /**
     * Determine the rarity of a lore entry.
     */
    private BookRarity determineRarity(LoreEntry entry) {
        boolean hasLocation = entry.getLocation() != null;
        int descLength = entry.getDescription() != null ? entry.getDescription().length() : 0;

        // Check metadata for first discovery
        boolean isFirstDiscovery = entry.hasMetadata("first_discovery") &&
            "true".equalsIgnoreCase(entry.getMetadata("first_discovery"));

        // Type-based adjustments
        LoreType type = entry.getType();
        if (type == LoreType.QUEST || type == LoreType.EVENT) {
            // Quests and events are at least rare
            BookRarity baseRarity = BookRarity.determineRarity(isFirstDiscovery, hasLocation, descLength);
            if (baseRarity.ordinal() < BookRarity.RARE.ordinal()) {
                return BookRarity.RARE;
            }
            return baseRarity;
        }

        return BookRarity.determineRarity(isFirstDiscovery, hasLocation, descLength);
    }

    /**
     * Build the pages of a lore book.
     */
    private List<String> buildBookPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();

        // Title page
        StringBuilder titlePage = new StringBuilder();
        titlePage.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                .append(entry.getName()).append("\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append("Type: ")
                .append(ChatColor.GRAY).append(formatTypeName(entry.getType())).append("\n");
        titlePage.append(ChatColor.DARK_GRAY).append("Rarity: ")
                .append(rarity.getColoredName()).append("\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append("Author: ")
                .append(ChatColor.GRAY).append(entry.getSubmittedBy() != null ? entry.getSubmittedBy() : "Unknown");
        pages.add(titlePage.toString());

        // Content pages
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            // Split description into pages
            List<String> contentPages = splitIntoPages(description);
            pages.addAll(contentPages);
        }

        // Location page (if applicable)
        if (entry.getLocation() != null) {
            StringBuilder locationPage = new StringBuilder();
            locationPage.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                    .append("Location\n\n");
            locationPage.append(ChatColor.GRAY)
                    .append("World: ").append(entry.getLocation().getWorld().getName()).append("\n")
                    .append("X: ").append((int) entry.getLocation().getX()).append("\n")
                    .append("Y: ").append((int) entry.getLocation().getY()).append("\n")
                    .append("Z: ").append((int) entry.getLocation().getZ());
            pages.add(locationPage.toString());
        }

        // Metadata page (if present)
        if (entry.hasMetadata()) {
            Map<String, String> metadata = entry.getAllMetadata();
            if (!metadata.isEmpty()) {
                StringBuilder metaPage = new StringBuilder();
                metaPage.append(ChatColor.DARK_PURPLE).append(ChatColor.BOLD)
                        .append("Details\n\n");
                for (Map.Entry<String, String> meta : metadata.entrySet()) {
                    metaPage.append(ChatColor.DARK_GRAY)
                            .append(formatMetaKey(meta.getKey())).append(": ")
                            .append(ChatColor.GRAY).append(meta.getValue()).append("\n");
                }
                pages.add(metaPage.toString());
            }
        }

        return pages;
    }

    /**
     * Split text into book pages.
     */
    private List<String> splitIntoPages(String text) {
        List<String> pages = new ArrayList<>();

        // Add color formatting
        text = ChatColor.BLACK + text;

        int start = 0;
        while (start < text.length() && pages.size() < MAX_PAGES) {
            int end = Math.min(start + MAX_CHARS_PER_PAGE, text.length());

            // Try to break at a word boundary
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            pages.add(text.substring(start, end).trim());
            start = end + 1;
        }

        return pages;
    }

    /**
     * Build the item lore (inventory tooltip).
     */
    private List<String> buildItemLore(LoreEntry entry, BookRarity rarity) {
        List<String> lore = new ArrayList<>();

        lore.add("");
        lore.add(ChatColor.GRAY + "Type: " + formatTypeName(entry.getType()));
        lore.add(ChatColor.GRAY + "Rarity: " + rarity.getColoredName());
        lore.add("");

        // Truncated description
        String desc = entry.getDescription();
        if (desc != null && !desc.isEmpty()) {
            if (desc.length() > 60) {
                desc = desc.substring(0, 57) + "...";
            }
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + desc);
            lore.add("");
        }

        lore.add(ChatColor.DARK_GRAY + "ID: " + entry.getId().substring(0, 8));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Right-click to read");

        return lore;
    }

    /**
     * Format a lore type name for display.
     */
    private String formatTypeName(LoreType type) {
        if (type == null) return "Unknown";
        String name = type.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Format a metadata key for display.
     */
    private String formatMetaKey(String key) {
        if (key == null) return "Unknown";
        String name = key.replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Clear the book cache.
     */
    public void clearCache() {
        bookCache.clear();
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        clearCache();
        logger.info("LoreBookManager shutdown complete");
    }
}
