package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 검수(검사) 기록: 납품·완료 신고를 받고 검사한 결과 */
public class InspectionVO {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    private Long inspId;
    private Long purchaseId;
    private LocalDate notifiedDate;
    private LocalDate inspectedDate;
    private String result;
    private String note;
    private String inspectorId;
    private String inspectorName;
    private LocalDateTime createdAt;

    public boolean isPassed() { return PASS.equals(result); }

    /** 완료 신고(납품)일부터 검사한 날까지 걸린 날 */
    public long getDays() {
        return notifiedDate == null || inspectedDate == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(notifiedDate, inspectedDate);
    }

    public Long getInspId() { return inspId; }
    public void setInspId(Long inspId) { this.inspId = inspId; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public LocalDate getNotifiedDate() { return notifiedDate; }
    public void setNotifiedDate(LocalDate notifiedDate) { this.notifiedDate = notifiedDate; }
    public LocalDate getInspectedDate() { return inspectedDate; }
    public void setInspectedDate(LocalDate inspectedDate) { this.inspectedDate = inspectedDate; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getInspectorId() { return inspectorId; }
    public void setInspectorId(String inspectorId) { this.inspectorId = inspectorId; }
    public String getInspectorName() { return inspectorName; }
    public void setInspectorName(String inspectorName) { this.inspectorName = inspectorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
