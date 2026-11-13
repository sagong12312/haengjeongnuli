package egovframework.haengjeongnuli.board.service;

import java.time.LocalDateTime;

/** 게시글 (BOARD_POST 테이블) */
public class BoardPostVO {

    private Long postId;
    private String boardType;
    private String title;
    private String content;
    private String category;
    private String openScope;
    private String pinnedYn = "N";
    private int viewCount;
    private String writer;
    private String writerDept;
    private LocalDateTime createdAt;

    private java.util.List<egovframework.haengjeongnuli.cmm.file.AttachFileVO> files = new java.util.ArrayList<>();

    public boolean isPinned() { return "Y".equals(pinnedYn); }

    public java.util.List<egovframework.haengjeongnuli.cmm.file.AttachFileVO> getFiles() { return files; }
    public void setFiles(java.util.List<egovframework.haengjeongnuli.cmm.file.AttachFileVO> files) { this.files = files; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getBoardType() { return boardType; }
    public void setBoardType(String boardType) { this.boardType = boardType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOpenScope() { return openScope; }
    public void setOpenScope(String openScope) { this.openScope = openScope; }
    public String getPinnedYn() { return pinnedYn; }
    public void setPinnedYn(String pinnedYn) { this.pinnedYn = pinnedYn; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public String getWriter() { return writer; }
    public void setWriter(String writer) { this.writer = writer; }
    public String getWriterDept() { return writerDept; }
    public void setWriterDept(String writerDept) { this.writerDept = writerDept; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
