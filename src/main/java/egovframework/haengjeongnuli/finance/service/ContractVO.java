package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 계약대장 한 줄 (지방자치단체를 당사자로 하는 계약에 관한 법률) */
public class ContractVO {

    public static final List<String> KINDS = List.of("공사", "물품", "용역");
    public static final List<String> METHODS = List.of("일반경쟁", "제한경쟁", "지명경쟁", "수의계약");
    public static final String ACTIVE = "ACTIVE";
    public static final String DONE = "DONE";
    public static final String TERMINATED = "TERMINATED";

    private Long contractId;
    private String contractNo;
    private int contractYear;
    private String title;
    private String contractKind;
    private String method;
    private String privateReason;
    private String party;
    private String partyBizNo;
    private long amount;
    private Long deposit;
    private LocalDate contractDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long budgetId;
    private String status;
    private String statusNote;
    private String deptName;
    private String createdBy;
    private String createdName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 이 계약의 구매 건 (견적·검수 기록) */
    private Long purchaseId;
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case ACTIVE -> "이행 중";
            case DONE -> "완료";
            case TERMINATED -> "해제·해지";
            default -> "";
        };
    }

    /** 끝나는 날이 지났는데 아직 이행 중인지 */
    public boolean isOverdue() {
        return ACTIVE.equals(status) && endDate != null && endDate.isBefore(LocalDate.now());
    }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public int getContractYear() { return contractYear; }
    public void setContractYear(int contractYear) { this.contractYear = contractYear; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContractKind() { return contractKind; }
    public void setContractKind(String contractKind) { this.contractKind = contractKind; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPrivateReason() { return privateReason; }
    public void setPrivateReason(String privateReason) { this.privateReason = privateReason; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public String getPartyBizNo() { return partyBizNo; }
    public void setPartyBizNo(String partyBizNo) { this.partyBizNo = partyBizNo; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public Long getDeposit() { return deposit; }
    public void setDeposit(Long deposit) { this.deposit = deposit; }
    public LocalDate getContractDate() { return contractDate; }
    public void setContractDate(LocalDate contractDate) { this.contractDate = contractDate; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedName() { return createdName; }
    public void setCreatedName(String createdName) { this.createdName = createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
