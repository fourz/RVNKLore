package org.fourz.RVNKLore.service;

import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for lore submission/approval workflow operations.
 * Exposes submission management for cross-plugin access via RVNKCore ServiceRegistry.
 */
public interface ISubmissionService {

    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The lore entry ID
     * @return Future containing list of submissions
     */
    CompletableFuture<List<LoreSubmissionDTO>> getSubmissions(String entryId);

    /**
     * Get a specific submission by ID.
     *
     * @param submissionId The submission ID
     * @return Future containing the submission, or empty if not found
     */
    CompletableFuture<Optional<LoreSubmissionDTO>> getSubmission(int submissionId);

    /**
     * Create a new submission for a lore entry.
     *
     * @param entryId The lore entry ID
     * @param submitterUuid The UUID of the player submitting
     * @param content The submission content
     * @return Future containing the created submission
     */
    CompletableFuture<Optional<LoreSubmissionDTO>> createSubmission(String entryId, UUID submitterUuid, String content);

    /**
     * Approve a submission.
     *
     * @param submissionId The submission ID to approve
     * @param approverUuid The UUID of the approving player/admin
     * @return Future containing true if approved successfully
     */
    CompletableFuture<Boolean> approveSubmission(int submissionId, UUID approverUuid);

    /**
     * Reject a submission.
     *
     * @param submissionId The submission ID to reject
     * @param reason The rejection reason
     * @return Future containing true if rejected successfully
     */
    CompletableFuture<Boolean> rejectSubmission(int submissionId, String reason);

    /**
     * Get pending submissions awaiting approval.
     *
     * @return Future containing list of pending submissions
     */
    CompletableFuture<List<LoreSubmissionDTO>> getPendingSubmissions();

    /**
     * Get submissions by a specific player.
     *
     * @param submitterUuid The player UUID
     * @return Future containing list of player's submissions
     */
    CompletableFuture<List<LoreSubmissionDTO>> getSubmissionsByPlayer(UUID submitterUuid);

    /**
     * Get the current/latest approved submission for a lore entry.
     *
     * @param entryId The lore entry ID
     * @return Future containing the current submission, or empty if none approved
     */
    CompletableFuture<Optional<LoreSubmissionDTO>> getCurrentSubmission(String entryId);

    /**
     * Check if the service is in fallback mode due to errors.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();
}
