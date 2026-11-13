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

import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.board.service.impl.BoardServiceImpl;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.AttachFileVO;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;

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
    public String list(@RequestParam(defaultValue = "notice") String tab,
                       @RequestParam(required = false) String q, Model model) {
        Tab current = TABS.stream().filter(t -> t.id().equals(tab)).findFirst().orElse(TABS.get(0));
        model.addAttribute("tabs", TABS);
        model.addAttribute("current", current);
        model.addAttribute("q", q);
        model.addAttribute("posts", boardService.list(current.id(), q, 50));
        model.addAttribute("total", boardService.count(current.id(), q));
        return "board/list";
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
        boardService.write(form, files, loginUser.name(), loginUser.dept());
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
        boardService.write(form, files, loginUser.name(), loginUser.dept());
        ra.addFlashAttribute("message", "자료가 등록되었습니다.");
        return "redirect:/board?tab=archive";
    }

    /** 첨부파일 내려받기. 브라우저가 열어 실행하지 않도록 항상 '내려받기'로 보낸다. */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable long fileId) {
        AttachFileVO f = boardService.fileForDownload(fileId);
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
