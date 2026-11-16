package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.time.Year;

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

/** 민방위: 대원 명부, 교육 통지·참석 (담당 부서만) */
@Controller
public class CdController {

    private final CdService service;
    private final AuditService audit;
    private final PrivacyPurgeService purge;

    public CdController(CdService service, AuditService audit, PrivacyPurgeService purge) {
        this.service = service;
        this.audit = audit;
        this.purge = purge;
    }

    private void allowed(LoginUser me) {
        if (!service.canAccess(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/civil-defense")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "true") boolean active,
                       @RequestParam(required = false) String unit, @RequestParam(required = false) String q, Model model) {
        allowed(me);
        model.addAttribute("rows", service.list(active, unit, q));
        model.addAttribute("overAge", active ? service.overAge() : java.util.List.of());
        model.addAttribute("trainings", service.trainings(Year.now().getValue()));
        model.addAttribute("units", service.units());
        model.addAttribute("active", active);
        model.addAttribute("unit", unit);
        model.addAttribute("q", q);
        model.addAttribute("maxAge", service.getMaxAge());
        model.addAttribute("keepDays", service.getKeepDays());
        model.addAttribute("reasons", CdMemberVO.RELEASE_REASONS);
        model.addAttribute("today", LocalDate.now());
        audit.record(AuditAction.CD_VIEW, "민방위 명부 목록", (active ? "편성" : "해제") + (unit == null ? "" : " " + unit));
        return "civildefense/list";
    }

    @PostMapping("/civil-defense")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
                           @RequestParam(required = false) String address, @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String unitName,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedOn,
                           RedirectAttributes ra) {
        allowed(me);
        CdService.Result r = service.register(name, birthDate, address, phone, unitName, joinedOn, me);
        if (r.ok()) {
            audit.record(AuditAction.CD_SAVE, "민방위 대원 " + r.id(), "편성");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil-defense";
    }

    @GetMapping("/civil-defense/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        CdMemberVO m = service.get(id);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        audit.record(AuditAction.CD_VIEW, "민방위 대원 " + id, null);
        model.addAttribute("m", m);
        model.addAttribute("history", service.history(id));
        model.addAttribute("reasons", CdMemberVO.RELEASE_REASONS);
        model.addAttribute("keepDays", service.getKeepDays());
        model.addAttribute("today", LocalDate.now());
        return "civildefense/view";
    }

    @PostMapping("/civil-defense/{id:\\d+}/{action:update|release}")
    public String memberAction(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                               @RequestParam(required = false) String address, @RequestParam(required = false) String phone,
                               @RequestParam(required = false) String unitName,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                               @RequestParam(required = false) String reason, RedirectAttributes ra) {
        allowed(me);
        CdService.Result r = "update".equals(action) ? service.update(id, address, phone, unitName, me) : service.release(id, on, reason, me);
        if (r.ok()) {
            audit.record(AuditAction.CD_SAVE, "민방위 대원 " + id, action + (reason == null ? "" : " " + reason));
        }
        ra.addFlashAttribute("message", r.message());
        return "release".equals(action) && r.ok() ? "redirect:/civil-defense" : "redirect:/civil-defense/" + id;
    }

    @PostMapping("/civil-defense/trainings")
    public String createTraining(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate heldOn,
                                 @RequestParam(required = false) String place, @RequestParam(required = false) Integer hours,
                                 RedirectAttributes ra) {
        allowed(me);
        CdService.Result r = service.createTraining(title, heldOn, place, hours, me);
        if (r.ok()) {
            audit.record(AuditAction.CD_SAVE, "민방위 교육 " + r.id(), title);
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() ? "redirect:/civil-defense/trainings/" + r.id() : "redirect:/civil-defense";
    }

    @GetMapping("/civil-defense/trainings/{id:\\d+}")
    public String training(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        allowed(me);
        CdTrainingVO t = service.training(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        audit.record(AuditAction.CD_VIEW, "민방위 교육 대상자 " + id, null);
        model.addAttribute("t", t);
        model.addAttribute("attends", service.attends(id));
        model.addAttribute("units", service.units());
        model.addAttribute("results", CdAttendVO.RESULTS);
        return "civildefense/training";
    }

    @PostMapping("/civil-defense/trainings/{id:\\d+}/{action:invite|result}")
    public String trainingAction(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                                 @RequestParam(required = false) String unit, @RequestParam(required = false) Long memberId,
                                 @RequestParam(required = false) String result, @RequestParam(required = false) String note,
                                 RedirectAttributes ra) {
        allowed(me);
        CdService.Result r = "invite".equals(action) ? service.invite(id, unit, me)
                : memberId == null ? new CdService.Result(false, "대상자를 고르세요.", null) : service.setResult(id, memberId, result, note, me);
        if (r.ok()) {
            audit.record(AuditAction.CD_SAVE, "민방위 교육 " + id, action + " " + (unit == null ? "" : unit) + (result == null ? "" : result));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil-defense/trainings/" + id;
    }

    /** 보유기간이 지난 해제 대원을 지금 파기 (매일 새벽에도 자동으로 한다) */
    @PostMapping("/civil-defense/purge")
    public String purgeNow(@AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        allowed(me);
        PrivacyPurgeService.Purged p = purge.purge(me.getLoginId());
        ra.addFlashAttribute("message", "보유기간이 지난 개인정보를 파기했습니다: 민방위 " + p.civilDefense() + "명, 외국인주민 " + p.foreignResidents() + "명");
        return "redirect:/civil-defense?active=false";
    }
}
