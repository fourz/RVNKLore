package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.search.LoreSearchService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized tab completion logic reusable across all subcommands.
 * Uses LoreSearchService for name lookups against LoreManager's in-memory cache.
 */
public class TabCompletionUtil {
    private final LoreSearchService searchService;

    public TabCompletionUtil(LoreSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Suggest lore entry names matching the partial input.
     *
     * @param partial The partial name typed by the player
     * @return Up to 5 matching lore entry names
     */
    public List<String> completeLoreEntryNames(String partial) {
        return searchService.searchNames(partial, 5);
    }

    /**
     * Suggest lore entry names matching the partial input, filtered by type.
     *
     * @param partial The partial name typed by the player
     * @param type The lore type to filter by
     * @return Up to 5 matching lore entry names of the given type
     */
    public List<String> completeLoreEntryNames(String partial, LoreType type) {
        return searchService.searchNames(partial, type, 5);
    }

    /**
     * Suggest online player names matching the partial input.
     *
     * @param partial The partial name typed by the player
     * @return Up to 10 matching online player names
     */
    public List<String> completePlayerNames(String partial) {
        String lowerPartial = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lowerPartial))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Suggest enum values matching the partial input.
     *
     * @param enumClass The enum class to suggest values from
     * @param partial The partial value typed by the player
     * @return Matching enum names
     */
    public <E extends Enum<E>> List<String> completeEnum(Class<E> enumClass, String partial) {
        String lowerPartial = partial.toLowerCase();
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .filter(name -> name.toLowerCase().startsWith(lowerPartial))
                .collect(Collectors.toList());
    }
}
