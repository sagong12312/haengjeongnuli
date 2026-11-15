package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/** 기안하기 입력값 */
public class ApprovalForm {

    public static final List<String> KINDS = List.of("일반 기안", "지출결의", "협조 요청", "보고");
    public static final List<String> RETENTIONS = List.of("1년", "3년", "5년", "10년", "30년", "준영구", "영구");
    public static final List<String> DISCLOSURES = List.of("공개", "부분공개", "비공개");
    /** 정보공개법 제9조 제1항 각 호 */
    public static final List<String> REASONS = List.of("1", "2", "3", "4", "5", "6", "7", "8");
    public static final int MAX_REVIEWERS = 4;
    /** 인사 화면의 복무 신청이 만드는 문서 종류 (기안하기 화면에서는 고르지 않는다) */
    public static final String DUTY = "복무 신청";

    public static boolean isValidKind(String k) {
        return k != null && (KINDS.contains(k) || DUTY.equals(k));
    }

    /** 지출결의 문서 종류 */
    public static final String SPENDING = "지출결의";
    /** 지급 방법 (지방회계법 시행령 제36조·제37조: 계좌 지급이 원칙, 현금은 제52조의 경우) */
    public static final List<String> PAY_METHODS = List.of("계좌이체", "현금");

    private String kind = "일반 기안";
    private String retention = "1년";
    private String disclosure = "공개";
    private List<String> reason = new ArrayList<>();
    private String reasonDetail;
    private String receiverType = "internal";
    private String receiver;
    private String via;
    private String reference;
    private String sender;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;
    private String title;
    private String body;
    /** 검토자 아이디 (위에서부터 차례대로, 빈 칸은 무시) */
    private List<String> reviewers = new ArrayList<>();
    /** 결재권자 아이디 */
    private String finalApprover;

    // 지출결의
    private Long budgetId;
    /** 쉼표가 들어간 입력을 받는다 */
    private String payAmount;
    private String payee;
    private String payMethod = "계좌이체";

    public boolean isSpending() { return SPENDING.equals(kind); }

    /** "1,234,000" → 1234000, 잘못된 값이면 null */
    public Long payAmountValue() {
        if (payAmount == null) {
            return null;
        }
        String t = payAmount.replace(",", "").replace("원", "").trim();
        return t.matches("\\d{1,15}") ? Long.valueOf(t) : null;
    }

    /** 빈 칸을 뺀 검토자 */
    public List<String> realReviewers() {
        List<String> out = new ArrayList<>();
        if (reviewers != null) {
            for (String r : reviewers) {
                if (r != null && !r.isBlank()) {
                    out.add(r.trim());
                }
            }
        }
        return out;
    }

    /** 기존 문서 내용을 입력값으로 옮긴다 (고치기·다시 기안) */
    public static ApprovalForm from(ApprovalDocVO d) {
        ApprovalForm f = new ApprovalForm();
        f.kind = d.getKind();
        f.retention = d.getRetention();
        f.disclosure = d.getDisclosure();
        f.reason = new ArrayList<>(d.getRestrictReasonList());
        f.reasonDetail = d.getRestrictDetail();
        f.receiverType = d.getReceiverType();
        f.receiver = d.getReceiver();
        f.via = d.getVia();
        f.reference = d.getReferenceTo();
        f.sender = d.getSender();
        f.effectiveDate = d.getEffectiveDate();
        f.title = d.getTitle();
        f.body = d.getBody();
        f.budgetId = d.getBudgetId();
        f.payAmount = d.getPayAmount() == null ? null : String.format("%,d", d.getPayAmount());
        f.payee = d.getPayee();
        f.payMethod = d.getPayMethod() == null ? "계좌이체" : d.getPayMethod();
        List<ApprovalLineVO> lines = d.getLines();
        for (int i = 0; i < lines.size(); i++) {
            if (i == lines.size() - 1) {
                f.finalApprover = lines.get(i).getApproverId();
            } else {
                f.reviewers.add(lines.get(i).getApproverId());
            }
        }
        return f;
    }

    public Long getBudgetId() { return budgetId; }
    public void setBudgetId(Long budgetId) { this.budgetId = budgetId; }
    public String getPayAmount() { return payAmount; }
    public void setPayAmount(String payAmount) { this.payAmount = payAmount; }
    public String getPayee() { return payee; }
    public void setPayee(String payee) { this.payee = payee; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getRetention() { return retention; }
    public void setRetention(String retention) { this.retention = retention; }
    public String getDisclosure() { return disclosure; }
    public void setDisclosure(String disclosure) { this.disclosure = disclosure; }
    public List<String> getReason() { return reason; }
    public void setReason(List<String> reason) { this.reason = reason; }
    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
    public String getReceiverType() { return receiverType; }
    public void setReceiverType(String receiverType) { this.receiverType = receiverType; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public List<String> getReviewers() { return reviewers; }
    public void setReviewers(List<String> reviewers) { this.reviewers = reviewers; }
    public String getFinalApprover() { return finalApprover; }
    public void setFinalApprover(String finalApprover) { this.finalApprover = finalApprover; }
}
