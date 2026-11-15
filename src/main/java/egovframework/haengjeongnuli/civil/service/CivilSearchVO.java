package egovframework.haengjeongnuli.civil.service;

/** 민원 목록 검색 조건 */
public class CivilSearchVO {

    private CivilKind kind;
    private CivilStatus status;
    private String q;
    private int limit = 50;
    /** 이 법정민원 사무들만 (업무 화면에서 쓴다) */
    private java.util.List<String> affairs;
    /** 처리 완료 제외 */
    private boolean openOnly;

    public java.util.List<String> getAffairs() { return affairs; }
    public void setAffairs(java.util.List<String> affairs) { this.affairs = affairs; }
    public boolean isOpenOnly() { return openOnly; }
    public void setOpenOnly(boolean openOnly) { this.openOnly = openOnly; }

    public CivilKind getKind() { return kind; }
    public void setKind(CivilKind kind) { this.kind = kind; }
    public CivilStatus getStatus() { return status; }
    public void setStatus(CivilStatus status) { this.status = status; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = (q == null || q.isBlank()) ? null : q.trim(); }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = Math.max(1, Math.min(limit, 200)); }
}
