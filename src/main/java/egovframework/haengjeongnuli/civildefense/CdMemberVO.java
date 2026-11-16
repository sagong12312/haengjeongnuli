package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 민방위 대원 (주민등록번호는 받지 않고 이름·생년월일로 구분) */
public class CdMemberVO {

    public static final String ACTIVE = "ACTIVE";
    public static final String RELEASED = "RELEASED";
    public static final List<String> RELEASE_REASONS = List.of("연령 초과", "전출", "편성 제외 대상", "사망", "기타");

    private Long memberId;
    private String name;
    private LocalDate birthDate;
    private String address;
    private String phone;
    private String unitName;
    private LocalDate joinedOn;
    private String status;
    private LocalDate releasedOn;
    private String releaseReason;
    private LocalDate purgeAfter;
    private String createdBy;
    private LocalDateTime createdAt;

    /** 목록용: 이름 가운데 가림 */
    public String getMaskedName() {
        if (name == null || name.length() < 2) {
            return "*";
        }
        return name.length() == 2 ? name.charAt(0) + "*" : name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public boolean isActive() { return ACTIVE.equals(status); }
    public int getAgeThisYear() { return LocalDate.now().getYear() - birthDate.getYear(); }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public LocalDate getJoinedOn() { return joinedOn; }
    public void setJoinedOn(LocalDate joinedOn) { this.joinedOn = joinedOn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getReleasedOn() { return releasedOn; }
    public void setReleasedOn(LocalDate releasedOn) { this.releasedOn = releasedOn; }
    public String getReleaseReason() { return releaseReason; }
    public void setReleaseReason(String releaseReason) { this.releaseReason = releaseReason; }
    public LocalDate getPurgeAfter() { return purgeAfter; }
    public void setPurgeAfter(LocalDate purgeAfter) { this.purgeAfter = purgeAfter; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
