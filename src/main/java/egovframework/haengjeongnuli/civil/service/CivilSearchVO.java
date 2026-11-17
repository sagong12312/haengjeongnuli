package egovframework.haengjeongnuli.civil.service;

/** 민원 목록 검색 조건 */
public class CivilSearchVO {

    private CivilKind kind;
    private CivilCategory category;
    private CivilStatus status;
    private String q;
    private int limit = 50;
    // 보는 사람 (목록 접근통제). 창구 직원·관리자는 전체, 그 밖에는 담당·접수·처리 부서 민원만
    private boolean viewerAll;
    private String viewerId;
    private String viewerDept;

    /** 보는 사람에 맞춘 검색 조건 (보는 사람을 정하지 않으면 아무것도 나오지 않는다) */
    public static CivilSearchVO forViewer(egovframework.haengjeongnuli.cmm.LoginUser u) {
        CivilSearchVO s = new CivilSearchVO();
        s.applyViewer(u);
        return s;
    }

    public void applyViewer(egovframework.haengjeongnuli.cmm.LoginUser u) {
        this.viewerAll = u != null && (u.isCounterStaff() || u.isAdmin());
        this.viewerId = u == null ? "-" : u.getLoginId();
        this.viewerDept = u == null || u.dept().isEmpty() ? null : u.dept();
    }

    public boolean isViewerAll() { return viewerAll; }
    public String getViewerId() { return viewerId; }
    public String getViewerDept() { return viewerDept; }

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
    public CivilCategory getCategory() { return category; }
    public void setCategory(CivilCategory category) { this.category = category; }
    public CivilStatus getStatus() { return status; }
    public void setStatus(CivilStatus status) { this.status = status; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = (q == null || q.isBlank()) ? null : q.trim(); }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = Math.max(1, Math.min(limit, 200)); }
}
