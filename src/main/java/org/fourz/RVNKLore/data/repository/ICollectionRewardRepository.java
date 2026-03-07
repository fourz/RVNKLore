package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.data.model.CollectionReward;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for collection reward definitions and per-player claim tracking.
 */
public interface ICollectionRewardRepository {

    CompletableFuture<List<CollectionReward>> findByCollection(String collectionId);

    CompletableFuture<Boolean> addReward(CollectionReward reward);

    CompletableFuture<Boolean> removeReward(int rewardId);

    CompletableFuture<Boolean> claimReward(int rewardId, UUID playerId);

    CompletableFuture<Boolean> hasPlayerClaimed(int rewardId, UUID playerId);

    CompletableFuture<List<CollectionReward>> getUnclaimedRewards(UUID playerId);

    CompletableFuture<List<CollectionReward>> getClaimedRewards(UUID playerId);
}
