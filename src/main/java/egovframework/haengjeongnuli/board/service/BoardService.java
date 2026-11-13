package egovframework.haengjeongnuli.board.service;

import java.util.List;

/** 게시판 서비스 */
public interface BoardService {

    BoardPostVO write(BoardPostForm form, String writer, String writerDept);

    List<BoardPostVO> list(String boardType, String q, int limit);

    int count(String boardType, String q);
}
