package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;
import java.util.List;

/** 시행문 발송 기록 (수신자별) */
public class DispatchVO {

    public static final List<String> METHODS = List.of("문서유통(전자)", "전자우편", "팩스", "우편", "인편", "기타");

    private Long dispatchId;
    private Long docId;
    private String receiver;
    private String method;
    private String note;
    private String dispatchedBy;
    private String dispatchedName;
    private LocalDateTime dispatchedAt;

    public Long getDispatchId() { return dispatchId; }
    public void setDispatchId(Long dispatchId) { this.dispatchId = dispatchId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String dispatchedBy) { this.dispatchedBy = dispatchedBy; }
    public String getDispatchedName() { return dispatchedName; }
    public void setDispatchedName(String dispatchedName) { this.dispatchedName = dispatchedName; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
}
