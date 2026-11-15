package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDateTime;

/** 지출품의(지출원인행위)로 묶은 예산: 품의 금액 안에서 지출결의로 나눠 집행한다 */
public class CommitVO {
    private Long commitId;
    private Long docId;
    private Long budgetId;
    private String deptName;
    private String spendType;
    private String title;
    private long amount;
    private long spent;
    private String closedYn;
    private String closedBy;
    private LocalDateTime closedAt;
    private String closeNote;
    private LocalDateTime createdAt;

    /** 아직 지출하지 않은 금액 (잔액 해제했으면 0) */
    public long getRemaining() { return isClosed() ? 0 : amount - spent; }
    public boolean isClosed() { return "Y".equals(closedYn); }

    public Long getCommitId() { return commitId; }
    public void setCommitId(Long commitId) { this.commitId = commitId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getSpendType() { return spendType; }
    public void setSpendType(String spendType) { this.spendType = spendType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public long getSpent() { return spent; }
    public void setSpent(long spent) { this.spent = spent; }
    public String getClosedYn() { return closedYn; }
    public void setClosedYn(String closedYn) { this.closedYn = closedYn; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
