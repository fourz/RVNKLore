package org.fourz.RVNKLore.config.dto;

/**
 * DTO for SQLite configuration settings.
 * Used to transfer SQLite config data from configuration to consumers.
 */
public class SQLiteSettingsDTO {
    private final String filePath;
    private final String tablePrefix;

    public SQLiteSettingsDTO(String filePath, String tablePrefix) {
        this.filePath = filePath;
        this.tablePrefix = tablePrefix;
    }

    public String getFilePath() { return filePath; }
    public String getTablePrefix() { return tablePrefix; }
}
