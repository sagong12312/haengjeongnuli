package egovframework.haengjeongnuli.hygiene.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 위생 영업소: 식품 영업(식품위생법)과 공중위생영업(공중위생관리법) 신고 수리 */
public class FoodBusinessVO {
    /** 영업신고를 해야 하는 식품 업종 (식품위생법 시행령 제25조 제1항) */
    public static final List<String> FOOD_TYPES = List.of("휴게음식점영업", "일반음식점영업", "위탁급식영업", "제과점영업",
            "즉석판매제조·가공업", "식품운반업", "식품소분·판매업", "식품냉동·냉장업", "용기·포장류제조업");
    /** 공중위생영업 (공중위생관리법 제2조: 숙박·목욕장·이용·미용·세탁·건물위생관리업) */
    public static final List<String> PUBLIC_TYPES = List.of("숙박업", "목욕장업", "이용업", "미용업", "세탁업", "건물위생관리업");
    public static final List<String> TYPES = java.util.stream.Stream.concat(FOOD_TYPES.stream(), PUBLIC_TYPES.stream()).toList();

    /** 업종의 근거 법률 */
    public static String lawOf(String type) {
        return type != null && PUBLIC_TYPES.contains(type) ? "공중위생관리법" : "식품위생법";
    }

    public String getLawName() { return lawOf(bizType); }

    private Long bizId;
    private String bizName;
    private String ownerName;
    private String address;
    private String bizType;
    private LocalDate reportedOn;
    private String receiptNo;
    /** OPEN / CLOSED */
    private String status;
    private LocalDate closedOn;
    private String createdBy;
    private LocalDateTime createdAt;
    // 최근 점검 (조회용)
    private LocalDate lastCheckedOn;
    private String lastResult;
    private int checkCount;

    public boolean isOpen() { return "OPEN".equals(status); }

    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getBizName() { return bizName; }
    public void setBizName(String bizName) { this.bizName = bizName; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public LocalDate getReportedOn() { return reportedOn; }
    public void setReportedOn(LocalDate reportedOn) { this.reportedOn = reportedOn; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getClosedOn() { return closedOn; }
    public void setClosedOn(LocalDate closedOn) { this.closedOn = closedOn; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDate getLastCheckedOn() { return lastCheckedOn; }
    public void setLastCheckedOn(LocalDate lastCheckedOn) { this.lastCheckedOn = lastCheckedOn; }
    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }
    public int getCheckCount() { return checkCount; }
    public void setCheckCount(int checkCount) { this.checkCount = checkCount; }
}
