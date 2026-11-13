package egovframework.haengjeongnuli.approval.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 전자결재: 문서함 목록과 기안하기 */
@Controller
@RequestMapping("/approval")
public class ApprovalController {

    public record Box(String id, String label, String desc, String status, String badge) {}

    static final List<Box> BOXES = List.of(
            new Box("wait", "결재 대기", "내가 결재할 문서", "결재 대기", "badge-orange"),
            new Box("prog", "진행 중", "내가 올린 결재 진행", "진행 중", "badge-blue"),
            new Box("done", "완료", "결재가 끝난 문서", "완료", "badge-green"),
            new Box("back", "반려", "돌려받은 문서", "반려", "badge-red"),
            new Box("recv", "수신 문서", "접수·배부할 문서", "접수 대기", "badge-gray"));

    @GetMapping
    public String list(@RequestParam(defaultValue = "wait") String box, Model model) {
        Box current = BOXES.stream().filter(b -> b.id().equals(box)).findFirst().orElse(BOXES.get(0));
        model.addAttribute("boxes", BOXES);
        model.addAttribute("current", current);
        return "approval/list";
    }

    @GetMapping("/draft")
    public String draftForm() {
        return "approval/draft";
    }

    /** 결재 상신·임시저장 (저장소 연동 전에는 안내만 한다) */
    @PostMapping("/draft")
    public String draftSubmit(@RequestParam(defaultValue = "send") String action, RedirectAttributes ra) {
        if ("save".equals(action)) {
            ra.addFlashAttribute("message", "임시저장되었습니다.");
            return "redirect:/approval/draft";
        }
        ra.addFlashAttribute("message", "결재가 상신되었습니다. 진행 상황은 '진행 중' 문서함에서 확인할 수 있습니다.");
        return "redirect:/approval?box=prog";
    }
}
