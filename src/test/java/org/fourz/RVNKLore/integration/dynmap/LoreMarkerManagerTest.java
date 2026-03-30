package org.fourz.RVNKLore.integration.dynmap;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LoreMarkerManager marker CRUD and filtering logic.
 */
@DisplayName("LoreMarkerManager")
@ExtendWith(MockitoExtension.class)
class LoreMarkerManagerTest {

    @Mock private RVNKLore plugin;
    @Mock private ConfigManager configManager;
    @Mock private MarkerAPI markerApi;
    @Mock private MarkerSet markerSet;
    @Mock private MarkerIcon icon;
    @Mock private Marker marker;
    @Mock private World world;
    @Mock private LoreManager loreManager;
    @Mock private java.util.logging.Logger javaLogger;

    private LoreMarkerManager manager;

    private Map<LoreType, MarkerSet> markerSets;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getConfigManager()).thenReturn(configManager);
        lenient().when(plugin.getLogger()).thenReturn(javaLogger);
        lenient().when(plugin.getLoreManager()).thenReturn(loreManager);
        lenient().when(world.getName()).thenReturn("world");

        // Create per-type marker sets — all location types share the same mock for simplicity
        markerSets = new EnumMap<>(LoreType.class);
        for (LoreType type : LoreType.values()) {
            if (type.isLocationCapable()) {
                markerSets.put(type, markerSet);
            }
        }

        manager = new LoreMarkerManager(plugin, markerApi, markerSets);
    }

    private LoreEntry createEntry(LoreType type, boolean approved, boolean hasLocation) {
        UUID id = UUID.randomUUID();
        Location loc = hasLocation ? new Location(world, 100, 64, 200) : null;
        return new LoreEntry(id, type, "Test Entry", "A test description", null,
            loc, "TestPlayer", approved, new Timestamp(System.currentTimeMillis()));
    }

    @Test
    @DisplayName("shouldHaveMarker returns true for approved landmark with location")
    void approvedLandmarkWithLocation() {
        when(configManager.isDynmapOnlyApproved()).thenReturn(true);
        LoreEntry entry = createEntry(LoreType.LANDMARK, true, true);
        assertTrue(manager.shouldHaveMarker(entry));
    }

    @Test
    @DisplayName("shouldHaveMarker returns false for unapproved entry when only-approved is on")
    void unapprovedEntryRejected() {
        when(configManager.isDynmapOnlyApproved()).thenReturn(true);
        LoreEntry entry = createEntry(LoreType.LANDMARK, false, true);
        assertFalse(manager.shouldHaveMarker(entry));
    }

    @Test
    @DisplayName("shouldHaveMarker returns false for non-location types")
    void nonLocationTypeRejected() {
        LoreEntry entry = createEntry(LoreType.ITEM, true, true);
        assertFalse(manager.shouldHaveMarker(entry));
    }

    @Test
    @DisplayName("shouldHaveMarker returns false for entry without location")
    void noLocationRejected() {
        LoreEntry entry = createEntry(LoreType.CITY, true, false);
        assertFalse(manager.shouldHaveMarker(entry));
    }

    @Test
    @DisplayName("shouldHaveMarker returns false for null entry")
    void nullEntryRejected() {
        assertFalse(manager.shouldHaveMarker(null));
    }

    @Test
    @DisplayName("All location types are supported")
    void allLocationTypesSupported() {
        when(configManager.isDynmapOnlyApproved()).thenReturn(false);

        for (LoreType type : LoreMarkerManager.getLocationTypes()) {
            LoreEntry entry = createEntry(type, false, true);
            assertTrue(manager.shouldHaveMarker(entry), type + " should be a location type");
        }
    }

    @Test
    @DisplayName("createOrUpdateMarker creates marker for valid entry")
    void createMarkerForValidEntry() {
        when(configManager.isDynmapOnlyApproved()).thenReturn(true);
        when(configManager.getDynmapIcon(LoreType.LANDMARK)).thenReturn("pin");
        when(configManager.isDynmapPopupEnabled()).thenReturn(true);
        when(configManager.getDynmapMaxDescriptionLength()).thenReturn(200);
        when(markerApi.getMarkerIcon("pin")).thenReturn(icon);
        when(markerSet.findMarker(anyString())).thenReturn(null);
        when(markerSet.createMarker(anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyDouble(), any(MarkerIcon.class), anyBoolean()))
            .thenReturn(marker);

        LoreEntry entry = createEntry(LoreType.LANDMARK, true, true);
        manager.createOrUpdateMarker(entry);

        verify(markerSet).createMarker(eq("rvnklore_" + entry.getId()), eq("Test Entry"),
            eq("world"), eq(100.0), eq(64.0), eq(200.0), eq(icon), eq(false));
        verify(marker).setDescription(anyString());
        assertEquals(1, manager.getMarkerCount());
    }

    @Test
    @DisplayName("deleteMarker removes cached marker")
    void deleteMarkerRemovesCached() {
        // Setup: first create a marker
        when(configManager.isDynmapOnlyApproved()).thenReturn(false);
        when(configManager.getDynmapIcon(LoreType.CITY)).thenReturn("bighouse");
        when(configManager.isDynmapPopupEnabled()).thenReturn(false);
        when(markerApi.getMarkerIcon("bighouse")).thenReturn(icon);
        when(markerSet.findMarker(anyString())).thenReturn(null);
        when(markerSet.createMarker(anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyDouble(), any(MarkerIcon.class), anyBoolean()))
            .thenReturn(marker);

        LoreEntry entry = createEntry(LoreType.CITY, true, true);
        manager.createOrUpdateMarker(entry);
        assertEquals(1, manager.getMarkerCount());

        // Delete it
        manager.deleteMarker(entry.getId());
        assertEquals(0, manager.getMarkerCount());
        verify(marker).deleteMarker();
    }

    @Test
    @DisplayName("generatePopupHtml contains entry name and type")
    void popupContainsNameAndType() {
        when(configManager.getDynmapMaxDescriptionLength()).thenReturn(200);
        LoreEntry entry = createEntry(LoreType.MONUMENT, true, true);
        String html = manager.generatePopupHtml(entry);

        assertTrue(html.contains("Test Entry"));
        assertTrue(html.contains("MONUMENT"));
        assertTrue(html.contains("TestPlayer"));
    }

    @Test
    @DisplayName("generatePopupHtml truncates long descriptions")
    void popupTruncatesLongDescription() {
        when(configManager.getDynmapMaxDescriptionLength()).thenReturn(10);
        LoreEntry entry = createEntry(LoreType.LANDMARK, true, true);
        // Override description to be longer than max
        entry.setDescription("This is a very long description that should be truncated");
        String html = manager.generatePopupHtml(entry);

        assertTrue(html.contains("This is a ..."));
    }

    @Test
    @DisplayName("cleanup removes all markers")
    void cleanupRemovesAll() {
        when(configManager.isDynmapOnlyApproved()).thenReturn(false);
        when(configManager.getDynmapIcon(any(LoreType.class))).thenReturn("sign");
        when(configManager.isDynmapPopupEnabled()).thenReturn(false);
        when(markerApi.getMarkerIcon("sign")).thenReturn(icon);
        when(markerSet.findMarker(anyString())).thenReturn(null);
        when(markerSet.createMarker(anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyDouble(), any(MarkerIcon.class), anyBoolean()))
            .thenReturn(marker);

        // Add two markers
        manager.createOrUpdateMarker(createEntry(LoreType.LANDMARK, true, true));
        manager.createOrUpdateMarker(createEntry(LoreType.CITY, true, true));
        assertEquals(2, manager.getMarkerCount());

        manager.cleanup();
        assertEquals(0, manager.getMarkerCount());
    }
}
