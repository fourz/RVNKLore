package org.fourz.RVNKLore.config.dto;

/**
 * DTO for MySQL configuration settings.
 * Used to transfer MySQL config data from configuration to consumers.
 */
public class MySQLSettingsDTO {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;
    private final String tablePrefix;

    public MySQLSettingsDTO(String host, int port, String database, String username, String password, boolean useSSL, String tablePrefix) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.tablePrefix = tablePrefix;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isUseSSL() { return useSSL; }
    public String getTablePrefix() { return tablePrefix; }
}
