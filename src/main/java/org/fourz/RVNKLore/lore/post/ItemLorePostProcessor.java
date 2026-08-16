package org.fourz.RVNKLore.lore.post;

import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LorePostProcessor;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;

/**
 * Registers a newly-persisted ITEM entry in {@link ItemManager}.
 * Returns false (triggering rollback) if the database insert into lore_item fails.
 *
 * <p>Calls {@code registerLoreItem().join()} — this is intentionally synchronous
 * because the caller ({@code addLoreEntrySync}) already runs off the main thread
 * via {@code CompletableFuture.supplyAsync}. Issue #967 tracks moving the whole
 * chain to a non-blocking async pipeline.
 */
public class ItemLorePostProcessor implements LorePostProcessor {

    private final ItemManager itemManager;
    private final LogManager logger;

    public ItemLorePostProcessor(RVNKLore plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        this.logger = LogManager.getInstance(plugin, "ItemLorePostProcessor");
    }

    @Override
    public boolean appliesTo(LoreEntry entry) {
        return entry.getType() == LoreType.ITEM && itemManager != null;
    }

    @Override
    public boolean process(LoreEntry entry) {
        Material material = resolveMaterial(entry);
        ItemProperties itemProps = new ItemProperties(material, entry.getName());
        itemProps.setLoreEntryId(entry.getId());
        if (entry.getNbtData() != null) {
            itemProps.setNbtData(entry.getNbtData());
        }

        try {
            UUID entryUUID = UUID.fromString(entry.getId());
            boolean itemSuccess = itemManager.registerLoreItem(entryUUID, itemProps).join();
            if (!itemSuccess) {
                logger.warning("Item registration failed for: " + entry.getName() + " - rolling back lore entry");
                entry.addMetadata("validation_errors", "Item registration failed in database");
                return false;
            }
            logger.debug("Registered item in ItemManager: " + entry.getName() + " (lore entry " + entry.getId() + ")");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to register item in ItemManager: " + e.getMessage());
            entry.addMetadata("validation_errors", "Item registration failed: " + e.getMessage());
            return false;
        }
    }

    private Material resolveMaterial(LoreEntry entry) {
        String materialName = entry.getMetadata("material");
        if (materialName != null) {
            try {
                return Material.valueOf(materialName);
            } catch (IllegalArgumentException ignored) {}
        }
        return Material.DIAMOND_SWORD;
    }
}
