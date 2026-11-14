package egovframework.haengjeongnuli.board.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.board.service.BoardCommentVO;
import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardSearchVO;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.board.service.impl.BoardServiceImpl;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 게시판: 공지사항·건의사항·자료실, 글쓰기, 자료 올리기, 첨부파일 내려받기 */
@Controller
@RequestMapping("/board")
public class BoardController {

    public record Tab(String id, String label) {}

    static final List<Tab> TABS = List.of(
            new Tab("notice", "공지사항"),
            new Tab("suggest", "건의사항"),
            new Tab("archive", "자료실"));

    private final BoardService boardService;
    private final FileStorage fileStorage;

    public BoardController(BoardService boardService, FileStorage fileStorage) {
        this.boardService = boardService;
        this.fileStorage = fileStorage;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser,
                       @RequestParam(defaultValue = "notice") String tab,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        Tab current = TABS.stream().filter(t -> t.id().equals(tab)).findFirst().orElse(TABS.get(0));
        BoardSearchVO search = BoardSearchVO.of(current.id(), q, page, loginUser);
        int total = boardService.count(search);
        int totalPages = Math.max(1, (total + search.getSize() - 1) / search.getSize());
        if (search.getPage() > totalPages) {
            search = BoardSearchVO.of(current.id(), q, totalPages, loginUser);
        }
        model.addAttribute("tabs", TABS);
        model.addAttribute("current", current);
        model.addAttribute("q", search.getQ());
        model.addAttribute("posts", boardService.list(search));
        model.addAttribute("total", total);
        model.addAttribute("page", search.getPage());
        model.addAttribute("totalPages", totalPages);
        // 쪽 번호는 현재 쪽 앞뒤로 최대 10개
        int from = Math.max(1, search.getPage() - 4);
        int to = Math.min(totalPages, from + 9);
        model.addAttribute("pageFrom", from);
        model.addAttribute("pageTo", to);
        return "board/list";
    }

    /** 게시글 상세: 내용·첨부파일·댓글. 같은 로그인 안에서는 조회수를 한 번만 센다. */
    @GetMapping("/{postId}")
    public String view(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                       HttpSession session, Model model, RedirectAttributes ra, HttpServletResponse response) {
        BoardPostVO post = boardService.get(postId);
        if (post == null) {
            ra.addFlashAttribute("message", "이미 삭제되었거나 없는 게시물입니다.");
            return "redirect:/board";
        }
        if (!post.canView(loginUser)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "error/403";
        }
        @SuppressWarnings("unchecked")
        java.util.Set<Long> seen = (java.util.Set<Long>) session.getAttribute("viewedPosts");
        if (seen == null) {
            seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
            session.setAttribute("viewedPosts", seen);
        }
        BoardPostVO viewed = boardService.view(postId, seen.add(postId));
        model.addAttribute("post", viewed);
        model.addAttribute("comments", boardService.comments(postId));
        model.addAttribute("commentMax", BoardCommentVO.MAX_LENGTH);
        model.addAttribute("tabLabel", TABS.stream().filter(x -> x.id().equals(viewed.getBoardType()))
                .map(Tab::label).findFirst().orElse("게시판"));
        return "board/view";
    }

    @PostMapping("/{postId}/comments")
    public String addComment(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                             @RequestParam(defaultValue = "") String content,
                             RedirectAttributes ra, HttpServletResponse response) {
        BoardService.CommentResult r = boardService.addComment(postId, content, loginUser);
        return commentResult(r, postId, "댓글을 달았습니다.", ra, response);
    }

    @PostMapping("/{postId}/comments/{commentId}/delete")
    public String deleteComment(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                                @PathVariable long commentId, RedirectAttributes ra, HttpServletResponse response) {
        BoardService.CommentResult r = boardService.deleteComment(postId, commentId, loginUser);
        return commentResult(r, postId, "댓글을 삭제했습니다.", ra, response);
    }

    private String commentResult(BoardService.CommentResult r, long postId, String okMessage,
                                 RedirectAttributes ra, HttpServletResponse response) {
        switch (r) {
            case FORBIDDEN -> {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return "error/403";
            }
            case NOT_FOUND -> ra.addFlashAttribute("message", "이미 삭제되었거나 없는 글·댓글입니다.");
            case INVALID -> ra.addFlashAttribute("message",
                    "댓글은 1~" + BoardCommentVO.MAX_LENGTH + "자로 입력하세요.");
            case DONE -> ra.addFlashAttribute("message", okMessage);
        }
        return "redirect:/board/" + postId + "#comments";
    }

    @GetMapping("/write")
    public String writeForm(Model model) {
        model.addAttribute("postForm", new BoardPostForm());
        addFileRules(model);
        return "board/write";
    }

    @PostMapping("/write")
    public String write(@AuthenticationPrincipal LoginUser loginUser, @Validated @ModelAttribute("postForm") BoardPostForm form, BindingResult result,
                        @RequestParam(value = "files", required = false) List<MultipartFile> files,
                        Model model, RedirectAttributes ra, HttpServletResponse response) {
        if ("archive".equals(form.getBoard())) {
            result.rejectValue("board", "board", "자료실에는 '자료 올리기'로 올려 주세요.");
        }
        if (form.getContent() == null || form.getContent().isBlank()) {
            result.rejectValue("content", "content", "내용을 입력하세요.");
        }
        checkFiles(files, result, false);
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addFileRules(model);
            return "board/write";
        }
        boardService.write(form, files, loginUser);
        ra.addFlashAttribute("message", "게시글이 등록되었습니다.");
        return "redirect:/board?tab=" + form.getBoard();
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        BoardPostForm form = new BoardPostForm();
        form.setBoard("archive");
        model.addAttribute("postForm", form);
        addFileRules(model);
        return "board/upload";
    }

    @PostMapping("/upload")
    public String upload(@AuthenticationPrincipal LoginUser loginUser, @Validated @ModelAttribute("postForm") BoardPostForm form, BindingResult result,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         Model model, RedirectAttributes ra, HttpServletResponse response) {
        form.setBoard("archive");
        checkFiles(files, result, true);
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addFileRules(model);
            return "board/upload";
        }
        boardService.write(form, files, loginUser);
        ra.addFlashAttribute("message", "자료가 등록되었습니다.");
        return "redirect:/board?tab=archive";
    }

    /** 게시글 수정 화면 (글쓴이 본인·관리자) */
    @GetMapping("/{postId}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                           Model model, RedirectAttributes ra, HttpServletResponse response) {
        BoardPostVO post = boardService.get(postId);
        if (post == null) {
            ra.addFlashAttribute("message", "이미 삭제되었거나 없는 게시물입니다.");
            return "redirect:/board";
        }
        if (!post.canEdit(loginUser)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "error/403";
        }
        BoardPostForm form = new BoardPostForm();
        form.setBoard(post.getBoardType());
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        form.setCategory(post.getCategory());
        form.setScope(post.getOpenScope());
        form.setPinned(post.isPinned());
        model.addAttribute("postForm", form);
        model.addAttribute("post", post);
        addFileRules(model);
        return "board/edit";
    }

    @PostMapping("/{postId}/edit")
    public String edit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                       @Validated @ModelAttribute("postForm") BoardPostForm form, BindingResult result,
                       @RequestParam(value = "files", required = false) List<MultipartFile> files,
                       @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds,
                       Model model, RedirectAttributes ra, HttpServletResponse response) {
        BoardPostVO post = boardService.get(postId);
        if (post == null) {
            ra.addFlashAttribute("message", "이미 삭제되었거나 없는 게시물입니다.");
            return "redirect:/board";
        }
        if (!post.canEdit(loginUser)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "error/403";
        }
        form.setBoard(post.getBoardType());   // 게시판 종류는 바꿀 수 없다
        if (!"archive".equals(post.getBoardType()) && (form.getContent() == null || form.getContent().isBlank())) {
            result.rejectValue("content", "content", "내용을 입력하세요.");
        }
        checkFiles(files, result, false);
        if (!result.hasErrors()) {
            BoardService.UpdateResult r = boardService.update(postId, form, files, removeFileIds, loginUser);
            if (r == BoardService.UpdateResult.UPDATED) {
                ra.addFlashAttribute("message", "게시물을 수정했습니다.");
                return "redirect:/board/" + postId;
            }
            if (r == BoardService.UpdateResult.NEED_FILE) {
                result.reject("files.required", "자료실 글에는 파일이 하나 이상 있어야 합니다.");
            }
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("post", post);
        addFileRules(model);
        return "board/edit";
    }

    /** 게시물 삭제 (글쓴이 본인·관리자) */
    @PostMapping("/{postId}/delete")
    public String delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
                         @RequestParam(defaultValue = "notice") String tab,
                         RedirectAttributes ra, HttpServletResponse response) {
        BoardService.DeleteResult r = boardService.delete(postId, loginUser);
        if (r == BoardService.DeleteResult.FORBIDDEN) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "error/403";
        }
        ra.addFlashAttribute("message", r == BoardService.DeleteResult.DELETED
                ? "게시물을 삭제했습니다." : "이미 삭제되었거나 없는 게시물입니다.");
        String safeTab = TABS.stream().anyMatch(x -> x.id().equals(tab)) ? tab : "notice";
        return "redirect:/board?tab=" + safeTab;
    }

    /** 첨부파일 내려받기. 브라우저가 열어 실행하지 않도록 항상 '내려받기'로 보낸다. */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long fileId) {
        AttachFileVO f = boardService.fileForDownload(fileId, loginUser);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorage.path(f.getStoredName());
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(f.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    private void checkFiles(List<MultipartFile> files, BindingResult result, boolean required) {
        List<MultipartFile> real = BoardServiceImpl.nonEmpty(files);
        if (required && real.isEmpty()) {
            result.reject("files.required", "올릴 파일을 하나 이상 선택하세요.");
        }
        for (MultipartFile f : real) {
            String name = FileStorage.cleanName(f.getOriginalFilename());
            if (!fileStorage.isAllowed(name)) {
                result.reject("files.ext", "올릴 수 없는 형식의 파일입니다: " + name);
            }
        }
    }

    private void addFileRules(Model model) {
        model.addAttribute("allowedExt", String.join(", ", fileStorage.getAllowedExtensions().stream().sorted().toList()));
    }
}
