package org.fourz.RVNKLore.discovery;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKQuests.event.QuestCompleteEvent;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;

/**
 * Listens for RVNKQuests QuestCompleteEvent to trigger QUEST_COMPLETE discoveries.
 * Only registered when RVNKQuests is present on the server.
 *
 * <p>Lore entries can be linked to quests via metadata:
 * {@code entry.addMetadata("discovery_trigger", "quest:<quest_id>")}
 */
public class QuestDiscoveryListener implements Listener {

    private final RVNKLore plugin;
    private final DiscoveryManager discoveryManager;
    private final LoreManager loreManager;
    private final LogManager logger;

    public QuestDiscoveryListener(RVNKLore plugin, DiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.discoveryManager = discoveryManager;
        this.loreManager = plugin.getLoreManager();
        this.logger = LogManager.getInstance(plugin, "QuestDiscoveryListener");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuestComplete(QuestCompleteEvent event) {
        Player player = event.getPlayer();
        String questId = event.getQuestId();

        logger.debug("Quest completed: " + questId + " by " + player.getName());

        // Find lore entries tagged with this quest as a discovery trigger
        // Convention: metadata "discovery_trigger" = "quest:<quest_id>"
        String triggerKey = "quest:" + questId;

        // Search all entries for matching trigger metadata
        List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();
        for (LoreEntry entry : allEntries) {
            if (!entry.isApproved()) continue;

            String trigger = entry.getMetadata("discovery_trigger");
            if (triggerKey.equalsIgnoreCase(trigger)) {
                discoveryManager.triggerDiscovery(
                    player, entry,
                    DiscoveryTriggerType.QUEST_COMPLETE,
                    player.getLocation()
                );
                logger.debug("Triggered quest discovery: " + entry.getName() + " for " + player.getName());
            }
        }
    }
}
