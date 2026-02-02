/**
 * PlaceholderAPI integration for RVNKLore statistics and discovery tracking.
 *
 * <p>This package provides PlaceholderAPI expansion support for RVNKLore,
 * enabling lore statistics to be displayed in chat, scoreboards, holograms,
 * and other plugins that support PlaceholderAPI.</p>
 *
 * <h2>Available Placeholders</h2>
 * <ul>
 *   <li>{@code %rvnklore_total_discovered%} - Total lore entries discovered by player</li>
 *   <li>{@code %rvnklore_total_entries%} - Total available lore entries</li>
 *   <li>{@code %rvnklore_discovery_percentage%} - Discovery completion percentage</li>
 *   <li>{@code %rvnklore_items_discovered%} - Items discovered by type</li>
 *   <li>{@code %rvnklore_locations_discovered%} - Locations discovered (landmarks + cities)</li>
 *   <li>{@code %rvnklore_characters_discovered%} - Characters discovered (players + factions)</li>
 *   <li>{@code %rvnklore_collection_<name>_progress%} - Specific collection progress</li>
 * </ul>
 *
 * <h2>Performance Features</h2>
 * <ul>
 *   <li>5-second cache TTL to reduce database load</li>
 *   <li>Async data retrieval using CompletableFuture</li>
 *   <li>Graceful fallback on timeout or error</li>
 *   <li>Reflection-based registration (no hard dependency)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // In scoreboard plugin config:
 * lines:
 *   - "&6Lore Progress"
 *   - "&7Discovered: %rvnklore_discovery_percentage%"
 *   - "&7Items: %rvnklore_items_discovered%"
 * }</pre>
 *
 * @see org.fourz.RVNKLore.integration.placeholder.RVNKLorePlaceholderExpansion
 * @see org.fourz.RVNKLore.service.IPlayerService
 * @see org.fourz.RVNKLore.service.ILoreService
 * @see org.fourz.RVNKLore.service.ICollectionService
 * @since feat-06 PlaceholderAPI Integration
 */
package org.fourz.RVNKLore.integration.placeholder;
