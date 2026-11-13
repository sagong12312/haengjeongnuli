package egovframework.haengjeongnuli.board.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardService;

@Service("boardService")
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;

    public BoardServiceImpl(BoardMapper boardMapper) {
        this.boardMapper = boardMapper;
    }

    @Override
    @Transactional
    public BoardPostVO write(BoardPostForm form, String writer, String writerDept) {
        BoardPostVO vo = new BoardPostVO();
        vo.setPostId(boardMapper.nextId());
        vo.setBoardType(form.getBoard());
        vo.setTitle(form.getTitle().trim());
        vo.setContent(form.getContent());
        vo.setCategory(form.getCategory());
        vo.setOpenScope(form.getScope());
        // 상단 고정은 공지사항에서만
        vo.setPinnedYn("notice".equals(form.getBoard()) && form.isPinned() ? "Y" : "N");
        vo.setWriter(writer);
        vo.setWriterDept(writerDept);
        vo.setCreatedAt(LocalDateTime.now());
        boardMapper.insertPost(vo);
        return vo;
    }

    @Override
    public List<BoardPostVO> list(String boardType, String q, int limit) {
        return boardMapper.selectPostList(boardType, blankToNull(q), Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public int count(String boardType, String q) {
        return boardMapper.selectPostCount(boardType, blankToNull(q));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
