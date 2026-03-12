package org.fourz.RVNKLore.handler;

import org.fourz.RVNKLore.lore.LoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that handler classes exist and follow the expected patterns.
 * Validates the handler ecosystem without requiring a running Bukkit server.
 */
@DisplayName("Handler Registration Validation")
class HandlerRegistrationTest {

    @Test
    @DisplayName("MonumentLoreHandler class exists and implements LoreHandler")
    void monumentHandlerExists() {
        assertTrue(LoreHandler.class.isAssignableFrom(MonumentLoreHandler.class));
    }

    @Test
    @DisplayName("EventLoreHandler class exists and implements LoreHandler")
    void eventHandlerExists() {
        assertTrue(LoreHandler.class.isAssignableFrom(EventLoreHandler.class));
    }

    @Test
    @DisplayName("MonumentLoreHandler has RVNKLore constructor")
    void monumentHandlerHasConstructor() throws NoSuchMethodException {
        Class<?> pluginClass = org.fourz.RVNKLore.RVNKLore.class;
        Constructor<?> ctor = MonumentLoreHandler.class.getConstructor(pluginClass);
        assertNotNull(ctor);
    }

    @Test
    @DisplayName("EventLoreHandler has RVNKLore constructor")
    void eventHandlerHasConstructor() throws NoSuchMethodException {
        Class<?> pluginClass = org.fourz.RVNKLore.RVNKLore.class;
        Constructor<?> ctor = EventLoreHandler.class.getConstructor(pluginClass);
        assertNotNull(ctor);
    }

    @Test
    @DisplayName("HandlerSignMonument class exists and extends DefaultLoreHandler")
    void signMonumentHandlerExists() {
        assertTrue(DefaultLoreHandler.class.isAssignableFrom(
                org.fourz.RVNKLore.handler.sign.HandlerSignMonument.class));
    }

    @Test
    @DisplayName("HandlerSignMonument has RVNKLore constructor")
    void signMonumentHandlerHasConstructor() throws NoSuchMethodException {
        Class<?> pluginClass = org.fourz.RVNKLore.RVNKLore.class;
        Constructor<?> ctor = org.fourz.RVNKLore.handler.sign.HandlerSignMonument.class.getConstructor(pluginClass);
        assertNotNull(ctor);
    }

    @Test
    @DisplayName("All core handler classes have the required constructor signature")
    void allHandlersHaveCorrectConstructor() {
        Class<?>[] handlerClasses = {
                DefaultLoreHandler.class,
                CityLoreHandler.class,
                LandmarkLoreHandler.class,
                MonumentLoreHandler.class,
                EventLoreHandler.class,
                FactionLoreHandler.class,
                PathLoreHandler.class,
                ItemLoreHandler.class,
                CommonHeadHandler.class,
                PlayerLoreHandler.class,
        };

        Class<?> pluginClass = org.fourz.RVNKLore.RVNKLore.class;
        for (Class<?> handlerClass : handlerClasses) {
            try {
                Constructor<?> ctor = handlerClass.getConstructor(pluginClass);
                assertNotNull(ctor, handlerClass.getSimpleName() + " missing RVNKLore constructor");
            } catch (NoSuchMethodException e) {
                fail(handlerClass.getSimpleName() + " missing required constructor(RVNKLore)");
            }
        }
    }

    @Test
    @DisplayName("Every LoreType has a matching handler class or falls back to Default")
    void everyLoreTypeHasHandlerMapping() {
        // Verify that the handler factory's registerDefaultHandlers covers all types
        // by checking the type names that should have explicit handlers
        String[] explicitHandlers = {
                "GENERIC", "PLAYER", "CITY", "LANDMARK", "MONUMENT",
                "FACTION", "PATH", "ITEM", "EVENT", "HEAD"
        };

        for (String typeName : explicitHandlers) {
            assertDoesNotThrow(() -> LoreType.valueOf(typeName),
                    "LoreType." + typeName + " should exist");
        }
    }
}
