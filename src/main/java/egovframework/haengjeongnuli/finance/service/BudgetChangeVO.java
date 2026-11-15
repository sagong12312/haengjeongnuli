package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDateTime;
import java.util.List;

/** 승인된 세출예산의 추가경정(증감)·이월 요청 */
public class BudgetChangeVO {

    public static final String SUPP = "SUPP";
    public static final String CARRY = "CARRY";
    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** 지방재정법 제50조 제1항(명시이월)·제2항(사고이월) */
    public static final List<String> CARRY_KINDS = List.of("명시이월", "사고이월");

    private Long changeId;
    private Long budgetId;
    private String changeType;
    private Integer roundNo;
    private String carryKind;
    private long delta;
    private String reason;
    private String status;
    private String requesterId;
    private String requesterName;
    private LocalDateTime requestedAt;
    private String reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Long newBudgetId;
    // 목록 화면용
    private String deptName;
    private String code;
    private int fiscalYear;

    public String getTypeLabel() {
        return SUPP.equals(changeType) ? "제" + (roundNo == null ? "" : roundNo) + "회 추가경정" : carryKind;
    }

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case REQUESTED -> "승인 대기";
            case APPROVED -> "반영";
            case REJECTED -> "반려";
            default -> "";
        };
    }

    public Long getChangeId() { return changeId; }
    public void setChangeId(Long changeId) { this.changeId = changeId; }
    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getCarryKind() { return carryKind; }
    public void setCarryKind(String carryKind) { this.carryKind = carryKind; }
    public long getDelta() { return delta; }
    public void setDelta(long delta) { this.delta = delta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getNewBudgetId() { return newBudgetId; }
    public void setNewBudgetId(Long newBudgetId) { this.newBudgetId = newBudgetId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
}
