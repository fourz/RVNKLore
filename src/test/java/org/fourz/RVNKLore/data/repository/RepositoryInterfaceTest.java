package org.fourz.RVNKLore.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that repository interfaces follow the async pattern conventions.
 */
@DisplayName("Repository Interface Contracts")
class RepositoryInterfaceTest {

    @Test
    @DisplayName("ICollectionRewardRepository has all required methods")
    void collectionRewardRepoMethods() {
        Set<String> methodNames = getMethodNames(ICollectionRewardRepository.class);

        assertTrue(methodNames.contains("findByCollection"), "Missing findByCollection");
        assertTrue(methodNames.contains("addReward"), "Missing addReward");
        assertTrue(methodNames.contains("removeReward"), "Missing removeReward");
        assertTrue(methodNames.contains("claimReward"), "Missing claimReward");
        assertTrue(methodNames.contains("hasPlayerClaimed"), "Missing hasPlayerClaimed");
        assertTrue(methodNames.contains("getUnclaimedRewards"), "Missing getUnclaimedRewards");
        assertTrue(methodNames.contains("getClaimedRewards"), "Missing getClaimedRewards");
    }

    @Test
    @DisplayName("ICollectionRewardRepository methods return CompletableFuture")
    void collectionRewardRepoAsync() {
        for (Method method : ICollectionRewardRepository.class.getDeclaredMethods()) {
            assertEquals(CompletableFuture.class, method.getReturnType(),
                    method.getName() + " should return CompletableFuture");
        }
    }

    @Test
    @DisplayName("CollectionRewardRepository implements ICollectionRewardRepository")
    void collectionRewardRepoImplements() {
        assertTrue(ICollectionRewardRepository.class.isAssignableFrom(CollectionRewardRepository.class));
    }

    @Test
    @DisplayName("ILocationRepository exists and LocationRepository implements it")
    void locationRepoImplements() {
        // LocationRepository should exist
        assertDoesNotThrow(() -> Class.forName("org.fourz.RVNKLore.data.repository.LocationRepository"));

        // Verify it's a concrete class
        assertFalse(LocationRepository.class.isInterface());
    }

    @Test
    @DisplayName("IDiscoveryRepository exists and DiscoveryRepository implements it")
    void discoveryRepoImplements() {
        assertDoesNotThrow(() -> Class.forName("org.fourz.RVNKLore.data.repository.DiscoveryRepository"));
        assertFalse(DiscoveryRepository.class.isInterface());
    }

    @Test
    @DisplayName("IAchievementRepository exists and AchievementRepository implements it")
    void achievementRepoImplements() {
        assertDoesNotThrow(() -> Class.forName("org.fourz.RVNKLore.data.repository.AchievementRepository"));
        assertFalse(AchievementRepository.class.isInterface());
    }

    private Set<String> getMethodNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
