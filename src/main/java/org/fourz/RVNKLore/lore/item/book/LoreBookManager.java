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
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.service.ILoreBookService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the creation and handling of lore books.
 * Lore books are written books that contain lore entry content.
 */
public class LoreBookManager implements ILoreBookService {

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

        logger.debug("LoreBookManager initialized");
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
        String rawName = entry.getName() != null ? entry.getName() : "Unknown";
        String displayName = formatSlugName(rawName);
        String title = rarity.getColor() + displayName;
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
        meta.setDisplayName(rarity.getColor() + "" + ChatColor.BOLD + displayName);

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
     * Get or create a lore book for a quest item key.
     *
     * <p>Looks up the lore entry by name. If not found, auto-creates a QUEST
     * entry with the provided seed data (approved immediately) and generates
     * the book. Future edits to the DB description are reflected on next call.</p>
     *
     * @param questItemKey Unique key used as the lore entry name
     * @param title        Seed title when auto-creating
     * @param description  Seed description when auto-creating
     * @return Future containing the book, or empty on failure
     */
    @Override
    public CompletableFuture<Optional<ItemStack>> getOrCreateQuestBook(
            String questItemKey, String title, String description) {
        return CompletableFuture.supplyAsync(() -> {
            // Look up existing entry by name
            Optional<LoreEntry> existing = loreManager.getLoreEntryByName(questItemKey).join();
            LoreEntry entry;
            if (existing.isPresent()) {
                entry = existing.get();
            } else {
                // Auto-create: seed a new QUEST lore entry
                entry = new LoreEntry(questItemKey, description, LoreType.QUEST, (String) null);
                entry.setApproved(true);
                boolean created = loreManager.addLoreEntry(entry).join();
                if (!created) {
                    logger.warning("Failed to create lore entry for quest book: " + questItemKey);
                    return Optional.empty();
                }
                logger.debug("Auto-created lore entry for quest book: " + questItemKey);
            }
            ItemStack book = createLoreBook(entry);
            if (book != null && title != null && !title.isEmpty()) {
                BookMeta meta = (BookMeta) book.getItemMeta();
                if (meta != null) {
                    String displayTitle = title.length() > 32 ? title.substring(0, 29) + "..." : title;
                    meta.setTitle(displayTitle);
                    meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + displayTitle);
                    book.setItemMeta(meta);
                }
            }
            return Optional.ofNullable(book);
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
     * Get all obtainable lore books, sourced from the WRITTEN_BOOK item catalog (#1646).
     *
     * <p>Previously this iterated the lore-entry cache ({@code getAllLoreEntriesSync}) and kept any
     * approved entry — which read from an unreliable cache (under-reporting books) and applied no
     * material/type filter (listing non-book entries as books). It now queries the {@code lore_item}
     * catalog for obtainable WRITTEN_BOOK items, so the list is deterministic and actually books.</p>
     *
     * @return CompletableFuture with the list of obtainable book items (id = lore_entry_id, or name)
     */
    public CompletableFuture<List<BookListEntry>> getObtainableBooks() {
        ItemManager itemManager = loreManager.getItemManager();
        if (itemManager == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return itemManager.getObtainableWrittenBooks().thenApply(items -> {
            List<BookListEntry> books = new ArrayList<>();
            for (ItemProperties item : items) {
                String id = item.getLoreEntryId() != null ? item.getLoreEntryId() : item.getDisplayName();
                books.add(new BookListEntry(
                    id,
                    item.getDisplayName(),
                    LoreType.ITEM,
                    BookRarity.fromString(item.getRarity())
                ));
            }
            books.sort(Comparator.comparing(BookListEntry::name));
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
        // Type-specific templates
        if (entry.getType() == LoreType.PLAYER) {
            return buildPlayerProfilePages(entry, rarity);
        }
        if (entry.getType() == LoreType.EVENT) {
            return buildEventPages(entry, rarity);
        }
        if (entry.getType() == LoreType.QUEST) {
            return buildQuestPages(entry, rarity);
        }
        if (entry.getType() == LoreType.LANDMARK || entry.getType() == LoreType.MONUMENT) {
            return buildLandmarkPages(entry, rarity);
        }
        if (entry.getType() == LoreType.CITY || entry.getType() == LoreType.TAVERN
                || entry.getType() == LoreType.GUILD) {
            return buildSettlementPages(entry, rarity);
        }
        if (entry.getType() == LoreType.SHRINE) {
            return buildShrinePages(entry, rarity);
        }
        if (entry.getType() == LoreType.FACTION) {
            return buildFactionPages(entry, rarity);
        }
        if (entry.getType() == LoreType.PATH) {
            return buildPathScrollPages(entry, rarity);
        }
        if (entry.getType() == LoreType.ITEM) {
            return buildItemCompendiumPages(entry, rarity);
        }

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
     * Build pages for a PLAYER type lore entry using the chronicle layout.
     *
     * <p>Page 1 — Identity: name, optional title/faction from metadata, status, since date.
     * <p>Page 2+ — Biography: description prose with a "◆ History" header on the first page.
     * <p>Final page — Record: any extra metadata fields not already shown on the identity page.
     */
    private List<String> buildPlayerProfilePages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Identity ---
        StringBuilder identity = new StringBuilder();
        identity.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Chronicle —\n\n");
        identity.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n");

        String title = entry.getMetadata("title");
        if (title != null && !title.isEmpty()) {
            identity.append(ChatColor.GOLD).append(ChatColor.ITALIC)
                    .append(title).append(ChatColor.RESET).append("\n");
        }
        identity.append("\n");

        String faction = entry.getMetadata("faction");
        if (faction != null && !faction.isEmpty()) {
            identity.append(ChatColor.DARK_GRAY).append("Faction: ")
                    .append(ChatColor.GRAY).append(faction).append("\n");
        }

        String status = entry.getMetadata("status");
        identity.append(ChatColor.DARK_GRAY).append("Status: ")
                .append(ChatColor.GRAY).append(status != null ? status : "Active").append("\n");

        if (entry.getCreatedAt() != null) {
            String dateStr = entry.getCreatedAt().toLocalDateTime().format(dateFmt);
            identity.append(ChatColor.DARK_GRAY).append("Since: ")
                    .append(ChatColor.GRAY).append(dateStr);
        }

        pages.add(identity.toString());

        // --- Pages 2+: Biography ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String bioHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "◆ History\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - bioHeader.length();

            if (description.length() <= firstPageCapacity) {
                pages.add(bioHeader + description);
            } else {
                // Break at word boundary for first bio page
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(bioHeader + description.substring(0, end).trim());

                // Remaining text on standard split pages
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) {
                    pages.addAll(splitIntoPages(remaining));
                }
            }
        }

        // --- Final page: Additional metadata record (fields not shown on identity page) ---
        Set<String> identityKeys = new HashSet<>(Arrays.asList(
                "title", "faction", "status", "validation_errors", "material"));
        Map<String, String> allMeta = entry.getAllMetadata();
        Map<String, String> extraMeta = new LinkedHashMap<>();
        for (Map.Entry<String, String> m : allMeta.entrySet()) {
            if (!identityKeys.contains(m.getKey())) {
                extraMeta.put(m.getKey(), m.getValue());
            }
        }
        if (!extraMeta.isEmpty()) {
            StringBuilder recordPage = new StringBuilder();
            recordPage.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                    .append("◆ Record\n\n");
            for (Map.Entry<String, String> m : extraMeta.entrySet()) {
                recordPage.append(ChatColor.DARK_GRAY)
                        .append(formatMetaKey(m.getKey())).append(": ")
                        .append(ChatColor.GRAY).append(m.getValue()).append("\n");
            }
            pages.add(recordPage.toString());
        }

        return pages;
    }

    /**
     * Build pages for an EVENT type lore entry using the archive record layout.
     *
     * <p>Page 1 — Archive Record: name, inline location, recorded date, filed by.
     * <p>Page 2+ — Account: description prose with section header.
     * <p>Optional — Participants page from metadata key "participants".
     */
    private List<String> buildEventPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Archive Record ---
        StringBuilder record = new StringBuilder();
        record.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Archive Record —\n\n");
        record.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n\n");

        // Location inline (not a separate page for events)
        if (entry.getLocation() != null) {
            String locName = entry.getMetadata("location_name");
            if (locName != null && !locName.isEmpty()) {
                record.append(ChatColor.DARK_GRAY).append("Location: ")
                        .append(ChatColor.GRAY).append(locName).append("\n");
            } else {
                record.append(ChatColor.DARK_GRAY).append("Location: ")
                        .append(ChatColor.GRAY)
                        .append(entry.getLocation().getWorld().getName())
                        .append(String.format(" (%d, %d, %d)",
                                (int) entry.getLocation().getX(),
                                (int) entry.getLocation().getY(),
                                (int) entry.getLocation().getZ()))
                        .append("\n");
            }
        }

        // Prefer metadata event_date, fall back to createdAt
        String eventDate = entry.getMetadata("event_date");
        if (eventDate != null && !eventDate.isEmpty()) {
            record.append(ChatColor.DARK_GRAY).append("Date: ")
                    .append(ChatColor.GRAY).append(eventDate).append("\n");
        } else if (entry.getCreatedAt() != null) {
            record.append(ChatColor.DARK_GRAY).append("Recorded: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt)).append("\n");
        }

        if (entry.getSubmittedBy() != null) {
            record.append(ChatColor.DARK_GRAY).append("Filed by: ")
                    .append(ChatColor.GRAY).append(entry.getSubmittedBy());
        }

        pages.add(record.toString());

        // --- Pages 2+: Account ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String accountHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Account\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - accountHeader.length();

            if (description.length() <= firstPageCapacity) {
                pages.add(accountHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(accountHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) {
                    pages.addAll(splitIntoPages(remaining));
                }
            }
        }

        // --- Optional: Participants page ---
        String participants = entry.getMetadata("participants");
        if (participants != null && !participants.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Participants\n\n" + ChatColor.GRAY + participants);
        }

        return pages;
    }

    /**
     * Build pages for a QUEST type lore entry using the notice board layout.
     *
     * <p>Page 1 — Notice: name, status (Open default), difficulty, posted by, date.
     * <p>Page 2+ — Objective: description prose with section header.
     * <p>Optional — Reward page from metadata key "reward".
     */
    private List<String> buildQuestPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Notice ---
        StringBuilder notice = new StringBuilder();
        notice.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Notice Board —\n\n");
        notice.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n\n");

        String status = entry.getMetadata("status");
        notice.append(ChatColor.DARK_GRAY).append("Status: ")
                .append(ChatColor.GRAY).append(status != null ? status : "Open").append("\n");

        String difficulty = entry.getMetadata("difficulty");
        if (difficulty != null && !difficulty.isEmpty()) {
            notice.append(ChatColor.DARK_GRAY).append("Difficulty: ")
                    .append(ChatColor.GRAY).append(difficulty).append("\n");
        }

        if (entry.getSubmittedBy() != null) {
            notice.append(ChatColor.DARK_GRAY).append("Posted by: ")
                    .append(ChatColor.GRAY).append(entry.getSubmittedBy()).append("\n");
        }

        if (entry.getCreatedAt() != null) {
            notice.append(ChatColor.DARK_GRAY).append("Posted: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
        }

        pages.add(notice.toString());

        // --- Pages 2+: Objective ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String objectiveHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Objective\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - objectiveHeader.length();

            if (description.length() <= firstPageCapacity) {
                pages.add(objectiveHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(objectiveHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) {
                    pages.addAll(splitIntoPages(remaining));
                }
            }
        }

        // --- Optional: Reward page ---
        String reward = entry.getMetadata("reward");
        if (reward != null && !reward.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Reward\n\n" + ChatColor.GRAY + reward);
        }

        return pages;
    }

    /**
     * Build pages for LANDMARK / MONUMENT using the survey record layout.
     *
     * <p>Page 1 — Survey Record: name, type label, world, coords, discovered by, recorded date.
     * <p>Page 2+ — Description: prose with section header.
     * <p>Optional — Notes page from metadata key "notes".
     */
    private List<String> buildLandmarkPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Survey Record ---
        StringBuilder record = new StringBuilder();
        record.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Survey Record —\n\n");
        record.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n");
        record.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append(formatTypeName(entry.getType())).append(ChatColor.RESET).append("\n\n");

        if (entry.getLocation() != null) {
            record.append(ChatColor.DARK_GRAY).append("World: ")
                    .append(ChatColor.GRAY).append(entry.getLocation().getWorld().getName()).append("\n");
            record.append(ChatColor.DARK_GRAY).append("Coords: ")
                    .append(ChatColor.GRAY)
                    .append(String.format("%d, %d, %d",
                            (int) entry.getLocation().getX(),
                            (int) entry.getLocation().getY(),
                            (int) entry.getLocation().getZ()))
                    .append("\n");
        }

        if (entry.getSubmittedBy() != null) {
            record.append(ChatColor.DARK_GRAY).append("Discovered by: ")
                    .append(ChatColor.GRAY).append(entry.getSubmittedBy()).append("\n");
        }

        if (entry.getCreatedAt() != null) {
            record.append(ChatColor.DARK_GRAY).append("Recorded: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
        }

        pages.add(record.toString());

        // --- Pages 2+: Description ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String descHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Description\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - descHeader.length();
            if (description.length() <= firstPageCapacity) {
                pages.add(descHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(descHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) pages.addAll(splitIntoPages(remaining));
            }
        }

        // --- Optional: Notes ---
        String notes = entry.getMetadata("notes");
        if (notes != null && !notes.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Notes\n\n" + ChatColor.GRAY + notes);
        }

        return pages;
    }

    /**
     * Build pages for CITY / TAVERN / GUILD using a settlement record layout.
     *
     * <p>Header label varies: Settlement Record (CITY), Establishment Record (TAVERN), Guild Charter (GUILD).
     * <p>Page 1 — Record: name, region or world, coords, founder/proprietor/guildmaster, est. date.
     * <p>Page 2+ — History/About/Charter: prose with section header.
     * <p>Optional — Notable page from metadata key "notable".
     */
    private List<String> buildSettlementPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // Type-specific framing
        String frameHeader;
        String historyLabel;
        String founderLabel;
        switch (entry.getType()) {
            case TAVERN:
                frameHeader = "— Establishment Record —";
                historyLabel = "◆ About";
                founderLabel = "Proprietor: ";
                break;
            case GUILD:
                frameHeader = "— Guild Charter —";
                historyLabel = "◆ Charter";
                founderLabel = "Guildmaster: ";
                break;
            default: // CITY
                frameHeader = "— Settlement Record —";
                historyLabel = "◆ History";
                founderLabel = "Founded by: ";
                break;
        }

        // --- Page 1 ---
        StringBuilder record = new StringBuilder();
        record.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append(frameHeader).append("\n\n");
        record.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n\n");

        // Region from metadata preferred; fall back to world name
        String region = entry.getMetadata("region");
        if (region != null && !region.isEmpty()) {
            record.append(ChatColor.DARK_GRAY).append("Region: ")
                    .append(ChatColor.GRAY).append(region).append("\n");
        } else if (entry.getLocation() != null) {
            record.append(ChatColor.DARK_GRAY).append("World: ")
                    .append(ChatColor.GRAY).append(entry.getLocation().getWorld().getName()).append("\n");
        }

        if (entry.getLocation() != null) {
            record.append(ChatColor.DARK_GRAY).append("Coords: ")
                    .append(ChatColor.GRAY)
                    .append(String.format("%d, %d, %d",
                            (int) entry.getLocation().getX(),
                            (int) entry.getLocation().getY(),
                            (int) entry.getLocation().getZ()))
                    .append("\n");
        }

        if (entry.getSubmittedBy() != null) {
            record.append(ChatColor.DARK_GRAY).append(founderLabel)
                    .append(ChatColor.GRAY).append(entry.getSubmittedBy()).append("\n");
        }

        if (entry.getCreatedAt() != null) {
            record.append(ChatColor.DARK_GRAY).append("Est.: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
        }

        pages.add(record.toString());

        // --- Pages 2+: History / About / Charter ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String histHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + historyLabel + "\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - histHeader.length();
            if (description.length() <= firstPageCapacity) {
                pages.add(histHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(histHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) pages.addAll(splitIntoPages(remaining));
            }
        }

        // --- Optional: Notable features ---
        String notable = entry.getMetadata("notable");
        if (notable != null && !notable.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Notable\n\n" + ChatColor.GRAY + notable);
        }

        return pages;
    }

    /**
     * Build pages for SHRINE using the sacred site layout.
     *
     * <p>Page 1 — Sacred Site: name, world, coords, consecrated by, date.
     * <p>Page 2+ — Inscription: description prose with section header.
     */
    private List<String> buildShrinePages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Sacred Site ---
        StringBuilder record = new StringBuilder();
        record.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Sacred Site —\n\n");
        record.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n\n");

        if (entry.getLocation() != null) {
            record.append(ChatColor.DARK_GRAY).append("World: ")
                    .append(ChatColor.GRAY).append(entry.getLocation().getWorld().getName()).append("\n");
            record.append(ChatColor.DARK_GRAY).append("Coords: ")
                    .append(ChatColor.GRAY)
                    .append(String.format("%d, %d, %d",
                            (int) entry.getLocation().getX(),
                            (int) entry.getLocation().getY(),
                            (int) entry.getLocation().getZ()))
                    .append("\n");
        }

        if (entry.getSubmittedBy() != null) {
            record.append(ChatColor.DARK_GRAY).append("Consecrated by: ")
                    .append(ChatColor.GRAY).append(entry.getSubmittedBy()).append("\n");
        }

        if (entry.getCreatedAt() != null) {
            record.append(ChatColor.DARK_GRAY).append("Date: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
        }

        pages.add(record.toString());

        // --- Pages 2+: Inscription ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String inscHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Inscription\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - inscHeader.length();
            if (description.length() <= firstPageCapacity) {
                pages.add(inscHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(inscHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) pages.addAll(splitIntoPages(remaining));
            }
        }

        return pages;
    }

    /**
     * Build pages for FACTION using the faction codex layout.
     *
     * <p>Page 1 — Codex: name, optional motto, leader, status, est. date.
     * <p>Page 2+ — Charter: description prose with section header.
     * <p>Optional — Territories page from metadata key "territories".
     */
    private List<String> buildFactionPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Faction Codex ---
        StringBuilder codex = new StringBuilder();
        codex.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Faction Codex —\n\n");
        codex.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n");

        String motto = entry.getMetadata("motto");
        if (motto != null && !motto.isEmpty()) {
            codex.append(ChatColor.GRAY).append(ChatColor.ITALIC)
                    .append("“").append(motto).append("”")
                    .append(ChatColor.RESET).append("\n");
        }
        codex.append("\n");

        // Leader: prefer metadata "leader", fall back to submittedBy
        String leader = entry.getMetadata("leader");
        if (leader == null || leader.isEmpty()) leader = entry.getSubmittedBy();
        if (leader != null) {
            codex.append(ChatColor.DARK_GRAY).append("Leader: ")
                    .append(ChatColor.GRAY).append(leader).append("\n");
        }

        String status = entry.getMetadata("status");
        codex.append(ChatColor.DARK_GRAY).append("Status: ")
                .append(ChatColor.GRAY).append(status != null ? status : "Active").append("\n");

        if (entry.getCreatedAt() != null) {
            codex.append(ChatColor.DARK_GRAY).append("Est.: ")
                    .append(ChatColor.GRAY)
                    .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
        }

        pages.add(codex.toString());

        // --- Pages 2+: Charter ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String charterHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Charter\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - charterHeader.length();
            if (description.length() <= firstPageCapacity) {
                pages.add(charterHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(charterHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) pages.addAll(splitIntoPages(remaining));
            }
        }

        // --- Optional: Territories ---
        String territories = entry.getMetadata("territories");
        if (territories != null && !territories.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Territories\n\n" + ChatColor.GRAY + territories);
        }

        return pages;
    }

    /**
     * Build pages for PATH type lore entries using the cartographer road survey layout.
     *
     * <p>Page 1 — Route Header: route name (large), "Origin → Destination" if parseable,
     *               type badge "ROAD SURVEY".
     * <p>Page 2 — Route Overview: terrain notes, approximate distance, notable waypoints
     *            from description (wrapped at ~18 chars/line).
     * <p>Page 3 — Hazards & Warnings: "Hazards: [details]", "Seasonal: Unknown".
     * <p>Page 4 — Points of Interest: cross-reference footer with related landmarks/cities,
     *            "Ravenkraft Road Survey — Office of Cartography".
     */
    private List<String> buildPathScrollPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Route Header ---
        StringBuilder header = new StringBuilder();
        header.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Road Survey —\n\n");
        header.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n");

        // Parse origin → destination from description or metadata
        String origin = entry.getMetadata("origin");
        String destination = entry.getMetadata("destination");
        if ((origin != null && !origin.isEmpty()) || (destination != null && !destination.isEmpty())) {
            header.append(ChatColor.GRAY);
            if (origin != null && !origin.isEmpty()) {
                header.append(origin);
            } else {
                header.append("Unknown");
            }
            header.append(ChatColor.DARK_GRAY).append(" → ").append(ChatColor.GRAY);
            if (destination != null && !destination.isEmpty()) {
                header.append(destination);
            } else {
                header.append("Unknown");
            }
            header.append("\n");
        }

        header.append("\n").append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("ROAD SURVEY\n");

        pages.add(header.toString());

        // --- Page 2: Route Overview ---
        StringBuilder overview = new StringBuilder();
        overview.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("◆ Overview\n\n").append(ChatColor.BLACK);

        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            // Wrap text at ~18 chars per line for readability
            String[] words = description.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() + word.length() + 1 > 18) {
                    overview.append(line).append("\n");
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
            if (line.length() > 0) {
                overview.append(line);
            }
        } else {
            overview.append("No terrain information recorded.");
        }

        pages.add(overview.toString());

        // --- Page 3: Hazards & Warnings ---
        StringBuilder hazards = new StringBuilder();
        hazards.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("◆ Hazards & Warnings\n\n").append(ChatColor.BLACK);

        String hazardInfo = entry.getMetadata("hazards");
        if (hazardInfo != null && !hazardInfo.isEmpty()) {
            hazards.append("Hazards: ").append(hazardInfo);
        } else {
            hazards.append("Hazards: None noted");
        }
        hazards.append("\n\nSeasonal: Unknown");

        pages.add(hazards.toString());

        // --- Page 4: Points of Interest & Footer ---
        StringBuilder footer = new StringBuilder();
        footer.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("◆ See Also\n\n").append(ChatColor.GRAY);

        // Extract landmark/city names from description if present
        String pointsOfInterest = entry.getMetadata("points_of_interest");
        if (pointsOfInterest != null && !pointsOfInterest.isEmpty()) {
            footer.append(pointsOfInterest);
        } else {
            footer.append("—");
        }

        footer.append("\n\n").append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("Ravenkraft Road Survey\n")
                .append("Office of Cartography");

        pages.add(footer.toString());

        return pages;
    }

    /**
     * Build pages for ITEM-type lore entries using a compendium catalogue layout.
     *
     * <p>Page 1 — Catalogue Entry: name, material, item type, rarity, obtainability.
     * <p>Page 2+ — Lore: description prose with a "◆ Lore" section header.
     * <p>Optional — Provenance: submitter, creation date, and any curator notes.
     */
    private List<String> buildItemCompendiumPages(LoreEntry entry, BookRarity rarity) {
        List<String> pages = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // --- Page 1: Catalogue Entry ---
        StringBuilder catalogue = new StringBuilder();
        catalogue.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                .append("— Item Compendium —\n\n");
        catalogue.append(rarity.getColor()).append(ChatColor.BOLD)
                .append(entry.getName()).append(ChatColor.RESET).append("\n");

        // Material line
        String material = entry.getMetadata("material");
        if (material != null && !material.isEmpty()) {
            String matDisplay = material.replace("_", " ");
            matDisplay = matDisplay.substring(0, 1).toUpperCase() + matDisplay.substring(1).toLowerCase();
            catalogue.append(ChatColor.DARK_GRAY).append("Material: ")
                    .append(ChatColor.GRAY).append(matDisplay).append("\n");
        }

        // Item type line
        String itemType = entry.getMetadata("item_type");
        if (itemType != null && !itemType.isEmpty() && !"STANDARD".equalsIgnoreCase(itemType)) {
            catalogue.append(ChatColor.DARK_GRAY).append("Type: ")
                    .append(ChatColor.GRAY).append(formatMetaKey(itemType.toLowerCase())).append("\n");
        }

        // Rarity line (item rarity from metadata, not book rarity)
        String itemRarity = entry.getMetadata("rarity");
        if (itemRarity != null && !itemRarity.isEmpty() && !"COMMON".equalsIgnoreCase(itemRarity)) {
            catalogue.append(ChatColor.DARK_GRAY).append("Rarity: ")
                    .append(ChatColor.GRAY).append(formatMetaKey(itemRarity.toLowerCase())).append("\n");
        }

        // Obtainability
        String obtainable = entry.getMetadata("is_obtainable");
        if ("false".equalsIgnoreCase(obtainable)) {
            catalogue.append(ChatColor.RED).append(ChatColor.ITALIC).append("Not obtainable in survival\n");
        }

        catalogue.append(ChatColor.DARK_GRAY).append("\nID: ")
                .append(ChatColor.GRAY).append(entry.getId().substring(0, 8));

        pages.add(catalogue.toString());

        // --- Pages 2+: Description / Lore ---
        String description = entry.getDescription();
        if (description != null && !description.isEmpty()) {
            String loreHeader = ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "◆ Lore\n\n" + ChatColor.BLACK;
            int firstPageCapacity = MAX_CHARS_PER_PAGE - loreHeader.length();
            if (description.length() <= firstPageCapacity) {
                pages.add(loreHeader + description);
            } else {
                int end = firstPageCapacity;
                int lastSpace = description.lastIndexOf(' ', end);
                if (lastSpace > 0) end = lastSpace;
                pages.add(loreHeader + description.substring(0, end).trim());
                String remaining = description.substring(end).trim();
                if (!remaining.isEmpty()) pages.addAll(splitIntoPages(remaining));
            }
        }

        // --- Optional: Curator Notes ---
        String notes = entry.getMetadata("notes");
        if (notes != null && !notes.isEmpty()) {
            pages.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC
                    + "◆ Notes\n\n" + ChatColor.GRAY + notes);
        }

        // --- Optional: Provenance ---
        boolean hasProvenance = entry.getSubmittedBy() != null || entry.getCreatedAt() != null;
        if (hasProvenance) {
            StringBuilder prov = new StringBuilder();
            prov.append(ChatColor.DARK_GRAY).append(ChatColor.ITALIC)
                    .append("◆ Provenance\n\n").append(ChatColor.RESET);
            if (entry.getSubmittedBy() != null) {
                prov.append(ChatColor.DARK_GRAY).append("Catalogued by: ")
                        .append(ChatColor.GRAY).append(entry.getSubmittedBy()).append("\n");
            }
            if (entry.getCreatedAt() != null) {
                prov.append(ChatColor.DARK_GRAY).append("Recorded: ")
                        .append(ChatColor.GRAY)
                        .append(entry.getCreatedAt().toLocalDateTime().format(dateFmt));
            }
            pages.add(prov.toString());
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
     * Format a slug name (underscore_separated) into Title Case for display.
     * e.g. "grotsnouts_journal" → "Grotsnouts Journal"
     */
    private String formatSlugName(String slug) {
        if (slug == null || slug.isEmpty()) return slug;
        String[] words = slug.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
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
        logger.debug("LoreBookManager shutdown complete");
    }
}
