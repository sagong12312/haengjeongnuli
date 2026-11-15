package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 증명 발급대장 한 줄. 증명서 자체는 주민등록·인감 전산 시스템에서 발급하고, 여기서는 창구 발급 사실과 수수료를 기록한다.
 * 창구 수수료(1통): 주민등록표 등·초본 400원(이해관계인 신청 500원), 인감증명서 600원 — 지자체 민원 수수료 안내 기준.
 * 실제 금액과 면제 대상(주민등록법 시행규칙 제18조 등)은 기관 기준으로 확인한다.
 */
public class CertIssueVO {

    /** 증명 종류 → 1통 창구 수수료 */
    public static final Map<String, Integer> FEES = new LinkedHashMap<>();
    static {
        FEES.put("주민등록표 등본", 400);
        FEES.put("주민등록표 초본", 400);
        FEES.put("인감증명서", 600);
        FEES.put("본인서명사실확인서", 600);
        FEES.put("기타 증명", 0);
    }
    public static final List<String> APPLICANT_TYPES = List.of("본인", "세대원", "위임받은 사람", "이해관계인");
    /** 이해관계인이 신청한 주민등록표 1통 수수료 */
    public static final int INTERESTED_FEE = 500;

    private Long issueId;
    private String issueNo;
    private int issueYear;
    private String certType;
    private String applicantName;
    private String applicantType;
    private String purpose;
    private int copies;
    private long fee;
    private String exemptReason;
    private String idCheckedYn;
    private String deptName;
    private String issuedBy;
    private String issuedName;
    private LocalDateTime issuedAt;

    /** 기본 수수료 (면제 전) */
    public static long standardFee(String type, String applicantType, int copies) {
        int each = FEES.getOrDefault(type, 0);
        if ("이해관계인".equals(applicantType) && type != null && type.startsWith("주민등록표")) {
            each = INTERESTED_FEE;
        }
        return (long) each * copies;
    }

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public int getIssueYear() { return issueYear; }
    public void setIssueYear(int issueYear) { this.issueYear = issueYear; }
    public String getCertType() { return certType; }
    public void setCertType(String certType) { this.certType = certType; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantType() { return applicantType; }
    public void setApplicantType(String applicantType) { this.applicantType = applicantType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public int getCopies() { return copies; }
    public void setCopies(int copies) { this.copies = copies; }
    public long getFee() { return fee; }
    public void setFee(long fee) { this.fee = fee; }
    public String getExemptReason() { return exemptReason; }
    public void setExemptReason(String exemptReason) { this.exemptReason = exemptReason; }
    public String getIdCheckedYn() { return idCheckedYn; }
    public void setIdCheckedYn(String idCheckedYn) { this.idCheckedYn = idCheckedYn; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public String getIssuedName() { return issuedName; }
    public void setIssuedName(String issuedName) { this.issuedName = issuedName; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
}
