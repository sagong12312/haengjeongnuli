package egovframework.haengjeongnuli.board.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.cmm.file.AttachFileVO;

/** 게시판 서비스 */
public interface BoardService {

    /** 글과 첨부파일을 저장한다. 파일 확장자 검사는 호출 전에 끝나 있어야 한다. */
    BoardPostVO write(BoardPostForm form, List<MultipartFile> files, String writer, String writerDept);

    /** 목록 (각 글의 첨부파일 포함) */
    List<BoardPostVO> list(String boardType, String q, int limit);

    int count(String boardType, String q);

    /** 내려받을 파일 정보. 없으면 null. 내려받은 횟수를 하나 올린다. */
    AttachFileVO fileForDownload(long fileId);
}
