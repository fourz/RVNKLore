package org.fourz.RVNKLore.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for manipulating head/skull textures
 */
public class HeadUtil {
    // Cache of mob type to texture data for common mobs
    private static final Map<EntityType, String> MOB_TEXTURES = new HashMap<>();
    
    static {
        // Initialize with common mob head textures
        MOB_TEXTURES.put(EntityType.ZOMBIE, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNzY5NzI5Nzk3M2UwMmI3OWJjMTA2OTg0NjBiNTFhNjM5YzYwZTVlNDE3NzM0ZTExIn19fQ==");
        MOB_TEXTURES.put(EntityType.CREEPER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQyNTQ4MzhjMzNlYTIyN2ZmY2EyMjNkZGRhYWJmZTBiMDIxNWY3MGRhNjQ5ZTk0NDQ3N2Y0NDM3MGNhNjk1MiJ9fX0=");
        MOB_TEXTURES.put(EntityType.SKELETON, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzAxMjY4ZTljNDkyZGExZjBkODgyNzFjYjQ5MmE0YjMwMjM5NWY1MTVhN2JiZjc3ZjRhMjBiOTVmYzAyZWIyIn19fQ==");
        MOB_TEXTURES.put(EntityType.SPIDER, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q1NDE1NDFkYWFmODRjOGI4ZTJjNDVhNzUzMWU5M2E3ZTU0OTVjZWNkMDFmMmViZjE5NGJiZTY3ZDM2ZjA4MyJ9fX0=");
        MOB_TEXTURES.put(EntityType.ENDERMAN, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E1OWJiMGE3YTMyOTY1YjNkOTBkOGVhZmE4OTlkMTgzNWY0MjQ1MDllYWRkNGU2YjcwOWFkYTUwYjljZiJ9fX0=");
        // Add more mob textures as needed
    }
    
    /**
     * Apply a texture to a skull meta using base64 texture data
     */
    public static void applyTextureData(SkullMeta meta, String textureData) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", textureData));
        
        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Bukkit.getLogger().warning("Failed to apply texture to head: " + e.getMessage());
        }
    }
    
    /**
     * Apply a mob texture to a skull meta
     */
    public static void applyMobTexture(SkullMeta meta, EntityType entityType) {
        String textureData = MOB_TEXTURES.get(entityType);
        if (textureData != null) {
            applyTextureData(meta, textureData);
        }
    }
    
    /**
     * Check if texture data is valid
     */
    public static boolean isValidTextureData(String textureData) {
        // Simple validation - base64 should be longer than 20 chars and not contain spaces
        return textureData != null && textureData.length() > 20 && !textureData.contains(" ");
    }
    
    /**
     * Get texture data for a mob type
     */
    public static String getMobTextureData(EntityType entityType) {
        return MOB_TEXTURES.get(entityType);
    }
    
    /**
     * Apply texture from a URL (for modern head creation).
     * This creates the texture data format expected by Minecraft.
     *
     * @param meta The skull meta to apply texture to
     * @param textureUrl The URL of the texture
     */
    public static void applyTextureFromUrl(SkullMeta meta, String textureUrl) {
        if (textureUrl == null || textureUrl.trim().isEmpty()) {
            return;
        }
        
        // Create the texture JSON format
        String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        String encodedTexture = Base64.getEncoder().encodeToString(textureJson.getBytes());
        
        applyTextureData(meta, encodedTexture);
    }
    
    /**
     * Get all available mob types that have texture data.
     *
     * @return Set of EntityTypes with available textures
     */
    public static Set<EntityType> getAvailableMobTypes() {
        return MOB_TEXTURES.keySet();
    }
    
    /**
     * Add a new mob texture to the registry.
     *
     * @param entityType The mob type
     * @param textureData The base64 encoded texture data
     */
    public static void addMobTexture(EntityType entityType, String textureData) {
        if (isValidTextureData(textureData)) {
            MOB_TEXTURES.put(entityType, textureData);
        }
    }
    
    /**
     * Create a GameProfile with texture data for advanced head manipulation.
     *
     * @param textureData Base64 encoded texture data
     * @return GameProfile with texture applied
     */
    public static GameProfile createTexturedProfile(String textureData) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", textureData));
        return profile;
    }
    
    /**
     * Validate if an EntityType is supported for head creation.
     *
     * @param entityType The entity type to check
     * @return True if the entity type can be used for heads
     */
    public static boolean isSupportedMobType(EntityType entityType) {
        // Check if it's in our registry or if it's a generally supported mob
        return MOB_TEXTURES.containsKey(entityType) || 
               entityType.isSpawnable() && entityType.isAlive();
    }
}
