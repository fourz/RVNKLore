package org.fourz.RVNKLore.lore.submission;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.LoreSubmission;
import org.fourz.RVNKLore.service.ISubmissionService;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for lore submission/approval workflow operations.
 * Implements ISubmissionService for RVNKCore ServiceRegistry integration.
 * 
 * This manager handles:
 * - Creating new submissions for lore entries
 * - Submission approval/rejection workflow
 * - Querying pending submissions
 * - Managing submission versions and history
 */
public class SubmissionManager implements ISubmissionService {

    private final RVNKLore plugin;
    private final LogManager logger;
    private volatile boolean fallbackMode = false;
    
    // Cache for frequently accessed submissions
    private final Map<Integer, LoreSubmission> submissionCache = new ConcurrentHashMap<>();
    private final Map<String, List<LoreSubmission>> entrySubmissionsCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a new SubmissionManager.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public SubmissionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SubmissionManager");
        logger.info("Initializing SubmissionManager...");

        // Verify database availability
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - submission features may be limited");
            fallbackMode = true;
        }
    }

    /** Helper to get prefixed table name */
    private String t(String baseName) {
        DatabaseConnection conn = plugin.getDatabaseManager().getDatabaseConnection();
        return conn != null ? conn.table(baseName) : baseName;
    }
    
    // ==================== Sync Internal Methods ====================
    
    /**
     * Get all submissions for a lore entry (sync internal method).
     *
     * @param entryId The lore entry ID
     * @return List of submissions for the entry
     */
    public List<LoreSubmissionDTO> getSubmissionsSync(String entryId) {
        if (entryId == null || entryId.isEmpty()) {
            logger.warning("Cannot get submissions for null/empty entry ID");
            return Collections.emptyList();
        }
        
        // Check cache first
        List<LoreSubmission> cached = entrySubmissionsCache.get(entryId);
        if (cached != null) {
            return cached.stream()
                .map(LoreSubmissionDTO::from)
                .toList();
        }
        
        List<LoreSubmission> submissions = new ArrayList<>();
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Collections.emptyList();
        }
        
        String sql = "SELECT * FROM " + t("lore_submission") + " WHERE entry_id = ? ORDER BY content_version DESC";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    submissions.add(mapResultSetToSubmission(rs));
                }
            }
            // Update cache
            entrySubmissionsCache.put(entryId, submissions);
        } catch (SQLException e) {
            logger.error("Failed to get submissions for entry: " + entryId, e);
            fallbackMode = true;
        }
        
        return submissions.stream()
            .map(LoreSubmissionDTO::from)
            .toList();
    }
    
    /**
     * Get a specific submission by ID (sync internal method).
     *
     * @param submissionId The submission ID
     * @return The submission, or empty if not found
     */
    public Optional<LoreSubmissionDTO> getSubmissionSync(int submissionId) {
        // Check cache first
        LoreSubmission cached = submissionCache.get(submissionId);
        if (cached != null) {
            return Optional.of(LoreSubmissionDTO.from(cached));
        }
        
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM " + t("lore_submission") + " WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, submissionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LoreSubmission submission = mapResultSetToSubmission(rs);
                    submissionCache.put(submissionId, submission);
                    return Optional.of(LoreSubmissionDTO.from(submission));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get submission: " + submissionId, e);
            fallbackMode = true;
        }
        
        return Optional.empty();
    }
    
    /**
     * Create a new submission for a lore entry (sync internal method).
     *
     * @param entryId The lore entry ID
     * @param submitterUuid The UUID of the player submitting
     * @param content The submission content
     * @return The created submission, or empty if failed
     */
    public Optional<LoreSubmissionDTO> createSubmissionSync(String entryId, UUID submitterUuid, String content) {
        if (entryId == null || submitterUuid == null || content == null) {
            logger.warning("Cannot create submission with null parameters");
            return Optional.empty();
        }
        
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Optional.empty();
        }
        
        // Get next version number
        int nextVersion = getNextVersionNumber(entryId);
        String slug = "lore-" + entryId + "-v" + nextVersion + "-" + System.currentTimeMillis();
        
        String sql = "INSERT INTO " + t("lore_submission") + " (entry_id, submitter_uuid, content, slug, " +
                     "content_version, is_current_version, status, approval_status) " +
                     "VALUES (?, ?, ?, ?, ?, FALSE, 'PENDING_APPROVAL', 'PENDING')";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, entryId);
            stmt.setString(2, submitterUuid.toString());
            stmt.setString(3, content);
            stmt.setString(4, slug);
            stmt.setInt(5, nextVersion);
            
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        int newId = keys.getInt(1);
                        // Invalidate cache
                        entrySubmissionsCache.remove(entryId);
                        // Auto-approve if approval workflow is disabled
                        if (!plugin.getConfigManager().requireApproval()) {
                            approveSubmissionSync(newId, submitterUuid);
                            logger.debug("Auto-approved submission " + newId + " (requireApproval=false)");
                        }
                        // Return the new submission
                        return getSubmissionSync(newId);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create submission for entry: " + entryId, e);
            fallbackMode = true;
        }
        
        return Optional.empty();
    }
    
    /**
     * Approve a submission (sync internal method).
     *
     * @param submissionId The submission ID to approve
     * @param approverUuid The UUID of the approving player/admin
     * @return True if approved successfully
     */
    public boolean approveSubmissionSync(int submissionId, UUID approverUuid) {
        Optional<LoreSubmissionDTO> submissionOpt = getSubmissionSync(submissionId);
        if (submissionOpt.isEmpty()) {
            logger.warning("Cannot approve non-existent submission: " + submissionId);
            return false;
        }
        
        LoreSubmissionDTO submission = submissionOpt.get();
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return false;
        }
        
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Mark previous current version as not current
            String unsetCurrentSql = "UPDATE " + t("lore_submission") + " SET is_current_version = FALSE " +
                                     "WHERE entry_id = ? AND is_current_version = TRUE";
            try (PreparedStatement stmt = conn.prepareStatement(unsetCurrentSql)) {
                stmt.setString(1, submission.entryId());
                stmt.executeUpdate();
            }
            
            // Approve this submission and mark as current
            String approveSql = "UPDATE " + t("lore_submission") + " SET approval_status = 'APPROVED', " +
                               "status = 'ACTIVE', approved_by = ?, approved_at = ?, " +
                               "is_current_version = TRUE, updated_at = ? " +
                               "WHERE id = ?";
            Timestamp now = new Timestamp(System.currentTimeMillis());
            try (PreparedStatement stmt = conn.prepareStatement(approveSql)) {
                stmt.setString(1, approverUuid.toString());
                stmt.setTimestamp(2, now);
                stmt.setTimestamp(3, now);
                stmt.setInt(4, submissionId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            
            // Invalidate caches
            submissionCache.remove(submissionId);
            entrySubmissionsCache.remove(submission.entryId());
            
            logger.info("Approved submission " + submissionId + " by " + approverUuid);
            return true;
            
        } catch (SQLException e) {
            logger.error("Failed to approve submission: " + submissionId, e);
            fallbackMode = true;
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            }
            return false;
        } finally {
            if (conn != null) {
                try { 
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ex) { /* ignore */ }
            }
        }
    }
    
    /**
     * Reject a submission (sync internal method).
     *
     * @param submissionId The submission ID to reject
     * @param reason The rejection reason
     * @return True if rejected successfully
     */
    public boolean rejectSubmissionSync(int submissionId, String reason) {
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return false;
        }
        
        String sql = "UPDATE " + t("lore_submission") + " SET approval_status = 'REJECTED', " +
                     "status = 'ARCHIVED', updated_at = ? WHERE id = ?";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, now);
            stmt.setInt(2, submissionId);
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                // Invalidate cache
                LoreSubmission cached = submissionCache.remove(submissionId);
                if (cached != null) {
                    entrySubmissionsCache.remove(cached.getEntryId());
                }
                logger.info("Rejected submission " + submissionId + ": " + reason);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Failed to reject submission: " + submissionId, e);
            fallbackMode = true;
        }
        
        return false;
    }
    
    /**
     * Get pending submissions awaiting approval (sync internal method).
     *
     * @return List of pending submissions
     */
    public List<LoreSubmissionDTO> getPendingSubmissionsSync() {
        List<LoreSubmission> pending = new ArrayList<>();
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Collections.emptyList();
        }
        
        String sql = "SELECT * FROM " + t("lore_submission") + " WHERE approval_status = 'PENDING' " +
                     "ORDER BY submission_date ASC";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                pending.add(mapResultSetToSubmission(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to get pending submissions", e);
            fallbackMode = true;
        }
        
        return pending.stream()
            .map(LoreSubmissionDTO::from)
            .toList();
    }
    
    /**
     * Get submissions by a specific player (sync internal method).
     *
     * @param submitterUuid The player UUID
     * @return List of player's submissions
     */
    public List<LoreSubmissionDTO> getSubmissionsByPlayerSync(UUID submitterUuid) {
        if (submitterUuid == null) {
            return Collections.emptyList();
        }
        
        List<LoreSubmission> submissions = new ArrayList<>();
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Collections.emptyList();
        }
        
        String sql = "SELECT * FROM " + t("lore_submission") + " WHERE submitter_uuid = ? " +
                     "ORDER BY submission_date DESC";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, submitterUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    submissions.add(mapResultSetToSubmission(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get submissions for player: " + submitterUuid, e);
            fallbackMode = true;
        }
        
        return submissions.stream()
            .map(LoreSubmissionDTO::from)
            .toList();
    }
    
    /**
     * Get the current/latest approved submission for a lore entry (sync internal method).
     *
     * @param entryId The lore entry ID
     * @return The current submission, or empty if none approved
     */
    public Optional<LoreSubmissionDTO> getCurrentSubmissionSync(String entryId) {
        if (entryId == null || entryId.isEmpty()) {
            return Optional.empty();
        }
        
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            fallbackMode = true;
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM " + t("lore_submission") + " WHERE entry_id = ? AND is_current_version = TRUE";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(LoreSubmissionDTO.from(mapResultSetToSubmission(rs)));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get current submission for entry: " + entryId, e);
            fallbackMode = true;
        }
        
        return Optional.empty();
    }
    
    // ==================== Async Interface Methods (ISubmissionService) ====================
    
    @Override
    public CompletableFuture<List<LoreSubmissionDTO>> getSubmissions(String entryId) {
        return CompletableFuture.supplyAsync(() -> getSubmissionsSync(entryId));
    }
    
    @Override
    public CompletableFuture<Optional<LoreSubmissionDTO>> getSubmission(int submissionId) {
        return CompletableFuture.supplyAsync(() -> getSubmissionSync(submissionId));
    }
    
    @Override
    public CompletableFuture<Optional<LoreSubmissionDTO>> createSubmission(String entryId, UUID submitterUuid, String content) {
        return CompletableFuture.supplyAsync(() -> createSubmissionSync(entryId, submitterUuid, content));
    }
    
    @Override
    public CompletableFuture<Boolean> approveSubmission(int submissionId, UUID approverUuid) {
        return CompletableFuture.supplyAsync(() -> approveSubmissionSync(submissionId, approverUuid));
    }
    
    @Override
    public CompletableFuture<Boolean> rejectSubmission(int submissionId, String reason) {
        return CompletableFuture.supplyAsync(() -> rejectSubmissionSync(submissionId, reason));
    }
    
    @Override
    public CompletableFuture<List<LoreSubmissionDTO>> getPendingSubmissions() {
        return CompletableFuture.supplyAsync(this::getPendingSubmissionsSync);
    }
    
    @Override
    public CompletableFuture<List<LoreSubmissionDTO>> getSubmissionsByPlayer(UUID submitterUuid) {
        return CompletableFuture.supplyAsync(() -> getSubmissionsByPlayerSync(submitterUuid));
    }
    
    @Override
    public CompletableFuture<Optional<LoreSubmissionDTO>> getCurrentSubmission(String entryId) {
        return CompletableFuture.supplyAsync(() -> getCurrentSubmissionSync(entryId));
    }
    
    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get database connection helper.
     */
    private DatabaseConnection getDbConnection() {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            return null;
        }
        return plugin.getDatabaseManager().getDatabaseConnection();
    }
    
    /**
     * Get the next version number for a lore entry.
     */
    private int getNextVersionNumber(String entryId) {
        DatabaseConnection dbConnection = getDbConnection();
        if (dbConnection == null) {
            return 1;
        }
        
        String sql = "SELECT MAX(content_version) FROM " + t("lore_submission") + " WHERE entry_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int max = rs.getInt(1);
                    return max + 1;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get next version for entry: " + entryId, e);
        }
        
        return 1;
    }
    
    /**
     * Map a ResultSet row to a LoreSubmission object.
     */
    private LoreSubmission mapResultSetToSubmission(ResultSet rs) throws SQLException {
        return new LoreSubmission(
            rs.getInt("id"),
            rs.getString("entry_id"),
            rs.getString("slug"),
            rs.getString("visibility"),
            rs.getString("status"),
            rs.getString("submitter_uuid"),
            rs.getString("created_by"),
            rs.getTimestamp("submission_date"),
            rs.getString("approval_status"),
            rs.getString("approved_by"),
            rs.getTimestamp("approved_at"),
            rs.getInt("view_count"),
            rs.getTimestamp("last_viewed_at"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getInt("content_version"),
            rs.getBoolean("is_current_version"),
            rs.getString("content")
        );
    }
    
    /**
     * Clear all caches.
     */
    public void clearCaches() {
        submissionCache.clear();
        entrySubmissionsCache.clear();
        logger.debug("Submission caches cleared");
    }
    
    /**
     * Shutdown and cleanup resources.
     */
    public void shutdown() {
        clearCaches();
        logger.info("SubmissionManager shutdown complete");
    }
}
