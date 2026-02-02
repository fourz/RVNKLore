# Database Integration API Reference

This document provides comprehensive reference for implementing database integration in Minecraft plugin development, specifically tailored for RVNKLore project patterns.

## Table of Contents
- [Database Architecture](#database-architecture)
- [Connection Management](#connection-management)
- [Query Execution](#query-execution)
- [Transaction Handling](#transaction-handling)
- [Data Access Objects (DAO)](#data-access-objects-dao)
- [Migration System](#migration-system)
- [Performance Optimization](#performance-optimization)
- [Error Handling](#error-handling)
- [RVNKLore Integration](#rvnklore-integration)

## Database Architecture

### Database Manager Core

```java
public class DatabaseManager {
    private static DatabaseManager instance;
    private final RVNKLore plugin;
    private final LogManager logger;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;
    private final DatabaseMigrator migrator;
    
    private DatabaseManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.migrator = new DatabaseMigrator(this);
    }
    
    public static DatabaseManager getInstance() {
        return instance;
    }
    
    public static void initialize(RVNKLore plugin) {
        if (instance == null) {
            instance = new DatabaseManager(plugin);
            instance.setupDatabase();
        }
    }
    
    private void setupDatabase() {
        try {
            this.databaseType = DatabaseType.fromString(
                ConfigManager.getString("database.type", "sqlite"));
            
            setupDataSource();
            runMigrations();
            
            logger.info("Database initialized successfully (" + databaseType + ")");
            
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        
        switch (databaseType) {
            case SQLITE:
                setupSQLiteConfig(config);
                break;
            case MYSQL:
                setupMySQLConfig(config);
                break;
            case POSTGRESQL:
                setupPostgreSQLConfig(config);
                break;
        }
        
        this.dataSource = new HikariDataSource(config);
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}

public enum DatabaseType {
    SQLITE("sqlite", "org.sqlite.JDBC"),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),
    POSTGRESQL("postgresql", "org.postgresql.Driver");
    
    private final String name;
    private final String driverClass;
    
    DatabaseType(String name, String driverClass) {
        this.name = name;
        this.driverClass = driverClass;
    }
    
    public static DatabaseType fromString(String name) {
        for (DatabaseType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown database type: " + name);
    }
    
    // Getters...
}
```

### Connection Pool Configuration

```java
private void setupSQLiteConfig(HikariConfig config) {
    File dbFile = new File(plugin.getDataFolder(), 
        ConfigManager.getString("database.sqlite.file", "lore_data.db"));
    
    config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
    config.setDriverClassName(DatabaseType.SQLITE.getDriverClass());
    
    // SQLite-specific optimizations
    config.addDataSourceProperty("journal_mode", "WAL");
    config.addDataSourceProperty("synchronous", "NORMAL");
    config.addDataSourceProperty("cache_size", "10000");
    config.addDataSourceProperty("foreign_keys", "true");
    
    // Connection pool settings for SQLite
    config.setMaximumPoolSize(1); // SQLite is single-threaded
    config.setMinimumIdle(1);
    config.setConnectionTimeout(30000);
    config.setIdleTimeout(0); // Never timeout
    config.setMaxLifetime(0); // Never expire
}

private void setupMySQLConfig(HikariConfig config) {
    String host = ConfigManager.getString("database.mysql.host", "localhost");
    int port = ConfigManager.getInt("database.mysql.port", 3306);
    String database = ConfigManager.getString("database.mysql.database", "rvnklore");
    String username = ConfigManager.getString("database.mysql.username", "rvnklore_user");
    String password = ConfigManager.getString("database.mysql.password", "");
    
    config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", host, port, database));
    config.setDriverClassName(DatabaseType.MYSQL.getDriverClass());
    config.setUsername(username);
    config.setPassword(password);
    
    // MySQL-specific optimizations
    config.addDataSourceProperty("useServerPrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.addDataSourceProperty("useLocalSessionState", "true");
    config.addDataSourceProperty("useLocalTransactionState", "true");
    config.addDataSourceProperty("rewriteBatchedStatements", "true");
    config.addDataSourceProperty("cacheResultSetMetadata", "true");
    config.addDataSourceProperty("cacheServerConfiguration", "true");
    config.addDataSourceProperty("elideSetAutoCommits", "true");
    config.addDataSourceProperty("maintainTimeStats", "false");
    
    // Connection pool settings
    config.setMaximumPoolSize(ConfigManager.getInt("database.mysql.pool.maximum-pool-size", 10));
    config.setMinimumIdle(ConfigManager.getInt("database.mysql.pool.minimum-idle", 2));
    config.setConnectionTimeout(ConfigManager.getLong("database.mysql.pool.connection-timeout", 30000));
    config.setIdleTimeout(ConfigManager.getLong("database.mysql.pool.idle-timeout", 600000));
    config.setMaxLifetime(ConfigManager.getLong("database.mysql.pool.max-lifetime", 1800000));
}

private void setupPostgreSQLConfig(HikariConfig config) {
    String host = ConfigManager.getString("database.postgresql.host", "localhost");
    int port = ConfigManager.getInt("database.postgresql.port", 5432);
    String database = ConfigManager.getString("database.postgresql.database", "rvnklore");
    String username = ConfigManager.getString("database.postgresql.username", "rvnklore_user");
    String password = ConfigManager.getString("database.postgresql.password", "");
    
    config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
    config.setDriverClassName(DatabaseType.POSTGRESQL.getDriverClass());
    config.setUsername(username);
    config.setPassword(password);
    
    // PostgreSQL-specific optimizations
    config.addDataSourceProperty("prepareThreshold", "0");
    config.addDataSourceProperty("defaultRowFetchSize", "50");
    config.addDataSourceProperty("tcpKeepAlive", "true");
    
    // Connection pool settings
    config.setMaximumPoolSize(ConfigManager.getInt("database.postgresql.pool.maximum-pool-size", 10));
    config.setMinimumIdle(ConfigManager.getInt("database.postgresql.pool.minimum-idle", 2));
    config.setConnectionTimeout(ConfigManager.getLong("database.postgresql.pool.connection-timeout", 30000));
    config.setIdleTimeout(ConfigManager.getLong("database.postgresql.pool.idle-timeout", 600000));
    config.setMaxLifetime(ConfigManager.getLong("database.postgresql.pool.max-lifetime", 1800000));
}
```

## Connection Management

### Connection Utilities

```java
public class ConnectionManager {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    
    public ConnectionManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
    }
    
    public <T> T executeWithConnection(ConnectionFunction<T> function) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            return function.apply(connection);
        } catch (SQLException e) {
            logger.error("Database operation failed", e);
            throw e;
        }
    }
    
    public void executeWithConnection(ConnectionConsumer consumer) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            consumer.accept(connection);
        } catch (SQLException e) {
            logger.error("Database operation failed", e);
            throw e;
        }
    }
    
    public <T> CompletableFuture<T> executeAsync(ConnectionFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithConnection(function);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    public CompletableFuture<Void> executeAsync(ConnectionConsumer consumer) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeWithConnection(consumer);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    @FunctionalInterface
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
    
    @FunctionalInterface
    public interface ConnectionConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
```

### Connection Health Monitoring

```java
public class ConnectionHealthMonitor {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final ScheduledExecutorService scheduler;
    private volatile boolean isHealthy = true;
    
    public ConnectionHealthMonitor(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startHealthCheck();
    }
    
    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 30, 30, TimeUnit.SECONDS);
    }
    
    private void performHealthCheck() {
        try {
            boolean healthy = testConnection();
            
            if (!healthy && isHealthy) {
                logger.warning("Database connection became unhealthy");
                isHealthy = false;
                onHealthStatusChanged(false);
                
            } else if (healthy && !isHealthy) {
                logger.info("Database connection recovered");
                isHealthy = true;
                onHealthStatusChanged(true);
            }
            
        } catch (Exception e) {
            logger.error("Health check failed", e);
            if (isHealthy) {
                isHealthy = false;
                onHealthStatusChanged(false);
            }
        }
    }
    
    private boolean testConnection() {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }
    
    private void onHealthStatusChanged(boolean healthy) {
        DatabaseHealthEvent event = new DatabaseHealthEvent(healthy);
        Bukkit.getPluginManager().callEvent(event);
    }
    
    public boolean isHealthy() {
        return isHealthy;
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

## Query Execution

### Query Builder

```java
public class QueryBuilder {
    private final StringBuilder query = new StringBuilder();
    private final List<Object> parameters = new ArrayList<>();
    private final DatabaseType databaseType;
    
    public QueryBuilder(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }
    
    public QueryBuilder select(String... columns) {
        query.append("SELECT ");
        if (columns.length == 0) {
            query.append("*");
        } else {
            query.append(String.join(", ", columns));
        }
        return this;
    }
    
    public QueryBuilder from(String table) {
        query.append(" FROM ").append(table);
        return this;
    }
    
    public QueryBuilder where(String condition) {
        query.append(" WHERE ").append(condition);
        return this;
    }
    
    public QueryBuilder and(String condition) {
        query.append(" AND ").append(condition);
        return this;
    }
    
    public QueryBuilder or(String condition) {
        query.append(" OR ").append(condition);
        return this;
    }
    
    public QueryBuilder orderBy(String column, String direction) {
        query.append(" ORDER BY ").append(column).append(" ").append(direction);
        return this;
    }
    
    public QueryBuilder limit(int count) {
        switch (databaseType) {
            case SQLITE:
            case MYSQL:
            case POSTGRESQL:
                query.append(" LIMIT ").append(count);
                break;
        }
        return this;
    }
    
    public QueryBuilder offset(int count) {
        switch (databaseType) {
            case SQLITE:
            case POSTGRESQL:
                query.append(" OFFSET ").append(count);
                break;
            case MYSQL:
                // MySQL uses LIMIT offset, count
                String currentQuery = query.toString();
                if (currentQuery.contains("LIMIT")) {
                    query.setLength(0);
                    query.append(currentQuery.replace("LIMIT", "LIMIT " + count + ","));
                }
                break;
        }
        return this;
    }
    
    public QueryBuilder parameter(Object value) {
        parameters.add(value);
        return this;
    }
    
    public PreparedStatement prepare(Connection connection) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(query.toString());
        
        for (int i = 0; i < parameters.size(); i++) {
            stmt.setObject(i + 1, parameters.get(i));
        }
        
        return stmt;
    }
    
    public String build() {
        return query.toString();
    }
    
    public List<Object> getParameters() {
        return new ArrayList<>(parameters);
    }
}
```

### Prepared Statement Manager

```java
public class PreparedStatementManager {
    private final Map<String, String> preparedQueries = new HashMap<>();
    private final DatabaseType databaseType;
    
    public PreparedStatementManager(DatabaseType databaseType) {
        this.databaseType = databaseType;
        initializePreparedQueries();
    }
    
    private void initializePreparedQueries() {
        // Lore entry queries
        preparedQueries.put("SELECT_LORE_BY_ID", 
            "SELECT * FROM lore_entry WHERE id = ?");
        preparedQueries.put("SELECT_LORE_BY_CREATOR", 
            "SELECT * FROM lore_entry WHERE creator_uuid = ?");
        preparedQueries.put("INSERT_LORE_ENTRY", 
            "INSERT INTO lore_entry (id, title, content, creator_uuid, creation_date, category) VALUES (?, ?, ?, ?, ?, ?)");
        preparedQueries.put("UPDATE_LORE_CONTENT", 
            "UPDATE lore_entry SET content = ?, last_modified = ? WHERE id = ?");
        preparedQueries.put("DELETE_LORE_ENTRY", 
            "DELETE FROM lore_entry WHERE id = ?");
            
        // Player discovery queries
        preparedQueries.put("SELECT_PLAYER_DISCOVERIES", 
            "SELECT * FROM player_discovery WHERE player_uuid = ?");
        preparedQueries.put("INSERT_DISCOVERY", 
            "INSERT INTO player_discovery (player_uuid, lore_id, discovery_date, discovery_location) VALUES (?, ?, ?, ?)");
        preparedQueries.put("CHECK_DISCOVERY_EXISTS", 
            "SELECT COUNT(*) FROM player_discovery WHERE player_uuid = ? AND lore_id = ?");
            
        // Location queries
        preparedQueries.put("SELECT_LOCATIONS_BY_WORLD", 
            "SELECT * FROM lore_location WHERE world_name = ?");
        preparedQueries.put("SELECT_NEARBY_LOCATIONS", 
            "SELECT * FROM lore_location WHERE world_name = ? AND " + getDistanceClause());
        preparedQueries.put("INSERT_LORE_LOCATION", 
            "INSERT INTO lore_location (lore_id, world_name, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)");
    }
    
    private String getDistanceClause() {
        switch (databaseType) {
            case SQLITE:
                return "((x - ?) * (x - ?) + (y - ?) * (y - ?) + (z - ?) * (z - ?)) <= (? * ?)";
            case MYSQL:
                return "ST_Distance_Sphere(POINT(x, z), POINT(?, ?)) <= ?";
            case POSTGRESQL:
                return "ST_DWithin(ST_MakePoint(x, z), ST_MakePoint(?, ?), ?)";
            default:
                return "1 = 1"; // Fallback
        }
    }
    
    public PreparedStatement createPreparedStatement(Connection connection, String queryKey, Object... parameters) throws SQLException {
        String query = preparedQueries.get(queryKey);
        if (query == null) {
            throw new IllegalArgumentException("Unknown prepared query: " + queryKey);
        }
        
        PreparedStatement stmt = connection.prepareStatement(query);
        
        for (int i = 0; i < parameters.length; i++) {
            stmt.setObject(i + 1, parameters[i]);
        }
        
        return stmt;
    }
    
    public String getQuery(String queryKey) {
        return preparedQueries.get(queryKey);
    }
}
```

## Transaction Handling

### Transaction Manager

```java
public class TransactionManager {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    
    public TransactionManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
    }
    
    public <T> T executeTransaction(TransactionFunction<T> function) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            
            try {
                T result = function.apply(connection);
                connection.commit();
                return result;
                
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException("Transaction failed", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    
    public void executeTransaction(TransactionConsumer consumer) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            
            try {
                consumer.accept(connection);
                connection.commit();
                
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException("Transaction failed", e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    
    public <T> CompletableFuture<T> executeTransactionAsync(TransactionFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeTransaction(function);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    @FunctionalInterface
    public interface TransactionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
    
    @FunctionalInterface
    public interface TransactionConsumer {
        void accept(Connection connection) throws SQLException;
    }
}

// Example transaction usage
public class LoreTransactionOperations {
    private final TransactionManager transactionManager;
    
    public void createLoreWithLocation(LoreEntry lore, LoreLocation location) throws SQLException {
        transactionManager.executeTransaction(connection -> {
            // Insert lore entry
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO lore_entry (id, title, content, creator_uuid, creation_date, category) VALUES (?, ?, ?, ?, ?, ?)")) {
                
                stmt.setString(1, lore.getId());
                stmt.setString(2, lore.getTitle());
                stmt.setString(3, lore.getContent());
                stmt.setString(4, lore.getCreatorUuid().toString());
                stmt.setTimestamp(5, Timestamp.from(lore.getCreationDate().toInstant()));
                stmt.setString(6, lore.getCategory());
                stmt.executeUpdate();
            }
            
            // Insert lore location
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO lore_location (lore_id, world_name, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                
                stmt.setString(1, location.getLoreId());
                stmt.setString(2, location.getWorldName());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setFloat(6, location.getYaw());
                stmt.setFloat(7, location.getPitch());
                stmt.executeUpdate();
            }
            
            logger.info("Created lore entry and location in transaction: " + lore.getId());
        });
    }
}
```

## Data Access Objects (DAO)

### Base DAO Implementation

```java
public abstract class BaseDAO<T> {
    protected final DatabaseManager databaseManager;
    protected final PreparedStatementManager stmtManager;
    protected final LogManager logger;
    
    public BaseDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.stmtManager = databaseManager.getPreparedStatementManager();
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
    }
    
    protected abstract T mapResultSet(ResultSet rs) throws SQLException;
    protected abstract String getTableName();
    protected abstract String getIdColumn();
    
    public Optional<T> findById(String id) throws SQLException {
        String query = "SELECT * FROM " + getTableName() + " WHERE " + getIdColumn() + " = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    public List<T> findAll() throws SQLException {
        String query = "SELECT * FROM " + getTableName();
        List<T> results = new ArrayList<>();
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
        }
        
        return results;
    }
    
    public void delete(String id) throws SQLException {
        String query = "DELETE FROM " + getTableName() + " WHERE " + getIdColumn() + " = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, id);
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                logger.info("Deleted " + getTableName() + " record: " + id);
            }
        }
    }
    
    public boolean exists(String id) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + getTableName() + " WHERE " + getIdColumn() + " = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
```

### Lore Entry DAO

```java
public class LoreEntryDAO extends BaseDAO<LoreEntry> {
    
    public LoreEntryDAO(DatabaseManager databaseManager) {
        super(databaseManager);
    }
    
    @Override
    protected LoreEntry mapResultSet(ResultSet rs) throws SQLException {
        LoreEntry entry = new LoreEntry();
        entry.setId(rs.getString("id"));
        entry.setTitle(rs.getString("title"));
        entry.setContent(rs.getString("content"));
        entry.setCreatorUuid(UUID.fromString(rs.getString("creator_uuid")));
        entry.setCreationDate(rs.getTimestamp("creation_date"));
        entry.setLastModified(rs.getTimestamp("last_modified"));
        entry.setCategory(rs.getString("category"));
        entry.setPublic(rs.getBoolean("is_public"));
        entry.setTags(parseTagsFromJson(rs.getString("tags")));
        return entry;
    }
    
    @Override
    protected String getTableName() {
        return "lore_entry";
    }
    
    @Override
    protected String getIdColumn() {
        return "id";
    }
    
    public void save(LoreEntry entry) throws SQLException {
        if (exists(entry.getId())) {
            update(entry);
        } else {
            insert(entry);
        }
    }
    
    private void insert(LoreEntry entry) throws SQLException {
        String query = "INSERT INTO lore_entry (id, title, content, creator_uuid, creation_date, category, is_public, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, entry.getId());
            stmt.setString(2, entry.getTitle());
            stmt.setString(3, entry.getContent());
            stmt.setString(4, entry.getCreatorUuid().toString());
            stmt.setTimestamp(5, new Timestamp(entry.getCreationDate().getTime()));
            stmt.setString(6, entry.getCategory());
            stmt.setBoolean(7, entry.isPublic());
            stmt.setString(8, convertTagsToJson(entry.getTags()));
            
            stmt.executeUpdate();
            logger.info("Inserted lore entry: " + entry.getId());
        }
    }
    
    private void update(LoreEntry entry) throws SQLException {
        String query = "UPDATE lore_entry SET title = ?, content = ?, last_modified = ?, category = ?, is_public = ?, tags = ? WHERE id = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, entry.getTitle());
            stmt.setString(2, entry.getContent());
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.setString(4, entry.getCategory());
            stmt.setBoolean(5, entry.isPublic());
            stmt.setString(6, convertTagsToJson(entry.getTags()));
            stmt.setString(7, entry.getId());
            
            stmt.executeUpdate();
            logger.info("Updated lore entry: " + entry.getId());
        }
    }
    
    public List<LoreEntry> findByCreator(UUID creatorUuid) throws SQLException {
        String query = "SELECT * FROM lore_entry WHERE creator_uuid = ? ORDER BY creation_date DESC";
        List<LoreEntry> results = new ArrayList<>();
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, creatorUuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
            }
        }
        
        return results;
    }
    
    public List<LoreEntry> findByCategory(String category) throws SQLException {
        String query = "SELECT * FROM lore_entry WHERE category = ? AND is_public = true ORDER BY title";
        List<LoreEntry> results = new ArrayList<>();
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, category);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
            }
        }
        
        return results;
    }
    
    public List<LoreEntry> searchByContent(String searchTerm) throws SQLException {
        String query = "SELECT * FROM lore_entry WHERE (title LIKE ? OR content LIKE ?) AND is_public = true ORDER BY title";
        List<LoreEntry> results = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
            }
        }
        
        return results;
    }
    
    private String convertTagsToJson(List<String> tags) {
        // Convert tags list to JSON string
        return JsonUtils.toJson(tags);
    }
    
    private List<String> parseTagsFromJson(String json) {
        // Parse JSON string to tags list
        return JsonUtils.fromJson(json, List.class);
    }
}
```

### Player Discovery DAO

```java
public class PlayerDiscoveryDAO extends BaseDAO<PlayerDiscovery> {
    
    public PlayerDiscoveryDAO(DatabaseManager databaseManager) {
        super(databaseManager);
    }
    
    @Override
    protected PlayerDiscovery mapResultSet(ResultSet rs) throws SQLException {
        PlayerDiscovery discovery = new PlayerDiscovery();
        discovery.setId(rs.getLong("id"));
        discovery.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        discovery.setLoreId(rs.getString("lore_id"));
        discovery.setDiscoveryDate(rs.getTimestamp("discovery_date"));
        discovery.setDiscoveryLocation(LocationUtil.fromString(rs.getString("discovery_location")));
        return discovery;
    }
    
    @Override
    protected String getTableName() {
        return "player_discovery";
    }
    
    @Override
    protected String getIdColumn() {
        return "id";
    }
    
    public void recordDiscovery(UUID playerUuid, String loreId, Location location) throws SQLException {
        String query = "INSERT INTO player_discovery (player_uuid, lore_id, discovery_date, discovery_location) VALUES (?, ?, ?, ?)";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, loreId);
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.setString(4, LocationUtil.toString(location));
            
            stmt.executeUpdate();
            logger.info("Recorded discovery for player " + playerUuid + ": " + loreId);
        }
    }
    
    public boolean hasPlayerDiscovered(UUID playerUuid, String loreId) throws SQLException {
        String query = "SELECT COUNT(*) FROM player_discovery WHERE player_uuid = ? AND lore_id = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, loreId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    
    public List<PlayerDiscovery> getPlayerDiscoveries(UUID playerUuid) throws SQLException {
        String query = "SELECT * FROM player_discovery WHERE player_uuid = ? ORDER BY discovery_date DESC";
        List<PlayerDiscovery> results = new ArrayList<>();
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, playerUuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
            }
        }
        
        return results;
    }
    
    public int getDiscoveryCount(String loreId) throws SQLException {
        String query = "SELECT COUNT(*) FROM player_discovery WHERE lore_id = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            
            stmt.setString(1, loreId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
```

## Migration System

### Database Migration Framework

```java
public class DatabaseMigrator {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final List<Migration> migrations;
    
    public DatabaseMigrator(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        this.migrations = new ArrayList<>();
        initializeMigrations();
    }
    
    private void initializeMigrations() {
        migrations.add(new Migration001_InitialSchema());
        migrations.add(new Migration002_AddLoreCategories());
        migrations.add(new Migration003_AddPlayerDiscoveries());
        migrations.add(new Migration004_AddLoreLocations());
        migrations.add(new Migration005_AddIndexes());
        migrations.add(new Migration006_AddTagsSupport());
    }
    
    public void runMigrations() throws SQLException {
        createMigrationTable();
        
        int currentVersion = getCurrentVersion();
        int targetVersion = getLatestVersion();
        
        if (currentVersion >= targetVersion) {
            logger.info("Database is up to date (version " + currentVersion + ")");
            return;
        }
        
        logger.info("Migrating database from version " + currentVersion + " to " + targetVersion);
        
        for (Migration migration : migrations) {
            if (migration.getVersion() > currentVersion) {
                runMigration(migration);
            }
        }
        
        logger.info("Database migration completed");
    }
    
    private void createMigrationTable() throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS schema_version (" +
                      "version INTEGER PRIMARY KEY, " +
                      "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                      ")";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
    
    private int getCurrentVersion() throws SQLException {
        String query = "SELECT MAX(version) FROM schema_version";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    
    private int getLatestVersion() {
        return migrations.stream()
            .mapToInt(Migration::getVersion)
            .max()
            .orElse(0);
    }
    
    private void runMigration(Migration migration) throws SQLException {
        logger.info("Running migration: " + migration.getDescription());
        
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            
            try {
                migration.up(connection, databaseManager.getDatabaseType());
                recordMigration(connection, migration.getVersion());
                connection.commit();
                
                logger.info("Migration completed: " + migration.getDescription());
                
            } catch (Exception e) {
                connection.rollback();
                throw new SQLException("Migration failed: " + migration.getDescription(), e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    
    private void recordMigration(Connection connection, int version) throws SQLException {
        String query = "INSERT INTO schema_version (version) VALUES (?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        }
    }
}

public abstract class Migration {
    public abstract int getVersion();
    public abstract String getDescription();
    public abstract void up(Connection connection, DatabaseType databaseType) throws SQLException;
    
    protected void executeUpdate(Connection connection, String sql) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
}
```

### Example Migration Classes

```java
public class Migration001_InitialSchema extends Migration {
    
    @Override
    public int getVersion() {
        return 1;
    }
    
    @Override
    public String getDescription() {
        return "Create initial schema";
    }
    
    @Override
    public void up(Connection connection, DatabaseType databaseType) throws SQLException {
        // Create lore_entry table
        String createLoreEntry = getCreateLoreEntrySQL(databaseType);
        executeUpdate(connection, createLoreEntry);
        
        // Create lore_location table
        String createLoreLocation = getCreateLoreLocationSQL(databaseType);
        executeUpdate(connection, createLoreLocation);
        
        // Create player_discovery table
        String createPlayerDiscovery = getCreatePlayerDiscoverySQL(databaseType);
        executeUpdate(connection, createPlayerDiscovery);
    }
    
    private String getCreateLoreEntrySQL(DatabaseType databaseType) {
        switch (databaseType) {
            case SQLITE:
                return "CREATE TABLE lore_entry (" +
                       "id TEXT PRIMARY KEY, " +
                       "title TEXT NOT NULL, " +
                       "content TEXT NOT NULL, " +
                       "creator_uuid TEXT NOT NULL, " +
                       "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                       "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                       "category TEXT DEFAULT 'general', " +
                       "is_public BOOLEAN DEFAULT true" +
                       ")";
                       
            case MYSQL:
                return "CREATE TABLE lore_entry (" +
                       "id VARCHAR(255) PRIMARY KEY, " +
                       "title VARCHAR(255) NOT NULL, " +
                       "content TEXT NOT NULL, " +
                       "creator_uuid CHAR(36) NOT NULL, " +
                       "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                       "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                       "category VARCHAR(100) DEFAULT 'general', " +
                       "is_public BOOLEAN DEFAULT true" +
                       ") ENGINE=InnoDB";
                       
            case POSTGRESQL:
                return "CREATE TABLE lore_entry (" +
                       "id VARCHAR(255) PRIMARY KEY, " +
                       "title VARCHAR(255) NOT NULL, " +
                       "content TEXT NOT NULL, " +
                       "creator_uuid UUID NOT NULL, " +
                       "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                       "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                       "category VARCHAR(100) DEFAULT 'general', " +
                       "is_public BOOLEAN DEFAULT true" +
                       ")";
        }
        
        throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
    
    // Similar methods for other tables...
}

public class Migration006_AddTagsSupport extends Migration {
    
    @Override
    public int getVersion() {
        return 6;
    }
    
    @Override
    public String getDescription() {
        return "Add tags support to lore entries";
    }
    
    @Override
    public void up(Connection connection, DatabaseType databaseType) throws SQLException {
        // Add tags column to lore_entry table
        String addTagsColumn = getAddTagsColumnSQL(databaseType);
        executeUpdate(connection, addTagsColumn);
        
        // Create indexes for better performance
        if (databaseType != DatabaseType.SQLITE) {
            String createTagIndex = "CREATE INDEX idx_lore_entry_tags ON lore_entry(tags)";
            executeUpdate(connection, createTagIndex);
        }
    }
    
    private String getAddTagsColumnSQL(DatabaseType databaseType) {
        switch (databaseType) {
            case SQLITE:
                return "ALTER TABLE lore_entry ADD COLUMN tags TEXT DEFAULT '[]'";
            case MYSQL:
                return "ALTER TABLE lore_entry ADD COLUMN tags JSON DEFAULT ('[]')";
            case POSTGRESQL:
                return "ALTER TABLE lore_entry ADD COLUMN tags JSONB DEFAULT '[]'";
        }
        
        throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
}
```

## Performance Optimization

### Connection Pool Optimization

```java
public class DatabasePerformanceManager {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final DatabaseMetrics metrics;
    
    public DatabasePerformanceManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        this.metrics = new DatabaseMetrics();
        startPerformanceMonitoring();
    }
    
    private void startPerformanceMonitoring() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
            RVNKLore.getInstance(),
            this::collectMetrics,
            200L, // Start after 10 seconds
            1200L  // Every minute
        );
    }
    
    private void collectMetrics() {
        HikariDataSource dataSource = databaseManager.getDataSource();
        
        metrics.setActiveConnections(dataSource.getHikariPoolMXBean().getActiveConnections());
        metrics.setIdleConnections(dataSource.getHikariPoolMXBean().getIdleConnections());
        metrics.setTotalConnections(dataSource.getHikariPoolMXBean().getTotalConnections());
        metrics.setThreadsAwaitingConnection(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        
        // Log warnings for performance issues
        if (metrics.getThreadsAwaitingConnection() > 0) {
            logger.warning("Database connection pool exhausted - " + 
                          metrics.getThreadsAwaitingConnection() + " threads waiting");
        }
        
        if (metrics.getActiveConnections() > (metrics.getTotalConnections() * 0.8)) {
            logger.warning("High database connection usage: " + 
                          metrics.getActiveConnections() + "/" + metrics.getTotalConnections());
        }
    }
    
    public DatabaseMetrics getMetrics() {
        return metrics;
    }
}

public class DatabaseMetrics {
    private volatile int activeConnections;
    private volatile int idleConnections;
    private volatile int totalConnections;
    private volatile int threadsAwaitingConnection;
    
    // Getters and setters...
}
```

### Query Optimization

```java
public class QueryOptimizer {
    private final DatabaseType databaseType;
    private final Map<String, String> optimizedQueries = new HashMap<>();
    
    public QueryOptimizer(DatabaseType databaseType) {
        this.databaseType = databaseType;
        initializeOptimizedQueries();
    }
    
    private void initializeOptimizedQueries() {
        // Optimized nearby location query
        switch (databaseType) {
            case SQLITE:
                optimizedQueries.put("NEARBY_LOCATIONS", 
                    "SELECT * FROM lore_location WHERE world_name = ? " +
                    "AND ((x - ?) * (x - ?) + (z - ?) * (z - ?)) <= (? * ?) " +
                    "ORDER BY ((x - ?) * (x - ?) + (z - ?) * (z - ?)) LIMIT 50");
                break;
                
            case MYSQL:
                optimizedQueries.put("NEARBY_LOCATIONS",
                    "SELECT *, ST_Distance_Sphere(POINT(x, z), POINT(?, ?)) as distance " +
                    "FROM lore_location " +
                    "WHERE world_name = ? " +
                    "HAVING distance <= ? " +
                    "ORDER BY distance LIMIT 50");
                break;
                
            case POSTGRESQL:
                optimizedQueries.put("NEARBY_LOCATIONS",
                    "SELECT *, ST_Distance(ST_MakePoint(x, z), ST_MakePoint(?, ?)) as distance " +
                    "FROM lore_location " +
                    "WHERE world_name = ? " +
                    "AND ST_DWithin(ST_MakePoint(x, z), ST_MakePoint(?, ?), ?) " +
                    "ORDER BY distance LIMIT 50");
                break;
        }
        
        // Optimized discovery statistics query
        optimizedQueries.put("DISCOVERY_STATS",
            "SELECT " +
            "COUNT(DISTINCT lore_id) as total_lore, " +
            "COUNT(*) as total_discoveries, " +
            "COUNT(DISTINCT player_uuid) as unique_discoverers " +
            "FROM player_discovery");
    }
    
    public String getOptimizedQuery(String queryKey) {
        return optimizedQueries.get(queryKey);
    }
}
```

## Error Handling

### Database Error Handler

```java
public class DatabaseErrorHandler {
    private final LogManager logger;
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
    private final long errorResetInterval = 300_000; // 5 minutes
    
    public DatabaseErrorHandler() {
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        startErrorCountReset();
    }
    
    public void handleSQLException(SQLException e, String operation) {
        String errorKey = operation + ":" + e.getSQLState();
        errorCounts.merge(errorKey, 1, Integer::sum);
        
        // Log error with context
        logger.error("Database error during " + operation + 
                    " (SQLState: " + e.getSQLState() + 
                    ", ErrorCode: " + e.getErrorCode() + ")", e);
        
        // Check for critical errors
        if (isCriticalError(e)) {
            handleCriticalError(e, operation);
        }
        
        // Check for connection errors
        if (isConnectionError(e)) {
            handleConnectionError(e, operation);
        }
        
        // Suggest solutions
        suggestSolution(e, operation);
    }
    
    private boolean isCriticalError(SQLException e) {
        // Database corruption, disk full, etc.
        return e.getSQLState() != null && (
            e.getSQLState().startsWith("08") || // Connection errors
            e.getSQLState().startsWith("53") || // Insufficient resources
            e.getSQLState().equals("HY000")     // General error
        );
    }
    
    private boolean isConnectionError(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("08");
    }
    
    private void handleCriticalError(SQLException e, String operation) {
        logger.error("CRITICAL DATABASE ERROR - Operation: " + operation);
        
        // Notify administrators
        DatabaseErrorEvent event = new DatabaseErrorEvent(e, operation, true);
        Bukkit.getPluginManager().callEvent(event);
        
        // Consider disabling database-dependent features
        if (errorCounts.values().stream().mapToInt(Integer::intValue).sum() > 10) {
            logger.error("Multiple database errors detected - consider investigating");
        }
    }
    
    private void handleConnectionError(SQLException e, String operation) {
        logger.warning("Database connection error - attempting recovery");
        
        // Trigger connection pool refresh
        DatabaseManager.getInstance().refreshConnectionPool();
    }
    
    private void suggestSolution(SQLException e, String operation) {
        switch (e.getSQLState()) {
            case "23000": // Integrity constraint violation
                logger.info("Suggestion: Check for duplicate keys or foreign key violations");
                break;
            case "42000": // Syntax error
                logger.info("Suggestion: Check SQL syntax in query for operation: " + operation);
                break;
            case "28000": // Invalid authorization
                logger.info("Suggestion: Check database credentials and permissions");
                break;
        }
    }
    
    private void startErrorCountReset() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
            RVNKLore.getInstance(),
            errorCounts::clear,
            errorResetInterval / 50, // Convert to ticks
            errorResetInterval / 50
        );
    }
}
```

## RVNKLore Integration

### Database Service Integration

```java
public class DatabaseService {
    private final DatabaseManager databaseManager;
    private final LoreEntryDAO loreEntryDAO;
    private final PlayerDiscoveryDAO playerDiscoveryDAO;
    private final LoreLocationDAO loreLocationDAO;
    private final TransactionManager transactionManager;
    
    public DatabaseService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.loreEntryDAO = new LoreEntryDAO(databaseManager);
        this.playerDiscoveryDAO = new PlayerDiscoveryDAO(databaseManager);
        this.loreLocationDAO = new LoreLocationDAO(databaseManager);
        this.transactionManager = new TransactionManager(databaseManager);
    }
    
    // Lore management operations
    public CompletableFuture<Optional<LoreEntry>> getLoreEntry(String loreId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loreEntryDAO.findById(loreId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    public CompletableFuture<Void> saveLoreEntry(LoreEntry entry) {
        return CompletableFuture.runAsync(() -> {
            try {
                loreEntryDAO.save(entry);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    // Discovery management operations
    public CompletableFuture<Boolean> recordDiscovery(Player player, String loreId, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!playerDiscoveryDAO.hasPlayerDiscovered(player.getUniqueId(), loreId)) {
                    playerDiscoveryDAO.recordDiscovery(player.getUniqueId(), loreId, location);
                    return true;
                }
                return false;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
    
    // Complex operations using transactions
    public CompletableFuture<Void> createLoreWithLocation(LoreEntry entry, LoreLocation location) {
        return CompletableFuture.runAsync(() -> {
            try {
                transactionManager.executeTransaction(connection -> {
                    // Insert lore entry
                    loreEntryDAO.save(entry);
                    
                    // Insert location
                    loreLocationDAO.save(location);
                    
                    // Log creation
                    LogManager.getInstance(RVNKLore.getInstance())
                        .info("Created lore with location: " + entry.getId());
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, AsyncExecutor.getDatabaseExecutor());
    }
}

// Integration with existing managers
public class LoreManager {
    private final DatabaseService databaseService;
    
    private LoreManager() {
        this.databaseService = new DatabaseService();
    }
    
    public void createLore(Player creator, String loreId, String title, String content, Location location) {
        // Create lore entry
        LoreEntry entry = new LoreEntry();
        entry.setId(loreId);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setCreatorUuid(creator.getUniqueId());
        entry.setCreationDate(new Date());
        
        // Create location
        LoreLocation loreLocation = new LoreLocation();
        loreLocation.setLoreId(loreId);
        loreLocation.setLocation(location);
        
        // Save to database
        databaseService.createLoreWithLocation(entry, loreLocation)
            .thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(creator, "&a✓ Lore created successfully: " + title);
                });
            })
            .exceptionally(throwable -> {
                logger.error("Failed to create lore: " + loreId, throwable);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendMessage(creator, "&c✖ Failed to create lore");
                });
                return null;
            });
    }
}
```

## Best Practices

### 1. **Connection Management**
- Use connection pooling for production environments
- Always close connections, statements, and result sets
- Use try-with-resources for automatic resource management
- Monitor connection pool health and performance

### 2. **Query Optimization**
- Use prepared statements to prevent SQL injection
- Implement proper indexing for frequently queried columns
- Use batch operations for multiple inserts/updates
- Consider database-specific optimizations

### 3. **Transaction Handling**
- Use transactions for related operations
- Keep transactions as short as possible
- Implement proper rollback mechanisms
- Consider isolation levels for concurrent access

### 4. **Error Handling**
- Implement comprehensive error handling and logging
- Provide meaningful error messages to users
- Monitor database health and performance metrics
- Implement retry mechanisms for transient failures

This Database Integration API reference provides comprehensive patterns for implementing robust database systems in the RVNKLore plugin, ensuring data integrity, performance, and reliability while maintaining proper integration with existing plugin systems.
