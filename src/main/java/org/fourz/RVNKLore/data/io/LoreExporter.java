package org.fourz.RVNKLore.data.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles exporting lore entries to various formats (JSON, YAML).
 * Supports full database exports, filtered exports by type, and single entry exports.
 */
public class LoreExporter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Gson gson;
    private final Yaml yaml;

    /**
     * Supported export formats
     */
    public enum ExportFormat {
        JSON("json"),
        YAML("yaml");

        private final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        public static ExportFormat fromString(String format) {
            for (ExportFormat f : values()) {
                if (f.name().equalsIgnoreCase(format)) {
                    return f;
                }
            }
            return JSON; // Default to JSON
        }
    }

    public LoreExporter(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreExporter");

        // Initialize Gson with pretty printing
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

        // Initialize YAML with pretty printing
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    /**
     * Export all lore entries to the specified format.
     *
     * @param format The export format (JSON or YAML)
     * @return The file that was created, or null if export failed
     */
    public File exportAll(ExportFormat format) {
        List<LoreEntry> entries = plugin.getLoreManager().getAllLoreEntriesSync();
        String filename = generateFilename("all", format);
        return exportEntries(entries, filename, format);
    }

    /**
     * Export lore entries of a specific type.
     *
     * @param type The lore type to export
     * @param format The export format (JSON or YAML)
     * @return The file that was created, or null if export failed
     */
    public File exportByType(LoreType type, ExportFormat format) {
        List<LoreEntry> entries = plugin.getLoreManager().getLoreEntriesByTypeSync(type);
        String filename = generateFilename(type.name().toLowerCase(), format);
        return exportEntries(entries, filename, format);
    }

    /**
     * Export a single lore entry by ID.
     *
     * @param entryId The ID of the entry to export
     * @param format The export format (JSON or YAML)
     * @return The file that was created, or null if export failed
     */
    public File exportById(String entryId, ExportFormat format) {
        Optional<LoreEntry> entry = plugin.getLoreManager().getLoreEntryByIdSync(entryId);
        if (entry.isEmpty()) {
            logger.warning("Cannot export: Entry not found with ID: " + entryId);
            return null;
        }

        String filename = generateFilename(sanitizeForFilename(entry.get().getName()), format);
        return exportEntries(Collections.singletonList(entry.get()), filename, format);
    }

    /**
     * Export a list of lore entries to a file.
     *
     * @param entries The entries to export
     * @param filename The filename to use
     * @param format The export format
     * @return The file that was created, or null if export failed
     */
    private File exportEntries(List<LoreEntry> entries, String filename, ExportFormat format) {
        if (entries == null || entries.isEmpty()) {
            logger.warning("No entries to export");
            return null;
        }

        File exportDir = new File(plugin.getDataFolder(), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File exportFile = new File(exportDir, filename);

        try {
            String content;
            switch (format) {
                case JSON:
                    content = exportToJson(entries);
                    break;
                case YAML:
                    content = exportToYaml(entries);
                    break;
                default:
                    logger.error("Unsupported export format: " + format);
                    return null;
            }

            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(content);
            }

            logger.info("Exported " + entries.size() + " entries to " + exportFile.getAbsolutePath());
            return exportFile;

        } catch (IOException e) {
            logger.error("Failed to export entries to " + filename, e);
            return null;
        }
    }

    /**
     * Convert lore entries to JSON format.
     *
     * @param entries The entries to convert
     * @return JSON string representation
     */
    private String exportToJson(List<LoreEntry> entries) {
        JsonObject root = new JsonObject();
        root.addProperty("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        root.addProperty("plugin_version", plugin.getDescription().getVersion());
        root.addProperty("entry_count", entries.size());

        JsonArray entriesArray = new JsonArray();
        for (LoreEntry entry : entries) {
            JsonObject entryJson = new JsonObject();
            entryJson.addProperty("id", entry.getId());
            entryJson.addProperty("name", entry.getName());
            entryJson.addProperty("description", entry.getDescription());
            entryJson.addProperty("type", entry.getType().name());

            if (entry.getNbtData() != null && !entry.getNbtData().isEmpty()) {
                entryJson.addProperty("nbt_data", entry.getNbtData());
            }

            if (entry.getLocation() != null) {
                JsonObject locationJson = new JsonObject();
                locationJson.addProperty("world", entry.getLocation().getWorld().getName());
                locationJson.addProperty("x", entry.getLocation().getX());
                locationJson.addProperty("y", entry.getLocation().getY());
                locationJson.addProperty("z", entry.getLocation().getZ());
                entryJson.add("location", locationJson);
            }

            entryJson.addProperty("submitted_by", entry.getSubmittedBy());
            entryJson.addProperty("approved", entry.isApproved());
            entryJson.addProperty("created_at", entry.getCreatedAt().toString());

            // Include metadata if present
            if (entry.hasMetadata()) {
                JsonObject metadataJson = new JsonObject();
                for (Map.Entry<String, String> meta : entry.getAllMetadata().entrySet()) {
                    metadataJson.addProperty(meta.getKey(), meta.getValue());
                }
                entryJson.add("metadata", metadataJson);
            }

            entriesArray.add(entryJson);
        }

        root.add("entries", entriesArray);
        return gson.toJson(root);
    }

    /**
     * Convert lore entries to YAML format.
     *
     * @param entries The entries to convert
     * @return YAML string representation
     */
    private String exportToYaml(List<LoreEntry> entries) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        root.put("plugin_version", plugin.getDescription().getVersion());
        root.put("entry_count", entries.size());

        List<Map<String, Object>> entriesList = new ArrayList<>();
        for (LoreEntry entry : entries) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("id", entry.getId());
            entryMap.put("name", entry.getName());
            entryMap.put("description", entry.getDescription());
            entryMap.put("type", entry.getType().name());

            if (entry.getNbtData() != null && !entry.getNbtData().isEmpty()) {
                entryMap.put("nbt_data", entry.getNbtData());
            }

            if (entry.getLocation() != null) {
                Map<String, Object> locationMap = new LinkedHashMap<>();
                locationMap.put("world", entry.getLocation().getWorld().getName());
                locationMap.put("x", entry.getLocation().getX());
                locationMap.put("y", entry.getLocation().getY());
                locationMap.put("z", entry.getLocation().getZ());
                entryMap.put("location", locationMap);
            }

            entryMap.put("submitted_by", entry.getSubmittedBy());
            entryMap.put("approved", entry.isApproved());
            entryMap.put("created_at", entry.getCreatedAt().toString());

            // Include metadata if present
            if (entry.hasMetadata()) {
                entryMap.put("metadata", new LinkedHashMap<>(entry.getAllMetadata()));
            }

            entriesList.add(entryMap);
        }

        root.put("entries", entriesList);
        return yaml.dump(root);
    }

    /**
     * Generate a filename for an export.
     *
     * @param identifier The identifier for this export (e.g., "all", type name, entry name)
     * @param format The export format
     * @return A filename with timestamp
     */
    private String generateFilename(String identifier, ExportFormat format) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        return String.format("lore_export_%s_%s.%s",
            identifier,
            sdf.format(new Date()),
            format.getExtension()
        );
    }

    /**
     * Sanitize a string for use in a filename.
     *
     * @param input The input string
     * @return A sanitized string safe for filenames
     */
    private String sanitizeForFilename(String input) {
        if (input == null) {
            return "unnamed";
        }
        return input.replaceAll("[^a-zA-Z0-9-_]", "_").toLowerCase();
    }
}
