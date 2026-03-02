package org.fourz.RVNKLore.api;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.api.controller.LoreApiServlet;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.IServletRegistrationService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Initializer for RVNKLore REST API integration with RVNKCore.
 *
 * <p>Registers the LoreApiServlet with RVNKCore's IServletRegistrationService
 * to expose REST endpoints under /api/lore/*.</p>
 *
 * <h2>API Endpoints:</h2>
 * <ul>
 *   <li>GET /api/lore/entries - List all lore entries</li>
 *   <li>GET /api/lore/entries/{id} - Get specific entry</li>
 *   <li>GET /api/lore/entries/type/{type} - Get entries by type</li>
 *   <li>GET /api/lore/entries/search?q={query} - Search entries</li>
 *   <li>POST /api/lore/submit - Submit new entry</li>
 *   <li>GET /api/lore/player/{uuid}/collection - Player collection</li>
 *   <li>GET /api/lore/collections - List collections</li>
 *   <li>GET /api/lore/types - List lore types</li>
 *   <li>GET /api/lore/stats - Get statistics</li>
 *   <li>GET /api/lore/health - Health check</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class LoreApiInitializer {

    private static final String API_PATH = "/api/lore/*";
    private static final String DISPLAY_NAME = "RVNKLore API";

    private final RVNKLore plugin;
    private final LogManager logger;

    private boolean initialized = false;
    private IServletRegistrationService servletService = null;

    /**
     * Creates a new API initializer.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public LoreApiInitializer(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreApiInitializer");
    }

    /**
     * Initializes the REST API by registering with RVNKCore's servlet service.
     *
     * @return true if API was successfully registered, false otherwise
     */
    public boolean initialize() {
        if (initialized) {
            logger.warning("API already initialized");
            return true;
        }

        if (!plugin.isRVNKCoreAvailable()) {
            logger.info("RVNKCore not available - REST API disabled");
            return false;
        }

        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core == null) {
                logger.warning("RVNKCore instance is null - REST API disabled");
                return false;
            }

            ServiceRegistry serviceRegistry = core.getServiceRegistry();
            if (serviceRegistry == null) {
                logger.warning("ServiceRegistry is null - REST API disabled");
                return false;
            }

            servletService = serviceRegistry.getService(IServletRegistrationService.class);
            if (servletService == null) {
                logger.info("IServletRegistrationService not available - REST API server may not be running");
                return false;
            }

            if (!servletService.isServerRunning()) {
                logger.info("REST API server not running - RVNKLore API endpoints disabled");
                return false;
            }

            LoreApiServlet servlet = new LoreApiServlet(plugin);
            boolean registered = servletService.registerServlet(API_PATH, servlet, DISPLAY_NAME, true);

            if (registered) {
                initialized = true;
                logger.info("REST API registered at: " + servletService.getBaseUrl() + "/api/lore/*");
                logger.info("Available endpoints: /entries, /submit, /player/{uuid}/collection, /collections, /types, /stats, /health");
                return true;
            } else {
                logger.warning("Failed to register servlet - path may already be registered");
                return false;
            }

        } catch (Exception e) {
            logger.warning("Failed to initialize REST API: " + e.getMessage());
            return false;
        }
    }

    /**
     * Shuts down the REST API by unregistering from RVNKCore.
     */
    public void shutdown() {
        if (!initialized || servletService == null) {
            return;
        }
        try {
            servletService.unregisterServlet(API_PATH);
            logger.info("REST API unregistered");
        } catch (Exception e) {
            logger.warning("Failed to unregister REST API: " + e.getMessage());
        }
        initialized = false;
        servletService = null;
    }

    /**
     * Checks if the API is currently initialized.
     *
     * @return true if API is active, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}
