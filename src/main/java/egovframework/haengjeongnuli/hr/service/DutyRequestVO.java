package egovframework.haengjeongnuli.hr.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 복무 신청 한 건. 결재 상태는 연결된 전자결재 문서(DOC_STATUS)를 따른다 */
public class DutyRequestVO {
    private Long reqId;
    private String loginId;
    private String userName;
    private String deptName;
    private DutyKind kind;
    private LocalDate startDate;
    private LocalDate endDate;
    private String halfDay;
    private String startTime;
    private String endTime;
    private Double days;
    private Double hours;
    private String destination;
    private String reason;
    private Long docId;
    private LocalDateTime createdAt;
    private String docStatus;
    /** 출장: IN(관내)·OUT(관외), 하루 여행시간, 공용차량, 숙박, 어림 여비, 정산 금액 */
    private String tripArea;
    private Double tripHours;
    private String officialCar;
    private Integer lodgingNights;
    private String lodgingRegion;
    private Long estAmount;
    private Long settledAmount;

    public String getStatusLabel() {
        if (docStatus == null) {
            return "—";
        }
        return switch (docStatus) {
            case "PROG" -> "결재 중";
            case "DONE" -> "승인";
            case "REJECTED" -> "반려";
            case "DRAFT" -> "회수";
            default -> docStatus;
        };
    }

    public String getStatusBadge() {
        if (docStatus == null) {
            return "badge-gray";
        }
        return switch (docStatus) {
            case "PROG" -> "badge-orange";
            case "DONE" -> "badge-green";
            case "REJECTED" -> "badge-red";
            default -> "badge-gray";
        };
    }

    /** 화면에 보여 줄 기간 */
    public String getPeriodText() {
        if (kind == DutyKind.OVERTIME) {
            return startDate + " " + startTime + "~" + endTime + " (" + fmt(hours) + "시간)";
        }
        String range = startDate.equals(endDate) ? startDate.toString() : startDate + " ~ " + endDate;
        if (halfDay != null) {
            range += "AM".equals(halfDay) ? " 오전" : " 오후";
        }
        return range + " (" + fmt(days) + "일)";
    }

    private static String fmt(Double d) {
        if (d == null) {
            return "0";
        }
        return d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }

    public Long getReqId() { return reqId; }
    public void setReqId(Long reqId) { this.reqId = reqId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public DutyKind getKind() { return kind; }
    public void setKind(DutyKind kind) { this.kind = kind; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getHalfDay() { return halfDay; }
    public void setHalfDay(String halfDay) { this.halfDay = halfDay; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Double getDays() { return days; }
    public void setDays(Double days) { this.days = days; }
    public Double getHours() { return hours; }
    public void setHours(Double hours) { this.hours = hours; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDocStatus() { return docStatus; }
    public void setDocStatus(String docStatus) { this.docStatus = docStatus; }

    public String getTripArea() { return tripArea; }
    public void setTripArea(String tripArea) { this.tripArea = tripArea; }
    public Double getTripHours() { return tripHours; }
    public void setTripHours(Double tripHours) { this.tripHours = tripHours; }
    public String getOfficialCar() { return officialCar; }
    public void setOfficialCar(String officialCar) { this.officialCar = officialCar; }
    public Integer getLodgingNights() { return lodgingNights; }
    public void setLodgingNights(Integer lodgingNights) { this.lodgingNights = lodgingNights; }
    public String getLodgingRegion() { return lodgingRegion; }
    public void setLodgingRegion(String lodgingRegion) { this.lodgingRegion = lodgingRegion; }
    public Long getEstAmount() { return estAmount; }
    public void setEstAmount(Long estAmount) { this.estAmount = estAmount; }
    public Long getSettledAmount() { return settledAmount; }
    public void setSettledAmount(Long settledAmount) { this.settledAmount = settledAmount; }

    public String getTripAreaLabel() {
        return "IN".equals(tripArea) ? "관내" : "OUT".equals(tripArea) ? "관외" : "—";
    }
}
