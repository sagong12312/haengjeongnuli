package egovframework.haengjeongnuli.cmm.secapp;

import java.time.LocalDateTime;

/** 업무용 PC에 설치해야 할 보안 프로그램 한 가지 */
public class SecurityAppVO {

    private Long appId;
    private String name;
    private String purpose;
    private String guide;
    private String url;
    private String requiredYn;
    /** 올리면 직원은 다시 확인해야 한다 (새 판, 재설치 요청) */
    private int version;
    private int sortNo;
    private String useYn;
    private String updatedBy;
    private LocalDateTime updatedAt;
    /** 목록에서: 내가 지금 판을 확인한 때 (없으면 null) */
    private LocalDateTime ackedAt;
    /** 관리자 목록에서: 지금 판을 확인한 사용 중 직원 수 */
    private Integer ackCount;

    public boolean isRequired() { return "Y".equals(requiredYn); }
    public boolean isInUse() { return "Y".equals(useYn); }
    public boolean isAcked() { return ackedAt != null; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getGuide() { return guide; }
    public void setGuide(String guide) { this.guide = guide; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getRequiredYn() { return requiredYn; }
    public void setRequiredYn(String requiredYn) { this.requiredYn = requiredYn; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public int getSortNo() { return sortNo; }
    public void setSortNo(int sortNo) { this.sortNo = sortNo; }
    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(LocalDateTime ackedAt) { this.ackedAt = ackedAt; }
    public Integer getAckCount() { return ackCount; }
    public void setAckCount(Integer ackCount) { this.ackCount = ackCount; }
}
