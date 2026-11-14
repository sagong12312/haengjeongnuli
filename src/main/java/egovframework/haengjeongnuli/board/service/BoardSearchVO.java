package egovframework.haengjeongnuli.board.service;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 게시판 목록 조건: 게시판·검색어·쪽 번호, 그리고 보는 사람(공개 범위 확인용) */
public class BoardSearchVO {

    public static final int PAGE_SIZE = 20;

    private String boardType;
    private String q;
    private int page = 1;
    private int size = PAGE_SIZE;

    private String viewerId;
    private String viewerDept;
    private boolean viewerAdmin;

    public static BoardSearchVO of(String boardType, String q, int page, LoginUser viewer) {
        BoardSearchVO s = new BoardSearchVO();
        s.boardType = boardType;
        s.q = (q == null || q.isBlank()) ? null : q.trim();
        s.page = Math.max(1, page);
        s.viewerId = viewer.getLoginId();
        s.viewerDept = viewer.dept().isEmpty() ? null : viewer.dept();
        s.viewerAdmin = viewer.isAdmin();
        return s;
    }

    public BoardSearchVO size(int size) {
        this.size = Math.max(1, Math.min(size, 200));
        return this;
    }

    public int getStartRow() { return (page - 1) * size; }
    public int getEndRow() { return page * size; }

    public String getBoardType() { return boardType; }
    public String getQ() { return q; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public String getViewerId() { return viewerId; }
    public String getViewerDept() { return viewerDept; }
    public boolean isViewerAdmin() { return viewerAdmin; }
}
