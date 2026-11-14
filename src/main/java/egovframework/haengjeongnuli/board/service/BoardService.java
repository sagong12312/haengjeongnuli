package egovframework.haengjeongnuli.board.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;

/** 게시판 서비스 */
public interface BoardService {

    /** 글과 첨부파일을 저장한다. 파일 확장자 검사는 호출 전에 끝나 있어야 한다. */
    BoardPostVO write(BoardPostForm form, List<MultipartFile> files, LoginUser writer);

    /** 목록 (각 글의 첨부파일 포함) */
    List<BoardPostVO> list(String boardType, String q, int limit);

    int count(String boardType, String q);

    /** 내려받을 파일 정보. 없으면 null. 내려받은 횟수를 하나 올린다. */
    AttachFileVO fileForDownload(long fileId);

    enum DeleteResult { DELETED, NOT_FOUND, FORBIDDEN }

    /** 글을 삭제 표시한다. 글쓴이 본인이나 관리자만 가능 */
    DeleteResult delete(long postId, LoginUser by);

    /** 수정 화면용: 삭제되지 않은 글과 첨부파일. 없으면 null */
    BoardPostVO get(long postId);

    enum UpdateResult { UPDATED, NOT_FOUND, FORBIDDEN, NEED_FILE }

    /**
     * 글을 고친다. 글쓴이 본인이나 관리자만 가능.
     * removeFileIds 의 첨부파일은 지우고 newFiles 는 더한다. 자료실 글은 파일이 하나 이상 남아야 한다.
     */
    UpdateResult update(long postId, BoardPostForm form, List<MultipartFile> newFiles,
                        List<Long> removeFileIds, LoginUser by);
}
