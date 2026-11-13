package egovframework.haengjeongnuli.board.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 게시판: 공지사항·건의사항·자료실, 글쓰기, 자료 올리기 */
@Controller
@RequestMapping("/board")
public class BoardController {

    public record Tab(String id, String label) {}

    static final List<Tab> TABS = List.of(
            new Tab("notice", "공지사항"),
            new Tab("suggest", "건의사항"),
            new Tab("archive", "자료실"));

    @GetMapping
    public String list(@RequestParam(defaultValue = "notice") String tab, Model model) {
        Tab current = TABS.stream().filter(t -> t.id().equals(tab)).findFirst().orElse(TABS.get(0));
        model.addAttribute("tabs", TABS);
        model.addAttribute("current", current);
        model.addAttribute("rowCount", 8);
        return "board/list";
    }

    @GetMapping("/write")
    public String writeForm() {
        return "board/write";
    }

    @PostMapping("/write")
    public String write(RedirectAttributes ra) {
        ra.addFlashAttribute("message", "게시글이 등록되었습니다.");
        return "redirect:/board";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "board/upload";
    }

    @PostMapping("/upload")
    public String upload(RedirectAttributes ra) {
        ra.addFlashAttribute("message", "자료가 등록되었습니다.");
        return "redirect:/board?tab=archive";
    }
}
