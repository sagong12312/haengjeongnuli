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

/** 환경·위생: 식품·공중위생 영업소 대장, 위생 점검, 행정처분 대장, 처리 중인 영업신고 민원 */
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
        model.addAttribute("foodTypes", FoodBusinessVO.FOOD_TYPES);
        model.addAttribute("publicTypes", FoodBusinessVO.PUBLIC_TYPES);
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
        model.addAttribute("sanctions", sanctions.byBusiness(id));
        model.addAttribute("sanctionKinds", egovframework.haengjeongnuli.hygiene.service.SanctionVO.KINDS);
        java.util.Map<String, String> basis = new java.util.LinkedHashMap<>();
        for (String k : egovframework.haengjeongnuli.hygiene.service.SanctionVO.KINDS) {
            basis.put(k, egovframework.haengjeongnuli.hygiene.service.SanctionService.defaultBasis(b.getLawName(), k));
        }
        model.addAttribute("basisHint", basis);
        return "hygiene/view";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.hygiene.service.SanctionService sanctions;

    @PostMapping("/hygiene/{id:\\d+}/sanctions")
    public String sanction(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String kind,
                           @RequestParam(required = false) String violation, @RequestParam(required = false) String basis,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate decidedOn,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
                           @RequestParam(required = false) String amount, @RequestParam(required = false) Long checkId,
                           @RequestParam(defaultValue = "false") boolean publish, RedirectAttributes ra) {
        Long amt = null;
        if (amount != null && !amount.isBlank()) {
            String t = amount.replace(",", "").replace("원", "").trim();
            amt = t.matches("\\d{1,15}") ? Long.valueOf(t) : -1L;
        }
        HygieneService.Result r = sanctions.record(id, kind, violation, basis, decidedOn, startDate, endDate, amt, checkId, publish, me);
        if (r.ok()) {
            audit.record(AuditAction.HYGIENE_SANCTION, "영업소 " + id, kind + " " + r.message());
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hygiene/" + id;
    }

    @PostMapping("/hygiene/sanctions/{sid:\\d+}/{action:cancel|publish|unpublish}")
    public String sanctionAction(@AuthenticationPrincipal LoginUser me, @PathVariable long sid, @PathVariable String action,
                                 @RequestParam(required = false) String reason, RedirectAttributes ra) {
        egovframework.haengjeongnuli.hygiene.service.SanctionVO s = sanctions.get(sid);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        HygieneService.Result r = "cancel".equals(action) ? sanctions.cancel(sid, reason, me) : sanctions.setPublic(sid, "publish".equals(action), me);
        if (r.ok()) {
            audit.record(AuditAction.HYGIENE_SANCTION, "처분 " + s.getSanctionNo(), action + (reason == null ? "" : " " + reason));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hygiene/" + s.getBizId();
    }

    /** 행정처분 대장 (연도별, 법률별) */
    @GetMapping("/hygiene/sanctions")
    public String sanctionList(@RequestParam(required = false) Integer year, @RequestParam(required = false) String law, Model model) {
        int now = java.time.Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now : year;
        model.addAttribute("rows", sanctions.range(y, law));
        model.addAttribute("year", y);
        model.addAttribute("years", List.of(now - 2, now - 1, now));
        model.addAttribute("law", law);
        return "hygiene/sanctions";
    }

    @GetMapping("/hygiene/sanctions.csv")
    public org.springframework.http.ResponseEntity<byte[]> sanctionCsv(@RequestParam(required = false) Integer year,
                                                                       @RequestParam(required = false) String law) {
        int now = java.time.Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now : year;
        egovframework.haengjeongnuli.cmm.text.Csv csv = egovframework.haengjeongnuli.cmm.text.Csv.of("처분번호", "처분일", "상호", "업종",
                "대표자", "소재지", "법률", "처분", "시작일", "끝나는 날", "금액", "위반 내용", "근거", "공개", "상태", "취소 사유");
        for (egovframework.haengjeongnuli.hygiene.service.SanctionVO s : sanctions.range(y, law)) {
            csv.row(s.getSanctionNo(), s.getDecidedOn(), s.getBizName(), s.getBizType(), s.getOwnerName(), s.getAddress(), s.getLawName(),
                    s.getKind(), s.getStartDate(), s.getEndDate(), s.getAmount(), s.getViolation(), s.getBasis(), s.isPublic() ? "공개" : "",
                    s.isCanceled() ? "취소" : "유효", s.getCancelReason());
        }
        audit.record(AuditAction.FIN_EXPORT, "행정처분 대장 " + y, law);
        return csv.download(y + "_행정처분대장.csv");
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
