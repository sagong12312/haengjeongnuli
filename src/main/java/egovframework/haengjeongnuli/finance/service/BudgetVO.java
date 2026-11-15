package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDateTime;

/** 예산 한 줄. 세출(EXP): 세부사업 × 통계목, 세입(REV): 세입 목 */
public class BudgetVO {
    public static final String EXP = "EXP";
    public static final String REV = "REV";
    public static final String DRAFT = "DRAFT";
    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private Long budgetId;
    private String kind;
    private int fiscalYear;
    private Long detailId;
    private String code;
    private long amount;
    private String basis;
    private String status;
    private String deptName;
    private String requesterId;
    private String requesterName;
    private LocalDateTime requestedAt;
    private String reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 화면용 (조인)
    private String codeName;
    private String detailName;
    private String unitName;
    private String policyName;
    private String policyType;
    private String funcCode;
    /** 집행액(세출) 또는 수납액(세입), 취소분 제외 */
    private long used;

    public boolean isEditable() { return DRAFT.equals(status) || REJECTED.equals(status); }
    public boolean isApproved() { return APPROVED.equals(status); }
    public long getBalance() { return isApproved() ? amount - used : 0; }

    /** 집행률(수납률) % 소수 첫째 자리 */
    public double getRate() {
        return amount == 0 ? 0 : Math.round(used * 1000.0 / amount) / 10.0;
    }

    public String getStatusLabel() {
        return switch (status) {
            case DRAFT -> "작성 중";
            case REQUESTED -> "승인 요청";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
            default -> status;
        };
    }

    public String getStatusBadge() {
        return switch (status) {
            case REQUESTED -> "badge-orange";
            case APPROVED -> "badge-green";
            case REJECTED -> "badge-red";
            default -> "badge-gray";
        };
    }

    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }
    public String getDetailName() { return detailName; }
    public void setDetailName(String detailName) { this.detailName = detailName; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public String getFuncCode() { return funcCode; }
    public void setFuncCode(String funcCode) { this.funcCode = funcCode; }
    public long getUsed() { return used; }
    public void setUsed(long used) { this.used = used; }
}
