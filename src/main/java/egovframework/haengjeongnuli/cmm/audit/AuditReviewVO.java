package egovframework.haengjeongnuli.cmm.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 접속기록 점검 결과 */
public class AuditReviewVO {
    private Long reviewId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String result;
    private String chainOkYn;
    private String note;
    private String reviewedBy;
    private String reviewedName;
    private LocalDateTime reviewedAt;

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getChainOkYn() { return chainOkYn; }
    public void setChainOkYn(String chainOkYn) { this.chainOkYn = chainOkYn; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedName() { return reviewedName; }
    public void setReviewedName(String reviewedName) { this.reviewedName = reviewedName; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
