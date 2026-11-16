package egovframework.haengjeongnuli.hygiene.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 위생 행정처분 한 건.
 * 영업정지·일부 시설 사용중지·영업소 폐쇄·허가(등록) 취소: 식품위생법 제75조, 공중위생관리법 제11조 (정지는 6개월 이내)
 * 과징금: 식품위생법 제82조, 공중위생관리법 제11조의2 (영업정지를 갈음)
 * 과태료·시정명령도 같은 대장에 남긴다.
 */
public class SanctionVO {

    public static final List<String> KINDS = List.of("시정명령", "영업정지", "일부 시설 사용중지", "영업소 폐쇄", "허가·등록 취소",
            "과징금", "과태료");
    /** 기간이 있는 처분 */
    public static final List<String> PERIOD_KINDS = List.of("영업정지", "일부 시설 사용중지");
    /** 금액이 있는 처분 */
    public static final List<String> AMOUNT_KINDS = List.of("과징금", "과태료");
    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELED = "CANCELED";

    private Long sanctionId;
    private String sanctionNo;
    private Long bizId;
    private String lawName;
    private String kind;
    private String violation;
    private String basis;
    private LocalDate decidedOn;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long amount;
    private Long checkId;
    private String publicYn;
    private String status;
    private String cancelReason;
    private String createdBy;
    private String createdName;
    private LocalDateTime createdAt;
    private String bizName;
    private String bizType;
    private String address;
    private String ownerName;

    public boolean isPeriod() { return PERIOD_KINDS.contains(kind); }
    public boolean isMoney() { return AMOUNT_KINDS.contains(kind); }
    public boolean isCanceled() { return CANCELED.equals(status); }
    public boolean isPublic() { return "Y".equals(publicYn); }

    /** 오늘 영업정지(사용중지) 기간 안인지 */
    public boolean isInForce() {
        LocalDate t = LocalDate.now();
        return !isCanceled() && isPeriod() && startDate != null && endDate != null && !t.isBefore(startDate) && !t.isAfter(endDate);
    }

    /** 화면·서식용 처분 내용 한 줄 */
    public String getSummary() {
        StringBuilder b = new StringBuilder(kind);
        if (isPeriod() && startDate != null) {
            b.append(" ").append(startDate).append(" ~ ").append(endDate);
        }
        if (isMoney() && amount != null) {
            b.append(" ").append(String.format("%,d원", amount));
        }
        return b.toString();
    }

    public Long getSanctionId() { return sanctionId; }
    public void setSanctionId(Long sanctionId) { this.sanctionId = sanctionId; }
    public String getSanctionNo() { return sanctionNo; }
    public void setSanctionNo(String sanctionNo) { this.sanctionNo = sanctionNo; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getLawName() { return lawName; }
    public void setLawName(String lawName) { this.lawName = lawName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getViolation() { return violation; }
    public void setViolation(String violation) { this.violation = violation; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public LocalDate getDecidedOn() { return decidedOn; }
    public void setDecidedOn(LocalDate decidedOn) { this.decidedOn = decidedOn; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public String getPublicYn() { return publicYn; }
    public void setPublicYn(String publicYn) { this.publicYn = publicYn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedName() { return createdName; }
    public void setCreatedName(String createdName) { this.createdName = createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getBizName() { return bizName; }
    public void setBizName(String bizName) { this.bizName = bizName; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
}
