package org.fourz.RVNKLore.lore;

import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;

import java.sql.Timestamp;

/**
 * Represents a lore submission with versioning and approval status.
 * Used to track lore content versions, approvals, and submission history.
 */
public class LoreSubmission {
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

    /**
     * Default constructor for new submissions
     */
    public LoreSubmission() {
        this.submissionDate = new Timestamp(System.currentTimeMillis());
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.contentVersion = 1;
        this.viewCount = 0;
        this.visibility = "PUBLIC";
        this.status = "PENDING_APPROVAL";
        this.approvalStatus = "PENDING";
        this.isCurrentVersion = true;
    }

    /**
     * Constructor for creating a new submission with basic properties
     */
    public LoreSubmission(int entryId, String submitterUuid, String content) {
        this();
        this.entryId = entryId;
        this.submitterUuid = submitterUuid;
        this.createdBy = submitterUuid;
        this.content = content;
        
        // Generate a slug from the current timestamp to ensure uniqueness
        this.slug = "lore-" + entryId + "-" + System.currentTimeMillis();
    }

    /**
     * Full constructor for creating a submission with all properties
     */
    public LoreSubmission(int id, int entryId, String slug, String visibility, 
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
    }

    /**
     * Constructor for creating from DTO
     */
    public LoreSubmission(LoreSubmissionDTO dto) {
        this.id = dto.getId();
        this.entryId = dto.getEntryId();
        this.slug = dto.getSlug();
        this.visibility = dto.getVisibility();
        this.status = dto.getStatus();
        this.submitterUuid = dto.getSubmitterUuid();
        this.createdBy = dto.getCreatedBy();
        this.submissionDate = dto.getSubmissionDate();
        this.approvalStatus = dto.getApprovalStatus();
        this.approvedBy = dto.getApprovedBy();
        this.approvedAt = dto.getApprovedAt();
        this.viewCount = dto.getViewCount();
        this.lastViewedAt = dto.getLastViewedAt();
        this.createdAt = dto.getCreatedAt();
        this.updatedAt = dto.getUpdatedAt();
        this.contentVersion = dto.getContentVersion();
        this.isCurrentVersion = dto.isCurrentVersion();
        this.content = dto.getContent();
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmitterUuid() {
        return submitterUuid;
    }

    public void setSubmitterUuid(String submitterUuid) {
        this.submitterUuid = submitterUuid;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(Timestamp submissionDate) {
        this.submissionDate = submissionDate;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public Timestamp getLastViewedAt() {
        return lastViewedAt;
    }

    public void setLastViewedAt(Timestamp lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(int contentVersion) {
        this.contentVersion = contentVersion;
    }

    public boolean isCurrentVersion() {
        return isCurrentVersion;
    }

    public void setCurrentVersion(boolean currentVersion) {
        isCurrentVersion = currentVersion;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Increment the view count for this submission
     */
    public void incrementViewCount() {
        this.viewCount++;
        this.lastViewedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Approve this submission
     *
     * @param approverUuid UUID of the staff member who approved
     */
    public void approve(String approverUuid) {
        this.approvalStatus = "APPROVED";
        this.approvedBy = approverUuid;
        this.approvedAt = new Timestamp(System.currentTimeMillis());
        this.status = "ACTIVE";
    }

    /**
     * Reject this submission
     *
     * @param approverUuid UUID of the staff member who rejected
     */
    public void reject(String approverUuid) {
        this.approvalStatus = "REJECTED";
        this.approvedBy = approverUuid;
        this.approvedAt = new Timestamp(System.currentTimeMillis());
        this.status = "ARCHIVED";
        this.isCurrentVersion = false;
    }

    /**
     * Create a new version based on this submission
     *
     * @param content New content for the submission
     * @param submitterUuid UUID of the user creating the new version
     * @return A new LoreSubmission object with incremented version
     */
    public LoreSubmission createNewVersion(String content, String submitterUuid) {
        LoreSubmission newVersion = new LoreSubmission();
        newVersion.entryId = this.entryId;
        newVersion.slug = this.slug + "-v" + (this.contentVersion + 1);
        newVersion.visibility = this.visibility;
        newVersion.status = "PENDING_APPROVAL";
        newVersion.submitterUuid = submitterUuid;
        newVersion.createdBy = submitterUuid;
        newVersion.contentVersion = this.contentVersion + 1;
        newVersion.content = content;
        
        // Mark this version as no longer current
        this.isCurrentVersion = false;
        
        return newVersion;
    }
}
