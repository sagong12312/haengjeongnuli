package egovframework.haengjeongnuli.building.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 건축 인허가 대장 한 건 */
public class BuildingCaseVO {
    public static final String PERMIT = "PERMIT";
    public static final String REPORT = "REPORT";

    private Long caseId;
    private String kind;
    private String receiptNo;
    private String ownerName;
    private String site;
    private String buildingUse;
    private Double floorArea;
    private Integer floors;
    private String factoryYn = "N";
    private LocalDate decidedOn;
    private LocalDate startDeadline;
    private String extendedYn = "N";
    private LocalDate startedOn;
    /** VALID(유효) / STARTED(착공) / CANCELLED(취소·효력 상실 처리) */
    private String status;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;

    public String getKindLabel() { return PERMIT.equals(kind) ? "건축허가" : "건축신고"; }

    /** 착수기한이 지났는데 착공하지 않았는지 (허가는 취소 대상, 신고는 효력 상실) */
    public boolean isOverdue() {
        return "VALID".equals(status) && startDeadline != null && LocalDate.now().isAfter(startDeadline);
    }

    /** 착수기한이 30일 안으로 남았는지 */
    public boolean isDeadlineNear() {
        return "VALID".equals(status) && startDeadline != null && !isOverdue()
                && !LocalDate.now().plusDays(30).isBefore(startDeadline);
    }

    public String getStatusLabel() {
        if (isOverdue()) {
            return PERMIT.equals(kind) ? "착수기한 지남 (허가 취소 대상)" : "착수기한 지남 (신고 효력 상실)";
        }
        return switch (status) {
            case "STARTED" -> "착공";
            case "CANCELLED" -> PERMIT.equals(kind) ? "허가 취소" : "효력 상실";
            default -> "착공 전";
        };
    }

    public String getStatusBadge() {
        if (isOverdue()) return "badge-red";
        if (isDeadlineNear()) return "badge-orange";
        return switch (status) {
            case "STARTED" -> "badge-green";
            case "CANCELLED" -> "badge-gray";
            default -> "badge-blue";
        };
    }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getBuildingUse() { return buildingUse; }
    public void setBuildingUse(String buildingUse) { this.buildingUse = buildingUse; }
    public Double getFloorArea() { return floorArea; }
    public void setFloorArea(Double floorArea) { this.floorArea = floorArea; }
    public Integer getFloors() { return floors; }
    public void setFloors(Integer floors) { this.floors = floors; }
    public String getFactoryYn() { return factoryYn; }
    public void setFactoryYn(String factoryYn) { this.factoryYn = factoryYn; }
    public LocalDate getDecidedOn() { return decidedOn; }
    public void setDecidedOn(LocalDate decidedOn) { this.decidedOn = decidedOn; }
    public LocalDate getStartDeadline() { return startDeadline; }
    public void setStartDeadline(LocalDate startDeadline) { this.startDeadline = startDeadline; }
    public String getExtendedYn() { return extendedYn; }
    public void setExtendedYn(String extendedYn) { this.extendedYn = extendedYn; }
    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
