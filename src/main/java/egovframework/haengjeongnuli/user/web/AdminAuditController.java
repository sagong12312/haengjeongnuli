package egovframework.haengjeongnuli.user.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditSearchVO;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 시스템 관리 > 접속·작업 기록 (관리자 전용, /admin/**) */
@Controller
@RequestMapping("/admin/audit")
public class AdminAuditController {

    private final AuditService auditService;
    private final egovframework.haengjeongnuli.cmm.audit.AuditReviewService review;

    public AdminAuditController(AuditService auditService, egovframework.haengjeongnuli.cmm.audit.AuditReviewService review) {
        this.auditService = auditService;
        this.review = review;
    }

    /** 접속기록 점검: 위·변조 확인, 이상 징후, 점검 결과 기록 */
    @GetMapping("/review")
    public String reviewPage(@org.springframework.web.bind.annotation.RequestParam(required = false)
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                             @org.springframework.web.bind.annotation.RequestParam(required = false)
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                             Model model) {
        java.time.LocalDate firstOfThisMonth = java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate f = from == null ? firstOfThisMonth.minusMonths(1) : from;
        java.time.LocalDate t = to == null ? firstOfThisMonth.minusDays(1) : to;
        if (t.isBefore(f)) {
            t = f;
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(f, t) > 366) {
            t = f.plusDays(366);
        }
        model.addAttribute("rep", review.report(f, t));
        model.addAttribute("chain", review.verify());
        model.addAttribute("reviews", review.reviews());
        model.addAttribute("results", egovframework.haengjeongnuli.cmm.audit.AuditReviewService.RESULTS);
        model.addAttribute("alerts", review);
        return "user/admin-audit-review";
    }

    @org.springframework.web.bind.annotation.PostMapping("/review")
    public String saveReview(@org.springframework.web.bind.annotation.RequestParam
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                             @org.springframework.web.bind.annotation.RequestParam
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                             @org.springframework.web.bind.annotation.RequestParam(required = false) String result,
                             @org.springframework.web.bind.annotation.RequestParam(required = false) String note,
                             @org.springframework.security.core.annotation.AuthenticationPrincipal egovframework.haengjeongnuli.cmm.LoginUser me,
                             org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        String problem = review.save(from, to, result, note, me);
        if (problem == null) {
            auditService.record(AuditAction.AUDIT_REVIEW, from + "~" + to, result);
        }
        ra.addFlashAttribute("message", problem == null ? "점검 결과를 기록했습니다." : problem);
        return "redirect:/admin/audit/review?from=" + from + "&to=" + to;
    }

    @GetMapping
    public String list(@ModelAttribute("search") AuditSearchVO search, Model model) {
        int total = auditService.count(search);
        int totalPages = Math.max(1, (total + AuditSearchVO.PAGE_SIZE - 1) / AuditSearchVO.PAGE_SIZE);
        if (search.getPage() > totalPages) {
            search.setPage(totalPages);
        }
        model.addAttribute("logs", auditService.search(search));
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("actions", AuditAction.values());
        return "user/admin-audit";
    }
}
