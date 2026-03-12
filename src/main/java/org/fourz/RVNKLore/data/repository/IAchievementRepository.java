package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.achievement.AchievementProgress;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for player achievement progress persistence.
 */
public interface IAchievementRepository {

    CompletableFuture<Boolean> saveProgress(AchievementProgress progress);

    CompletableFuture<List<AchievementProgress>> loadPlayerProgress(UUID playerId);

    CompletableFuture<Map<UUID, List<AchievementProgress>>> loadAllProgress();

    CompletableFuture<Boolean> deleteProgress(UUID playerId, String achievementId);

    CompletableFuture<Boolean> deleteAllProgress(UUID playerId);
}
