package egovframework.haengjeongnuli.welfare.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 기초생활보장 급여 신청 한 건 */
public class WelfareCaseVO {
    /** 급여의 종류 (국민기초생활 보장법 제7조 제1항) */
    public static final List<String> BENEFITS = List.of("생계급여", "주거급여", "의료급여", "교육급여", "해산급여", "장제급여", "자활급여");

    private Long caseId;
    private String caseNo;
    private String applicantName;
    private LocalDate birthDate;
    private String address;
    private String phone;
    private LocalDate appliedOn;
    private String benefits;
    private LocalDate deadline;
    private String extendReason;
    /** RECEIVED / SURVEY / GRANTED / DENIED / WITHDRAWN */
    private String status;
    private String assigneeId;
    private String assigneeName;
    private String surveyNote;
    private String grantedBenefits;
    private String decisionSummary;
    private String benefitMethod;
    private LocalDate benefitStart;
    private LocalDate decidedOn;
    private LocalDate notifiedOn;
    private String createdBy;
    private LocalDateTime createdAt;

    public boolean isOpen() { return "RECEIVED".equals(status) || "SURVEY".equals(status); }

    public boolean isOverdue() { return isOpen() && deadline != null && LocalDate.now().isAfter(deadline); }

    public List<String> getBenefitList() {
        return benefits == null ? List.of() : Arrays.stream(benefits.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getStatusLabel() {
        if (isOverdue()) return "통지 기한 지남";
        return switch (status) {
            case "RECEIVED" -> "접수";
            case "SURVEY" -> "조사 중";
            case "GRANTED" -> "지급 결정";
            case "DENIED" -> "부적합 결정";
            case "WITHDRAWN" -> "취하";
            default -> status;
        };
    }

    public String getStatusBadge() {
        if (isOverdue()) return "badge-red";
        return switch (status) {
            case "SURVEY" -> "badge-blue";
            case "GRANTED" -> "badge-green";
            case "DENIED" -> "badge-orange";
            default -> "badge-gray";
        };
    }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getCaseNo() { return caseNo; }
    public void setCaseNo(String caseNo) { this.caseNo = caseNo; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public void setAppliedOn(LocalDate appliedOn) { this.appliedOn = appliedOn; }
    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getExtendReason() { return extendReason; }
    public void setExtendReason(String extendReason) { this.extendReason = extendReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public String getSurveyNote() { return surveyNote; }
    public void setSurveyNote(String surveyNote) { this.surveyNote = surveyNote; }
    public String getGrantedBenefits() { return grantedBenefits; }
    public void setGrantedBenefits(String grantedBenefits) { this.grantedBenefits = grantedBenefits; }
    public String getDecisionSummary() { return decisionSummary; }
    public void setDecisionSummary(String decisionSummary) { this.decisionSummary = decisionSummary; }
    public String getBenefitMethod() { return benefitMethod; }
    public void setBenefitMethod(String benefitMethod) { this.benefitMethod = benefitMethod; }
    public LocalDate getBenefitStart() { return benefitStart; }
    public void setBenefitStart(LocalDate benefitStart) { this.benefitStart = benefitStart; }
    public LocalDate getDecidedOn() { return decidedOn; }
    public void setDecidedOn(LocalDate decidedOn) { this.decidedOn = decidedOn; }
    public LocalDate getNotifiedOn() { return notifiedOn; }
    public void setNotifiedOn(LocalDate notifiedOn) { this.notifiedOn = notifiedOn; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
