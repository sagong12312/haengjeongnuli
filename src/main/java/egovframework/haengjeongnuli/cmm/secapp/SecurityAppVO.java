package egovframework.haengjeongnuli.cmm.secapp;

import java.time.LocalDateTime;

/** 업무용 PC에 설치해야 할 보안 프로그램 한 가지 */
public class SecurityAppVO {

    private Long appId;
    private String name;
    private String purpose;
    private String guide;
    private String url;
    /** Y: 강제 (설치하지 않으면 쓰지 못함), N: 권고 */
    private String requiredYn;
    /** STAFF: 직원 업무용 PC(행정누리), CITIZEN: 민원인 PC(전자민원), BOTH: 둘 다 */
    private String target = "STAFF";
    /** 설치 확인 주소: 프로그램이 이 PC 에 띄우는 확인용 주소 (http(s)://127.0.0.1 또는 localhost 만) */
    private String checkUrl;
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
    public String getLevelLabel() { return isRequired() ? "강제" : "권고"; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getTargetLabel() { return "CITIZEN".equals(target) ? "민원인 PC (전자민원)" : "BOTH".equals(target) ? "직원·민원인 모두" : "직원 PC (행정누리)"; }
    public boolean isForStaff() { return !"CITIZEN".equals(target); }
    public boolean isForCitizen() { return "CITIZEN".equals(target) || "BOTH".equals(target); }
    public String getCheckUrl() { return checkUrl; }
    public void setCheckUrl(String checkUrl) { this.checkUrl = checkUrl; }
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
