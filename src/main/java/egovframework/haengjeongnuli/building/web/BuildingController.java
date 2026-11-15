package egovframework.haengjeongnuli.building.web;

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

import egovframework.haengjeongnuli.building.service.BuildingCaseVO;
import egovframework.haengjeongnuli.building.service.BuildingService;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 건축·토지: 건축 인허가 대장과 처리 중인 건축 민원 */
@Controller
public class BuildingController {

    private final BuildingService service;
    private final CivilService civilService;
    private final AuditService audit;

    public BuildingController(BuildingService service, CivilService civilService, AuditService audit) {
        this.service = service;
        this.civilService = civilService;
        this.audit = audit;
    }

    @GetMapping("/building")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String q, Model model) {
        List<BuildingCaseVO> cases = service.list(q);
        CivilSearchVO s = CivilSearchVO.forViewer(me);
        s.setAffairs(List.of("BUILD_REPORT", "BUILD_REPORT_REVIEW", "BUILD_PERMIT"));
        s.setOpenOnly(true);
        s.setLimit(50);
        model.addAttribute("cases", cases);
        model.addAttribute("q", q);
        model.addAttribute("complaints", civilService.search(s));
        model.addAttribute("canEdit", service.canEdit(me));
        model.addAttribute("depts", String.join("·", service.getDepts()));
        model.addAttribute("overdue", cases.stream().filter(BuildingCaseVO::isOverdue).count());
        model.addAttribute("near", cases.stream().filter(BuildingCaseVO::isDeadlineNear).count());
        model.addAttribute("today", LocalDate.now());
        return "building/list";
    }

    @PostMapping("/building")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String kind,
                           @RequestParam(required = false) String receiptNo, @RequestParam(required = false) String owner,
                           @RequestParam(required = false) String site, @RequestParam(required = false) String use,
                           @RequestParam(required = false) Double area, @RequestParam(required = false) Integer floors,
                           @RequestParam(defaultValue = "false") boolean factory,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate decidedOn,
                           @RequestParam(required = false) String note, RedirectAttributes ra) {
        BuildingService.Result r = service.register(kind, receiptNo, owner, site, use, area, floors, factory, decidedOn, note, me);
        if (r.ok()) {
            audit.record(AuditAction.BUILDING_EDIT, site, "등록");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/building";
    }

    @GetMapping("/building/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        BuildingCaseVO c = service.get(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("c", c);
        model.addAttribute("canEdit", service.canEdit(me));
        model.addAttribute("today", LocalDate.now());
        return "building/view";
    }

    @PostMapping("/building/{id:\\d+}/start")
    public String start(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                        RedirectAttributes ra) {
        return done(service.started(id, on, me), id, "착공", ra);
    }

    @PostMapping("/building/{id:\\d+}/extend")
    public String extend(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(defaultValue = "12") int months, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        return done(service.extend(id, months, reason, me), id, "착수기한 연장", ra);
    }

    @PostMapping("/building/{id:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return done(service.cancel(id, reason, me), id, "취소·효력 상실", ra);
    }

    private String done(BuildingService.Result r, long id, String what, RedirectAttributes ra) {
        if (r.ok()) {
            audit.record(AuditAction.BUILDING_EDIT, "건축 " + id, what);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/building/" + id;
    }
}
