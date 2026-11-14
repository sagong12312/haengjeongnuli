package egovframework.haengjeongnuli.board.service;

import java.time.LocalDateTime;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 댓글 (BOARD_COMMENT 테이블) */
public class BoardCommentVO {

    public static final int MAX_LENGTH = 1000;

    private Long commentId;
    private Long postId;
    private String content;
    private String writer;
    private String writerId;
    private String writerDept;
    private LocalDateTime createdAt;
    private String deletedYn = "N";

    /** 댓글 쓴 사람이나 관리자만 지울 수 있다 */
    public boolean canDelete(LoginUser user) {
        return user != null && (user.isAdmin() || (writerId != null && writerId.equals(user.getLoginId())));
    }

    public boolean isDeleted() { return "Y".equals(deletedYn); }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getWriter() { return writer; }
    public void setWriter(String writer) { this.writer = writer; }
    public String getWriterId() { return writerId; }
    public void setWriterId(String writerId) { this.writerId = writerId; }
    public String getWriterDept() { return writerDept; }
    public void setWriterDept(String writerDept) { this.writerDept = writerDept; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDeletedYn() { return deletedYn; }
    public void setDeletedYn(String deletedYn) { this.deletedYn = deletedYn; }
}
