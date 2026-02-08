package org.fourz.RVNKLore.achievement;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.reward.*;
import org.fourz.RVNKLore.data.repository.AchievementRepository;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages achievements, progress tracking, and reward distribution.
 */
public class AchievementManager {

    private final RVNKLore plugin;
    private final LogManager logger;
    private AchievementRepository achievementRepository;

    // Achievement registry
    private final Map<String, Achievement> achievements = new ConcurrentHashMap<>();

    // Player progress cache (player UUID -> achievement ID -> progress)
    private final Map<UUID, Map<String, AchievementProgress>> playerProgress = new ConcurrentHashMap<>();

    // Reward handlers
    private final List<RewardHandler> rewardHandlers = new ArrayList<>();

    // Configuration
    private boolean enableNotifications = true;
    private boolean enableBroadcast = true;
    private boolean enableSounds = true;

    public AchievementManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AchievementManager");
    }

    /**
     * Initialize the achievement system.
     */
    public void initialize() {
        logger.info("Initializing AchievementManager...");

        // Register default reward handlers
        registerRewardHandler(new ItemRewardHandler(plugin));
        registerRewardHandler(new ExperienceRewardHandler(plugin));
        registerRewardHandler(new CommandRewardHandler(plugin));
        registerRewardHandler(new PermissionRewardHandler(plugin));

        // Load configuration
        loadConfiguration();

        // Create default achievements
        createDefaultAchievements();

        // Load player progress from database
        try {
            this.achievementRepository = plugin.getDatabaseManager().getAchievementRepository();
            if (achievementRepository != null) {
                Map<UUID, List<AchievementProgress>> allProgress = achievementRepository.loadAllProgress().join();
                for (Map.Entry<UUID, List<AchievementProgress>> entry : allProgress.entrySet()) {
                    Map<String, AchievementProgress> progressMap = playerProgress.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>());
                    for (AchievementProgress progress : entry.getValue()) {
                        progressMap.put(progress.getAchievementId(), progress);
                    }
                }
                logger.info("Loaded achievement progress for " + allProgress.size() + " players from database");
            }
        } catch (Exception e) {
            logger.warning("Failed to load achievement progress from database: " + e.getMessage());
        }

        logger.info("AchievementManager initialized with " + achievements.size() + " achievements");
    }

    /**
     * Load configuration from config.yml
     */
    private void loadConfiguration() {
        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().getConfig() != null) {
                enableNotifications = plugin.getConfigManager().getConfig()
                    .getBoolean("achievements.notifications.enabled", true);
                enableBroadcast = plugin.getConfigManager().getConfig()
                    .getBoolean("achievements.broadcast.enabled", true);
                enableSounds = plugin.getConfigManager().getConfig()
                    .getBoolean("achievements.sounds.enabled", true);
            }
        } catch (Exception e) {
            logger.debug("Using default achievement settings: " + e.getMessage());
        }
    }

    /**
     * Create default achievements.
     */
    private void createDefaultAchievements() {
        // Discovery milestones
        registerAchievement(new Achievement.Builder("first_discovery", "First Steps", AchievementType.DISCOVERY_COUNT)
            .description("Discover your first lore entry")
            .targetCount(1)
            .points(5)
            .icon("BOOK")
            .reward(AchievementReward.experience(50))
            .build());

        registerAchievement(new Achievement.Builder("lore_seeker_10", "Lore Seeker", AchievementType.DISCOVERY_COUNT)
            .description("Discover 10 lore entries")
            .targetCount(10)
            .points(20)
            .icon("BOOKSHELF")
            .reward(AchievementReward.experience(200))
            .build());

        registerAchievement(new Achievement.Builder("lore_master_50", "Lore Master", AchievementType.DISCOVERY_COUNT)
            .description("Discover 50 lore entries")
            .targetCount(50)
            .points(50)
            .icon("ENCHANTING_TABLE")
            .reward(AchievementReward.experience(500))
            .reward(AchievementReward.permission("rvnklore.title.loremaster"))
            .build());

        registerAchievement(new Achievement.Builder("lore_sage_100", "Lore Sage", AchievementType.DISCOVERY_COUNT)
            .description("Discover 100 lore entries")
            .targetCount(100)
            .points(100)
            .icon("DRAGON_EGG")
            .reward(AchievementReward.experience(1000))
            .reward(AchievementReward.permission("rvnklore.title.loresage"))
            .build());

        // Collection achievements
        registerAchievement(new Achievement.Builder("first_collection", "Collector", AchievementType.COLLECTION_COMPLETE)
            .description("Complete your first collection")
            .targetCount(1)
            .points(25)
            .icon("CHEST")
            .reward(AchievementReward.experience(300))
            .build());

        // Explorer achievements
        registerAchievement(new Achievement.Builder("explorer_5", "Explorer", AchievementType.MULTI_CATEGORY)
            .description("Discover entries in 3 different categories")
            .targetCount(3)
            .points(15)
            .icon("COMPASS")
            .reward(AchievementReward.experience(150))
            .build());

        logger.debug("Created " + achievements.size() + " default achievements");
    }

    /**
     * Register an achievement.
     */
    public void registerAchievement(Achievement achievement) {
        if (achievement == null) return;
        achievements.put(achievement.getId(), achievement);
        logger.debug("Registered achievement: " + achievement.getName());
    }

    /**
     * Register a reward handler.
     */
    public void registerRewardHandler(RewardHandler handler) {
        rewardHandlers.add(handler);
        logger.debug("Registered reward handler: " + handler.getDescription());
    }

    /**
     * Get an achievement by ID.
     */
    public Optional<Achievement> getAchievement(String id) {
        return Optional.ofNullable(achievements.get(id));
    }

    /**
     * Get all achievements.
     */
    public Collection<Achievement> getAllAchievements() {
        return new ArrayList<>(achievements.values());
    }

    /**
     * Get a player's progress for an achievement.
     */
    public AchievementProgress getProgress(UUID playerId, String achievementId) {
        Map<String, AchievementProgress> playerMap = playerProgress.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        return playerMap.computeIfAbsent(achievementId, k -> {
            Achievement achievement = achievements.get(achievementId);
            int target = achievement != null ? achievement.getTargetCount() : 1;
            return new AchievementProgress(playerId, achievementId, target);
        });
    }

    /**
     * Increment progress for an achievement.
     *
     * @param player The player
     * @param achievementId The achievement ID
     * @return true if the achievement was completed by this increment
     */
    public boolean incrementProgress(Player player, String achievementId) {
        return incrementProgress(player, achievementId, 1);
    }

    /**
     * Increment progress for an achievement by a specific amount.
     */
    public boolean incrementProgress(Player player, String achievementId, int amount) {
        AchievementProgress progress = getProgress(player.getUniqueId(), achievementId);

        if (progress.isCompleted()) {
            return false; // Already completed
        }

        boolean completed = progress.increment(amount);

        if (completed) {
            handleAchievementUnlock(player, achievementId, progress);
        }

        // Persist progress to database
        persistProgress(progress);

        return completed;
    }

    /**
     * Set progress for an achievement.
     */
    public boolean setProgress(Player player, String achievementId, int value) {
        AchievementProgress progress = getProgress(player.getUniqueId(), achievementId);

        if (progress.isCompleted()) {
            return false;
        }

        boolean completed = progress.setProgress(value);

        if (completed) {
            handleAchievementUnlock(player, achievementId, progress);
        }

        return completed;
    }

    /**
     * Handle achievement unlock: fire event, notify, grant rewards.
     */
    private void handleAchievementUnlock(Player player, String achievementId, AchievementProgress progress) {
        Achievement achievement = achievements.get(achievementId);
        if (achievement == null) {
            logger.warning("Achievement not found for unlock: " + achievementId);
            return;
        }

        // Fire event
        AchievementUnlockEvent event = new AchievementUnlockEvent(player, achievement, progress);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        // Send notification
        if (!event.isSuppressNotification() && enableNotifications) {
            sendUnlockNotification(player, achievement);
        }

        // Grant rewards
        if (!event.isSuppressRewards()) {
            grantRewards(player, achievement);
        }

        logger.info(player.getName() + " unlocked achievement: " + achievement.getName());
    }

    /**
     * Send unlock notification to player.
     */
    private void sendUnlockNotification(Player player, Achievement achievement) {
        // Title
        player.sendTitle(
            ChatColor.GOLD + "Achievement Unlocked!",
            ChatColor.YELLOW + achievement.getName(),
            10, 40, 10
        );

        // Chat message
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "★ " + ChatColor.BOLD + "Achievement Unlocked!" + ChatColor.GOLD + " ★");
        player.sendMessage(ChatColor.YELLOW + achievement.getName());
        player.sendMessage(ChatColor.GRAY + achievement.getDescription());
        player.sendMessage(ChatColor.DARK_GRAY + "+" + achievement.getPoints() + " achievement points");
        player.sendMessage("");

        // Sound
        if (enableSounds) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Broadcast (if enabled and not hidden)
        if (enableBroadcast && !achievement.isHidden()) {
            String broadcast = ChatColor.GOLD + player.getName() + ChatColor.YELLOW +
                " unlocked the achievement " + ChatColor.GOLD + achievement.getName() + ChatColor.YELLOW + "!";
            Bukkit.broadcastMessage(broadcast);
        }
    }

    /**
     * Grant all rewards for an achievement.
     */
    private void grantRewards(Player player, Achievement achievement) {
        for (AchievementReward reward : achievement.getRewards()) {
            grantReward(player, reward);
        }
    }

    /**
     * Grant a single reward to a player.
     */
    public boolean grantReward(Player player, AchievementReward reward) {
        for (RewardHandler handler : rewardHandlers) {
            if (handler.canHandle(reward)) {
                try {
                    return handler.grantReward(player, reward);
                } catch (Exception e) {
                    logger.error("Error granting reward: " + reward, e);
                }
            }
        }
        logger.warning("No handler found for reward type: " + reward.getType());
        return false;
    }

    /**
     * Get player's total achievement points.
     */
    public int getPlayerPoints(UUID playerId) {
        int total = 0;
        Map<String, AchievementProgress> progressMap = playerProgress.get(playerId);
        if (progressMap != null) {
            for (AchievementProgress progress : progressMap.values()) {
                if (progress.isCompleted()) {
                    Achievement achievement = achievements.get(progress.getAchievementId());
                    if (achievement != null) {
                        total += achievement.getPoints();
                    }
                }
            }
        }
        return total;
    }

    /**
     * Get all completed achievements for a player.
     */
    public List<Achievement> getCompletedAchievements(UUID playerId) {
        List<Achievement> completed = new ArrayList<>();
        Map<String, AchievementProgress> progressMap = playerProgress.get(playerId);
        if (progressMap != null) {
            for (AchievementProgress progress : progressMap.values()) {
                if (progress.isCompleted()) {
                    Achievement achievement = achievements.get(progress.getAchievementId());
                    if (achievement != null) {
                        completed.add(achievement);
                    }
                }
            }
        }
        return completed;
    }

    /**
     * Manually grant an achievement to a player (admin command).
     */
    public boolean grantAchievement(Player player, String achievementId) {
        Achievement achievement = achievements.get(achievementId);
        if (achievement == null) {
            return false;
        }

        AchievementProgress progress = getProgress(player.getUniqueId(), achievementId);
        if (progress.isCompleted()) {
            return false; // Already has it
        }

        // Force complete
        progress.setProgress(progress.getTargetProgress());
        handleAchievementUnlock(player, achievementId, progress);
        return true;
    }

    /**
     * Revoke an achievement from a player (admin command).
     */
    public boolean revokeAchievement(UUID playerId, String achievementId) {
        Map<String, AchievementProgress> progressMap = playerProgress.get(playerId);
        if (progressMap != null) {
            progressMap.remove(achievementId);
            return true;
        }
        return false;
    }

    /**
     * Called when a player discovers a lore entry.
     * Updates relevant achievement progress.
     */
    public void onLoreDiscovery(Player player, String loreType) {
        // Increment discovery count achievements
        incrementProgress(player, "first_discovery");
        incrementProgress(player, "lore_seeker_10");
        incrementProgress(player, "lore_master_50");
        incrementProgress(player, "lore_sage_100");

        // Track category discovery for explorer achievements
        // TODO: Track unique categories discovered
    }

    /**
     * Called when a player completes a collection.
     */
    public void onCollectionComplete(Player player, String collectionId) {
        incrementProgress(player, "first_collection");

        // Check for specific collection achievements
        for (Achievement achievement : achievements.values()) {
            if (achievement.getType() == AchievementType.COLLECTION_COMPLETE) {
                String targetCollection = achievement.getTargetCollectionId();
                if (collectionId.equals(targetCollection)) {
                    incrementProgress(player, achievement.getId());
                }
            }
        }
    }

    /**
     * Persist a single progress entry to the database.
     */
    private void persistProgress(AchievementProgress progress) {
        if (achievementRepository != null) {
            achievementRepository.saveProgress(progress).exceptionally(ex -> {
                logger.warning("Failed to persist achievement progress: " + ex.getMessage());
                return false;
            });
        }
    }

    /**
     * Shutdown the achievement manager.
     */
    public void shutdown() {
        // Save all progress to database
        if (achievementRepository != null) {
            int saved = 0;
            for (Map.Entry<UUID, Map<String, AchievementProgress>> playerEntry : playerProgress.entrySet()) {
                for (AchievementProgress progress : playerEntry.getValue().values()) {
                    try {
                        achievementRepository.saveProgress(progress).join();
                        saved++;
                    } catch (Exception e) {
                        logger.warning("Failed to save progress on shutdown: " + e.getMessage());
                    }
                }
            }
            logger.info("Saved " + saved + " achievement progress records to database");
        }

        achievements.clear();
        playerProgress.clear();
        rewardHandlers.clear();
        logger.info("AchievementManager shutdown complete");
    }
}
