package org.fourz.RVNKLore.config;

/**
 * RVNKLore master operational mode (#1824), read from {@code general.mode}.
 *
 * <p>A single switch that composes over the granular {@code features.*.enabled} flags, so RVNKLore
 * can be installed on a server but held inert or run quiet without uninstalling. Absent or
 * unrecognized config values resolve to {@link #FULL} (see {@link ConfigManager#getMode()}) so an
 * existing config that lacks the key behaves exactly as before.</p>
 */
public enum LoreMode {

    /** Everything on: lore, items, collections, discovery, REST API, Dynmap, and player notifications. */
    FULL,

    /**
     * Full lore/items/discovery/REST, but player-facing notifications
     * (discovery / achievement / collection_completion) are suppressed. Data is still recorded.
     */
    QUIET,

    /**
     * Loaded but inert: no managers, services, REST API, listeners, or commands are registered.
     * Flip {@code general.mode} and restart to re-enable — no uninstall required.
     */
    OFF;

    /** @return true when the plugin should register no features (mode {@code off}). */
    public boolean isInert() {
        return this == OFF;
    }

    /** @return true when player-facing notifications must be suppressed (mode {@code quiet}). */
    public boolean suppressesNotifications() {
        return this == QUIET;
    }
}
