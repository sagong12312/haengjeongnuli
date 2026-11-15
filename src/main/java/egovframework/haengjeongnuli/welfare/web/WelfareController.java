package egovframework.haengjeongnuli.welfare.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import egovframework.haengjeongnuli.welfare.service.WelfareCaseVO;
import egovframework.haengjeongnuli.welfare.service.WelfareService;
import jakarta.servlet.http.HttpServletResponse;

/** 사회복지: 기초생활보장 급여 신청·조사·결정 (사회복지 담당만) */
@Controller
public class WelfareController {

    private final WelfareService service;
    private final AuditService audit;

    public WelfareController(WelfareService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/welfare")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "OPEN") String status,
                       @RequestParam(required = false) String q, Model model, HttpServletResponse response) {
        if (!service.canAccess(me)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            model.addAttribute("denied", true);
            return "welfare/list";
        }
        model.addAttribute("cases", service.list("ALL".equals(status) ? null : status, q));
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("benefits", WelfareCaseVO.BENEFITS);
        model.addAttribute("today", LocalDate.now());
        return "welfare/list";
    }

    @PostMapping("/welfare")
    public String apply(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birth,
                        @RequestParam(required = false) String address, @RequestParam(required = false) String phone,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedOn,
                        @RequestParam(required = false) List<String> benefits, RedirectAttributes ra) {
        allowed(me);
        WelfareService.Result r = service.apply(name, birth, address, phone, appliedOn, benefits, me);
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/welfare";
        }
        audit.record(AuditAction.WELFARE_EDIT, "복지 " + r.id(), "접수");
        return "redirect:/welfare/" + r.id();
    }

    @GetMapping("/welfare/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        WelfareCaseVO c = service.get(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        audit.record(AuditAction.WELFARE_VIEW, c.getCaseNo(), null);
        model.addAttribute("c", c);
        model.addAttribute("reasons", WelfareService.EXTEND_REASONS);
        model.addAttribute("objection", WelfareService.OBJECTION_GUIDE);
        model.addAttribute("today", LocalDate.now());
        return "welfare/view";
    }

    @PostMapping("/welfare/{id:\\d+}/survey")
    public String survey(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        allowed(me);
        return done(service.survey(id, note, me), id, "조사", ra);
    }

    @PostMapping("/welfare/{id:\\d+}/extend")
    public String extend(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(required = false) Integer reason, @RequestParam(required = false) String detail,
                         RedirectAttributes ra) {
        allowed(me);
        return done(service.extend(id, reason, detail, me), id, "통지 기한 연장", ra);
    }

    @PostMapping("/welfare/{id:\\d+}/decide")
    public String decide(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(defaultValue = "true") boolean grant, @RequestParam(required = false) List<String> granted,
                         @RequestParam(required = false) String summary, @RequestParam(required = false) String method,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate notifiedOn,
                         RedirectAttributes ra) {
        allowed(me);
        return done(service.decide(id, grant, granted, summary, method, start, notifiedOn, me), id, grant ? "지급 결정" : "부적합 결정", ra);
    }

    @PostMapping("/welfare/{id:\\d+}/withdraw")
    public String withdraw(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                           @RequestParam(required = false) String note, RedirectAttributes ra) {
        allowed(me);
        return done(service.withdraw(id, note, me), id, "취하", ra);
    }

    private void allowed(LoginUser me) {
        if (!service.canAccess(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String done(WelfareService.Result r, long id, String what, RedirectAttributes ra) {
        if (r.ok()) {
            audit.record(AuditAction.WELFARE_EDIT, "복지 " + id, what);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/welfare/" + id;
    }
}
