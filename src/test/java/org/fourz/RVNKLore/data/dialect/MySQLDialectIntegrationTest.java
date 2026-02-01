package org.fourz.RVNKLore.data.dialect;

import org.fourz.RVNKLore.data.query.MySQLQueryBuilder;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL dialect integration tests.
 *
 * <p>These tests validate MySQL-specific SQL generation and execution patterns
 * implemented in impl-10, impl-11, impl-12.
 *
 * <p><strong>Test Environment Requirements:</strong>
 * <ul>
 *   <li>MySQL server accessible (local or MCSS Dev server)</li>
 *   <li>Test database configured via system properties:
 *     <ul>
 *       <li>mysql.test.host (default: localhost)</li>
 *       <li>mysql.test.port (default: 3306)</li>
 *       <li>mysql.test.database (default: rvnklore_test)</li>
 *       <li>mysql.test.username (default: root)</li>
 *       <li>mysql.test.password (default: empty)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Skip Conditions:</strong>
 * Tests are skipped if MySQL connection fails (allows running on systems without MySQL).
 * Set system property: {@code -Dmysql.test.skip=false} to force execution.
 *
 * <p><strong>Related Tasks:</strong>
 * <ul>
 *   <li>test-07: MySQL dialect integration tests</li>
 *   <li>impl-10: MySQLDialect implementation</li>
 *   <li>impl-11: MySQLQueryBuilder implementation</li>
 *   <li>impl-12: MySQLConnection implementation</li>
 * </ul>
 */
@DisplayName("MySQL Dialect Integration Tests")
class MySQLDialectIntegrationTest {

    private static Connection connection;
    private static MySQLDialect dialect;
    private static boolean skipTests = false;

    // Test configuration from system properties
    private static final String TEST_HOST = System.getProperty("mysql.test.host", "localhost");
    private static final int TEST_PORT = Integer.parseInt(System.getProperty("mysql.test.port", "3306"));
    private static final String TEST_DATABASE = System.getProperty("mysql.test.database", "rvnklore_test");
    private static final String TEST_USERNAME = System.getProperty("mysql.test.username", "root");
    private static final String TEST_PASSWORD = System.getProperty("mysql.test.password", "");

    @BeforeAll
    static void setupMySQLConnection() {
        dialect = new MySQLDialect();

        try {
            // Attempt to connect to MySQL test database
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                TEST_HOST, TEST_PORT, TEST_DATABASE);
            connection = DriverManager.getConnection(url, TEST_USERNAME, TEST_PASSWORD);

            System.out.println("✓ MySQL connection established for integration tests");
            System.out.println("  Database: " + TEST_DATABASE);
            System.out.println("  Host: " + TEST_HOST + ":" + TEST_PORT);
        } catch (ClassNotFoundException e) {
            System.err.println("⚠ MySQL JDBC driver not found - skipping MySQL integration tests");
            skipTests = true;
        } catch (SQLException e) {
            System.err.println("⚠ MySQL connection failed - skipping MySQL integration tests");
            System.err.println("  Reason: " + e.getMessage());
            System.err.println("  Configure test database with system properties:");
            System.err.println("    -Dmysql.test.host=" + TEST_HOST);
            System.err.println("    -Dmysql.test.database=" + TEST_DATABASE);
            System.err.println("    -Dmysql.test.username=" + TEST_USERNAME);
            skipTests = true;
        }
    }

    @AfterAll
    static void closeMySQLConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("✓ MySQL connection closed");
            } catch (SQLException e) {
                System.err.println("⚠ Error closing MySQL connection: " + e.getMessage());
            }
        }
    }

    @BeforeEach
    void checkSkipCondition() {
        Assumptions.assumeFalse(skipTests, "MySQL connection not available");
    }

    @AfterEach
    void cleanupTestTables() throws SQLException {
        if (connection != null && !skipTests) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_items");
                stmt.execute("DROP TABLE IF EXISTS test_players");
                stmt.execute("DROP TABLE IF EXISTS test_upsert");
            }
        }
    }

    @Nested
    @DisplayName("1. Schema Creation Tests")
    class SchemaCreationTests {

        @Test
        @DisplayName("Create table with AUTO_INCREMENT primary key")
        void testAutoIncrementPrimaryKey() throws SQLException {
            String autoIncPK = dialect.getAutoIncrementPK();
            assertEquals("INT AUTO_INCREMENT PRIMARY KEY", autoIncPK);

            // Execute CREATE TABLE statement
            String createTableSQL = "CREATE TABLE test_items (" +
                "id " + autoIncPK + ", " +
                "name VARCHAR(100) NOT NULL" +
                ")";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }

            // Verify table exists and has correct structure
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "test_items", "id")) {
                assertTrue(rs.next(), "Column 'id' should exist");
                assertEquals("INT", rs.getString("TYPE_NAME"));
                assertEquals("YES", rs.getString("IS_AUTOINCREMENT"));
            }

            try (ResultSet rs = metaData.getPrimaryKeys(null, null, "test_items")) {
                assertTrue(rs.next(), "Primary key should exist");
                assertEquals("id", rs.getString("COLUMN_NAME"));
            }
        }

        @Test
        @DisplayName("Create table with BOOLEAN type (TINYINT(1))")
        void testBooleanType() throws SQLException {
            String boolType = dialect.getBooleanType();
            assertEquals("TINYINT(1)", boolType);

            String createTableSQL = "CREATE TABLE test_players (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "is_active " + boolType + " NOT NULL DEFAULT 1" +
                ")";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }

            // Verify column type
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "test_players", "is_active")) {
                assertTrue(rs.next(), "Column 'is_active' should exist");
                assertTrue(rs.getString("TYPE_NAME").contains("TINYINT"));
            }
        }

        @Test
        @DisplayName("Create table with TEXT types (VARCHAR, TEXT, MEDIUMTEXT)")
        void testTextTypes() throws SQLException {
            // Test different text lengths
            assertEquals("VARCHAR(100)", dialect.getTextType(100));
            assertEquals("VARCHAR(255)", dialect.getTextType(255));
            assertEquals("TEXT", dialect.getTextType(1000));
            assertEquals("TEXT", dialect.getTextType(65535));
            assertEquals("MEDIUMTEXT", dialect.getTextType(100000));
            assertEquals("TEXT", dialect.getTextType(0));
            assertEquals("TEXT", dialect.getTextType(-1));

            String createTableSQL = "CREATE TABLE test_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "short_text " + dialect.getTextType(50) + ", " +
                "medium_text " + dialect.getTextType(1000) + ", " +
                "long_text " + dialect.getTextType(100000) +
                ")";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }

            // Verify column types
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "test_items", null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");

                    switch (columnName) {
                        case "short_text":
                            assertTrue(typeName.contains("VARCHAR"));
                            break;
                        case "medium_text":
                            assertTrue(typeName.contains("TEXT"));
                            break;
                        case "long_text":
                            assertTrue(typeName.contains("MEDIUMTEXT"));
                            break;
                    }
                }
            }
        }

        @Test
        @DisplayName("Create table with TIMESTAMP columns")
        void testTimestampTypes() throws SQLException {
            String timestampDefault = dialect.getTimestampType(true);
            String timestampNullable = dialect.getTimestampType(false);

            assertEquals("TIMESTAMP DEFAULT CURRENT_TIMESTAMP", timestampDefault);
            assertEquals("TIMESTAMP NULL", timestampNullable);

            String createTableSQL = "CREATE TABLE test_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "created_at " + timestampDefault + ", " +
                "updated_at " + timestampNullable +
                ")";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }

            // Verify timestamp columns
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "test_items", "created_at")) {
                assertTrue(rs.next());
                assertEquals("TIMESTAMP", rs.getString("TYPE_NAME"));
            }
        }

        @Test
        @DisplayName("Table exists query works correctly")
        void testTableExistsQuery() throws SQLException {
            // Create test table
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE test_items (id INT PRIMARY KEY)");
            }

            // Test tableExists query
            String tableExistsSQL = dialect.getTableExistsQuery("test_items");
            assertEquals("SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                tableExistsSQL);

            try (PreparedStatement pstmt = connection.prepareStatement(tableExistsSQL)) {
                pstmt.setString(1, "test_items");
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next(), "test_items should exist");
                    assertEquals("test_items", rs.getString("TABLE_NAME"));
                }
            }

            // Test non-existent table
            try (PreparedStatement pstmt = connection.prepareStatement(tableExistsSQL)) {
                pstmt.setString(1, "nonexistent_table");
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertFalse(rs.next(), "nonexistent_table should not exist");
                }
            }
        }
    }

    @Nested
    @DisplayName("2. CRUD Operation Tests")
    class CrudOperationTests {

        @BeforeEach
        void setupTestTable() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE test_items (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "value INT DEFAULT 0" +
                    ")");
            }
        }

        @Test
        @DisplayName("INSERT with generated ID retrieval (LAST_INSERT_ID)")
        void testInsertWithGeneratedKey() throws SQLException {
            String insertSQL = "INSERT INTO test_items (name, value) VALUES (?, ?)";

            // Verify dialect requires generated keys flag
            assertTrue(dialect.requiresGeneratedKeysFlag());

            try (PreparedStatement pstmt = connection.prepareStatement(
                    insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, "Test Item");
                pstmt.setInt(2, 42);

                int rowsAffected = pstmt.executeUpdate();
                assertEquals(1, rowsAffected);

                // Extract generated ID using dialect method
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    int generatedId = dialect.extractGeneratedId(pstmt, rs, "id");
                    assertTrue(generatedId > 0, "Generated ID should be positive");
                    assertEquals(1, generatedId, "First insert should get ID 1");
                }
            }

            // Insert second item and verify ID increments
            try (PreparedStatement pstmt = connection.prepareStatement(
                    insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, "Second Item");
                pstmt.setInt(2, 100);
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    int generatedId = dialect.extractGeneratedId(pstmt, rs, "id");
                    assertEquals(2, generatedId, "Second insert should get ID 2");
                }
            }
        }

        @Test
        @DisplayName("SELECT operations with WHERE clause")
        void testSelectOperations() throws SQLException {
            // Insert test data
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO test_items (name, value) VALUES (?, ?)")) {
                pstmt.setString(1, "Item A");
                pstmt.setInt(2, 10);
                pstmt.executeUpdate();

                pstmt.setString(1, "Item B");
                pstmt.setInt(2, 20);
                pstmt.executeUpdate();

                pstmt.setString(1, "Item C");
                pstmt.setInt(2, 30);
                pstmt.executeUpdate();
            }

            // Test SELECT with WHERE
            String selectSQL = "SELECT id, name, value FROM test_items WHERE value >= ? ORDER BY value";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setInt(1, 20);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Item B", rs.getString("name"));
                    assertEquals(20, rs.getInt("value"));

                    assertTrue(rs.next());
                    assertEquals("Item C", rs.getString("name"));
                    assertEquals(30, rs.getInt("value"));

                    assertFalse(rs.next());
                }
            }
        }

        @Test
        @DisplayName("UPDATE operations")
        void testUpdateOperations() throws SQLException {
            // Insert test data
            int itemId;
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO test_items (name, value) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, "Original Name");
                pstmt.setInt(2, 50);
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    rs.next();
                    itemId = rs.getInt(1);
                }
            }

            // Update the item
            String updateSQL = "UPDATE test_items SET name = ?, value = ? WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
                pstmt.setString(1, "Updated Name");
                pstmt.setInt(2, 100);
                pstmt.setInt(3, itemId);

                int rowsAffected = pstmt.executeUpdate();
                assertEquals(1, rowsAffected);
            }

            // Verify update
            String selectSQL = "SELECT name, value FROM test_items WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setInt(1, itemId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Updated Name", rs.getString("name"));
                    assertEquals(100, rs.getInt("value"));
                }
            }
        }

        @Test
        @DisplayName("DELETE operations")
        void testDeleteOperations() throws SQLException {
            // Insert test data
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO test_items (name, value) VALUES (?, ?)")) {
                pstmt.setString(1, "Item to Delete");
                pstmt.setInt(2, 999);
                pstmt.executeUpdate();
            }

            // Delete the item
            String deleteSQL = "DELETE FROM test_items WHERE value = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, 999);

                int rowsAffected = pstmt.executeUpdate();
                assertEquals(1, rowsAffected);
            }

            // Verify deletion
            String selectSQL = "SELECT COUNT(*) FROM test_items WHERE value = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setInt(1, 999);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            }
        }
    }

    @Nested
    @DisplayName("3. Upsert Operation Tests")
    class UpsertOperationTests {

        @BeforeEach
        void setupTestTable() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE test_upsert (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, " +
                    "player_name VARCHAR(50) NOT NULL, " +
                    "score INT DEFAULT 0, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            }
        }

        @Test
        @DisplayName("ON DUPLICATE KEY UPDATE upsert")
        void testOnDuplicateKeyUpdate() throws SQLException {
            String[] keyColumns = {"player_uuid"};
            String[] allColumns = {"player_uuid", "player_name", "score"};
            String[] updateColumns = {"player_name", "score"};

            String upsertSQL = dialect.getUpsertSQL("test_upsert", keyColumns, allColumns, updateColumns);

            // Verify SQL structure
            assertTrue(upsertSQL.contains("INSERT INTO test_upsert"));
            assertTrue(upsertSQL.contains("ON DUPLICATE KEY UPDATE"));
            assertTrue(upsertSQL.contains("player_name = VALUES(player_name)"));
            assertTrue(upsertSQL.contains("score = VALUES(score)"));

            // Verify dialect doesn't need duplicate binding
            assertFalse(dialect.upsertNeedsDuplicateBinding());

            String testUUID = "550e8400-e29b-41d4-a716-446655440000";

            // First upsert (INSERT)
            try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
                pstmt.setString(1, testUUID);
                pstmt.setString(2, "PlayerOne");
                pstmt.setInt(3, 100);

                int rowsAffected = pstmt.executeUpdate();
                assertTrue(rowsAffected > 0);
            }

            // Verify insert
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT player_name, score FROM test_upsert WHERE player_uuid = ?")) {
                pstmt.setString(1, testUUID);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PlayerOne", rs.getString("player_name"));
                    assertEquals(100, rs.getInt("score"));
                }
            }

            // Second upsert (UPDATE)
            try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
                pstmt.setString(1, testUUID);
                pstmt.setString(2, "PlayerOneRenamed");
                pstmt.setInt(3, 200);

                int rowsAffected = pstmt.executeUpdate();
                assertTrue(rowsAffected > 0);
            }

            // Verify update
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT player_name, score FROM test_upsert WHERE player_uuid = ?")) {
                pstmt.setString(1, testUUID);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PlayerOneRenamed", rs.getString("player_name"));
                    assertEquals(200, rs.getInt("score"));
                    assertFalse(rs.next(), "Should only have one row");
                }
            }
        }

        @Test
        @DisplayName("REPLACE INTO operation")
        void testReplaceInto() throws SQLException {
            String[] columns = {"player_uuid", "player_name", "score"};
            String replaceSQL = dialect.getReplaceSQL("test_upsert", columns);

            assertTrue(replaceSQL.contains("REPLACE INTO test_upsert"));
            assertTrue(replaceSQL.contains("(player_uuid, player_name, score)"));
            assertTrue(replaceSQL.contains("VALUES (?, ?, ?)"));

            String testUUID = "550e8400-e29b-41d4-a716-446655440001";

            // First REPLACE (INSERT)
            try (PreparedStatement pstmt = connection.prepareStatement(replaceSQL)) {
                pstmt.setString(1, testUUID);
                pstmt.setString(2, "PlayerTwo");
                pstmt.setInt(3, 150);
                pstmt.executeUpdate();
            }

            // Second REPLACE (DELETE + INSERT)
            try (PreparedStatement pstmt = connection.prepareStatement(replaceSQL)) {
                pstmt.setString(1, testUUID);
                pstmt.setString(2, "PlayerTwoReplaced");
                pstmt.setInt(3, 300);
                pstmt.executeUpdate();
            }

            // Verify replace
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT player_name, score FROM test_upsert WHERE player_uuid = ?")) {
                pstmt.setString(1, testUUID);

                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PlayerTwoReplaced", rs.getString("player_name"));
                    assertEquals(300, rs.getInt("score"));
                }
            }
        }
    }

    @Nested
    @DisplayName("4. Transaction Tests")
    class TransactionTests {

        @BeforeEach
        void setupTestTable() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE test_items (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "value INT DEFAULT 0" +
                    ")");
            }
        }

        @Test
        @DisplayName("Transaction commit")
        void testTransactionCommit() throws SQLException {
            connection.setAutoCommit(false);

            try {
                // Insert multiple items in transaction
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "INSERT INTO test_items (name, value) VALUES (?, ?)")) {
                    pstmt.setString(1, "Item 1");
                    pstmt.setInt(2, 10);
                    pstmt.executeUpdate();

                    pstmt.setString(1, "Item 2");
                    pstmt.setInt(2, 20);
                    pstmt.executeUpdate();

                    pstmt.setString(1, "Item 3");
                    pstmt.setInt(2, 30);
                    pstmt.executeUpdate();
                }

                connection.commit();

                // Verify all items inserted
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_items")) {
                    assertTrue(rs.next());
                    assertEquals(3, rs.getInt(1));
                }
            } finally {
                connection.setAutoCommit(true);
            }
        }

        @Test
        @DisplayName("Transaction rollback")
        void testTransactionRollback() throws SQLException {
            connection.setAutoCommit(false);

            try {
                // Insert items
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "INSERT INTO test_items (name, value) VALUES (?, ?)")) {
                    pstmt.setString(1, "Item A");
                    pstmt.setInt(2, 100);
                    pstmt.executeUpdate();

                    pstmt.setString(1, "Item B");
                    pstmt.setInt(2, 200);
                    pstmt.executeUpdate();
                }

                // Rollback transaction
                connection.rollback();

                // Verify no items inserted
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_items")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            } finally {
                connection.setAutoCommit(true);
            }
        }

        @Test
        @DisplayName("Batch operations in transaction")
        void testBatchOperations() throws SQLException {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "INSERT INTO test_items (name, value) VALUES (?, ?)")) {

                    for (int i = 1; i <= 100; i++) {
                        pstmt.setString(1, "Batch Item " + i);
                        pstmt.setInt(2, i * 10);
                        pstmt.addBatch();
                    }

                    int[] results = pstmt.executeBatch();
                    assertEquals(100, results.length);
                }

                connection.commit();

                // Verify batch insert
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_items")) {
                    assertTrue(rs.next());
                    assertEquals(100, rs.getInt(1));
                }
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Nested
    @DisplayName("5. Connection Management Tests")
    class ConnectionManagementTests {

        @Test
        @DisplayName("Connection is valid and not read-only")
        void testConnectionValidity() throws SQLException {
            assertTrue(connection.isValid(5), "Connection should be valid");
            assertFalse(connection.isReadOnly(), "Connection should not be read-only");
        }

        @Test
        @DisplayName("Database metadata retrieval")
        void testDatabaseMetadata() throws SQLException {
            DatabaseMetaData metaData = connection.getMetaData();

            assertNotNull(metaData);
            assertEquals("MySQL", metaData.getDatabaseProductName());
            assertTrue(metaData.getDatabaseProductVersion().length() > 0);
            assertTrue(metaData.supportsTransactions());
            assertTrue(metaData.supportsGetGeneratedKeys());
        }

        @Test
        @DisplayName("Dialect name matches connection")
        void testDialectName() {
            assertEquals("MySQL", dialect.getName());
        }
    }
}
