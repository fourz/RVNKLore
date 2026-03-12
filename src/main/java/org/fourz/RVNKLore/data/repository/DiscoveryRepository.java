package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for lore_discovery enriched discovery data.
 * Uses HikariCP connection pool with async CompletableFuture pattern.
 */
public class DiscoveryRepository implements IDiscoveryRepository {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;

    public DiscoveryRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "DiscoveryRepository");
    }

    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    @Override
    public CompletableFuture<Boolean> recordDiscovery(UUID playerUuid, String entryId,
                                                       String triggerType, String world,
                                                       Double x, Double y, Double z,
                                                       boolean isFirstDiscovery) {
        return CompletableFuture.supplyAsync(() -> {
            // Check for duplicate first
            String checkSql = "SELECT COUNT(*) FROM " + t("lore_discovery") +
                    " WHERE player_uuid = ? AND entry_id = ?";

            try (Connection conn = dbConnection.getConnection()) {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, playerUuid.toString());
                    checkStmt.setString(2, entryId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return true; // Already recorded
                        }
                    }
                }

                String insertSql = "INSERT INTO " + t("lore_discovery") +
                        " (player_uuid, entry_id, trigger_type, world, x, y, z, is_first_discovery)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, entryId);
                    stmt.setString(3, triggerType);
                    stmt.setString(4, world);
                    if (x != null) {
                        stmt.setDouble(5, x);
                        stmt.setDouble(6, y);
                        stmt.setDouble(7, z);
                    } else {
                        stmt.setNull(5, Types.DOUBLE);
                        stmt.setNull(6, Types.DOUBLE);
                        stmt.setNull(7, Types.DOUBLE);
                    }
                    stmt.setBoolean(8, isFirstDiscovery);

                    return stmt.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to record discovery: player=" + playerUuid + ", entry=" + entryId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasDiscovered(UUID playerUuid, String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("lore_discovery") +
                    " WHERE player_uuid = ? AND entry_id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to check discovery: " + playerUuid + ", " + entryId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> getDiscoveredEntryIds(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> ids = new ArrayList<>();
            String sql = "SELECT entry_id FROM " + t("lore_discovery") +
                    " WHERE player_uuid = ? ORDER BY discovered_at";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getString("entry_id"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get discoveries for player: " + playerUuid, e);
            }
            return ids;
        });
    }

    @Override
    public CompletableFuture<UUID> getFirstDiscoverer(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid FROM " + t("lore_discovery") +
                    " WHERE entry_id = ? AND is_first_discovery = TRUE LIMIT 1";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("player_uuid"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get first discoverer for entry: " + entryId, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<String, UUID>> loadAllFirstDiscoverers() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, UUID> discoverers = new HashMap<>();
            String sql = "SELECT entry_id, player_uuid FROM " + t("lore_discovery") +
                    " WHERE is_first_discovery = TRUE";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    discoverers.put(
                        rs.getString("entry_id"),
                        UUID.fromString(rs.getString("player_uuid"))
                    );
                }
                logger.debug("Loaded " + discoverers.size() + " first discoverers from database");
            } catch (SQLException e) {
                logger.error("Failed to load first discoverers", e);
            }
            return discoverers;
        });
    }

    @Override
    public CompletableFuture<Integer> countPlayerDiscoveries(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("lore_discovery") +
                    " WHERE player_uuid = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to count discoveries for: " + playerUuid, e);
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> countFirstDiscoveries(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("lore_discovery") +
                    " WHERE player_uuid = ? AND is_first_discovery = TRUE";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to count first discoveries for: " + playerUuid, e);
                return 0;
            }
        });
    }
}
