package org.fourz.RVNKLore.api.model.request;

import org.fourz.RVNKLore.lore.LoreType;

import java.util.Map;

/**
 * Request DTO for submitting new lore entries via web form.
 */
public class LoreSubmissionRequest {
    private String name;
    private String description;
    private String type;
    private String submittedBy;
    private Map<String, String> metadata;

    // Default constructor for Gson
    public LoreSubmissionRequest() {}

    /**
     * Validates the request has required fields.
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
            && description != null && !description.trim().isEmpty()
            && type != null && !type.trim().isEmpty();
    }

    /**
     * Parses the type string to LoreType enum.
     * Returns GENERIC if invalid.
     */
    public LoreType getLoreType() {
        if (type == null) return LoreType.GENERIC;
        try {
            return LoreType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LoreType.GENERIC;
        }
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
