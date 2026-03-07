package org.fourz.RVNKLore.lore.item.collection.event;

/**
 * Enumeration of collection change event types.
 * Indicates what action triggered a collection change event.
 */
public enum CollectionEventType {
    /**
     * Player collection progress was updated.
     * Fired when items are added to a player's collection progress.
     */
    PROGRESS_UPDATE("progress_update"),

    /**
     * Player completed a collection (100% progress).
     * Fired when a collection reaches 100% completion for a player.
     */
    COMPLETED("completed"),

    /**
     * Collection rewards were granted to a player.
     * Fired after rewards are successfully distributed.
     */
    REWARD_GRANTED("reward_granted"),

    /**
     * Collection was created.
     * Fired when a new collection is added to the system.
     */
    CREATED("created"),

    /**
     * Collection was deleted.
     * Fired when a collection is removed from the system.
     */
    DELETED("deleted");

    private final String id;

    CollectionEventType(String id) {
        this.id = id;
    }

    /**
     * Get the string identifier for this event type.
     */
    public String getId() {
        return id;
    }

    /**
     * Parse event type from string identifier.
     */
    public static CollectionEventType fromId(String id) {
        for (CollectionEventType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return PROGRESS_UPDATE; // default
    }
}
