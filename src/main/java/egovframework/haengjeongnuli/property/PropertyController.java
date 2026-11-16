package egovframework.haengjeongnuli.property;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import egovframework.haengjeongnuli.cmm.text.Csv;

/** 공유재산 대장, 사용허가·대부 */
@Controller
public class PropertyController {

    private final PropertyService service;
    private final AuditService audit;

    public PropertyController(PropertyService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/property")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String cls,
                       @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean all, Model model) {
        model.addAttribute("rows", service.list(cls, q, all));
        model.addAttribute("ending", service.ending());
        model.addAttribute("cls", cls);
        model.addAttribute("q", q);
        model.addAttribute("all", all);
        model.addAttribute("kinds", PropertyVO.KINDS);
        model.addAttribute("classes", PropertyVO.CLASSES);
        model.addAttribute("canRegister", service.canRegister(me));
        model.addAttribute("depts", String.join("·", service.getDepts()));
        model.addAttribute("today", LocalDate.now());
        return "property/list";
    }

    @GetMapping("/property.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) String cls, @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "false") boolean all) {
        Csv csv = Csv.of("재산번호", "종류", "구분", "재산명", "소재지", "면적(㎡)", "평정가격", "취득일", "관리부서", "용도", "상태", "진행 중 허가·대부", "처분일", "처분 내용");
        for (PropertyVO p : service.list(cls, q, all)) {
            csv.row(p.getPropNo(), p.getPropKind(), p.getPropClass(), p.getName(), p.getLocation(), p.getArea(), p.getAppraised(),
                    p.getAcquiredOn(), p.getDeptName(), p.getUsePurpose(), p.isHeld() ? "보유" : "처분", p.getActiveUses(), p.getDisposedOn(),
                    p.getDisposeReason());
        }
        audit.record(AuditAction.FIN_EXPORT, "공유재산 대장", cls);
        return csv.download("공유재산대장.csv");
    }

    @PostMapping("/property")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String propKind,
                           @RequestParam(required = false) String propClass, @RequestParam(required = false) String name,
                           @RequestParam(required = false) String location, @RequestParam(required = false) String area,
                           @RequestParam(required = false) String appraised,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredOn,
                           @RequestParam(required = false) String deptName, @RequestParam(required = false) String usePurpose,
                           RedirectAttributes ra) {
        PropertyVO in = new PropertyVO();
        in.setPropKind(propKind);
        in.setPropClass(propClass);
        in.setName(name);
        in.setLocation(location);
        in.setArea(decimal(area));
        Long v = money(appraised);
        in.setAppraised(v == null ? -1 : v);
        in.setAcquiredOn(acquiredOn);
        in.setDeptName(deptName);
        in.setUsePurpose(usePurpose);
        PropertyService.Result r = service.register(in, me);
        if (r.ok()) {
            audit.record(AuditAction.PROPERTY_SAVE, "재산 " + r.id(), "등록: " + name);
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() ? "redirect:/property/" + r.id() : "redirect:/property";
    }

    @GetMapping("/property/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        PropertyVO p = service.get(id);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("p", p);
        model.addAttribute("uses", service.uses(id));
        model.addAttribute("canEdit", service.canEdit(p, me));
        model.addAttribute("canDispose", service.canRegister(me));
        model.addAttribute("today", LocalDate.now());
        return "property/view";
    }

    @PostMapping("/property/{id:\\d+}/{action:value|use|dispose}")
    public String action(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                         @RequestParam(required = false) String appraised, @RequestParam(required = false) String usePurpose,
                         @RequestParam(required = false) String userName, @RequestParam(required = false) String purpose,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         @RequestParam(required = false) String annualFee, @RequestParam(required = false) String feeNote,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        PropertyService.Result r = switch (action) {
            case "value" -> service.updateValue(id, money(appraised), usePurpose, me);
            case "use" -> service.addUse(id, userName, purpose, startDate, endDate, money(annualFee), feeNote, me);
            default -> service.dispose(id, on, reason, me);
        };
        if (r.ok()) {
            audit.record(AuditAction.PROPERTY_SAVE, "재산 " + id, action + " " + nz(userName) + nz(reason));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/property/" + id;
    }

    @PostMapping("/property/uses/{useId:\\d+}/end")
    public String endUse(@AuthenticationPrincipal LoginUser me, @PathVariable long useId, @RequestParam(required = false) String note,
                         RedirectAttributes ra) {
        PropertyService.Result r = service.endUse(useId, note, me);
        if (r.ok()) {
            audit.record(AuditAction.PROPERTY_SAVE, "사용허가·대부 " + useId, "종료 " + note);
        }
        ra.addFlashAttribute("message", r.message());
        return r.id() == null ? "redirect:/property" : "redirect:/property/" + r.id();
    }

    static Long money(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.replace(",", "").replace("원", "").trim();
        return t.matches("\\d{1,15}") ? Long.valueOf(t) : -1L;
    }

    static BigDecimal decimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(-1);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s + " ";
    }
}
