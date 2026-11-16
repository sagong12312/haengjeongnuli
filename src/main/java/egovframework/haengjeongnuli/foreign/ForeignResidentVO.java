package egovframework.haengjeongnuli.foreign;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 외국인주민 지원 대장 (외국인등록번호는 받지 않는다, 수집 동의일 필수) */
public class ForeignResidentVO {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    private Long resId;
    private String name;
    private String nationality;
    private LocalDate birthDate;
    private String address;
    private String phone;
    private String language;
    private String stayStatus;
    private LocalDate consentOn;
    private String status;
    private LocalDate endedOn;
    private String endReason;
    private LocalDate purgeAfter;
    private String createdBy;
    private LocalDateTime createdAt;
    private Integer supportCount;

    /** 목록용: 이름 가운데 가림 */
    public String getMaskedName() {
        if (name == null || name.length() < 2) {
            return "*";
        }
        return name.length() == 2 ? name.charAt(0) + "*" : name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public Long getResId() { return resId; }
    public void setResId(Long resId) { this.resId = resId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getStayStatus() { return stayStatus; }
    public void setStayStatus(String stayStatus) { this.stayStatus = stayStatus; }
    public LocalDate getConsentOn() { return consentOn; }
    public void setConsentOn(LocalDate consentOn) { this.consentOn = consentOn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEndedOn() { return endedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public LocalDate getPurgeAfter() { return purgeAfter; }
    public void setPurgeAfter(LocalDate purgeAfter) { this.purgeAfter = purgeAfter; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getSupportCount() { return supportCount; }
    public void setSupportCount(Integer supportCount) { this.supportCount = supportCount; }
}
