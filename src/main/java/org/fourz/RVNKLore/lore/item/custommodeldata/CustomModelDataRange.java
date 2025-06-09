package org.fourz.RVNKLore.lore.item.custommodeldata;

/**
 * Represents a range of model data IDs for a specific category.
 * Used to manage allocation boundaries and prevent conflicts.
 */
public class CustomModelDataRange {
    private final int start;
    private final int end;
    
    public CustomModelDataRange(int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("Start must be less than or equal to end");
        }
        this.start = start;
        this.end = end;
    }
    
    /**
     * Get the start of the range (inclusive).
     * 
     * @return The start value
     */
    public int getStart() {
        return start;
    }
    
    /**
     * Get the end of the range (inclusive).
     * 
     * @return The end value
     */
    public int getEnd() {
        return end;
    }
    
    /**
     * Get the total size of the range.
     * 
     * @return The range size
     */
    public int getSize() {
        return end - start + 1;
    }
    
    /**
     * Check if a value is within this range.
     * 
     * @param value The value to check
     * @return True if the value is within range
     */
    public boolean contains(int value) {
        return value >= start && value <= end;
    }
    
    @Override
    public String toString() {
        return "ModelDataRange{" + start + "-" + end + "}";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CustomModelDataRange that = (CustomModelDataRange) obj;
        return start == that.start && end == that.end;
    }
    
    @Override
    public int hashCode() {
        return 31 * start + end;
    }
}
