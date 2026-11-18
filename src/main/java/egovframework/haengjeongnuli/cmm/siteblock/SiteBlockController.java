package egovframework.haengjeongnuli.cmm.siteblock;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** 유해사이트 차단: 관리자 목록·설정, 직원이 내려받는 차단 스크립트 */
@Controller
public class SiteBlockController {

    private final SiteBlockService service;
    private final AuditService audit;

    public SiteBlockController(SiteBlockService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/admin/site-block")
    public String admin(Model model) {
        var c = service.conf();
        model.addAttribute("conf", c);
        model.addAttribute("dnsServers", service.dnsServers(c));
        model.addAttribute("rules", service.rules());
        model.addAttribute("categories", SiteBlockService.CATEGORIES);
        model.addAttribute("appeals", service.appeals());
        return "cmm/admin-site-block";
    }

    private String done(SiteBlockService.Result r, String what, RedirectAttributes ra) {
        if (r.ok()) {
            audit.record(AuditAction.SECURITY_APP_SAVE, "유해사이트 차단", what.length() > 200 ? what.substring(0, 200) : what);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/admin/site-block";
    }

    @PostMapping("/admin/site-block/rules")
    public String add(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String domains,
                      @RequestParam(required = false) String category, @RequestParam(required = false) String memo, RedirectAttributes ra) {
        return done(service.add(domains, category, memo, me.getLoginId()), "도메인 추가 (" + category + ")", ra);
    }

    @PostMapping("/admin/site-block/rules/delete")
    public String remove(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String domain, RedirectAttributes ra) {
        return done(service.remove(domain, me.getLoginId()), "도메인 삭제 " + domain, ra);
    }

    @PostMapping("/admin/site-block/dns")
    public String dns(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String mode,
                      @RequestParam(required = false) String dns1, @RequestParam(required = false) String dns2, RedirectAttributes ra) {
        return done(service.setDns(mode, dns1, dns2, me.getLoginId()), "DNS " + mode, ra);
    }

    @PostMapping("/admin/site-block/{action:register|reinstall}")
    public String app(@AuthenticationPrincipal LoginUser me, @PathVariable String action, RedirectAttributes ra) {
        return done("register".equals(action) ? service.registerApp(me.getLoginId()) : service.requestReinstall(me.getLoginId()), action, ra);
    }

    /* ---------------------------------------------------------------- 차단 안내 · 항의(이의) 신청 */

    /** 막힌 사이트에 가려 하면 PC 의 안내 프로그램이 이 화면으로 넘긴다 */
    @GetMapping("/site-blocked")
    public String notice(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String site, Model model) {
        String d = SiteBlockService.normalizeSite(site);
        model.addAttribute("site", d);
        model.addAttribute("reason", d == null ? null : service.reasonFor(d));
        model.addAttribute("me", me);
        model.addAttribute("mine", me == null ? java.util.List.of() : service.myAppeals(me.getLoginId()));
        return "cmm/site-blocked";
    }

    @PostMapping("/site-blocked/appeal")
    public String appeal(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String site,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        SiteBlockService.Result r = service.appeal(site, reason, me);
        if (r.ok()) {
            audit.record(AuditAction.SECURITY_APP_SAVE, "사이트 차단 해제 신청", String.valueOf(SiteBlockService.normalizeSite(site)));
        }
        ra.addFlashAttribute("message", r.message());
        String d = SiteBlockService.normalizeSite(site);
        return "redirect:/site-blocked" + (d == null ? "" : "?site=" + d);
    }

    @PostMapping("/admin/site-block/appeals/{id:\\d+}/{action:accept|reject}")
    public String decide(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                         @RequestParam(required = false) String answer, RedirectAttributes ra) {
        return done(service.decide(id, "accept".equals(action), answer, me.getLoginId()), "해제 신청 " + id + " " + action, ra);
    }

    /** 직원이 자기 PC 에 까는 차단 스크립트. 윈도우 PowerShell 5 가 한글을 깨뜨리지 않게 UTF-8 BOM 을 붙인다 */
    @GetMapping("/security-apps/site-block.ps1")
    public ResponseEntity<byte[]> script(@AuthenticationPrincipal LoginUser me, jakarta.servlet.http.HttpServletRequest request) {
        // 안내 프로그램이 넘길 행정누리 주소: 직원이 스크립트를 받은 그 주소
        String base = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        byte[] body = service.script(base).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        audit.record(AuditAction.SECURITY_APP_ACK, "유해사이트 차단 스크립트", "내려받기");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"site-block.ps1\"")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(out);
    }
}
