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

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
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
