package org.fourz.RVNKLore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for manipulating head/skull textures.
 * Uses Paper/Bukkit PlayerProfile API (no reflection).
 */
public class HeadUtil {
    // Cache of mob type to texture URL for common mobs
    private static final Map<EntityType, String> MOB_TEXTURE_URLS = new HashMap<>();
    // Cache of mob type to raw base64 texture data (for legacy callers)
    private static final Map<EntityType, String> MOB_TEXTURES = new HashMap<>();

    static {
        // Each base64 decodes to JSON: {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/..."}}}
        registerMobTexture(EntityType.ZOMBIE, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNzY5NzI5Nzk3M2UwMmI3OWJjMTA2OTg0NjBiNTFhNjM5YzYwZTVlNDE3NzM0ZTExIn19fQ==");
        registerMobTexture(EntityType.CREEPER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQyNTQ4MzhjMzNlYTIyN2ZmY2EyMjNkZGRhYWJmZTBiMDIxNWY3MGRhNjQ5ZTk0NDQ3N2Y0NDM3MGNhNjk1MiJ9fX0=");
        registerMobTexture(EntityType.SKELETON, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzAxMjY4ZTljNDkyZGExZjBkODgyNzFjYjQ5MmE0YjMwMjM5NWY1MTVhN2JiZjc3ZjRhMjBiOTVmYzAyZWIyIn19fQ==");
        registerMobTexture(EntityType.SPIDER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q1NDE1NDFkYWFmODRjOGI4ZTJjNDVhNzUzMWU5M2E3ZTU0OTVjZWNkMDFmMmViZjE5NGJiZTY3ZDM2ZjA4MyJ9fX0=");
        registerMobTexture(EntityType.ENDERMAN, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E1OWJiMGE3YTMyOTY1YjNkOTBkOGVhZmE4OTlkMTgzNWY0MjQ1MDllYWRkNGU2YjcwOWFkYTUwYjljZiJ9fX0=");
    }

    private static void registerMobTexture(EntityType type, String base64Data) {
        MOB_TEXTURES.put(type, base64Data);
        String url = extractTextureUrl(base64Data);
        if (url != null) {
            MOB_TEXTURE_URLS.put(type, url);
        }
    }

    /**
     * Apply a texture to a skull meta using base64 texture data.
     * Uses Bukkit PlayerProfile API instead of reflection.
     */
    public static void applyTextureData(SkullMeta meta, String textureData) {
        String textureUrl = extractTextureUrl(textureData);
        if (textureUrl == null) {
            Bukkit.getLogger().warning("Failed to extract texture URL from base64 data");
            return;
        }
        applyTextureFromUrl(meta, textureUrl);
    }

    /**
     * Apply a mob texture to a skull meta.
     */
    public static void applyMobTexture(SkullMeta meta, EntityType entityType) {
        String textureUrl = MOB_TEXTURE_URLS.get(entityType);
        if (textureUrl != null) {
            applyTextureFromUrl(meta, textureUrl);
        }
    }

    /**
     * Check if texture data is valid.
     */
    public static boolean isValidTextureData(String textureData) {
        return textureData != null && textureData.length() > 20 && !textureData.contains(" ");
    }

    /**
     * Get texture data for a mob type.
     */
    public static String getMobTextureData(EntityType entityType) {
        return MOB_TEXTURES.get(entityType);
    }

    /**
     * Apply texture from a URL using Bukkit PlayerProfile API.
     *
     * @param meta The skull meta to apply texture to
     * @param textureUrl The URL of the texture
     */
    public static void applyTextureFromUrl(SkullMeta meta, String textureUrl) {
        if (textureUrl == null || textureUrl.trim().isEmpty()) {
            return;
        }

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(textureUrl).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException e) {
            Bukkit.getLogger().warning("Invalid texture URL: " + textureUrl);
        }
    }

    /**
     * Get all available mob types that have texture data.
     */
    public static Set<EntityType> getAvailableMobTypes() {
        return MOB_TEXTURES.keySet();
    }

    /**
     * Add a new mob texture to the registry.
     */
    public static void addMobTexture(EntityType entityType, String textureData) {
        if (isValidTextureData(textureData)) {
            registerMobTexture(entityType, textureData);
        }
    }

    /**
     * Validate if an EntityType is supported for head creation.
     */
    public static boolean isSupportedMobType(EntityType entityType) {
        return MOB_TEXTURES.containsKey(entityType) ||
               entityType.isSpawnable() && entityType.isAlive();
    }

    /**
     * Extract the texture URL from base64-encoded texture JSON.
     */
    private static String extractTextureUrl(String base64Data) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Data));
            // Simple extraction: find "url":"..." pattern
            int urlIdx = json.indexOf("\"url\":\"");
            if (urlIdx == -1) return null;
            int start = urlIdx + 7;
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
