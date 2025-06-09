package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents rewards given for completing a head collection.
 * Supports both item and command-based rewards with configurable conditions.
 */
public class CollectionRewards {
    private final List<ItemStack> items;
    private final List<String> commands;
    private final int experiencePoints;
    private final String title;
    private final String completionMessage;
    
    /**
     * Default constructor creating empty rewards.
     */
    public CollectionRewards() {
        this.items = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.experiencePoints = 0;
        this.title = null;
        this.completionMessage = null;
    }
    
    /**
     * Constructor with specific reward configuration.
     *
     * @param experiencePoints Experience points to award
     * @param title Title to grant the player
     * @param completionMessage Message to display on completion
     */
    public CollectionRewards(int experiencePoints, String title, String completionMessage) {
        this.items = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.experiencePoints = experiencePoints;
        this.title = title;
        this.completionMessage = completionMessage;
    }
    
    /**
     * Add an item reward.
     *
     * @param item The item to reward
     */
    public void addItem(ItemStack item) {
        if (item != null) {
            items.add(item.clone());
        }
    }
    
    /**
     * Add a command reward.
     *
     * @param command The command to execute (without slash)
     */
    public void addCommand(String command) {
        if (command != null && !command.trim().isEmpty()) {
            commands.add(command.trim());
        }
    }
    
    /**
     * Get all item rewards.
     *
     * @return List of item rewards
     */
    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }
    
    /**
     * Get all command rewards.
     *
     * @return List of command rewards
     */
    public List<String> getCommands() {
        return new ArrayList<>(commands);
    }
    
    /**
     * Get experience points reward.
     *
     * @return Experience points to award
     */
    public int getExperiencePoints() {
        return experiencePoints;
    }
    
    /**
     * Get title reward.
     *
     * @return Title to grant or null if none
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Get completion message.
     *
     * @return Message to display on completion or null if none
     */
    public String getCompletionMessage() {
        return completionMessage;
    }
    
    /**
     * Check if there are any rewards configured.
     *
     * @return True if any rewards are present
     */
    public boolean hasRewards() {
        return !items.isEmpty() || !commands.isEmpty() || experiencePoints > 0 || 
               title != null || completionMessage != null;
    }
}
