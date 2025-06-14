package org.fourz.RVNKLore.lore.player;

/**
 * Record for storing name change information
 */
public class NameChangeRecord {
    private final String previousName;
    private final String newName;
    private final long timestamp;
    
    public NameChangeRecord(String previousName, String newName, long timestamp) {
        this.previousName = previousName;
        this.newName = newName;
        this.timestamp = timestamp;
    }
    
    public String getPreviousName() {
        return previousName;
    }
    
    public String getNewName() {
        return newName;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return previousName + " → " + newName;
    }
}
