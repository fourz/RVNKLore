package org.fourz.RVNKLore.data.io;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Handles importing lore entries from various formats (JSON, YAML).
 * Supports validation, duplicate detection, and preview mode.
 */
public class LoreImporter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Gson gson;
    private final Yaml yaml;

    /**
     * Import result summary
     */
    public static class ImportResult {
        private final int total;
        private final int successful;
        private final int skipped;
        private final int failed;
        private final List<String> errors;
        private final List<String> warnings;

        public ImportResult(int total, int successful, int skipped, int failed,
                          List<String> errors, List<String> warnings) {
            this.total = total;
            this.successful = successful;
            this.skipped = skipped;
            this.failed = failed;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }

        public int getTotal() { return total; }
        public int getSuccessful() { return successful; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }

        public boolean isSuccess() {
            return successful > 0 && failed == 0;
        }

        public String getSummary() {
            return String.format("Total: %d | Success: %d | Skipped: %d | Failed: %d",
                total, successful, skipped, failed);
        }
    }

    public LoreImporter(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreImporter");
        this.gson = new Gson();
        this.yaml = new Yaml();
    }

    /**
     * Import lore entries from a file with preview mode support.
     *
     * @param file The file to import from
     * @param preview If true, only validates without importing
     * @return Import result summary
     */
    public ImportResult importFromFile(File file, boolean preview) {
        if (!file.exists()) {
            return new ImportResult(0, 0, 0, 0,
                Collections.singletonList("File not found: " + file.getName()),
                Collections.emptyList());
        }

        String filename = file.getName().toLowerCase();
        if (filename.endsWith(".json")) {
            return importFromJson(file, preview);
        } else if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
            return importFromYaml(file, preview);
        } else {
            return new ImportResult(0, 0, 0, 0,
                Collections.singletonList("Unsupported file format. Use .json or .yaml"),
                Collections.emptyList());
        }
    }

    /**
     * Import from JSON file.
     *
     * @param file The JSON file
     * @param preview Preview mode flag
     * @return Import result
     */
    private ImportResult importFromJson(File file, boolean preview) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int successful = 0;
        int skipped = 0;
        int failed = 0;

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (!root.has("entries")) {
                errors.add("Invalid JSON format: missing 'entries' array");
                return new ImportResult(0, 0, 0, 0, errors, warnings);
            }

            JsonArray entries = root.getAsJsonArray("entries");
            int total = entries.size();

            logger.info((preview ? "Previewing" : "Importing") + " " + total + " entries from " + file.getName());

            for (JsonElement element : entries) {
                JsonObject entryJson = element.getAsJsonObject();

                try {
                    LoreEntry entry = parseJsonEntry(entryJson, warnings);

                    if (entry == null) {
                        failed++;
                        errors.add("Failed to parse entry: invalid data");
                        continue;
                    }

                    // Validate entry
                    if (!validateEntry(entry, warnings)) {
                        failed++;
                        errors.add("Invalid entry: " + entry.getName());
                        continue;
                    }

                    // Check for duplicates
                    if (plugin.getLoreManager().getLoreEntryByIdSync(entry.getId()).isPresent()) {
                        skipped++;
                        warnings.add("Skipped duplicate entry ID: " + entry.getId());
                        continue;
                    }

                    // Import if not in preview mode
                    if (!preview) {
                        if (plugin.getLoreManager().addLoreEntrySync(entry)) {
                            successful++;
                        } else {
                            failed++;
                            errors.add("Failed to import: " + entry.getName());
                        }
                    } else {
                        successful++;
                    }

                } catch (Exception e) {
                    failed++;
                    errors.add("Error processing entry: " + e.getMessage());
                    logger.error("Error parsing entry", e);
                }
            }

            return new ImportResult(total, successful, skipped, failed, errors, warnings);

        } catch (IOException e) {
            logger.error("Failed to read import file", e);
            errors.add("Failed to read file: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, errors, warnings);
        } catch (JsonParseException e) {
            logger.error("Failed to parse JSON", e);
            errors.add("Invalid JSON format: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, errors, warnings);
        }
    }

    /**
     * Import from YAML file.
     *
     * @param file The YAML file
     * @param preview Preview mode flag
     * @return Import result
     */
    @SuppressWarnings("unchecked")
    private ImportResult importFromYaml(File file, boolean preview) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int successful = 0;
        int skipped = 0;
        int failed = 0;

        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> root = yaml.load(reader);

            if (root == null || !root.containsKey("entries")) {
                errors.add("Invalid YAML format: missing 'entries' list");
                return new ImportResult(0, 0, 0, 0, errors, warnings);
            }

            List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("entries");
            int total = entries.size();

            logger.info((preview ? "Previewing" : "Importing") + " " + total + " entries from " + file.getName());

            for (Map<String, Object> entryMap : entries) {
                try {
                    LoreEntry entry = parseYamlEntry(entryMap, warnings);

                    if (entry == null) {
                        failed++;
                        errors.add("Failed to parse entry: invalid data");
                        continue;
                    }

                    // Validate entry
                    if (!validateEntry(entry, warnings)) {
                        failed++;
                        errors.add("Invalid entry: " + entry.getName());
                        continue;
                    }

                    // Check for duplicates
                    if (plugin.getLoreManager().getLoreEntryByIdSync(entry.getId()).isPresent()) {
                        skipped++;
                        warnings.add("Skipped duplicate entry ID: " + entry.getId());
                        continue;
                    }

                    // Import if not in preview mode
                    if (!preview) {
                        if (plugin.getLoreManager().addLoreEntrySync(entry)) {
                            successful++;
                        } else {
                            failed++;
                            errors.add("Failed to import: " + entry.getName());
                        }
                    } else {
                        successful++;
                    }

                } catch (Exception e) {
                    failed++;
                    errors.add("Error processing entry: " + e.getMessage());
                    logger.error("Error parsing entry", e);
                }
            }

            return new ImportResult(total, successful, skipped, failed, errors, warnings);

        } catch (IOException e) {
            logger.error("Failed to read import file", e);
            errors.add("Failed to read file: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, errors, warnings);
        } catch (Exception e) {
            logger.error("Failed to parse YAML", e);
            errors.add("Invalid YAML format: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, errors, warnings);
        }
    }

    /**
     * Parse a LoreEntry from JSON.
     *
     * @param json The JSON object
     * @param warnings List to add warnings to
     * @return Parsed LoreEntry or null if invalid
     */
    private LoreEntry parseJsonEntry(JsonObject json, List<String> warnings) {
        try {
            String id = getJsonString(json, "id");
            String name = getJsonString(json, "name");
            String description = getJsonString(json, "description");
            String typeStr = getJsonString(json, "type");

            if (id == null || name == null || description == null || typeStr == null) {
                warnings.add("Entry missing required fields (id, name, description, or type)");
                return null;
            }

            LoreType type;
            try {
                type = LoreType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                warnings.add("Invalid lore type: " + typeStr + ", defaulting to GENERIC");
                type = LoreType.GENERIC;
            }

            LoreEntry entry = new LoreEntry(id, name, description, type);

            // Optional fields
            if (json.has("nbt_data")) {
                entry.setNbtData(json.get("nbt_data").getAsString());
            }

            if (json.has("location")) {
                Location location = parseJsonLocation(json.getAsJsonObject("location"), warnings);
                if (location != null) {
                    entry.setLocation(location);
                }
            }

            if (json.has("submitted_by")) {
                entry.setSubmittedBy(json.get("submitted_by").getAsString());
            }

            if (json.has("approved")) {
                entry.setApproved(json.get("approved").getAsBoolean());
            }

            if (json.has("created_at")) {
                try {
                    entry.setCreatedAt(Timestamp.valueOf(json.get("created_at").getAsString()));
                } catch (Exception e) {
                    warnings.add("Invalid timestamp format, using current time");
                }
            }

            // Parse metadata
            if (json.has("metadata") && json.get("metadata").isJsonObject()) {
                JsonObject metadataJson = json.getAsJsonObject("metadata");
                for (Map.Entry<String, JsonElement> metaEntry : metadataJson.entrySet()) {
                    entry.addMetadata(metaEntry.getKey(), metaEntry.getValue().getAsString());
                }
            }

            return entry;

        } catch (Exception e) {
            logger.error("Error parsing JSON entry", e);
            return null;
        }
    }

    /**
     * Parse a LoreEntry from YAML map.
     *
     * @param map The YAML map
     * @param warnings List to add warnings to
     * @return Parsed LoreEntry or null if invalid
     */
    @SuppressWarnings("unchecked")
    private LoreEntry parseYamlEntry(Map<String, Object> map, List<String> warnings) {
        try {
            String id = (String) map.get("id");
            String name = (String) map.get("name");
            String description = (String) map.get("description");
            String typeStr = (String) map.get("type");

            if (id == null || name == null || description == null || typeStr == null) {
                warnings.add("Entry missing required fields (id, name, description, or type)");
                return null;
            }

            LoreType type;
            try {
                type = LoreType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                warnings.add("Invalid lore type: " + typeStr + ", defaulting to GENERIC");
                type = LoreType.GENERIC;
            }

            LoreEntry entry = new LoreEntry(id, name, description, type);

            // Optional fields
            if (map.containsKey("nbt_data")) {
                entry.setNbtData((String) map.get("nbt_data"));
            }

            if (map.containsKey("location")) {
                Location location = parseYamlLocation((Map<String, Object>) map.get("location"), warnings);
                if (location != null) {
                    entry.setLocation(location);
                }
            }

            if (map.containsKey("submitted_by")) {
                entry.setSubmittedBy((String) map.get("submitted_by"));
            }

            if (map.containsKey("approved")) {
                entry.setApproved((Boolean) map.get("approved"));
            }

            if (map.containsKey("created_at")) {
                try {
                    entry.setCreatedAt(Timestamp.valueOf((String) map.get("created_at")));
                } catch (Exception e) {
                    warnings.add("Invalid timestamp format, using current time");
                }
            }

            // Parse metadata
            if (map.containsKey("metadata") && map.get("metadata") instanceof Map) {
                Map<String, String> metadata = (Map<String, String>) map.get("metadata");
                for (Map.Entry<String, String> metaEntry : metadata.entrySet()) {
                    entry.addMetadata(metaEntry.getKey(), metaEntry.getValue());
                }
            }

            return entry;

        } catch (Exception e) {
            logger.error("Error parsing YAML entry", e);
            return null;
        }
    }

    /**
     * Parse location from JSON.
     */
    private Location parseJsonLocation(JsonObject json, List<String> warnings) {
        try {
            String worldName = json.get("world").getAsString();
            double x = json.get("x").getAsDouble();
            double y = json.get("y").getAsDouble();
            double z = json.get("z").getAsDouble();

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                warnings.add("World not found: " + worldName + ", location skipped");
                return null;
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            warnings.add("Invalid location format");
            return null;
        }
    }

    /**
     * Parse location from YAML map.
     */
    @SuppressWarnings("unchecked")
    private Location parseYamlLocation(Map<String, Object> map, List<String> warnings) {
        try {
            String worldName = (String) map.get("world");
            double x = ((Number) map.get("x")).doubleValue();
            double y = ((Number) map.get("y")).doubleValue();
            double z = ((Number) map.get("z")).doubleValue();

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                warnings.add("World not found: " + worldName + ", location skipped");
                return null;
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            warnings.add("Invalid location format");
            return null;
        }
    }

    /**
     * Validate a lore entry.
     *
     * @param entry The entry to validate
     * @param warnings List to add warnings to
     * @return true if valid, false otherwise
     */
    private boolean validateEntry(LoreEntry entry, List<String> warnings) {
        if (entry.getName() == null || entry.getName().trim().isEmpty()) {
            warnings.add("Entry has empty name");
            return false;
        }

        if (entry.getDescription() == null || entry.getDescription().trim().isEmpty()) {
            warnings.add("Entry has empty description: " + entry.getName());
            return false;
        }

        if (entry.getType() == null) {
            warnings.add("Entry has no type: " + entry.getName());
            return false;
        }

        // Type-specific validation
        if (!entry.isValid()) {
            warnings.add("Entry validation failed: " + entry.getName());
            return false;
        }

        return true;
    }

    /**
     * Get string from JSON object, handling nulls.
     */
    private String getJsonString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return json.get(key).getAsString();
    }
}
