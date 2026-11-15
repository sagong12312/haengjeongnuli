package egovframework.haengjeongnuli.hygiene.web;

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

import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.hygiene.service.FoodBusinessVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneCheckVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneService;

/** 환경·위생: 식품 영업소 대장, 위생 점검, 처리 중인 영업신고 민원 */
@Controller
public class HygieneController {

    private final HygieneService service;
    private final CivilService civilService;
    private final AuditService audit;

    public HygieneController(HygieneService service, CivilService civilService, AuditService audit) {
        this.service = service;
        this.civilService = civilService;
        this.audit = audit;
    }

    @GetMapping("/hygiene")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String q,
                       @RequestParam(required = false) String type, Model model) {
        CivilSearchVO s = CivilSearchVO.forViewer(me);
        s.setAffairs(List.of("FOOD_REPORT", "FOOD_CHANGE"));
        s.setOpenOnly(true);
        s.setLimit(50);
        model.addAttribute("businesses", service.list(q, type));
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("types", FoodBusinessVO.TYPES);
        model.addAttribute("complaints", civilService.search(s));
        model.addAttribute("canEdit", service.canEdit(me));
        model.addAttribute("depts", String.join("·", service.getDepts()));
        model.addAttribute("today", LocalDate.now());
        return "hygiene/list";
    }

    @PostMapping("/hygiene")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                           @RequestParam(required = false) String owner, @RequestParam(required = false) String address,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportedOn,
                           @RequestParam(required = false) String receiptNo, RedirectAttributes ra) {
        HygieneService.Result r = service.register(name, owner, address, type, reportedOn, receiptNo, me);
        if (r.ok()) {
            audit.record(AuditAction.HYGIENE_EDIT, "영업소 " + r.id(), "등록");
            ra.addFlashAttribute("message", r.message());
            return "redirect:/hygiene/" + r.id();
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hygiene";
    }

    @GetMapping("/hygiene/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        FoodBusinessVO b = service.get(id);
        if (b == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("b", b);
        model.addAttribute("checks", service.checks(id));
        model.addAttribute("actions", HygieneCheckVO.ACTIONS);
        model.addAttribute("canEdit", service.canEdit(me));
        model.addAttribute("today", LocalDate.now());
        return "hygiene/view";
    }

    @PostMapping("/hygiene/{id:\\d+}/check")
    public String check(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                        @RequestParam(required = false) String result, @RequestParam(required = false) String violation,
                        @RequestParam(required = false) String action, RedirectAttributes ra) {
        HygieneService.Result r = service.inspect(id, on, result, violation, action, me);
        if (r.ok()) {
            audit.record(AuditAction.HYGIENE_EDIT, "영업소 " + id, "점검 " + result);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hygiene/" + id;
    }

    @PostMapping("/hygiene/{id:\\d+}/close")
    public String close(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                        RedirectAttributes ra) {
        HygieneService.Result r = service.close(id, on, me);
        if (r.ok()) {
            audit.record(AuditAction.HYGIENE_EDIT, "영업소 " + id, "폐업");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hygiene/" + id;
    }
}
