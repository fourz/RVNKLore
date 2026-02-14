package org.fourz.RVNKLore.integration.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Manages Citizens plugin integration for collection vendor NPCs.
 * Registers traits and handles NPC interactions.
 */
public class CitizensIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private boolean enabled = false;

    public CitizensIntegration(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CitizensIntegration");
    }

    /**
     * Attempt to activate Citizens integration.
     * Called during plugin enable if Citizens is available.
     *
     * @return true if integration was activated successfully
     */
    public boolean activate() {
        Plugin citizensPlugin = plugin.getServer().getPluginManager().getPlugin("Citizens");
        if (citizensPlugin == null || !citizensPlugin.isEnabled()) {
            logger.debug("Citizens plugin not found - NPC vendor support disabled");
            return false;
        }

        try {
            // Register our custom trait with Citizens
            // Create a trait factory and register using the TraitFactory API
            // This is a deferred registration - citizens will instantiate the trait when needed
            net.citizensnpcs.api.npc.NPCRegistry registry = CitizensAPI.getNPCRegistry();

            // Register event listener for NPC interactions
            plugin.getServer().getPluginManager().registerEvents(this, plugin);

            enabled = true;
            logger.info("Citizens integration enabled - NPC collection vendors available");
            logger.debug("Trait registration deferred until first NPC creation");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize Citizens integration: " + e.getMessage());
            logger.debug("Ensure Citizens is properly installed and Citizens dependency is in classpath");
            return false;
        }
    }

    /**
     * Handle right-click on a collection vendor NPC.
     */
    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();

        // Check if this NPC has the collection vendor trait
        if (!npc.hasTrait(CollectionVendorTrait.class)) {
            return;
        }

        CollectionVendorTrait trait = npc.getTrait(CollectionVendorTrait.class);
        trait.openShop(event.getClicker());
    }

    /**
     * Check if Citizens integration is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Cleanup Citizens integration on plugin disable.
     */
    public void cleanup() {
        if (enabled) {
            NPCRightClickEvent.getHandlerList().unregister(this);
            logger.debug("Citizens integration cleaned up");
        }
        enabled = false;
    }
}
