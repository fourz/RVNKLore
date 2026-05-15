package org.fourz.RVNKLore.lore;

/**
 * Post-processor hook run after a lore entry is persisted to the database.
 * Implementations handle side-effects (item registration, map markers, discovery cache).
 * A false return value from {@link #process} triggers a full rollback of the entry.
 */
public interface LorePostProcessor {

    /** Whether this processor should run for the given entry. */
    boolean appliesTo(LoreEntry entry);

    /**
     * Execute the post-processing step.
     *
     * @return true to continue; false to abort and roll back the persisted entry
     */
    boolean process(LoreEntry entry);
}
