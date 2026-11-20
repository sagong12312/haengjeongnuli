package egovframework.haengjeongnuli.reg;

import java.time.LocalDate;
import java.util.Map;

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
import egovframework.haengjeongnuli.user.service.UserService;

/** 대장 화면 */
@Controller
public class RegController {

    private final RegService service;
    private final UserService users;
    private final AuditService audit;

    public RegController(RegService service, UserService users, AuditService audit) {
        this.service = service;
        this.users = users;
        this.audit = audit;
    }

    private LoginUser fresh(LoginUser me) {
        LoginUser u = users.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    private static RegDef def(String code) {
        RegDef d = RegCatalog.get(code);
        if (d == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return d;
    }

    private static Integer year(String y) {
        return y != null && y.matches("\\d{4}") ? Integer.valueOf(y) : null;
    }

    @GetMapping("/reg")
    public String hub(@AuthenticationPrincipal LoginUser me, Model model) {
        RegService.Viewer v = new RegService.Viewer(fresh(me));
        model.addAttribute("groups", RegCatalog.byGroup());
        model.addAttribute("counts", service.counts(v));
        model.addAttribute("due", service.dueSoon(v, 7));
        model.addAttribute("total", RegCatalog.all().size());
        return "reg/hub";
    }

    @GetMapping("/reg/search")
    public String search(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("items", service.searchAll(new RegService.Viewer(fresh(me)), q));
        return "reg/search";
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}")
    public String list(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status, @RequestParam(required = false) String view,
                       @RequestParam(required = false) String year, @RequestParam(defaultValue = "1") int page, Model model) {
        RegDef d = def(code);
        RegService.Viewer v = new RegService.Viewer(fresh(me));
        boolean open = "open".equals(view);
        boolean late = "late".equals(view);
        model.addAttribute("d", d);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("view", view == null ? "" : view);
        model.addAttribute("year", year(year) == null ? "" : year);
        model.addAttribute("years", java.util.stream.IntStream.rangeClosed(0, 4).map(i -> LocalDate.now().getYear() - i).boxed().toList());
        model.addAttribute("result", service.page(d, v, q, status, open, late, year(year), page));
        model.addAttribute("stats", service.stats(d, v, year(year)));
        return "reg/list";
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}/new")
    public String newForm(@PathVariable String code, Model model) {
        model.addAttribute("d", def(code));
        model.addAttribute("e", null);
        model.addAttribute("today", LocalDate.now());
        return "reg/form";
    }

    @PostMapping("/reg/{code:[a-zA-Z]+}")
    public String create(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @RequestParam Map<String, String> params,
                         RedirectAttributes ra) {
        RegDef d = def(code);
        RegService.Result r = service.save(d, null, params, params.get("status"), fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            ra.addFlashAttribute("draft", params);
            return "redirect:/reg/" + code + "/new";
        }
        audit.record(AuditAction.REG, d.getName() + " " + r.id(), "등록");
        return "redirect:/reg/" + code + "/" + r.id();
    }

    private Map<String, Object> entry(LoginUser me, RegDef d, long id) {
        Map<String, Object> e = service.get(id, new RegService.Viewer(fresh(me)));
        if (e == null || !d.getCode().equals(e.get("code"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return e;
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}")
    public String detail(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id, Model model) {
        RegDef d = def(code);
        Map<String, Object> e = entry(me, d, id);
        if (d.getFields().stream().anyMatch(RegDef.Field::pii) || d.getScope() == RegDef.Scope.SELF) {
            // 개인정보가 있는 대장은 누가 열어 봤는지 남긴다
            audit.record(AuditAction.REG, d.getName() + " " + id, "열람");
        }
        model.addAttribute("d", d);
        model.addAttribute("e", e);
        return "reg/view";
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}/print")
    public String print(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id, Model model) {
        RegDef d = def(code);
        model.addAttribute("d", d);
        model.addAttribute("e", entry(me, d, id));
        model.addAttribute("printedBy", fresh(me).getUserName());
        model.addAttribute("printedAt", java.time.LocalDateTime.now());
        audit.record(AuditAction.REG, d.getName() + " " + id, "인쇄");
        return "reg/print";
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id, Model model) {
        RegDef d = def(code);
        Map<String, Object> e = entry(me, d, id);
        if (!Boolean.TRUE.equals(e.get("canEdit"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("d", d);
        model.addAttribute("e", e);
        model.addAttribute("today", LocalDate.now());
        return "reg/form";
    }

    @PostMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}")
    public String update(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id,
                         @RequestParam Map<String, String> params, RedirectAttributes ra) {
        RegDef d = def(code);
        RegService.Result r = service.save(d, id, params, params.get("status"), fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            ra.addFlashAttribute("draft", params);
            return "redirect:/reg/" + code + "/" + id + "/edit";
        }
        audit.record(AuditAction.REG, d.getName() + " " + id, "수정");
        return "redirect:/reg/" + code + "/" + id;
    }

    @PostMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}/status")
    public String status(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id,
                         @RequestParam(required = false) String status, @RequestParam(required = false) String note, RedirectAttributes ra) {
        RegDef d = def(code);
        RegService.Result r = service.setStatus(id, status, note, fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (r.ok()) {
            audit.record(AuditAction.REG, d.getName() + " " + id, "상태 " + status);
        }
        return "redirect:/reg/" + code + "/" + id;
    }

    @PostMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}/delete")
    public String delete(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        RegDef d = def(code);
        RegService.Result r = service.delete(id, reason, fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/reg/" + code + "/" + id;
        }
        audit.record(AuditAction.REG, d.getName() + " " + id, "삭제: " + reason);
        return "redirect:/reg/" + code;
    }

    @PostMapping("/reg/{code:[a-zA-Z]+}/{id:\\d+}/copy")
    public String copy(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @PathVariable long id, RedirectAttributes ra) {
        def(code);
        RegService.Result r = service.copy(id, fresh(me));
        ra.addFlashAttribute("message", r.message());
        return "redirect:/reg/" + code + "/" + (r.ok() ? r.id() + "/edit" : String.valueOf(id));
    }

    @GetMapping("/reg/{code:[a-zA-Z]+}/export.csv")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal LoginUser me, @PathVariable String code, @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String status, @RequestParam(required = false) String view,
                                         @RequestParam(required = false) String year) {
        RegDef d = def(code);
        return service.csv(d, new RegService.Viewer(fresh(me)), q, status, "open".equals(view), "late".equals(view), year(year))
                .download(d.getName() + "_" + LocalDate.now() + ".csv");
    }
}
