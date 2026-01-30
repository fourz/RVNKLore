package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.lore.LoreSubmission;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Data Transfer Object for LoreSubmission using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 */
public record LoreSubmissionDTO(
    int id,
    String entryId,
    String slug,
    String visibility,
    String status,
    String submitterUuid,
    String createdBy,
    Timestamp submissionDate,
    String approvalStatus,
    String approvedBy,
    Timestamp approvedAt,
    int viewCount,
    Timestamp lastViewedAt,
    Timestamp createdAt,
    Timestamp updatedAt,
    int contentVersion,
    boolean isCurrentVersion,
    String content
) {
    /**
     * Compact constructor with validation.
     */
    public LoreSubmissionDTO {
        Objects.requireNonNull(entryId, "entryId cannot be null");
    }

    /**
     * Factory method from LoreSubmission domain object.
     *
     * @param submission The domain entity to convert
     * @return A new LoreSubmissionDTO, or null if submission is null
     */
    public static LoreSubmissionDTO from(LoreSubmission submission) {
        if (submission == null) return null;

        return new LoreSubmissionDTO(
            submission.getId(),
            submission.getEntryId(),
            submission.getSlug(),
            submission.getVisibility(),
            submission.getStatus(),
            submission.getSubmitterUuid(),
            submission.getCreatedBy(),
            submission.getSubmissionDate(),
            submission.getApprovalStatus(),
            submission.getApprovedBy(),
            submission.getApprovedAt(),
            submission.getViewCount(),
            submission.getLastViewedAt(),
            submission.getCreatedAt(),
            submission.getUpdatedAt(),
            submission.getContentVersion(),
            submission.isCurrentVersion(),
            submission.getContent()
        );
    }

    /**
     * Converts this DTO to a LoreSubmission domain object.
     *
     * @return A new LoreSubmission populated with DTO values
     */
    public LoreSubmission toEntity() {
        return new LoreSubmission(this);
    }

    /**
     * Builder for constructing LoreSubmissionDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for LoreSubmissionDTO.
     */
    public static class Builder {
        private int id;
        private String entryId;
        private String slug;
        private String visibility = "PUBLIC";
        private String status = "PENDING";
        private String submitterUuid;
        private String createdBy;
        private Timestamp submissionDate = new Timestamp(System.currentTimeMillis());
        private String approvalStatus = "PENDING";
        private String approvedBy;
        private Timestamp approvedAt;
        private int viewCount = 0;
        private Timestamp lastViewedAt;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());
        private Timestamp updatedAt = new Timestamp(System.currentTimeMillis());
        private int contentVersion = 1;
        private boolean isCurrentVersion = true;
        private String content;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder entryId(String entryId) {
            this.entryId = entryId;
            return this;
        }

        public Builder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public Builder visibility(String visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder submitterUuid(String submitterUuid) {
            this.submitterUuid = submitterUuid;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder submissionDate(Timestamp submissionDate) {
            this.submissionDate = submissionDate;
            return this;
        }

        public Builder approvalStatus(String approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder approvedAt(Timestamp approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public Builder viewCount(int viewCount) {
            this.viewCount = viewCount;
            return this;
        }

        public Builder lastViewedAt(Timestamp lastViewedAt) {
            this.lastViewedAt = lastViewedAt;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Timestamp updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder contentVersion(int contentVersion) {
            this.contentVersion = contentVersion;
            return this;
        }

        public Builder isCurrentVersion(boolean isCurrentVersion) {
            this.isCurrentVersion = isCurrentVersion;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public LoreSubmissionDTO build() {
            return new LoreSubmissionDTO(
                id, entryId, slug, visibility, status, submitterUuid, createdBy,
                submissionDate, approvalStatus, approvedBy, approvedAt,
                viewCount, lastViewedAt, createdAt, updatedAt,
                contentVersion, isCurrentVersion, content
            );
        }
    }
}