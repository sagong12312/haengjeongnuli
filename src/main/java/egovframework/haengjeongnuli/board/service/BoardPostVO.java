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
    private String writerId;
    private String deletedYn = "N";
    private LocalDateTime updatedAt;
    private int commentCount;
    private String updatedBy;

    private java.util.List<egovframework.haengjeongnuli.cmm.file.AttachFileVO> files = new java.util.ArrayList<>();

    public boolean isPinned() { return "Y".equals(pinnedYn); }
    public boolean isDeleted() { return "Y".equals(deletedYn); }

    /** 이 직원이 이 글을 지울 수 있는지: 관리자이거나 글쓴이 본인 */
    public boolean canDelete(egovframework.haengjeongnuli.cmm.LoginUser user) {
        return user != null && (user.isAdmin() || (writerId != null && writerId.equals(user.getLoginId())));
    }

    /** 볼 수 있는지: 전체 공개, 또는 '우리 부서'면 같은 부서·글쓴이·관리자 */
    public boolean canView(egovframework.haengjeongnuli.cmm.LoginUser user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin() || !"우리 부서".equals(openScope)) {
            return true;
        }
        return (writerId != null && writerId.equals(user.getLoginId()))
                || (writerDept != null && writerDept.equals(user.dept()));
    }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    /** 수정 권한은 삭제 권한과 같다 */
    public boolean canEdit(egovframework.haengjeongnuli.cmm.LoginUser user) { return canDelete(user); }

    public boolean isEdited() { return updatedAt != null; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getWriterId() { return writerId; }
    public void setWriterId(String writerId) { this.writerId = writerId; }
    public String getDeletedYn() { return deletedYn; }
    public void setDeletedYn(String deletedYn) { this.deletedYn = deletedYn; }

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
