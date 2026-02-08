package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementProgress;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for player achievement progress.
 */
public class AchievementRepository implements IAchievementRepository {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;

    public AchievementRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "AchievementRepository");
    }

    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    @Override
    public CompletableFuture<Boolean> saveProgress(AchievementProgress progress) {
        return CompletableFuture.supplyAsync(() -> {
            // Upsert: insert or update on conflict
            String sql;
            if ("SQLite".equals(dbConnection.getDialect().getName())) {
                sql = "INSERT OR REPLACE INTO " + t("player_achievement") +
                    " (player_uuid, achievement_id, current_progress, target_progress, completed, rewards_claimed, started_at, completed_at)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO " + t("player_achievement") +
                    " (player_uuid, achievement_id, current_progress, target_progress, completed, rewards_claimed, started_at, completed_at)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE current_progress = VALUES(current_progress)," +
                    " target_progress = VALUES(target_progress), completed = VALUES(completed)," +
                    " rewards_claimed = VALUES(rewards_claimed), completed_at = VALUES(completed_at)";
            }

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, progress.getPlayerId().toString());
                stmt.setString(2, progress.getAchievementId());
                stmt.setInt(3, progress.getCurrentProgress());
                stmt.setInt(4, progress.getTargetProgress());
                stmt.setBoolean(5, progress.isCompleted());
                stmt.setBoolean(6, progress.isRewardsClaimed());
                stmt.setLong(7, progress.getStartedAt());
                stmt.setLong(8, progress.getCompletedAt());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to save achievement progress: " + progress, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<AchievementProgress>> loadPlayerProgress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<AchievementProgress> progressList = new ArrayList<>();
            String sql = "SELECT * FROM " + t("player_achievement") + " WHERE player_uuid = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        progressList.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to load progress for player: " + playerId, e);
            }
            return progressList;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, List<AchievementProgress>>> loadAllProgress() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<AchievementProgress>> allProgress = new HashMap<>();
            String sql = "SELECT * FROM " + t("player_achievement");

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AchievementProgress progress = mapRow(rs);
                    allProgress.computeIfAbsent(progress.getPlayerId(), k -> new ArrayList<>())
                        .add(progress);
                }
                logger.debug("Loaded achievement progress for " + allProgress.size() + " players");
            } catch (SQLException e) {
                logger.error("Failed to load all achievement progress", e);
            }
            return allProgress;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteProgress(UUID playerId, String achievementId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("player_achievement") +
                    " WHERE player_uuid = ? AND achievement_id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, achievementId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete achievement progress", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteAllProgress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("player_achievement") + " WHERE player_uuid = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete all progress for: " + playerId, e);
                return false;
            }
        });
    }

    private AchievementProgress mapRow(ResultSet rs) throws SQLException {
        return new AchievementProgress(
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("achievement_id"),
            rs.getInt("current_progress"),
            rs.getInt("target_progress"),
            rs.getBoolean("completed"),
            rs.getBoolean("rewards_claimed"),
            rs.getLong("started_at"),
            rs.getLong("completed_at")
        );
    }
}
