package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 민원 (CIVIL_COMPLAINT 테이블) */
public class CivilComplaintVO {

    private Long complaintId;
    private String receiptNo;
    private CivilKind kind;
    private CivilCategory category;
    private String lawInterpretYn = "N";
    private LegalAffair legalAffair;
    private String route;
    private String applicantName;
    private String mobile;
    private String homePhone;
    private String email;
    private String address;
    private String notifyMethod;
    private String title;
    private String content;
    private String dept;
    private String assignee;
    private CivilStatus status;
    private LocalDateTime receivedAt;
    private LocalDate deadline;
    /** 처리기한 시각 (시간 단위로 계산하는 5일 이하 민원) */
    private java.time.LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private String registerId;
    private String assigneeId;
    /** 처리기간 일수 (0 = 즉시) */
    private Integer periodDays;
    private int extendCount;
    private String resultType;
    private String resultText;
    private String rejectRemedy;
    private String resultNotify;
    /** 민원인 팩스 번호 */
    private String faxNo;
    /** 결과를 팩스로 보낸 번호 */
    private String resultFaxNo;
    public String getFaxNo() { return faxNo; }
    public void setFaxNo(String faxNo) { this.faxNo = faxNo; }
    public String getResultFaxNo() { return resultFaxNo; }
    public void setResultFaxNo(String resultFaxNo) { this.resultFaxNo = resultFaxNo; }
    private String completedBy;

    /** 처리기한이 기준일 이내로 남았고 아직 끝나지 않았는지 (목록 강조용, 서비스에서 채움) */
    private boolean imminent;

    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public CivilKind getKind() { return kind; }
    public void setKind(CivilKind kind) { this.kind = kind; }
    public String getLawInterpretYn() { return lawInterpretYn; }
    public void setLawInterpretYn(String lawInterpretYn) { this.lawInterpretYn = lawInterpretYn; }
    public LegalAffair getLegalAffair() { return legalAffair; }
    public void setLegalAffair(LegalAffair legalAffair) { this.legalAffair = legalAffair; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getHomePhone() { return homePhone; }
    public void setHomePhone(String homePhone) { this.homePhone = homePhone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNotifyMethod() { return notifyMethod; }
    public void setNotifyMethod(String notifyMethod) { this.notifyMethod = notifyMethod; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public CivilStatus getStatus() { return status; }
    public void setStatus(CivilStatus status) { this.status = status; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public java.time.LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(java.time.LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    /** 처리기한을 시각까지 보여 줄지 (18시가 아닌 시각으로 끝나는 경우) */
    public boolean isDeadlineHasTime() {
        return deadlineAt != null && !deadlineAt.toLocalTime().equals(java.time.LocalTime.of(18, 0));
    }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public Integer getPeriodDays() { return periodDays; }
    public void setPeriodDays(Integer periodDays) { this.periodDays = periodDays; }
    public int getExtendCount() { return extendCount; }
    public void setExtendCount(int extendCount) { this.extendCount = extendCount; }
    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getRejectRemedy() { return rejectRemedy; }
    public void setRejectRemedy(String rejectRemedy) { this.rejectRemedy = rejectRemedy; }
    public String getResultNotify() { return resultNotify; }
    public void setResultNotify(String resultNotify) { this.resultNotify = resultNotify; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public boolean isDone() { return status == CivilStatus.DONE; }

    /** 처리기한이 지났는데 아직 처리하지 않았는지 */
    public boolean isOverdue() {
        return !isDone() && deadlineAt != null && LocalDateTime.now().isAfter(deadlineAt);
    }

    /** 처리기한을 넘겨 완료했는지 */
    public boolean isLate() {
        return isDone() && deadlineAt != null && completedAt != null && completedAt.isAfter(deadlineAt);
    }

    /** 접수 후 지난 날수 */
    public long getDaysSinceReceived() {
        return receivedAt == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(receivedAt.toLocalDate(), LocalDate.now());
    }

    /** 접수 30일이 지나 진행상황을 알려야 하는지 (시행령 제23조) */
    public boolean isProgressNoticeDue() {
        return !isDone() && getDaysSinceReceived() >= 30;
    }

    /** 처리기간을 연장할 수 있는지 (시행령 제21조: 한 차례, 민원인 동의 시 한 차례 더. 즉시 처리 민원 제외) */
    public boolean isExtendable() {
        return !isDone() && periodDays != null && periodDays > 0 && extendCount < 2;
    }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getRegisterId() { return registerId; }
    public void setRegisterId(String registerId) { this.registerId = registerId; }
    public boolean isImminent() { return imminent; }
    public void setImminent(boolean imminent) { this.imminent = imminent; }

    public CivilCategory getCategory() { return category; }
    public void setCategory(CivilCategory category) { this.category = category; }
    /** 분야 이름 (예전 민원은 비어 있어 '기타') */
    public String getCategoryLabel() { return category == null ? CivilCategory.ETC.getLabel() : category.getLabel(); }
}
