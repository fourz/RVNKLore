package org.fourz.RVNKLore.service;

import org.bukkit.inventory.ItemStack;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for lore book creation and retrieval.
 *
 * <p>Provides quest-system integration for lore books, allowing
 * quest books to be backed by the lore database with auto-seeding
 * when an entry is not yet present.</p>
 */
public interface ILoreBookService {

    /**
     * Create a lore book ItemStack from an existing lore entry ID.
     *
     * @param entryId The lore entry UUID or short-id prefix
     * @return Future containing the book, or empty if not found
     */
    CompletableFuture<Optional<ItemStack>> createLoreBookById(String entryId);

    /**
     * Get or create a lore book backed by the given quest item key.
     *
     * <p>Looks up the lore entry by {@code questItemKey} name. If no entry
     * exists, one is auto-created as a QUEST type with the provided seed
     * data and approved immediately. Returns the generated book ItemStack.</p>
     *
     * @param questItemKey Unique key used as the lore entry name (e.g. "grotsnouts_journal")
     * @param title        Seed title used when auto-creating a new entry
     * @param description  Seed description used when auto-creating a new entry
     * @return Future containing the book, or empty on failure
     */
    CompletableFuture<Optional<ItemStack>> getOrCreateQuestBook(
            String questItemKey, String title, String description);
}
