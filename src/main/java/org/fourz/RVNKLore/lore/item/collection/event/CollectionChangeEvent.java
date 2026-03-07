package org.fourz.RVNKLore.lore.item.collection.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.UUID;

/**
 * Event fired when a collection changes (progress, completion, rewards, etc.).
 * External plugins can listen to this event to react to collection system changes.
 *
 * Example listener:
 * <pre>
 * @EventHandler
 * public void onCollectionComplete(CollectionChangeEvent event) {
 *     if (event.getEventType() == CollectionEventType.COMPLETED) {
 *         Player player = Bukkit.getPlayer(event.getPlayerUuid());
 *         if (player != null) {
 *             player.sendMessage("You completed: " + event.getCollection().getName());
 *         }
 *     }
 * }
 * </pre>
 */
public class CollectionChangeEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final ItemCollection collection;
    private final UUID playerUuid;
    private final CollectionEventType eventType;
    private final long timestamp;
    private final double previousProgress;
    private final double currentProgress;

    /**
     * Create a new collection change event.
     *
     * @param collection The collection that changed
     * @param playerUuid The player affected (nullable for CREATED/DELETED events)
     * @param eventType The type of change
     * @param previousProgress Previous collection progress (0-1.0)
     * @param currentProgress Current collection progress (0-1.0)
     */
    public CollectionChangeEvent(ItemCollection collection, UUID playerUuid, CollectionEventType eventType,
                                 double previousProgress, double currentProgress) {
        super(true); // async = true, allow async listeners
        this.collection = collection;
        this.playerUuid = playerUuid;
        this.eventType = eventType;
        this.timestamp = System.currentTimeMillis();
        this.previousProgress = previousProgress;
        this.currentProgress = currentProgress;
    }

    /**
     * Get the collection that changed.
     */
    public ItemCollection getCollection() {
        return collection;
    }

    /**
     * Get the player UUID affected by this change.
     * May be null for CREATED/DELETED events affecting the collection itself.
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Get the type of change that occurred.
     */
    public CollectionEventType getEventType() {
        return eventType;
    }

    /**
     * Get the timestamp when this event was fired.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the collection progress before this change (0-1.0).
     * For CREATED/DELETED events, this is 0.
     */
    public double getPreviousProgress() {
        return previousProgress;
    }

    /**
     * Get the collection progress after this change (0-1.0).
     * For DELETED events, this is 0.
     */
    public double getCurrentProgress() {
        return currentProgress;
    }

    /**
     * Check if this event represents collection completion.
     */
    public boolean isCompletion() {
        return eventType == CollectionEventType.COMPLETED ||
               (currentProgress >= 1.0 && previousProgress < 1.0);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public String toString() {
        return "CollectionChangeEvent{" +
                "collection='" + collection.getId() + '\'' +
                ", playerUuid=" + playerUuid +
                ", eventType=" + eventType +
                ", progress=" + previousProgress + "->" + currentProgress +
                ", timestamp=" + timestamp +
                '}';
    }
}
