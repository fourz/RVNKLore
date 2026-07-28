package org.fourz.RVNKLore.data.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

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
        CollectionReward reward = new CollectionReward("col-456", CollectionReward.RewardType.CURRENCY, "{\"amount\":500.0}");

        assertEquals(-1, reward.getId());
        assertEquals("col-456", reward.getCollectionId());
        assertEquals(CollectionReward.RewardType.CURRENCY, reward.getRewardType());
        assertEquals("{\"amount\":500.0}", reward.getRewardData());
    }

    @Test
    @DisplayName("setId updates id")
    void setId() {
        CollectionReward reward = new CollectionReward("col-1", CollectionReward.RewardType.PERMISSION, "{\"permission\":\"rvnk.vip\"}");
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
        assertEquals(CollectionReward.RewardType.PERMISSION, CollectionReward.RewardType.fromString("permission"));
        assertEquals(CollectionReward.RewardType.CURRENCY, CollectionReward.RewardType.fromString("Currency"));
        assertEquals(CollectionReward.RewardType.COMMAND, CollectionReward.RewardType.fromString("COMMAND"));
        assertEquals(CollectionReward.RewardType.LORE_ITEM, CollectionReward.RewardType.fromString("lore_item"));
        assertEquals(CollectionReward.RewardType.ACHIEVEMENT, CollectionReward.RewardType.fromString("ACHIEVEMENT"));
    }

    @Test
    @DisplayName("RewardType.fromString returns ITEM for unknown values")
    void rewardTypeFromStringUnknown() {
        assertEquals(CollectionReward.RewardType.ITEM, CollectionReward.RewardType.fromString("INVALID"));
        assertEquals(CollectionReward.RewardType.ITEM, CollectionReward.RewardType.fromString(""));
    }

    @Test
    @DisplayName("Every persisted RewardType name still resolves")
    void allRewardTypesExist() {
        // These names are written to the reward_type column, so removing or renaming one is a
        // data-compatibility break and must fail here. Adding a new value is not a break, so the
        // expected count is derived from this list rather than hardcoded — the previous literal
        // 4 went stale the moment LORE_ITEM and ACHIEVEMENT were added (#1836).
        List<String> persisted = List.of(
                "ITEM", "PERMISSION", "COMMAND", "CURRENCY", "LORE_ITEM", "ACHIEVEMENT");

        for (String name : persisted) {
            assertDoesNotThrow(() -> CollectionReward.RewardType.valueOf(name),
                    name + " is persisted in reward_type and must remain a valid RewardType");
        }
        assertTrue(CollectionReward.RewardType.values().length >= persisted.size(),
                "RewardType lost a value that is persisted in reward_type");
    }
}
