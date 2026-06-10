package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for location-based lore handlers.
 *
 * <p>Consolidates the shared {@code validateEntry}, {@code createLoreItem}, and
 * {@code displayLore} logic that was duplicated across
 * LandmarkLoreHandler, MonumentLoreHandler, TavernLoreHandler,
 * ShrineLoreHandler, GuildLoreHandler, and PathLoreHandler.
 *
 * <p>Subclasses provide only the constructor arguments that distinguish them:
 * the icon material, accent color, submitter label, location label, and LoreType.
 */
public abstract class AbstractLocationLoreHandler extends DefaultLoreHandler {

    private final Material iconMaterial;
    private final ChatColor accentColor;
    private final String submitterLabel;
    private final String locationLabel;
    private final LoreType handlerType;
    private final String typeName;

    /**
     * @param plugin          the plugin instance
     * @param iconMaterial    the item material used in {@link #createLoreItem}
     * @param accentColor     the chat color used for the header and type line
     * @param submitterLabel  label before the submitter name, e.g. {@code "Discovered by:"}
     * @param locationLabel   label before the location coordinates, e.g. {@code "Location:"}
     * @param typeName        human-readable type string used in the type line, e.g. {@code "Landmark"}
     * @param handlerType     the {@link LoreType} this handler manages
     */
    protected AbstractLocationLoreHandler(
            RVNKLore plugin,
            Material iconMaterial,
            ChatColor accentColor,
            String submitterLabel,
            String locationLabel,
            String typeName,
            LoreType handlerType) {
        super(plugin);
        this.iconMaterial = iconMaterial;
        this.accentColor = accentColor;
        this.submitterLabel = submitterLabel;
        this.locationLabel = locationLabel;
        this.typeName = typeName;
        this.handlerType = handlerType;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing " + handlerType.name().toLowerCase() + " lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        List<String> validationErrors = new ArrayList<>();

        if (entry.getName() == null || entry.getName().isEmpty()) {
            validationErrors.add("Name is required");
        }

        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            validationErrors.add("Description is required");
        } else if (entry.getDescription().length() < 10) {
            validationErrors.add("Description too short");
        }

        if (entry.getLocation() == null) {
            validationErrors.add("Location is required");
        }

        if (!validationErrors.isEmpty()) {
            entry.addMetadata("validation_errors", String.join(";", validationErrors));
            logger.warning(typeName + " validation failed: " + String.join(", ", validationErrors));
            return false;
        }

        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(iconMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(accentColor + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + accentColor + typeName);

            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + submitterLabel + " " + ChatColor.YELLOW + entry.getSubmittedBy());
            }

            lore.add("");

            appendDescriptionLines(lore, entry);
            appendLocationLine(lore, entry);

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(accentColor + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + accentColor + typeName);

        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + submitterLabel + " " + ChatColor.YELLOW + entry.getSubmittedBy());
        }

        player.sendMessage("");

        for (String descLine : getDescriptionLines(entry)) {
            player.sendMessage(descLine);
        }

        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(formatLocationLine(entry, locationLabel));
        }
    }

    @Override
    public LoreType getHandlerType() {
        return handlerType;
    }

    // -------------------------------------------------------------------------
    // Shared helpers (accessible to subclasses)
    // -------------------------------------------------------------------------

    /**
     * Returns each description line prefixed with {@link ChatColor#WHITE}.
     */
    protected static List<String> getDescriptionLines(LoreEntry entry) {
        List<String> lines = new ArrayList<>();
        for (String line : entry.getDescription().split("\\n")) {
            lines.add(ChatColor.WHITE + line);
        }
        return lines;
    }

    /**
     * Appends description lines (white-prefixed) into {@code target}.
     */
    protected static void appendDescriptionLines(List<String> target, LoreEntry entry) {
        target.addAll(getDescriptionLines(entry));
    }

    /**
     * Appends the location line (preceded by an empty line) into {@code target}
     * when the entry has a non-null location.
     */
    protected static void appendLocationLine(List<String> target, LoreEntry entry) {
        if (entry.getLocation() != null) {
            target.add("");
            target.add(formatLocationLine(entry, "Location:"));
        }
    }

    /**
     * Formats a single location line using the supplied label.
     *
     * @param entry  entry whose location is rendered
     * @param label  prefix label, e.g. {@code "Location:"} or {@code "Starting Point:"}
     * @return the formatted string
     */
    protected static String formatLocationLine(LoreEntry entry, String label) {
        return ChatColor.GRAY + label + " "
                + ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at "
                + (int) entry.getLocation().getX() + ", "
                + (int) entry.getLocation().getY() + ", "
                + (int) entry.getLocation().getZ();
    }
}
