package org.fourz.RVNKLore.data.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CollectionReward model.
 */
@DisplayName("CollectionReward Model")
class CollectionRewardTest {

    @Test
    @DisplayName("Constructor with all fields sets values correctly")
    void constructorWithAllFields() {
        CollectionReward reward = new CollectionReward(42, "col-123", CollectionReward.RewardType.ITEM, "{\"item\":\"diamond\"}");

        assertEquals(42, reward.getId());
        assertEquals("col-123", reward.getCollectionId());
        assertEquals(CollectionReward.RewardType.ITEM, reward.getRewardType());
        assertEquals("{\"item\":\"diamond\"}", reward.getRewardData());
    }

    @Test
    @DisplayName("Convenience constructor defaults id to -1")
    void convenienceConstructor() {
        CollectionReward reward = new CollectionReward("col-456", CollectionReward.RewardType.EXPERIENCE, "500");

        assertEquals(-1, reward.getId());
        assertEquals("col-456", reward.getCollectionId());
        assertEquals(CollectionReward.RewardType.EXPERIENCE, reward.getRewardType());
        assertEquals("500", reward.getRewardData());
    }

    @Test
    @DisplayName("setId updates id")
    void setId() {
        CollectionReward reward = new CollectionReward("col-1", CollectionReward.RewardType.TITLE, "Champion");
        assertEquals(-1, reward.getId());

        reward.setId(99);
        assertEquals(99, reward.getId());
    }

    @Test
    @DisplayName("toString contains all fields")
    void toStringContainsFields() {
        CollectionReward reward = new CollectionReward(1, "col-1", CollectionReward.RewardType.COMMAND, "/give @p diamond 1");
        String result = reward.toString();

        assertTrue(result.contains("id=1"));
        assertTrue(result.contains("col-1"));
        assertTrue(result.contains("COMMAND"));
        assertTrue(result.contains("/give @p diamond 1"));
    }

    @Test
    @DisplayName("RewardType.fromString parses valid values")
    void rewardTypeFromStringValid() {
        assertEquals(CollectionReward.RewardType.ITEM, CollectionReward.RewardType.fromString("ITEM"));
        assertEquals(CollectionReward.RewardType.EXPERIENCE, CollectionReward.RewardType.fromString("experience"));
        assertEquals(CollectionReward.RewardType.TITLE, CollectionReward.RewardType.fromString("Title"));
        assertEquals(CollectionReward.RewardType.COMMAND, CollectionReward.RewardType.fromString("COMMAND"));
    }

    @Test
    @DisplayName("RewardType.fromString returns ITEM for unknown values")
    void rewardTypeFromStringUnknown() {
        assertEquals(CollectionReward.RewardType.ITEM, CollectionReward.RewardType.fromString("INVALID"));
        assertEquals(CollectionReward.RewardType.ITEM, CollectionReward.RewardType.fromString(""));
    }

    @Test
    @DisplayName("All four RewardType enum values exist")
    void allRewardTypesExist() {
        CollectionReward.RewardType[] types = CollectionReward.RewardType.values();
        assertEquals(4, types.length);
        assertNotNull(CollectionReward.RewardType.valueOf("ITEM"));
        assertNotNull(CollectionReward.RewardType.valueOf("EXPERIENCE"));
        assertNotNull(CollectionReward.RewardType.valueOf("TITLE"));
        assertNotNull(CollectionReward.RewardType.valueOf("COMMAND"));
    }
}
