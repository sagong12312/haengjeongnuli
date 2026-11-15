package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 문서함 검색 조건 */
public class ApprovalSearchVO {

    public static final int PAGE_SIZE = 20;

    /** wait / prog / done / back / recv / temp */
    private String box = "wait";
    private String loginId;
    private String dept;
    private String q;
    /** 1m / 3m / year / all */
    private String period = "all";
    private int page = 1;

    public static ApprovalSearchVO of(String box, LoginUser me) {
        ApprovalSearchVO s = new ApprovalSearchVO();
        s.box = box;
        s.loginId = me.getLoginId();
        s.dept = me.dept();
        return s;
    }

    /** 기간 시작 (전체면 null) */
    public LocalDateTime getFrom() {
        LocalDateTime now = LocalDateTime.now();
        return switch (period == null ? "all" : period) {
            case "1m" -> now.minusMonths(1);
            case "3m" -> now.minusMonths(3);
            case "year" -> now.withDayOfYear(1).toLocalDate().atStartOfDay();
            default -> null;
        };
    }

    /** 대결자 지정 기간 비교용 오늘 날짜 */
    public java.time.LocalDate getToday() { return java.time.LocalDate.now(); }

    public int getStartRow() { return (Math.max(page, 1) - 1) * PAGE_SIZE; }
    public int getEndRow() { return getStartRow() + PAGE_SIZE; }

    public String getBox() { return box; }
    public void setBox(String box) { this.box = box; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = (q == null || q.isBlank()) ? null : q.trim(); }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(page, 1); }
}
