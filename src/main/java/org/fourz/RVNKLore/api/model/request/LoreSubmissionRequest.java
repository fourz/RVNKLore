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

    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /**
     * Validates the request has required fields and enforces length limits.
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
            && description != null && !description.trim().isEmpty()
            && type != null && !type.trim().isEmpty()
            && name.length() <= MAX_NAME_LENGTH
            && description.length() <= MAX_DESCRIPTION_LENGTH;
    }

    /**
     * Sanitizes input fields by stripping control characters and Minecraft color codes.
     * Call after deserialization and before processing.
     */
    public void sanitize() {
        if (name != null) {
            name = stripControlChars(name.trim());
        }
        if (description != null) {
            description = stripControlChars(description.trim());
        }
        if (submittedBy != null) {
            submittedBy = stripControlChars(submittedBy.trim());
        }
    }

    private static String stripControlChars(String input) {
        // Remove Minecraft color codes (§X) and ASCII control characters
        return input.replaceAll("[§&][0-9a-fk-or]", "")
                     .replaceAll("[\\x00-\\x1F\\x7F]", "");
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
