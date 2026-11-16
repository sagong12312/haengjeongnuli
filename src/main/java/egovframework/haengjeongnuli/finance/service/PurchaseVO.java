package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 구매 건: 견적을 받아 업체를 고르고, 납품되면 검수한 뒤 지출결의로 대금을 지급한다 */
public class PurchaseVO {

    public static final List<String> TYPES = List.of("물품", "용역", "공사");
    /** 견적 받는 중 */
    public static final String QUOTING = "QUOTING";
    /** 업체를 골랐다 (계약·납품 진행) */
    public static final String SELECTED = "SELECTED";
    /** 검수(검사)에 합격했다 */
    public static final String INSPECTED = "INSPECTED";
    public static final String CANCELED = "CANCELED";

    private Long purchaseId;
    private String purNo;
    private int purYear;
    private String title;
    private String purType;
    private String deptName;
    private Long budgetId;
    private long estimatedAmount;
    private String status;
    private Long selectedQuoteId;
    private String selectReason;
    private String note;
    private String createdBy;
    private String createdName;
    private LocalDateTime createdAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private String vendor;
    private Long selectedAmount;
    private Integer quoteCount;

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case QUOTING -> "견적 받는 중";
            case SELECTED -> "업체 선정 · 납품 대기";
            case INSPECTED -> "검수 합격";
            case CANCELED -> "취소";
            default -> "";
        };
    }

    public boolean isOpen() { return QUOTING.equals(status) || SELECTED.equals(status); }

    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public String getPurNo() { return purNo; }
    public void setPurNo(String purNo) { this.purNo = purNo; }
    public int getPurYear() { return purYear; }
    public void setPurYear(int purYear) { this.purYear = purYear; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPurType() { return purType; }
    public void setPurType(String purType) { this.purType = purType; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public long getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(long estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSelectedQuoteId() { return selectedQuoteId; }
    public void setSelectedQuoteId(Long selectedQuoteId) { this.selectedQuoteId = selectedQuoteId; }
    public String getSelectReason() { return selectReason; }
    public void setSelectReason(String selectReason) { this.selectReason = selectReason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedName() { return createdName; }
    public void setCreatedName(String createdName) { this.createdName = createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public Long getSelectedAmount() { return selectedAmount; }
    public void setSelectedAmount(Long selectedAmount) { this.selectedAmount = selectedAmount; }
    public Integer getQuoteCount() { return quoteCount; }
    public void setQuoteCount(Integer quoteCount) { this.quoteCount = quoteCount; }
}
