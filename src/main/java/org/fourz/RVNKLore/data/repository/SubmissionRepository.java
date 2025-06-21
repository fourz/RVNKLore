package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.SQLException;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Repository for Lore Submission database operations
 * 
 * This repository manages the creation, retrieval, and updating of lore submissions
 * using the lore_submission table. All operations are asynchronous using CompletableFuture.
 */
public class SubmissionRepository {
    protected final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public SubmissionRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SubmissionRepository");
        this.databaseManager = databaseManager;
    }

    /**
     * Get a lore submission by ID.
     *
     * @param id The ID of the submission
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getLoreSubmission(int id) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_submission")
            .where("id = ?", id);
        
        return databaseManager.getQueryExecutor().executeQuery(query, LoreSubmissionDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting lore submission with ID: " + id, e);
                throw new CompletionException(e);
            });
    }

    /**
     * Get the current version of a lore submission for an entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getCurrentSubmission(int entryId) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_submission")
            .where("entry_id = ? AND is_current_version = ?", entryId, true);
        
        return databaseManager.getQueryExecutor().executeQuery(query, LoreSubmissionDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting current submission for entry ID: " + entryId, e);
                return null;
            });
    }
    
    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing a list of lore submission DTOs
     */
    public CompletableFuture<List<LoreSubmissionDTO>> getSubmissionsForEntry(int entryId) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_submission")
            .where("entry_id = ?", entryId)
            .orderBy("content_version", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreSubmissionDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting submissions for entry ID: " + entryId, e);
                return null;
            });
    }
    
    /**
     * Save a lore submission.
     *
     * @param dto The lore submission DTO to save
     * @return A future containing the saved lore submission ID
     */
    public CompletableFuture<Integer> saveLoreSubmission(LoreSubmissionDTO dto) {
        if (dto == null) {
            logger.error("Cannot save null LoreSubmissionDTO", null);
            return CompletableFuture.completedFuture(-1);
        }

        if (dto.getId() > 0) {
            // This is an update
            return updateExistingSubmission(dto);
        } else {
            // This is an insert
            return insertNewSubmission(dto);
        }
    }

    /**
     * Insert a new lore submission.
     *
     * @param dto The lore submission DTO to insert
     * @return A future containing the inserted lore submission ID
     */
    private CompletableFuture<Integer> insertNewSubmission(LoreSubmissionDTO dto) {
        // Ensure submission date is set if not already
        if (dto.getSubmissionDate() == null) {
            dto.setSubmissionDate(new Timestamp(System.currentTimeMillis()));
        }
        
        // Set default values if not provided
        if (dto.getApprovalStatus() == null) {
            dto.setApprovalStatus("PENDING");
        }
        
        if (dto.getStatus() == null) {
            dto.setStatus("PENDING");
        }
        
        // Default to version 1 for new submissions
        if (dto.getContentVersion() <= 0) {
            dto.setContentVersion(1);
        }
          QueryBuilder query = databaseManager.getQueryBuilder()
            .insert("lore_submission", false) // Don't allow upsert - submissions are append-only
            .columns("entry_id", "slug", "visibility", "status", "submitter_uuid", 
                     "created_by", "submission_date", "approval_status", "content_version", 
                     "is_current_version", "content")
            .values(dto.getEntryId(), dto.getSlug(), dto.getVisibility(), dto.getStatus(), 
                   dto.getSubmitterUuid(), dto.getCreatedBy(), dto.getSubmissionDate(), 
                   dto.getApprovalStatus(), dto.getContentVersion(), dto.isCurrentVersion(), dto.getContent());
        
        return databaseManager.getQueryExecutor().executeInsert(query)
            .exceptionally(e -> {
                logger.error("Error inserting new lore submission", e);
                return -1;
            });
    }

    /**
     * Update an existing lore submission.
     *
     * @param dto The lore submission DTO to update
     * @return A future containing the updated lore submission ID
     */
    private CompletableFuture<Integer> updateExistingSubmission(LoreSubmissionDTO dto) {
        // Set updated timestamp
        dto.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        
        QueryBuilder query = databaseManager.getQueryBuilder()
            .update("lore_submission")
            .set("entry_id", dto.getEntryId())
            .set("slug", dto.getSlug())
            .set("visibility", dto.getVisibility())
            .set("status", dto.getStatus())
            .set("submitter_uuid", dto.getSubmitterUuid())
            .set("created_by", dto.getCreatedBy())
            .set("submission_date", dto.getSubmissionDate())
            .set("approval_status", dto.getApprovalStatus())
            .set("approved_by", dto.getApprovedBy())
            .set("approved_at", dto.getApprovedAt())
            .set("view_count", dto.getViewCount())
            .set("last_viewed_at", dto.getLastViewedAt())
            .set("updated_at", dto.getUpdatedAt())
            .set("content_version", dto.getContentVersion())
            .set("is_current_version", dto.isCurrentVersion())
            .set("content", dto.getContent())
            .where("id = ?", dto.getId());
        
        return databaseManager.getQueryExecutor().executeUpdate(query)
            .thenApply(rowsAffected -> {
                if (rowsAffected > 0) {
                    return dto.getId();
                } else {
                    logger.error("No rows affected when updating submission ID: " + dto.getId(), null);
                    return -1;
                }
            })
            .exceptionally(e -> {
                logger.error("Error updating lore submission", e);
                return -1;
            });
    }
    
    /**
     * Approve a lore submission.
     *
     * @param submissionId The ID of the submission to approve
     * @param approverUuid The UUID of the staff member approving the submission
     * @return A future containing true if the submission was approved, false otherwise
     */
    public CompletableFuture<Boolean> approveSubmission(int submissionId, String approverUuid) {
        return databaseManager.getQueryExecutor().executeTransaction(conn -> {
            // 1. Get the submission
            QueryBuilder query = databaseManager.getQueryBuilder()
                .select("*")
                .from("lore_submission")
                .where("id = ?", submissionId);

            LoreSubmissionDTO submission = databaseManager.getQueryExecutor().executeQuery(query, LoreSubmissionDTO.class).join();
            if (submission == null) {
                logger.error("Failed to approve submission", 
                    new IllegalStateException("Submission not found for ID: " + submissionId));
                return false;
            }

            // 2. Check if it's already approved
            if ("APPROVED".equals(submission.getApprovalStatus())) {
                logger.warning("Submission " + submissionId + " is already approved");
                return true;
            }

            // 3. Mark any existing current version as not current
            QueryBuilder resetQuery = databaseManager.getQueryBuilder()
                .update("lore_submission")
                .set("is_current_version", false)
                .where("entry_id = ? AND id != ? AND is_current_version = ?", 
                      submission.getEntryId(), submissionId, true);

            databaseManager.getQueryExecutor().executeUpdate(resetQuery).join();

            // 4. Update the submission being approved
            QueryBuilder updateQuery = databaseManager.getQueryBuilder()
                .update("lore_submission")
                .set("approval_status", "APPROVED")
                .set("approved_by", approverUuid)
                .set("approved_at", new java.sql.Timestamp(System.currentTimeMillis()))
                .set("status", "ACTIVE")
                .set("is_current_version", true)
                .where("id = ?", submissionId);

            int rowsAffected = databaseManager.getQueryExecutor().executeUpdate(updateQuery).join();
            if (rowsAffected != 1) {
                logger.error("Failed to approve submission, rows affected: " + rowsAffected, 
                    new SQLException("Update failed for submission ID: " + submissionId));
                return false;
            }

            return true;
        });
    }
    
    /**
     * Approves a lore submission and creates the associated lore entry.
     *
     * @param submissionId The ID of the submission to approve
     * @param approverUuid The UUID of the staff member approving the submission
     * @return A future containing true if the submission was approved, false otherwise
     */
    public CompletableFuture<Boolean> approveLoreSubmission(int submissionId, String approverUuid) {
        return getLoreSubmission(submissionId)
            .thenCompose(submissionDto -> {
                if (submissionDto == null) {
                    return CompletableFuture.completedFuture(false);
                }
                
                // Update submission status
                submissionDto.setApprovalStatus("APPROVED");
                submissionDto.setApprovedBy(approverUuid);
                submissionDto.setApprovedAt(new Timestamp(System.currentTimeMillis()));
                
                // Create the lore entry
                LoreEntryDTO entryDto = new LoreEntryDTO();
                entryDto.setContent(submissionDto.getContent());
                entryDto.setSubmittedBy(submissionDto.getSubmitterUuid());
                entryDto.setSubmissionDate(submissionDto.getSubmissionDate());
                entryDto.setApproved(true);
                
                // Save both changes
                return databaseManager.saveLoreEntry(entryDto)
                    .thenCompose(loreId -> {
                        if (loreId != null && loreId > 0) {
                            return updateExistingSubmission(submissionDto)
                                .thenApply(id -> id > 0);
                        }
                        return CompletableFuture.completedFuture(false);
                    });
            });
    }
    
    /**
     * Search for lore submissions by keyword.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore entries
     */
    public CompletableFuture<List<LoreSubmissionDTO>> searchLoreSubmissions(String keyword) {
        String searchPattern = "%" + keyword + "%";
        
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_submission")
            .where("content LIKE ? OR slug LIKE ?", searchPattern, searchPattern)
            .orderBy("submission_date", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreSubmissionDTO.class)
            .exceptionally(e -> {
                logger.error("Error searching lore submissions with keyword: " + keyword, e);
                return null;
            });
    }
    
    /**
     * Search lore entries by their content in submissions.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntriesInSubmissions(String keyword) {
        String searchTerm = "%" + keyword + "%";
        
        QueryBuilder query = databaseManager.getQueryBuilder().select("e.*")
            .from("lore_entry e")
            .join("lore_submission s", "s.entry_id = e.id")
            .where("s.is_current_version = TRUE AND (s.content LIKE ? OR e.entry_type LIKE ?)", 
                  searchTerm, searchTerm)
            .orderBy("s.submitted_at", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error searching lore submissions", e);
                return List.of();
            });
    }
}
