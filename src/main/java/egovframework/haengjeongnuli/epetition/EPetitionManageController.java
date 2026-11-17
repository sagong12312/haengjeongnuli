package egovframework.haengjeongnuli.epetition;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 전자민원 운영: 공지사항·자주 묻는 질문 쓰기, 만족도 응답·처리 통계 보기 (민원 창구 직원·관리자) */
@Controller
public class EPetitionManageController {

    private final EPetitionPortalService portal;
    private final AuditService audit;

    public EPetitionManageController(EPetitionPortalService portal, AuditService audit) {
        this.portal = portal;
        this.audit = audit;
    }

    private void allowed(LoginUser me) {
        if (!portal.canManage(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/epetition")
    public String manage(@AuthenticationPrincipal LoginUser me, Model model) {
        allowed(me);
        model.addAttribute("notices", portal.notices(1, null).get("items"));
        model.addAttribute("faqs", portal.faqsForStaff());
        model.addAttribute("satisfaction", portal.recentSatisfaction());
        model.addAttribute("stats", portal.stats());
        return "epetition/manage";
    }

    @GetMapping("/epetition/notices/new")
    public String newNotice(@AuthenticationPrincipal LoginUser me, Model model) {
        allowed(me);
        model.addAttribute("kind", "notice");
        return "epetition/form";
    }

    @GetMapping("/epetition/notices/{id:\\d+}")
    public String editNotice(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        java.util.Map<String, Object> n = portal.noticeForStaff(id);
        if (n == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("kind", "notice");
        model.addAttribute("item", n);
        return "epetition/form";
    }

    @PostMapping("/epetition/notices")
    public String saveNotice(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long id,
                             @RequestParam(required = false) String title, @RequestParam(required = false) String content,
                             @RequestParam(defaultValue = "false") boolean pinned, RedirectAttributes ra) {
        allowed(me);
        EPetitionPortalService.Result r = portal.saveNotice(id, title, content, pinned, me);
        if (r.ok()) {
            audit.record(AuditAction.EPETITION_NOTICE_SAVE, "전자민원 공지 " + r.id(), id == null ? "등록" : "수정");
            ra.addFlashAttribute("message", r.message());
            return "redirect:/epetition";
        }
        ra.addFlashAttribute("message", r.message());
        return id == null ? "redirect:/epetition/notices/new" : "redirect:/epetition/notices/" + id;
    }

    @PostMapping("/epetition/notices/{id:\\d+}/delete")
    public String deleteNotice(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        allowed(me);
        EPetitionPortalService.Result r = portal.deleteNotice(id, me);
        if (r.ok()) {
            audit.record(AuditAction.EPETITION_NOTICE_SAVE, "전자민원 공지 " + id, "삭제");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/epetition";
    }

    @GetMapping("/epetition/faqs/new")
    public String newFaq(@AuthenticationPrincipal LoginUser me, Model model) {
        allowed(me);
        model.addAttribute("kind", "faq");
        return "epetition/form";
    }

    @GetMapping("/epetition/faqs/{id:\\d+}")
    public String editFaq(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        java.util.Map<String, Object> f = portal.faq(id);
        if (f == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("kind", "faq");
        model.addAttribute("item", f);
        return "epetition/form";
    }

    @PostMapping("/epetition/faqs")
    public String saveFaq(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long id,
                          @RequestParam(required = false) String category, @RequestParam(required = false) String question,
                          @RequestParam(required = false) String answer, @RequestParam(required = false) Integer sortOrder,
                          @RequestParam(defaultValue = "false") boolean use, RedirectAttributes ra) {
        allowed(me);
        EPetitionPortalService.Result r = portal.saveFaq(id, category, question, answer, sortOrder, use, me);
        if (r.ok()) {
            audit.record(AuditAction.EPETITION_FAQ_SAVE, "전자민원 FAQ " + r.id(), id == null ? "등록" : "수정");
            ra.addFlashAttribute("message", r.message());
            return "redirect:/epetition";
        }
        ra.addFlashAttribute("message", r.message());
        return id == null ? "redirect:/epetition/faqs/new" : "redirect:/epetition/faqs/" + id;
    }

    @PostMapping("/epetition/faqs/{id:\\d+}/delete")
    public String deleteFaq(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        allowed(me);
        EPetitionPortalService.Result r = portal.deleteFaq(id, me);
        if (r.ok()) {
            audit.record(AuditAction.EPETITION_FAQ_SAVE, "전자민원 FAQ " + id, "삭제");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/epetition";
    }
}
