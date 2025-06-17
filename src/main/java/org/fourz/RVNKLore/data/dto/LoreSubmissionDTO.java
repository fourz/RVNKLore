package org.fourz.RVNKLore.data.dto;

import java.sql.Timestamp;
import org.fourz.RVNKLore.lore.LoreSubmission;

/**
 * Data Transfer Object for LoreSubmission.
 * Used to transfer lore submission data between database and domain layers.
 */
public class LoreSubmissionDTO {
    private int id;
    private int entryId;
    private String slug;
    private String visibility;
    private String status;
    private String submitterUuid;
    private String createdBy;
    private Timestamp submissionDate;
    private String approvalStatus;
    private String approvedBy;
    private Timestamp approvedAt;
    private int viewCount;
    private Timestamp lastViewedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private int contentVersion;
    private boolean isCurrentVersion;
    private String content;

    public LoreSubmissionDTO() {}
    
    public LoreSubmissionDTO(int id, int entryId, String slug, String visibility, 
                           String status, String submitterUuid, String createdBy,
                           Timestamp submissionDate, String approvalStatus, String approvedBy,
                           Timestamp approvedAt, int viewCount, Timestamp lastViewedAt,
                           Timestamp createdAt, Timestamp updatedAt, int contentVersion,
                           boolean isCurrentVersion, String content) {
        this.id = id;
        this.entryId = entryId;
        this.slug = slug;
        this.visibility = visibility;
        this.status = status;
        this.submitterUuid = submitterUuid;
        this.createdBy = createdBy;
        this.submissionDate = submissionDate;
        this.approvalStatus = approvalStatus;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.viewCount = viewCount;
        this.lastViewedAt = lastViewedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.contentVersion = contentVersion;
        this.isCurrentVersion = isCurrentVersion;
        this.content = content;
    }    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getEntryId() { return entryId; }
    public void setEntryId(int entryId) { this.entryId = entryId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmitterUuid() { return submitterUuid; }
    public void setSubmitterUuid(String submitterUuid) { this.submitterUuid = submitterUuid; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Timestamp getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(Timestamp submissionDate) { this.submissionDate = submissionDate; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public Timestamp getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(Timestamp lastViewedAt) { this.lastViewedAt = lastViewedAt; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int contentVersion) { this.contentVersion = contentVersion; }
    public boolean isCurrentVersion() { return isCurrentVersion; }
    public void setCurrentVersion(boolean currentVersion) { this.isCurrentVersion = currentVersion; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    /**
     * Converts a LoreSubmission domain object to a DTO.
     */
    public static LoreSubmissionDTO fromLoreSubmission(LoreSubmission submission) {
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
     */
    public LoreSubmission toLoreSubmission() {
        return new LoreSubmission(this);
    }
}
