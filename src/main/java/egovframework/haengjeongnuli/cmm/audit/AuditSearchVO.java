package egovframework.haengjeongnuli.cmm.audit;

import java.time.LocalDate;

/** 기록 조회 조건 */
public class AuditSearchVO {

    public static final int PAGE_SIZE = 50;

    private String actorId;
    private AuditAction action;
    private LocalDate from;
    private LocalDate to;
    private int page = 1;

    public int getStartRow() { return (Math.max(1, page) - 1) * PAGE_SIZE; }
    public int getEndRow() { return Math.max(1, page) * PAGE_SIZE; }
    /** 끝 날짜는 그날 하루 전체를 포함하도록 다음 날 0시 전까지 */
    public LocalDate getToExclusive() { return to == null ? null : to.plusDays(1); }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = (actorId == null || actorId.isBlank()) ? null : actorId.trim(); }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public int getPage() { return Math.max(1, page); }
    public void setPage(int page) { this.page = Math.max(1, page); }
}
