package org.fourz.RVNKLore.data.dto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.fourz.RVNKLore.lore.LoreSubmission;

/**
 * Data Transfer Object for LoreSubmission.
 * Used to transfer lore submission data between database and domain layers.
 * Includes static helper methods for mapping between domain objects and DTOs.
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
    public void setContent(String content) { this.content = content; }    /**
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
     * Creates a DTO from a SQL ResultSet.
     * This is used by the repository layer for mapping database results.
     * 
     * @param rs The ResultSet containing the lore submission data
     * @return A new LoreSubmissionDTO populated from the ResultSet
     * @throws SQLException If a database access error occurs
     */
    public static LoreSubmissionDTO fromResultSet(ResultSet rs) throws SQLException {
        LoreSubmissionDTO dto = new LoreSubmissionDTO();
        
        dto.setId(rs.getInt("id"));
        dto.setEntryId(rs.getInt("entry_id"));
        dto.setSlug(rs.getString("slug"));
        dto.setVisibility(rs.getString("visibility"));
        dto.setStatus(rs.getString("status"));
        dto.setSubmitterUuid(rs.getString("submitter_uuid"));
        dto.setCreatedBy(rs.getString("created_by"));
        dto.setSubmissionDate(rs.getTimestamp("submission_date"));
        dto.setApprovalStatus(rs.getString("approval_status"));
        dto.setApprovedBy(rs.getString("approved_by"));
        dto.setApprovedAt(rs.getTimestamp("approved_at"));
        dto.setViewCount(rs.getInt("view_count"));
        dto.setLastViewedAt(rs.getTimestamp("last_viewed_at"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setUpdatedAt(rs.getTimestamp("updated_at"));
        dto.setContentVersion(rs.getInt("content_version"));
        dto.setCurrentVersion(rs.getBoolean("is_current_version"));
        dto.setContent(rs.getString("content"));
        
        return dto;
    }    /**
     * Converts this DTO to a LoreSubmission domain object.
     * 
     * @return A new LoreSubmission domain object populated from this DTO
     */
    public LoreSubmission toLoreSubmission() {
        return new LoreSubmission(this);
    }
    
    /**
     * Creates a new DTO with default values for a new submission.
     * 
     * @param content The submission content
     * @param submitterUuid The UUID of the player submitting the content
     * @param entryId The ID of the lore entry this submission is for (0 for new entries)
     * @return A new LoreSubmissionDTO with default values set
     */
    public static LoreSubmissionDTO createNew(String content, String submitterUuid, int entryId) {
        LoreSubmissionDTO dto = new LoreSubmissionDTO();
        dto.setContent(content);
        dto.setSubmitterUuid(submitterUuid);
        dto.setEntryId(entryId);
        dto.setApprovalStatus("PENDING");
        dto.setStatus("PENDING");
        dto.setContentVersion(1);
        dto.setCurrentVersion(true);
        dto.setSubmissionDate(new Timestamp(System.currentTimeMillis()));
        dto.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        return dto;
    }
}
