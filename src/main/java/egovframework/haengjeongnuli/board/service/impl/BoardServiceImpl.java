package egovframework.haengjeongnuli.board.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

@Service("boardService")
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;
    private final FileStorage fileStorage;

    public BoardServiceImpl(BoardMapper boardMapper, FileStorage fileStorage) {
        this.boardMapper = boardMapper;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public BoardPostVO write(BoardPostForm form, List<MultipartFile> files, String writer, String writerDept) {
        LocalDateTime now = LocalDateTime.now();
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
        vo.setCreatedAt(now);
        boardMapper.insertPost(vo);

        List<String> saved = new ArrayList<>();
        try {
            for (MultipartFile f : nonEmpty(files)) {
                AttachFileVO af = new AttachFileVO();
                af.setFileId(boardMapper.nextFileId());
                af.setPostId(vo.getPostId());
                af.setOriginalName(FileStorage.cleanName(f.getOriginalFilename()));
                af.setStoredName(fileStorage.save(f));
                saved.add(af.getStoredName());
                af.setFileSize(f.getSize());
                af.setCreatedAt(now);
                boardMapper.insertFile(af);
                vo.getFiles().add(af);
            }
        } catch (IOException e) {
            saved.forEach(fileStorage::deleteQuietly);
            throw new UncheckedIOException("첨부파일 저장 실패", e);
        } catch (RuntimeException e) {
            // DB 저장이 실패하면 디스크에 쓴 파일도 지운다
            saved.forEach(fileStorage::deleteQuietly);
            throw e;
        }
        return vo;
    }

    @Override
    public List<BoardPostVO> list(String boardType, String q, int limit) {
        List<BoardPostVO> posts = boardMapper.selectPostList(boardType, blankToNull(q), Math.max(1, Math.min(limit, 200)));
        if (!posts.isEmpty()) {
            List<Long> ids = posts.stream().map(BoardPostVO::getPostId).toList();
            Map<Long, List<AttachFileVO>> byPost = boardMapper.selectFilesByPostIds(ids).stream()
                    .collect(Collectors.groupingBy(AttachFileVO::getPostId));
            posts.forEach(p -> p.setFiles(byPost.getOrDefault(p.getPostId(), new ArrayList<>())));
        }
        return posts;
    }

    @Override
    public int count(String boardType, String q) {
        return boardMapper.selectPostCount(boardType, blankToNull(q));
    }

    @Override
    @Transactional
    public AttachFileVO fileForDownload(long fileId) {
        AttachFileVO f = boardMapper.selectFile(fileId);
        if (f != null) {
            boardMapper.increaseDownloadCount(fileId);
        }
        return f;
    }

    /** 이름 없이 비어 있는 파일 입력칸은 건너뛴다 */
    public static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
