package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 민원 (CIVIL_COMPLAINT 테이블) */
public class CivilComplaintVO {

    private Long complaintId;
    private String receiptNo;
    private CivilKind kind;
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
    private LocalDateTime completedAt;
    private String registerId;

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
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getRegisterId() { return registerId; }
    public void setRegisterId(String registerId) { this.registerId = registerId; }
    public boolean isImminent() { return imminent; }
    public void setImminent(boolean imminent) { this.imminent = imminent; }
}
