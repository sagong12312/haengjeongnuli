package egovframework.haengjeongnuli.property;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 행정재산 사용허가 또는 일반재산 대부 한 건 */
public class PropertyUseVO {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    private Long useId;
    private Long propId;
    private String useType;
    private String userName;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private long annualFee;
    private String feeNote;
    private String status;
    private String endNote;
    private String createdBy;
    private LocalDateTime createdAt;
    private String propNo;
    private String propName;
    private String deptName;

    public boolean isActive() { return ACTIVE.equals(status); }
    /** 끝나는 날까지 남은 날 */
    public long getDaysLeft() { return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate); }

    public Long getUseId() { return useId; }
    public void setUseId(Long useId) { this.useId = useId; }
    public Long getPropId() { return propId; }
    public void setPropId(Long propId) { this.propId = propId; }
    public String getUseType() { return useType; }
    public void setUseType(String useType) { this.useType = useType; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public long getAnnualFee() { return annualFee; }
    public void setAnnualFee(long annualFee) { this.annualFee = annualFee; }
    public String getFeeNote() { return feeNote; }
    public void setFeeNote(String feeNote) { this.feeNote = feeNote; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEndNote() { return endNote; }
    public void setEndNote(String endNote) { this.endNote = endNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPropNo() { return propNo; }
    public void setPropNo(String propNo) { this.propNo = propNo; }
    public String getPropName() { return propName; }
    public void setPropName(String propName) { this.propName = propName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
