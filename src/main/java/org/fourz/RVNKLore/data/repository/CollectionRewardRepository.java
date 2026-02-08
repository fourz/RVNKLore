package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for collection reward definitions and per-player claim tracking.
 * Uses collection_reward for definitions and player_reward_claim for per-player state.
 */
public class CollectionRewardRepository implements ICollectionRewardRepository {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;

    public CollectionRewardRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "CollectionRewardRepository");
    }

    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    @Override
    public CompletableFuture<List<CollectionReward>> findByCollection(String collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CollectionReward> rewards = new ArrayList<>();
            String sql = "SELECT id, collection_id, reward_type, reward_data FROM " +
                    t("collection_reward") + " WHERE collection_id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, collectionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rewards.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to find rewards for collection: " + collectionId, e);
            }
            return rewards;
        });
    }

    @Override
    public CompletableFuture<Boolean> addReward(CollectionReward reward) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + t("collection_reward") +
                    " (collection_id, reward_type, reward_data, is_claimed) VALUES (?, ?, ?, 0)";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, reward.getCollectionId());
                stmt.setString(2, reward.getRewardType().name());
                stmt.setString(3, reward.getRewardData());
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            reward.setId(keys.getInt(1));
                        }
                    }
                }
                return rows > 0;
            } catch (SQLException e) {
                logger.error("Failed to add reward: " + reward, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removeReward(int rewardId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("collection_reward") + " WHERE id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, rewardId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to remove reward: " + rewardId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> claimReward(int rewardId, UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            // Check if already claimed
            String checkSql = "SELECT 1 FROM " + t("player_reward_claim") +
                    " WHERE reward_id = ? AND player_uuid = ?";
            String insertSql = "INSERT INTO " + t("player_reward_claim") +
                    " (reward_id, player_uuid, claimed_at) VALUES (?, ?, ?)";

            try (Connection conn = dbConnection.getConnection()) {
                // Check for existing claim
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setInt(1, rewardId);
                    check.setString(2, playerId.toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            return false; // Already claimed
                        }
                    }
                }

                // Insert claim
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setInt(1, rewardId);
                    insert.setString(2, playerId.toString());
                    insert.setLong(3, System.currentTimeMillis());
                    return insert.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to claim reward " + rewardId + " for player " + playerId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPlayerClaimed(int rewardId, UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + t("player_reward_claim") +
                    " WHERE reward_id = ? AND player_uuid = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, rewardId);
                stmt.setString(2, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.error("Failed to check claim status", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<CollectionReward>> getUnclaimedRewards(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CollectionReward> rewards = new ArrayList<>();
            // Find rewards for collections the player has completed but not yet claimed
            String sql = "SELECT cr.id, cr.collection_id, cr.reward_type, cr.reward_data " +
                    "FROM " + t("collection_reward") + " cr " +
                    "INNER JOIN " + t("player_collection_progress") + " pcp " +
                    "  ON cr.collection_id = pcp.collection_id " +
                    "WHERE pcp.player_id = ? AND pcp.progress >= 1.0 " +
                    "  AND cr.id NOT IN (" +
                    "    SELECT reward_id FROM " + t("player_reward_claim") +
                    "    WHERE player_uuid = ?" +
                    "  )";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rewards.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get unclaimed rewards for: " + playerId, e);
            }
            return rewards;
        });
    }

    @Override
    public CompletableFuture<List<CollectionReward>> getClaimedRewards(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CollectionReward> rewards = new ArrayList<>();
            String sql = "SELECT cr.id, cr.collection_id, cr.reward_type, cr.reward_data " +
                    "FROM " + t("collection_reward") + " cr " +
                    "INNER JOIN " + t("player_reward_claim") + " prc " +
                    "  ON cr.id = prc.reward_id " +
                    "WHERE prc.player_uuid = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rewards.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to get claimed rewards for: " + playerId, e);
            }
            return rewards;
        });
    }

    private CollectionReward mapRow(ResultSet rs) throws SQLException {
        return new CollectionReward(
                rs.getInt("id"),
                rs.getString("collection_id"),
                CollectionReward.RewardType.fromString(rs.getString("reward_type")),
                rs.getString("reward_data")
        );
    }
}
