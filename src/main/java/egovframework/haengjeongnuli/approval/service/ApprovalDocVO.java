package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 전자결재 문서 */
public class ApprovalDocVO {

    public static final String DRAFT = "DRAFT";
    public static final String PROG = "PROG";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";

    private Long docId;
    private String docNo;
    private Integer docYear;
    private Integer docSeq;
    private String kind;
    private String retention;
    private String disclosure;
    private String restrictReasons;
    private String restrictDetail;
    private String receiverType;
    private String receiver;
    private String via;
    private String referenceTo;
    private String sender;
    private LocalDate effectiveDate;
    private String title;
    private String body;
    private String status;
    private String drafterId;
    private String drafterName;
    private String drafterPosition;
    private String drafterDept;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private String receivedBy;
    private LocalDateTime receivedAt;

    private List<ApprovalLineVO> lines = new ArrayList<>();
    private List<ApprovalFileVO> files = new ArrayList<>();

    public String getStatusLabel() {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case DRAFT -> "임시저장";
            case PROG -> "진행 중";
            case DONE -> "완료";
            case REJECTED -> "반려";
            default -> status;
        };
    }

    public String getStatusBadge() {
        if (status == null) {
            return "badge-gray";
        }
        return switch (status) {
            case PROG -> "badge-blue";
            case DONE -> "badge-green";
            case REJECTED -> "badge-red";
            default -> "badge-gray";
        };
    }

    public boolean isExternal() {
        return "external".equals(receiverType);
    }

    public boolean isDraftedBy(LoginUser u) {
        return u != null && u.getLoginId().equals(drafterId);
    }

    /** 지금 결재할 차례인 결재선 칸 */
    public ApprovalLineVO getPendingLine() {
        return lines.stream().filter(l -> ApprovalLineVO.PENDING.equals(l.getStatus())).findFirst().orElse(null);
    }

    public boolean isMyTurn(LoginUser u) {
        ApprovalLineVO p = getPendingLine();
        return PROG.equals(status) && p != null && u != null && u.getLoginId().equals(p.getApproverId());
    }

    /** 첫 결재자가 아직 처리하지 않았으면 기안자가 회수할 수 있다 */
    public boolean canWithdraw(LoginUser u) {
        return PROG.equals(status) && isDraftedBy(u)
                && lines.stream().noneMatch(l -> l.getActedAt() != null);
    }

    public boolean isOnLine(LoginUser u) {
        return u != null && lines.stream().anyMatch(l -> u.getLoginId().equals(l.getApproverId()));
    }

    /** 수신 부서 직원인지 (결재가 끝난 '수신자 지정' 문서만) */
    public boolean isReceiverDept(LoginUser u) {
        return DONE.equals(status) && isExternal() && u != null && receiver != null
                && !u.dept().isEmpty() && receiver.trim().equals(u.dept());
    }

    /** 읽을 수 있는 사람: 기안자, 결재선, 관리자(임시저장 제외), 수신 부서 */
    public boolean canView(LoginUser u) {
        if (u == null) {
            return false;
        }
        if (DRAFT.equals(status)) {
            return isDraftedBy(u);
        }
        return isDraftedBy(u) || isOnLine(u) || u.isAdmin() || isReceiverDept(u);
    }

    public List<String> getRestrictReasonList() {
        if (restrictReasons == null || restrictReasons.isBlank()) {
            return List.of();
        }
        return Arrays.stream(restrictReasons.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public Integer getDocYear() { return docYear; }
    public void setDocYear(Integer docYear) { this.docYear = docYear; }
    public Integer getDocSeq() { return docSeq; }
    public void setDocSeq(Integer docSeq) { this.docSeq = docSeq; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getRetention() { return retention; }
    public void setRetention(String retention) { this.retention = retention; }
    public String getDisclosure() { return disclosure; }
    public void setDisclosure(String disclosure) { this.disclosure = disclosure; }
    public String getRestrictReasons() { return restrictReasons; }
    public void setRestrictReasons(String restrictReasons) { this.restrictReasons = restrictReasons; }
    public String getRestrictDetail() { return restrictDetail; }
    public void setRestrictDetail(String restrictDetail) { this.restrictDetail = restrictDetail; }
    public String getReceiverType() { return receiverType; }
    public void setReceiverType(String receiverType) { this.receiverType = receiverType; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }
    public String getReferenceTo() { return referenceTo; }
    public void setReferenceTo(String referenceTo) { this.referenceTo = referenceTo; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDrafterId() { return drafterId; }
    public void setDrafterId(String drafterId) { this.drafterId = drafterId; }
    public String getDrafterName() { return drafterName; }
    public void setDrafterName(String drafterName) { this.drafterName = drafterName; }
    public String getDrafterPosition() { return drafterPosition; }
    public void setDrafterPosition(String drafterPosition) { this.drafterPosition = drafterPosition; }
    public String getDrafterDept() { return drafterDept; }
    public void setDrafterDept(String drafterDept) { this.drafterDept = drafterDept; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public List<ApprovalLineVO> getLines() { return lines; }
    public void setLines(List<ApprovalLineVO> lines) { this.lines = lines; }
    public List<ApprovalFileVO> getFiles() { return files; }
    public void setFiles(List<ApprovalFileVO> files) { this.files = files; }
}
