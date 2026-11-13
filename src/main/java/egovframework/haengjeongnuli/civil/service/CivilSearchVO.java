package egovframework.haengjeongnuli.civil.service;

/** 민원 목록 검색 조건 */
public class CivilSearchVO {

    private CivilKind kind;
    private CivilStatus status;
    private String q;
    private int limit = 50;

    public CivilKind getKind() { return kind; }
    public void setKind(CivilKind kind) { this.kind = kind; }
    public CivilStatus getStatus() { return status; }
    public void setStatus(CivilStatus status) { this.status = status; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = (q == null || q.isBlank()) ? null : q.trim(); }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = Math.max(1, Math.min(limit, 200)); }
}
