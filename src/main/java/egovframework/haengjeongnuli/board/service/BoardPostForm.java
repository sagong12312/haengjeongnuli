package egovframework.haengjeongnuli.board.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 글쓰기·자료 올리기 입력값 */
public class BoardPostForm {

    @Pattern(regexp = "notice|suggest|archive", message = "게시판을 고르세요.")
    private String board = "notice";
    @NotBlank
    private String scope = "전체 공개";
    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 300)
    private String title;
    private String content;
    @Size(max = 50)
    private String category;
    private boolean pinned;

    public String getBoard() { return board; }
    public void setBoard(String board) { this.board = board; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
