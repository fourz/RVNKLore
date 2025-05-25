package org.fourz.RVNKLore.cosmetic;

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
     * Add a command to execute as reward.
     * Commands should use {player} placeholder for the player name.
     *
     * @param command The command to execute
     */
    public void addCommand(String command) {
        if (command != null && !command.trim().isEmpty()) {
            commands.add(command);
        }
    }
    
    /**
     * Check if this reward set has any rewards configured.
     *
     * @return True if there are any rewards
     */
    public boolean hasRewards() {
        return !items.isEmpty() || !commands.isEmpty() || 
               experiencePoints > 0 || title != null;
    }
    
    // Getters
    public List<ItemStack> getItems() { 
        return new ArrayList<>(items); 
    }
    
    public List<String> getCommands() { 
        return new ArrayList<>(commands); 
    }
    
    public int getExperiencePoints() { 
        return experiencePoints; 
    }
    
    public String getTitle() { 
        return title; 
    }
    
    public String getCompletionMessage() { 
        return completionMessage; 
    }
}
