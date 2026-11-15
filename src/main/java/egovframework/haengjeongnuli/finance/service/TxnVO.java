package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 집행(세출 지출) 또는 수납(세입) 한 건 */
public class TxnVO {
    private Long txnId;
    private String kind;
    private int fiscalYear;
    private Long budgetId;
    private String code;
    private String codeName;
    private LocalDate txnDate;
    private long amount;
    private String party;
    private String description;
    private String deptName;
    private String createdBy;
    private String createdName;
    private LocalDateTime createdAt;
    private String cancelledYn = "N";
    private String cancelledBy;
    private LocalDateTime cancelledAt;

    public boolean isCancelled() { return "Y".equals(cancelledYn); }

    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }
    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedName() { return createdName; }
    public void setCreatedName(String createdName) { this.createdName = createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCancelledYn() { return cancelledYn; }
    public void setCancelledYn(String cancelledYn) { this.cancelledYn = cancelledYn; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
}
