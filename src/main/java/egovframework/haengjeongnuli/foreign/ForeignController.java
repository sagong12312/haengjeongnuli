package egovframework.haengjeongnuli.foreign;

import java.time.LocalDate;

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
import egovframework.haengjeongnuli.cmm.audit.PrivacyPurgeService;

/** 외국인주민: 국적별 현황(모든 직원), 지원 대장(담당 부서만) */
@Controller
public class ForeignController {

    private final ForeignService service;
    private final AuditService audit;
    private final PrivacyPurgeService purge;

    public ForeignController(ForeignService service, AuditService audit, PrivacyPurgeService purge) {
        this.service = service;
        this.audit = audit;
        this.purge = purge;
    }

    private void allowed(LoginUser me) {
        if (!service.canAccess(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/foreign")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "true") boolean active,
                       @RequestParam(required = false) String nationality, @RequestParam(required = false) String q, Model model) {
        boolean access = service.canAccess(me);
        model.addAttribute("stats", service.stats());
        model.addAttribute("access", access);
        model.addAttribute("depts", String.join("·", service.getDepts()));
        if (access) {
            model.addAttribute("rows", service.list(active, nationality, q));
            model.addAttribute("nationalities", service.nationalities());
            audit.record(AuditAction.FOREIGN_VIEW, "외국인주민 목록", active ? "지원 중" : "종료");
        }
        model.addAttribute("active", active);
        model.addAttribute("nationality", nationality);
        model.addAttribute("q", q);
        model.addAttribute("keepDays", service.getKeepDays());
        model.addAttribute("today", LocalDate.now());
        return "foreign/list";
    }

    @PostMapping("/foreign")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                           @RequestParam(required = false) String nationality,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
                           @RequestParam(required = false) String address, @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String language, @RequestParam(required = false) String stayStatus,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate consentOn,
                           @RequestParam(defaultValue = "false") boolean consent, RedirectAttributes ra) {
        allowed(me);
        ForeignService.Result r = service.register(name, nationality, birthDate, address, phone, language, stayStatus, consentOn, consent, me);
        if (r.ok()) {
            audit.record(AuditAction.FOREIGN_SAVE, "외국인주민 " + r.id(), "등록");
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() ? "redirect:/foreign/" + r.id() : "redirect:/foreign";
    }

    @GetMapping("/foreign/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        ForeignResidentVO r = service.get(id);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        audit.record(AuditAction.FOREIGN_VIEW, "외국인주민 " + id, null);
        model.addAttribute("r", r);
        model.addAttribute("supports", service.supports(id));
        model.addAttribute("kinds", ForeignSupportVO.KINDS);
        model.addAttribute("keepDays", service.getKeepDays());
        model.addAttribute("today", LocalDate.now());
        return "foreign/view";
    }

    @PostMapping("/foreign/{id:\\d+}/{action:support|end}")
    public String action(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                         @RequestParam(required = false) String kind, @RequestParam(required = false) String content,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        allowed(me);
        ForeignService.Result r = "support".equals(action) ? service.addSupport(id, on, kind, content, me) : service.end(id, on, reason, me);
        if (r.ok()) {
            audit.record(AuditAction.FOREIGN_SAVE, "외국인주민 " + id, action + " " + (kind == null ? "" : kind) + (reason == null ? "" : reason));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/foreign/" + id;
    }

    @PostMapping("/foreign/purge")
    public String purgeNow(@AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        allowed(me);
        PrivacyPurgeService.Purged p = purge.purge(me.getLoginId());
        ra.addFlashAttribute("message", "보유기간이 지난 개인정보를 파기했습니다: 외국인주민 " + p.foreignResidents() + "명, 민방위 " + p.civilDefense() + "명");
        return "redirect:/foreign?active=false";
    }
}
