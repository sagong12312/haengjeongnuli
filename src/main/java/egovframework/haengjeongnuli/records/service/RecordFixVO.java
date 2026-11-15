package egovframework.haengjeongnuli.records.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 등록정보 수정 요청·이력 (공공기록물법 시행규칙 제6조: 기록물관리책임자 확인, 수정일자·내용·사유 관리) */
public class RecordFixVO {

    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** 등록 취소 요청 (직접 등록한 기록물만) */
    public static final String CANCEL = "canceled";

    /** 고칠 수 있는 칸 (등록번호·처리과·구분·등록일자는 고치지 않는다) */
    public static final Map<String, String> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("title", "제목");
        FIELDS.put("counterpart", "발신·수신 기관");
        FIELDS.put("counterpartNo", "상대 문서번호");
        FIELDS.put("medium", "형태");
        FIELDS.put("pages", "쪽수");
        FIELDS.put("retention", "보존기간");
        FIELDS.put("disclosure", "공개 여부");
        FIELDS.put("note", "비고");
        FIELDS.put(CANCEL, "등록 취소");
    }

    private Long fixId;
    private Long regId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String reason;
    private String status;
    private String requestedBy;
    private String requestedName;
    private LocalDateTime requestedAt;
    private String confirmedBy;
    private String confirmedName;
    private LocalDateTime confirmedAt;
    /** 목록 화면용: 등록번호·제목 */
    private String regNo;
    private String title;

    public String getFieldLabel() { return FIELDS.getOrDefault(fieldName, fieldName); }

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case REQUESTED -> "확인 대기";
            case APPROVED -> "반영";
            case REJECTED -> "반려";
            default -> "";
        };
    }

    public Long getFixId() { return fixId; }
    public void setFixId(Long fixId) { this.fixId = fixId; }
    public Long getRegId() { return regId; }
    public void setRegId(Long regId) { this.regId = regId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestedName() { return requestedName; }
    public void setRequestedName(String requestedName) { this.requestedName = requestedName; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getConfirmedName() { return confirmedName; }
    public void setConfirmedName(String confirmedName) { this.confirmedName = confirmedName; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getRegNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
