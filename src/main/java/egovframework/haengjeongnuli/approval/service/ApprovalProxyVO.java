package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 대결자 지정: 결재권자(owner)가 휴가·출장 등으로 결재할 수 없는 기간에 직무를 대리하는 사람(proxy) */
public class ApprovalProxyVO {

    public static final java.util.List<String> REASONS = java.util.List.of("휴가", "출장", "교육", "그 밖의 사유");

    private Long proxyId;
    private String ownerId;
    private String ownerName;
    private String proxyLoginId;
    private String proxyName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LocalDateTime createdAt;
    private String canceledYn;

    public boolean isCanceled() { return "Y".equals(canceledYn); }

    public boolean isActiveOn(LocalDate day) {
        return !isCanceled() && !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public Long getProxyId() { return proxyId; }
    public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getProxyLoginId() { return proxyLoginId; }
    public void setProxyLoginId(String proxyLoginId) { this.proxyLoginId = proxyLoginId; }
    public String getProxyName() { return proxyName; }
    public void setProxyName(String proxyName) { this.proxyName = proxyName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCanceledYn() { return canceledYn; }
    public void setCanceledYn(String canceledYn) { this.canceledYn = canceledYn; }
}
