package org.fourz.RVNKLore.util;

import org.fourz.RVNKLore.RVNKLore;

/**
 * Manager for utility classes to provide centralized access
 */
public class UtilityManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private final DiagnosticUtil diagnosticUtil;
    private static UtilityManager instance;
    
    private UtilityManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "UtilityManager", plugin.getConfigManager().getLogLevel());
        this.diagnosticUtil = new DiagnosticUtil(plugin);
    }
    
    public static UtilityManager getInstance(RVNKLore plugin) {
        if (instance == null) {
            instance = new UtilityManager(plugin);
        }
        return instance;
    }
    
    public DiagnosticUtil getDiagnosticUtil() {
        return diagnosticUtil;
    }
    
    public void runHealthCheck() {
        debug.debug("Running periodic health check");
        
        try {
            boolean healthy = diagnosticUtil.checkSystemHealth();
            if (!healthy) {
                debug.warning("Health check failed, system may have issues");
                diagnosticUtil.logDiagnostics();
            }
        } catch (Exception e) {
            debug.error("Error during health check", e);
        }
    }
    
    public void cleanup() {
        instance = null;
    }
}
