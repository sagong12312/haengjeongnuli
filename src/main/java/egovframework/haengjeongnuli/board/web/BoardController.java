package egovframework.haengjeongnuli.board.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.board.service.BoardPostForm;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletResponse;

/** 게시판: 공지사항·건의사항·자료실, 글쓰기, 자료 올리기 */
@Controller
@RequestMapping("/board")
public class BoardController {

    public record Tab(String id, String label) {}

    static final List<Tab> TABS = List.of(
            new Tab("notice", "공지사항"),
            new Tab("suggest", "건의사항"),
            new Tab("archive", "자료실"));

    private final BoardService boardService;
    private final LoginUser loginUser;

    public BoardController(BoardService boardService, LoginUser loginUser) {
        this.boardService = boardService;
        this.loginUser = loginUser;
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
        return "board/write";
    }

    @PostMapping("/write")
    public String write(@Validated @ModelAttribute("postForm") BoardPostForm form, BindingResult result,
                        RedirectAttributes ra, HttpServletResponse response) {
        if ("archive".equals(form.getBoard())) {
            result.rejectValue("board", "board", "자료실에는 '자료 올리기'로 올려 주세요.");
        }
        if (form.getContent() == null || form.getContent().isBlank()) {
            result.rejectValue("content", "content", "내용을 입력하세요.");
        }
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "board/write";
        }
        boardService.write(form, loginUser.name(), loginUser.dept());
        ra.addFlashAttribute("message", "게시글이 등록되었습니다.");
        return "redirect:/board?tab=" + form.getBoard();
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        BoardPostForm form = new BoardPostForm();
        form.setBoard("archive");
        model.addAttribute("postForm", form);
        return "board/upload";
    }

    @PostMapping("/upload")
    public String upload(@Validated @ModelAttribute("postForm") BoardPostForm form, BindingResult result,
                         RedirectAttributes ra, HttpServletResponse response) {
        form.setBoard("archive");
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "board/upload";
        }
        boardService.write(form, loginUser.name(), loginUser.dept());
        ra.addFlashAttribute("message", "자료가 등록되었습니다. (첨부파일 저장은 아직 연결 전입니다)");
        return "redirect:/board?tab=archive";
    }
}
