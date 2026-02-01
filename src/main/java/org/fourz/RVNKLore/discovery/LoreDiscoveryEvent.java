package org.fourz.RVNKLore.discovery;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.fourz.RVNKLore.lore.LoreEntry;

import java.util.UUID;

/**
 * Event fired when a player discovers a lore entry.
 *
 * <p>This event is cancellable - cancelling prevents the discovery from being recorded
 * and suppresses notifications.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @EventHandler
 * public void onLoreDiscovery(LoreDiscoveryEvent event) {
 *     Player player = event.getPlayer();
 *     LoreEntry entry = event.getLoreEntry();
 *
 *     // Custom logic - e.g., grant rewards
 *     if (event.isFirstDiscovery()) {
 *         // This is the first player to discover this entry
 *         grantBonus(player, entry);
 *     }
 * }
 * }</pre>
 */
public class LoreDiscoveryEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LoreEntry loreEntry;
    private final DiscoveryTriggerType triggerType;
    private final Location triggerLocation;
    private final boolean firstDiscovery;
    private final boolean firstForPlayer;

    private boolean cancelled = false;
    private boolean suppressNotification = false;
    private String customMessage = null;

    /**
     * Creates a new lore discovery event.
     *
     * @param player The player who discovered the lore
     * @param loreEntry The lore entry that was discovered
     * @param triggerType The type of trigger that caused the discovery
     * @param triggerLocation The location where the discovery occurred
     * @param firstDiscovery Whether this is the first time anyone has discovered this entry
     * @param firstForPlayer Whether this is the first time this player has discovered this entry
     */
    public LoreDiscoveryEvent(Player player, LoreEntry loreEntry, DiscoveryTriggerType triggerType,
                              Location triggerLocation, boolean firstDiscovery, boolean firstForPlayer) {
        this.player = player;
        this.loreEntry = loreEntry;
        this.triggerType = triggerType;
        this.triggerLocation = triggerLocation;
        this.firstDiscovery = firstDiscovery;
        this.firstForPlayer = firstForPlayer;
    }

    /**
     * Gets the player who discovered the lore.
     *
     * @return The discovering player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the UUID of the discovering player.
     *
     * @return The player's UUID
     */
    public UUID getPlayerUuid() {
        return player.getUniqueId();
    }

    /**
     * Gets the lore entry that was discovered.
     *
     * @return The discovered lore entry
     */
    public LoreEntry getLoreEntry() {
        return loreEntry;
    }

    /**
     * Gets the type of trigger that caused this discovery.
     *
     * @return The trigger type
     */
    public DiscoveryTriggerType getTriggerType() {
        return triggerType;
    }

    /**
     * Gets the location where the discovery occurred.
     *
     * @return The trigger location, or null if not applicable
     */
    public Location getTriggerLocation() {
        return triggerLocation;
    }

    /**
     * Checks if this is the first time anyone has discovered this lore entry.
     *
     * @return true if this is the server-wide first discovery
     */
    public boolean isFirstDiscovery() {
        return firstDiscovery;
    }

    /**
     * Checks if this is the first time this player has discovered this entry.
     *
     * @return true if the player hasn't discovered this before
     */
    public boolean isFirstForPlayer() {
        return firstForPlayer;
    }

    /**
     * Sets whether to suppress the default discovery notification.
     *
     * @param suppress true to suppress the notification
     */
    public void setSuppressNotification(boolean suppress) {
        this.suppressNotification = suppress;
    }

    /**
     * Checks if the discovery notification should be suppressed.
     *
     * @return true if notification is suppressed
     */
    public boolean isSuppressNotification() {
        return suppressNotification;
    }

    /**
     * Sets a custom message to display instead of the default notification.
     *
     * @param message The custom message, or null to use default
     */
    public void setCustomMessage(String message) {
        this.customMessage = message;
    }

    /**
     * Gets the custom message for this discovery.
     *
     * @return The custom message, or null if using default
     */
    public String getCustomMessage() {
        return customMessage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
