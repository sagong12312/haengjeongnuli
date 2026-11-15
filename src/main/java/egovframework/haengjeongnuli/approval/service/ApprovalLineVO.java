package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;

/** 결재선의 한 칸 */
public class ApprovalLineVO {

    public static final String WAIT = "WAIT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private Long lineId;
    private Long docId;
    private int stepNo;
    /** 검토 / 결재 */
    private String role;
    private String approverId;
    private String approverName;
    private String approverPosition;
    private String approverDept;
    private String status;
    private String opinion;
    private LocalDateTime actedAt;
    /** 대결한 사람 (결재권자 본인이 처리했으면 null) */
    private String actedById;
    private String actedByName;
    private String actedByPosition;

    public static final String ROLE_REVIEW = "검토";
    public static final String ROLE_COOP = "협조";
    public static final String ROLE_FINAL = "결재";

    public boolean isProxyActed() { return actedById != null; }
    public boolean isFinalRole() { return ROLE_FINAL.equals(role); }

    public String getActedById() { return actedById; }
    public void setActedById(String actedById) { this.actedById = actedById; }
    public String getActedByName() { return actedByName; }
    public void setActedByName(String actedByName) { this.actedByName = actedByName; }
    public String getActedByPosition() { return actedByPosition; }
    public void setActedByPosition(String actedByPosition) { this.actedByPosition = actedByPosition; }

    public String getStatusLabel() {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case WAIT -> "대기";
            case PENDING -> "결재할 차례";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
            default -> status;
        };
    }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getApproverPosition() { return approverPosition; }
    public void setApproverPosition(String approverPosition) { this.approverPosition = approverPosition; }
    public String getApproverDept() { return approverDept; }
    public void setApproverDept(String approverDept) { this.approverDept = approverDept; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
}
