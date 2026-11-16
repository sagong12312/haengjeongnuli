package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 물품대장 한 줄: 취득한 물품의 위치·사용자·상태와 재물조사·처분 기록 */
public class AssetVO {

    /** 쓰는 중 */
    public static final String USE = "USE";
    /** 불용 (쓰지 않음, 처분 대기) */
    public static final String IDLE = "IDLE";
    public static final String DISPOSED = "DISPOSED";

    private Long assetId;
    private String assetNo;
    private String name;
    private String spec;
    private int quantity;
    private long unitPrice;
    private LocalDate acquiredDate;
    private Long purchaseId;
    private String deptName;
    private String location;
    private String userName;
    private String status;
    private LocalDate lastCheckedAt;
    private String lastCheckedBy;
    private LocalDate disposedAt;
    private String disposeReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private String purNo;

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case USE -> "사용";
            case IDLE -> "불용";
            case DISPOSED -> "처분";
            default -> "";
        };
    }

    public long getTotal() { return unitPrice * quantity; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getAssetNo() { return assetNo; }
    public void setAssetNo(String assetNo) { this.assetNo = assetNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
    public LocalDate getAcquiredDate() { return acquiredDate; }
    public void setAcquiredDate(LocalDate acquiredDate) { this.acquiredDate = acquiredDate; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDate lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getLastCheckedBy() { return lastCheckedBy; }
    public void setLastCheckedBy(String lastCheckedBy) { this.lastCheckedBy = lastCheckedBy; }
    public LocalDate getDisposedAt() { return disposedAt; }
    public void setDisposedAt(LocalDate disposedAt) { this.disposedAt = disposedAt; }
    public String getDisposeReason() { return disposeReason; }
    public void setDisposeReason(String disposeReason) { this.disposeReason = disposeReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPurNo() { return purNo; }
    public void setPurNo(String purNo) { this.purNo = purNo; }
}
