package org.fourz.RVNKLore.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cross-plugin access to player lore discovery (#1650).
 *
 * <p>Discovery persistence has existed since #1832 — {@code lore_discovery} carries
 * {@code UNIQUE(player_uuid, entry_id)} and is written on every proximity and item trigger — but it
 * was reachable only from inside RVNKLore. RVNKQuests therefore shipped
 * {@code LoreIntegrationImpl.grantLoreDiscovery()} as a stub that returned {@code true} without
 * writing anything, so a quest {@code LORE} reward reported success and did nothing. This interface
 * is the missing surface, not new capability.</p>
 *
 * <h2>Only JDK types cross this boundary</h2>
 *
 * <p>Consumers resolve this through RVNKCore's {@code ServiceRegistry} by reflection and cannot see
 * RVNKLore's classes. Every parameter and return value here is {@link UUID}, {@link String},
 * {@link List} or {@link CompletableFuture} — deliberately no {@code LoreEntry}, no
 * {@code DiscoveryTriggerType}, no Bukkit {@code Player}. A caller that had to construct an
 * RVNKLore type could not use this at all.</p>
 *
 * <p>Entries are addressed by their {@code lore_entry.id} string (a UUID in text form), which is
 * what {@code lore_discovery.entry_id} stores, so no lookup table is needed on either side.</p>
 */
public interface IDiscoveryService {

    /**
     * Record that a player has discovered a lore entry.
     *
     * <p><b>Works for offline players.</b> When the player is online this runs the full discovery
     * path — {@code LoreDiscoveryEvent} is fired (and may be cancelled), cooldowns apply, and a
     * notification is sent on first discovery. When the player is offline the row is still written,
     * silently: nothing is fired and nothing is shown, because there is nobody to show it to.</p>
     *
     * <p>That split matters for quest rewards specifically. A party member who is offline when a
     * beat completes still earns the reward (RVNKQuests #1983), and a grant that only worked for
     * online players would reintroduce exactly that gap on this path.</p>
     *
     * <p>Idempotent. The underlying table's unique constraint means a repeat grant for the same
     * player and entry returns {@code true} without inserting a duplicate — so a caller retrying
     * after a timeout cannot corrupt the record.</p>
     *
     * @param playerUuid  the discovering player; never null
     * @param entryId     {@code lore_entry.id} as a string; never null
     * @param triggerType a {@code DiscoveryTriggerType} name, e.g. {@code "QUEST_COMPLETE"}.
     *                    Unrecognised or null values are stored as {@code EXTERNAL} rather than
     *                    rejected — provenance is worth less than the discovery itself
     * @return true if the discovery is recorded (including when it already was); false if the
     *         entry does not exist, the event was cancelled, or the write failed
     */
    CompletableFuture<Boolean> grantDiscovery(UUID playerUuid, String entryId, String triggerType);

    /**
     * @param playerUuid the player to check; never null
     * @param entryId    {@code lore_entry.id} as a string; never null
     * @return true if this player has already discovered this entry
     */
    CompletableFuture<Boolean> hasDiscovered(UUID playerUuid, String entryId);

    /**
     * @param playerUuid the player to query; never null
     * @return every {@code lore_entry.id} this player has discovered; empty, never null
     */
    CompletableFuture<List<String>> getDiscoveredEntryIds(UUID playerUuid);
}
