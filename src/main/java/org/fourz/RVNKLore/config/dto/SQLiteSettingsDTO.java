package org.fourz.RVNKLore.config.dto;

/**
 * DTO for SQLite database connection settings.
 * Contains configuration specific to SQLite connections.
 */
public class SQLiteSettingsDTO {
    private final String database;
    private final boolean walMode;
    private final int busyTimeout;
    private final int cacheSize;

    /**
     * Create a new SQLite settings DTO.
     *
     * @param database The database filename
     * @param walMode Whether to use WAL journal mode
     * @param busyTimeout The busy timeout in milliseconds
     * @param cacheSize The cache size in pages (-4000 means 4MB)
     */
    public SQLiteSettingsDTO(String database, boolean walMode, int busyTimeout, int cacheSize) {
        this.database = database;
        this.walMode = walMode;
        this.busyTimeout = busyTimeout;
        this.cacheSize = cacheSize;
    }

    /**
     * Create a new SQLite settings DTO with default values.
     *
     * @param database The database filename
     */
    public SQLiteSettingsDTO(String database) {
        this(database, true, 30000, -4000);
    }

    /**
     * Get the database filename.
     *
     * @return The database filename
     */
    public String getDatabase() {
        return database;
    }

    /**
     * Check if WAL journal mode should be used.
     *
     * @return True if WAL journal mode should be used
     */
    public boolean isWalMode() {
        return walMode;
    }

    /**
     * Get the busy timeout in milliseconds.
     *
     * @return The busy timeout
     */
    public int getBusyTimeout() {
        return busyTimeout;
    }

    /**
     * Get the cache size in pages.
     *
     * @return The cache size
     */
    public int getCacheSize() {
        return cacheSize;
    }
}
