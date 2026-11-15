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

import egovframework.haengjeongnuli.board.service.BoardCommentVO;
import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardSearchVO;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;

@Service("boardService")
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;

    public BoardServiceImpl(BoardMapper boardMapper, FileStorage fileStorage, NotificationService notificationService) {
        this.boardMapper = boardMapper;
        this.fileStorage = fileStorage;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public BoardPostVO write(BoardPostForm form, List<MultipartFile> files, LoginUser writer) {
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
        vo.setWriter(writer.name());
        vo.setWriterDept(writer.dept().isEmpty() ? null : writer.dept());
        vo.setWriterId(writer.getLoginId());
        vo.setCreatedAt(now);
        boardMapper.insertPost(vo);
        vo.getFiles().addAll(saveFiles(vo.getPostId(), files, now));
        return vo;
    }

    /** 파일을 디스크와 DB에 저장한다. 중간에 실패하면 이번에 디스크에 쓴 파일을 지운다. */
    private List<AttachFileVO> saveFiles(long postId, List<MultipartFile> files, LocalDateTime now) {
        List<AttachFileVO> result = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        try {
            for (MultipartFile f : nonEmpty(files)) {
                AttachFileVO af = new AttachFileVO();
                af.setFileId(boardMapper.nextFileId());
                af.setPostId(postId);
                af.setOriginalName(FileStorage.cleanName(f.getOriginalFilename()));
                af.setStoredName(fileStorage.save(f));
                saved.add(af.getStoredName());
                af.setFileSize(f.getSize());
                af.setCreatedAt(now);
                boardMapper.insertFile(af);
                result.add(af);
            }
        } catch (IOException e) {
            saved.forEach(fileStorage::deleteQuietly);
            throw new UncheckedIOException("첨부파일 저장 실패", e);
        } catch (RuntimeException e) {
            // DB 저장이 실패하면 디스크에 쓴 파일도 지운다
            saved.forEach(fileStorage::deleteQuietly);
            throw e;
        }
        return result;
    }

    @Override
    public BoardPostVO get(long postId) {
        BoardPostVO post = boardMapper.selectPost(postId);
        if (post == null || post.isDeleted()) {
            return null;
        }
        post.setFiles(new ArrayList<>(boardMapper.selectFilesByPostIds(List.of(postId))));
        return post;
    }

    @Override
    @Transactional
    public UpdateResult update(long postId, BoardPostForm form, List<MultipartFile> newFiles,
                               List<Long> removeFileIds, LoginUser by) {
        BoardPostVO post = get(postId);
        if (post == null) {
            return UpdateResult.NOT_FOUND;
        }
        if (!post.canEdit(by)) {
            return UpdateResult.FORBIDDEN;
        }
        // 이 글에 붙은 파일만 지울 수 있다
        List<AttachFileVO> toRemove = post.getFiles().stream()
                .filter(f -> removeFileIds != null && removeFileIds.contains(f.getFileId()))
                .toList();
        int remaining = post.getFiles().size() - toRemove.size() + nonEmpty(newFiles).size();
        if ("archive".equals(post.getBoardType()) && remaining < 1) {
            return UpdateResult.NEED_FILE;
        }

        LocalDateTime now = LocalDateTime.now();
        post.setTitle(form.getTitle().trim());
        post.setContent(form.getContent());
        post.setCategory("archive".equals(post.getBoardType()) ? form.getCategory() : post.getCategory());
        post.setOpenScope(form.getScope());
        post.setPinnedYn("notice".equals(post.getBoardType()) && form.isPinned() ? "Y" : "N");
        post.setUpdatedAt(now);
        post.setUpdatedBy(by.getLoginId());
        boardMapper.updatePost(post);

        for (AttachFileVO f : toRemove) {
            boardMapper.deleteFile(f.getFileId(), postId);
        }
        saveFiles(postId, newFiles, now);

        // DB 반영이 확정된 뒤에만 디스크의 파일을 지운다 (되돌려지면 파일이 남아 있어야 하므로)
        List<String> storedToDelete = toRemove.stream().map(AttachFileVO::getStoredName).toList();
        if (!storedToDelete.isEmpty()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            storedToDelete.forEach(fileStorage::deleteQuietly);
                        }
                    });
        }
        return UpdateResult.UPDATED;
    }

    @Override
    public List<BoardPostVO> list(BoardSearchVO search) {
        List<BoardPostVO> posts = boardMapper.selectPostList(search);
        if (!posts.isEmpty()) {
            List<Long> ids = posts.stream().map(BoardPostVO::getPostId).toList();
            Map<Long, List<AttachFileVO>> byPost = boardMapper.selectFilesByPostIds(ids).stream()
                    .collect(Collectors.groupingBy(AttachFileVO::getPostId));
            posts.forEach(p -> p.setFiles(byPost.getOrDefault(p.getPostId(), new ArrayList<>())));
        }
        return posts;
    }

    @Override
    public int count(BoardSearchVO search) {
        return boardMapper.selectPostCount(search);
    }

    @Override
    @Transactional
    public BoardPostVO view(long postId, boolean countView) {
        BoardPostVO post = get(postId);
        if (post != null && countView) {
            boardMapper.increaseViewCount(postId);
            post.setViewCount(post.getViewCount() + 1);
        }
        return post;
    }

    @Override
    public List<BoardCommentVO> comments(long postId) {
        return boardMapper.selectComments(postId);
    }

    @Override
    @Transactional
    public CommentResult addComment(long postId, String content, LoginUser by) {
        BoardPostVO post = get(postId);
        if (post == null) {
            return CommentResult.NOT_FOUND;
        }
        if (!post.canView(by)) {
            return CommentResult.FORBIDDEN;
        }
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() || text.length() > BoardCommentVO.MAX_LENGTH) {
            return CommentResult.INVALID;
        }
        BoardCommentVO c = new BoardCommentVO();
        c.setCommentId(boardMapper.nextCommentId());
        c.setPostId(postId);
        c.setContent(text);
        c.setWriter(by.name());
        c.setWriterId(by.getLoginId());
        c.setWriterDept(by.dept().isEmpty() ? null : by.dept());
        c.setCreatedAt(LocalDateTime.now());
        boardMapper.insertComment(c);
        // 글쓴이에게 알림 (내 글에 내가 단 댓글은 알리지 않음)
        if (post.getWriterId() != null && !post.getWriterId().equals(by.getLoginId())) {
            notificationService.notify(post.getWriterId(),
                    by.name() + "님이 '" + post.getTitle() + "'에 댓글을 달았습니다.",
                    "/board/" + postId + "#comments");
        }
        return CommentResult.DONE;
    }

    @Override
    @Transactional
    public CommentResult deleteComment(long postId, long commentId, LoginUser by) {
        BoardCommentVO c = boardMapper.selectComment(commentId);
        if (c == null || c.isDeleted() || c.getPostId() != postId) {
            return CommentResult.NOT_FOUND;
        }
        if (!c.canDelete(by)) {
            return CommentResult.FORBIDDEN;
        }
        boardMapper.markCommentDeleted(commentId, by.getLoginId(), LocalDateTime.now());
        return CommentResult.DONE;
    }

    @Override
    @Transactional
    public AttachFileVO fileForView(long fileId, LoginUser viewer) {
        AttachFileVO f = boardMapper.selectFile(fileId);
        if (f == null) {
            return null;
        }
        BoardPostVO post = boardMapper.selectPost(f.getPostId());
        return post != null && post.canView(viewer) ? f : null;
    }

    @Override
    @Transactional
    public AttachFileVO fileForDownload(long fileId, LoginUser viewer) {
        AttachFileVO f = boardMapper.selectFile(fileId);
        if (f != null) {
            BoardPostVO post = boardMapper.selectPost(f.getPostId());
            if (post == null || !post.canView(viewer)) {
                return null;
            }
            boardMapper.increaseDownloadCount(fileId);
        }
        return f;
    }

    @Override
    @Transactional
    public DeleteResult delete(long postId, LoginUser by) {
        BoardPostVO post = boardMapper.selectPost(postId);
        if (post == null || post.isDeleted()) {
            return DeleteResult.NOT_FOUND;
        }
        if (!post.canDelete(by)) {
            return DeleteResult.FORBIDDEN;
        }
        boardMapper.markDeleted(postId, by.getLoginId(), LocalDateTime.now());
        return DeleteResult.DELETED;
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
