package egovframework.haengjeongnuli.property;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 공유재산 대장 한 줄 (공유재산 및 물품 관리법: 행정재산·일반재산) */
public class PropertyVO {

    public static final List<String> KINDS = List.of("토지", "건물", "공작물", "기타");
    /** 행정재산은 사용허가(제20조), 일반재산은 대부 */
    public static final List<String> CLASSES = List.of("행정재산", "일반재산");
    public static final String HELD = "HELD";
    public static final String DISPOSED = "DISPOSED";

    private Long propId;
    private String propNo;
    private String propKind;
    private String propClass;
    private String name;
    private String location;
    private BigDecimal area;
    private long appraised;
    private LocalDate acquiredOn;
    private String deptName;
    private String usePurpose;
    private String status;
    private LocalDate disposedOn;
    private String disposeReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private Integer activeUses;
    private LocalDate nextEnd;

    public boolean isHeld() { return HELD.equals(status); }
    /** 이 재산을 쓰게 하는 방식 */
    public String getUseType() { return "행정재산".equals(propClass) ? "사용허가" : "대부"; }
    /** 연간 사용료·대부료 하한 참고값: 평정가격의 1천분의 10 (시행령 제14조, 조례로 정함) */
    public long getMinAnnualFee() { return appraised / 100; }

    public Long getPropId() { return propId; }
    public void setPropId(Long propId) { this.propId = propId; }
    public String getPropNo() { return propNo; }
    public void setPropNo(String propNo) { this.propNo = propNo; }
    public String getPropKind() { return propKind; }
    public void setPropKind(String propKind) { this.propKind = propKind; }
    public String getPropClass() { return propClass; }
    public void setPropClass(String propClass) { this.propClass = propClass; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal area) { this.area = area; }
    public long getAppraised() { return appraised; }
    public void setAppraised(long appraised) { this.appraised = appraised; }
    public LocalDate getAcquiredOn() { return acquiredOn; }
    public void setAcquiredOn(LocalDate acquiredOn) { this.acquiredOn = acquiredOn; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getUsePurpose() { return usePurpose; }
    public void setUsePurpose(String usePurpose) { this.usePurpose = usePurpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDisposedOn() { return disposedOn; }
    public void setDisposedOn(LocalDate disposedOn) { this.disposedOn = disposedOn; }
    public String getDisposeReason() { return disposeReason; }
    public void setDisposeReason(String disposeReason) { this.disposeReason = disposeReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getActiveUses() { return activeUses; }
    public void setActiveUses(Integer activeUses) { this.activeUses = activeUses; }
    public LocalDate getNextEnd() { return nextEnd; }
    public void setNextEnd(LocalDate nextEnd) { this.nextEnd = nextEnd; }
}
