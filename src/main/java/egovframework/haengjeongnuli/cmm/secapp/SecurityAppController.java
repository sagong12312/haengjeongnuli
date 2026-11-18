package egovframework.haengjeongnuli.cmm.secapp;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 보안 프로그램 설치 안내 (직원), 항목 관리·확인 현황 (관리자, /admin/** ) */
@Controller
public class SecurityAppController {

    private final SecurityAppService apps;
    private final AuditService audit;

    public SecurityAppController(SecurityAppService apps, AuditService audit) {
        this.apps = apps;
        this.audit = audit;
    }

    @GetMapping("/security-apps")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String required, Model model) {
        model.addAttribute("apps", apps.forUser(me.getLoginId()));
        model.addAttribute("blocked", apps.pending(me.getLoginId()) > 0);
        return "cmm/security-apps";
    }

    @PostMapping("/security-apps/{id:\\d+}/ack")
    public String ack(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        if (apps.ack(id, me.getLoginId())) {
            SecurityAppVO a = apps.get(id);
            audit.record(AuditAction.SECURITY_APP_ACK, "보안 프로그램 " + id, a.getName() + " 판 " + a.getVersion());
            ra.addFlashAttribute("message", a.getName() + " 설치를 확인했습니다."
                    + (apps.pending(me.getLoginId()) == 0 ? " 이제 행정누리를 쓸 수 있습니다." : ""));
        } else {
            ra.addFlashAttribute("message", "안내 중인 항목이 아닙니다.");
        }
        return "redirect:/security-apps";
    }

    @GetMapping("/admin/security-apps")
    public String admin(@RequestParam(required = false) Long app, @RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("apps", apps.all());
        model.addAttribute("activeUsers", apps.activeUsers());
        if (app != null && apps.get(app) != null) {
            model.addAttribute("target", apps.get(app));
            model.addAttribute("notAcked", apps.notAcked(app));
        }
        model.addAttribute("editing", edit == null ? null : apps.get(edit));
        return "cmm/admin-security-apps";
    }

    @PostMapping("/admin/security-apps")
    public String save(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long appId,
                       @RequestParam(required = false) String name, @RequestParam(required = false) String purpose,
                       @RequestParam(required = false) String guide, @RequestParam(required = false) String url,
                       @RequestParam(defaultValue = "false") boolean required, @RequestParam(required = false) Integer sortNo,
                       @RequestParam(required = false) String target, @RequestParam(required = false) String checkUrl,
                       RedirectAttributes ra) {
        String problem = apps.save(appId, name, purpose, guide, url, required, sortNo, target, checkUrl, me.getLoginId());
        if (problem == null) {
            audit.record(AuditAction.SECURITY_APP_SAVE, "보안 프로그램 " + (appId == null ? "새 항목" : appId), name + " " + (url == null ? "" : url));
        }
        ra.addFlashAttribute("message", problem == null ? "저장했습니다: " + name : problem);
        return "redirect:/admin/security-apps";
    }

    @PostMapping("/admin/security-apps/{id:\\d+}/{action:bump|use|stop}")
    public String action(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action, RedirectAttributes ra) {
        boolean ok = switch (action) {
            case "bump" -> apps.bump(id, me.getLoginId());
            case "use" -> apps.setUse(id, true, me.getLoginId());
            default -> apps.setUse(id, false, me.getLoginId());
        };
        if (ok) {
            audit.record(AuditAction.SECURITY_APP_SAVE, "보안 프로그램 " + id, action);
        }
        ra.addFlashAttribute("message", !ok ? "없는 항목입니다." : switch (action) {
            case "bump" -> "모든 직원에게 다시 설치·확인을 요청했습니다.";
            case "use" -> "다시 안내합니다.";
            default -> "안내를 멈췄습니다.";
        });
        return "redirect:/admin/security-apps";
    }
}
