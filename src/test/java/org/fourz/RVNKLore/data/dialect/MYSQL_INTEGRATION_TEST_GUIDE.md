# MySQL Dialect Integration Test Guide

**Task**: test-07 - MySQL dialect integration tests
**Related Implementations**: impl-10 (MySQLDialect), impl-11 (MySQLQueryBuilder), impl-12 (MySQLConnection)

## Test Environment Setup

### Requirements

1. **MySQL Server** (version 5.7+ or 8.0+)
   - Local installation OR
   - MCSS Dev server database OR
   - Docker container: `docker run --name mysql-test -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=rvnklore_test -p 3306:3306 -d mysql:8.0`

2. **Test Database**: `rvnklore_test`

3. **Maven System Properties** (configure in IDE or mvn command):
   ```properties
   mysql.test.host=localhost
   mysql.test.port=3306
   mysql.test.database=rvnklore_test
   mysql.test.username=root
   mysql.test.password=test
   ```

### Running Tests

```bash
# With default localhost settings
mvn test -Dtest=MySQLDialectIntegrationTest

# With custom MySQL settings
mvn test -Dtest=MySQLDialectIntegrationTest \
  -Dmysql.test.host=192.168.1.100 \
  -Dmysql.test.database=rvnk_test \
  -Dmysql.test.username=rvnk_user \
  -Dmysql.test.password=secret

# Run all tests (will skip MySQL tests if connection fails)
mvn test
```

## Test Coverage

### 1. Schema Creation Tests (`MySQLDialectIntegrationTest`)

#### 1.1 AUTO_INCREMENT Primary Key
```sql
CREATE TABLE test_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
)
```
- ✅ Validates `dialect.getAutoIncrementPK()` returns `"INT AUTO_INCREMENT PRIMARY KEY"`
- ✅ Verifies column is auto-incrementing
- ✅ Confirms primary key constraint

#### 1.2 BOOLEAN Type (TINYINT(1))
```sql
CREATE TABLE test_players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    is_active TINYINT(1) NOT NULL DEFAULT 1
)
```
- ✅ Validates `dialect.getBooleanType()` returns `"TINYINT(1)"`
- ✅ Verifies boolean storage

#### 1.3 TEXT Types (VARCHAR, TEXT, MEDIUMTEXT)
```sql
CREATE TABLE test_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    short_text VARCHAR(50),
    medium_text TEXT,
    long_text MEDIUMTEXT
)
```
- ✅ VARCHAR for lengths ≤ 255
- ✅ TEXT for lengths 256-65535
- ✅ MEDIUMTEXT for lengths > 65535

#### 1.4 TIMESTAMP Columns
```sql
CREATE TABLE test_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL
)
```
- ✅ Default CURRENT_TIMESTAMP
- ✅ Nullable timestamps

#### 1.5 Table Existence Query
- ✅ Uses `information_schema.TABLES`
- ✅ Filters by `TABLE_SCHEMA = DATABASE()`

### 2. CRUD Operation Tests

#### 2.1 INSERT with Generated ID (LAST_INSERT_ID)
```java
PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
pstmt.executeUpdate();
ResultSet keys = pstmt.getGeneratedKeys();
int id = dialect.extractGeneratedId(pstmt, keys, "id");
```
- ✅ Validates `requiresGeneratedKeysFlag()` returns `true`
- ✅ Tests `extractGeneratedId()` retrieval
- ✅ Verifies auto-increment sequence

#### 2.2 SELECT Operations
- ✅ Simple SELECT with WHERE clause
- ✅ Multiple WHERE conditions (AND/OR)
- ✅ ORDER BY ascending/descending
- ✅ LIMIT and OFFSET pagination

#### 2.3 UPDATE Operations
- ✅ Single row updates
- ✅ Multi-row updates with WHERE
- ✅ UPDATE with LIMIT

#### 2.4 DELETE Operations
- ✅ Single row deletion
- ✅ Multi-row deletion with WHERE
- ✅ DELETE with LIMIT

### 3. Upsert Operation Tests

#### 3.1 ON DUPLICATE KEY UPDATE
```sql
INSERT INTO test_upsert (player_uuid, player_name, score)
VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE
    player_name = VALUES(player_name),
    score = VALUES(score)
```
- ✅ Validates `dialect.getUpsertSQL()` generates correct syntax
- ✅ Tests INSERT behavior (new row)
- ✅ Tests UPDATE behavior (duplicate key)
- ✅ Confirms `upsertNeedsDuplicateBinding()` returns `false`

#### 3.2 REPLACE INTO
```sql
REPLACE INTO test_upsert (player_uuid, player_name, score)
VALUES (?, ?, ?)
```
- ✅ Validates `dialect.getReplaceSQL()` generates correct syntax
- ✅ Tests DELETE + INSERT behavior

### 4. Transaction Tests

#### 4.1 Transaction Commit
```java
connection.setAutoCommit(false);
// ... execute statements ...
connection.commit();
```
- ✅ Multiple INSERT in single transaction
- ✅ Data persistence after commit

#### 4.2 Transaction Rollback
```java
connection.setAutoCommit(false);
// ... execute statements ...
connection.rollback();
```
- ✅ Data discarded after rollback
- ✅ No partial commits

#### 4.3 Batch Operations
```java
PreparedStatement pstmt = connection.prepareStatement(sql);
for (...) {
    pstmt.setX(...);
    pstmt.addBatch();
}
int[] results = pstmt.executeBatch();
connection.commit();
```
- ✅ Batch INSERT performance
- ✅ Transaction consistency for batch operations

### 5. Connection Management Tests

#### 5.1 Connection Validity
- ✅ `connection.isValid(5)` returns true
- ✅ `connection.isReadOnly()` returns false

#### 5.2 Database Metadata
- ✅ Product name: "MySQL"
- ✅ Version retrieval
- ✅ `supportsTransactions()` returns true
- ✅ `supportsGetGeneratedKeys()` returns true

#### 5.3 Dialect Name
- ✅ `dialect.getName()` returns "MySQL"

## Test Execution Strategy

### Graceful Skipping

All tests use `@Assumptions.assumeFalse(skipTests)` to skip gracefully if MySQL connection fails. This allows:
- ✅ Running full test suite on systems without MySQL
- ✅ CI/CD pipelines without MySQL setup
- ✅ Developer machines with SQLite-only setup

### Test Isolation

- Each test method has own database transaction
- Tables are dropped in `@AfterEach` cleanup
- No test dependencies on execution order

### Parametrized Testing (Future Enhancement)

Could extend to test multiple MySQL versions:
- MySQL 5.7 (legacy)
- MySQL 8.0 (current)
- MariaDB 10.x (compatibility)

## Manual Testing Checklist

When MySQL server is available, verify:

- [ ] All schema creation tests pass
- [ ] Generated IDs increment correctly
- [ ] Upsert operations work (both INSERT and UPDATE paths)
- [ ] Transactions commit successfully
- [ ] Transactions rollback without data corruption
- [ ] Batch operations handle 100+ rows efficiently
- [ ] Connection pooling (if HikariCP integrated)
- [ ] Reconnection after connection loss
- [ ] Concurrent access (multi-threaded inserts/updates)

## Integration with RVNKLore Plugin

### Real-World Usage Validation

After integration tests pass, verify on live server:

1. **Plugin Startup**:
   ```
   [RVNKLore] Initializing MySQL connection...
   [RVNKLore] Connected to MySQL database
   [RVNKLore] Creating database tables using MySQL dialect...
   [RVNKLore] Database initialized successfully
   ```

2. **Lore Entry Creation**:
   ```
   /lore add LANDMARK "The Great Library" --location
   ```
   - Verify INSERT with generated ID
   - Check AUTO_INCREMENT sequence

3. **Lore Entry Update**:
   ```
   /lore edit <id> --description "Updated text"
   ```
   - Verify UPDATE operations
   - Check timestamp updates

4. **Collection Progress (Upsert)**:
   ```
   Player collects item → upsert player_collection_progress
   ```
   - Verify ON DUPLICATE KEY UPDATE logic
   - Check score increments

5. **Batch Operations**:
   ```
   /lore import <file.json>
   ```
   - Verify batch INSERT performance
   - Check transaction consistency

## Troubleshooting

### Connection Refused
```
⚠ MySQL connection failed - skipping MySQL integration tests
  Reason: Connection refused: connect
```
**Solution**: Start MySQL server or use Docker container

### Access Denied
```
  Reason: Access denied for user 'root'@'localhost' (using password: YES)
```
**Solution**: Check `-Dmysql.test.username` and `-Dmysql.test.password`

### Database Not Found
```
  Reason: Unknown database 'rvnklore_test'
```
**Solution**: Create database manually:
```sql
CREATE DATABASE rvnklore_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### SSL Warnings
```
WARN: Establishing SSL connection without server's identity verification
```
**Solution**: Connection string includes `useSSL=false&allowPublicKeyRetrieval=true` for test environment

## Completion Criteria

test-07 is complete when:

1. ✅ `MySQLDialectIntegrationTest.java` compiles without errors
2. ✅ All tests pass with MySQL connection
3. ✅ Tests skip gracefully without MySQL
4. ✅ Maven dependency added: `mysql-connector-j:8.2.0` (test scope)
5. ✅ Documentation provided for test execution
6. ✅ Real-world plugin validation on MCSS Dev server

## Related Files

- `src/main/java/org/fourz/RVNKLore/data/dialect/MySQLDialect.java`
- `src/main/java/org/fourz/RVNKLore/data/query/MySQLQueryBuilder.java`
- `src/main/java/org/fourz/RVNKLore/data/MySQLConnection.java`
- `src/test/java/org/fourz/RVNKLore/data/dialect/MySQLDialectIntegrationTest.java`
- `pom.xml` (mysql-connector-j dependency)

## Next Steps

After test-07 completion:
1. Run tests on CI/CD pipeline with MySQL container
2. Performance benchmarking (MySQL vs SQLite)
3. HikariCP connection pooling integration (impl-13)
4. Async repository pattern migration (addresses current compilation errors)
