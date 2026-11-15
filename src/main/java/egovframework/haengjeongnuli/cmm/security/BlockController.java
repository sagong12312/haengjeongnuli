package egovframework.haengjeongnuli.cmm.security;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

/** 차단 안내와 이의 신청(차단된 사람), 차단 관리(관리자) */
@Controller
public class BlockController {

    private final AbuseGuard guard;
    private final AbuseMapper mapper;
    private final AuditService audit;
    private final NotificationService notifications;
    private final UserService users;

    public BlockController(AbuseGuard guard, AbuseMapper mapper, AuditService audit, NotificationService notifications,
                           UserService users) {
        this.guard = guard;
        this.mapper = mapper;
        this.audit = audit;
        this.notifications = notifications;
        this.users = users;
    }

    /** DB 시각 값(Timestamp)을 화면에서 쓰는 LocalDateTime 으로 바꾼다 */
    static java.util.List<Map<String, Object>> times(java.util.List<Map<String, Object>> rows) {
        rows.forEach(BlockController::times);
        return rows;
    }

    static Map<String, Object> times(Map<String, Object> row) {
        if (row != null) {
            row.replaceAll((k, v) -> v instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : v);
        }
        return row;
    }

    @GetMapping("/blocked")
    public String blocked(HttpServletRequest request, Model model) {
        Map<String, Object> b = guard.blockOf(request.getRemoteAddr());
        if (b == null) {
            return "redirect:/";
        }
        model.addAttribute("block", times(b));
        model.addAttribute("appealed", mapper.countAppeals(((Number) b.get("blockId")).longValue()) > 0);
        return "user/blocked";
    }

    @PostMapping("/blocked/appeal")
    @Transactional
    public String appeal(HttpServletRequest request, @RequestParam(required = false) String name,
                         @RequestParam(required = false) String contact, @RequestParam(required = false) String message,
                         RedirectAttributes ra) {
        String ip = request.getRemoteAddr();
        Map<String, Object> b = guard.blockOf(ip);
        if (b == null) {
            return "redirect:/";
        }
        long blockId = ((Number) b.get("blockId")).longValue();
        String n = name == null ? "" : name.trim();
        String m = message == null ? "" : message.trim();
        if (mapper.countAppeals(blockId) > 0) {
            ra.addFlashAttribute("message", "이미 이의를 신청했습니다. 관리자가 확인하면 차단이 풀립니다.");
        } else if (n.isEmpty() || n.length() > 100 || m.isEmpty() || m.length() > 1000
                || (contact != null && contact.trim().length() > 100)) {
            ra.addFlashAttribute("message", "이름과 내용을 적어 주세요 (이름 100자, 내용 1,000자 이내).");
        } else {
            mapper.insertAppeal(mapper.nextAppealId(), blockId, ip, n, contact == null || contact.isBlank() ? null : contact.trim(), m,
                    LocalDateTime.now());
            audit.recordAs(null, AuditAction.ABUSE_APPEAL, ip, n);
            for (LoginUser u : users.list()) {
                if (u.isEnabled() && u.isAdmin()) {
                    notifications.notify(u.getLoginId(), n + "님이 접속 차단(" + ip + ")에 이의를 신청했습니다.", "/admin/blocks");
                }
            }
            ra.addFlashAttribute("message", "이의 신청을 받았습니다. 관리자가 확인하면 차단이 풀립니다. 차단 시간이 지나도 저절로 풀립니다.");
        }
        return "redirect:/blocked";
    }

    @GetMapping("/admin/blocks")
    public String admin(Model model) {
        model.addAttribute("active", times(mapper.selectActive(LocalDateTime.now())));
        model.addAttribute("history", times(mapper.selectRecent()));
        model.addAttribute("appeals", times(mapper.selectAppeals()));
        model.addAttribute("events", guard.recentEvents());
        model.addAttribute("guard", guard);
        return "user/admin-blocks";
    }

    @PostMapping("/admin/blocks/release")
    public String release(@RequestParam String ip, @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        boolean ok = guard.release(ip, me.getLoginId());
        if (ok) {
            audit.record(AuditAction.ABUSE_RELEASE, ip, null);
        }
        ra.addFlashAttribute("message", ok ? ip + " 차단을 풀었습니다." : "지금 차단 중인 주소가 아닙니다.");
        return "redirect:/admin/blocks";
    }

    @PostMapping("/admin/blocks/appeals/{id}")
    @Transactional
    public String handleAppeal(@PathVariable long id, @RequestParam boolean accept, @RequestParam(required = false) String note,
                               @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        String ip = mapper.openAppealIp(id);
        if (ip == null) {
            ra.addFlashAttribute("message", "이미 처리했거나 없는 이의 신청입니다.");
            return "redirect:/admin/blocks";
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (!accept && n == null) {
            ra.addFlashAttribute("message", "기각 사유를 적으세요.");
            return "redirect:/admin/blocks";
        }
        mapper.closeAppeal(id, accept ? "ACCEPTED" : "REJECTED", me.getLoginId(), LocalDateTime.now(),
                n == null ? null : n.length() > 500 ? n.substring(0, 500) : n);
        if (accept) {
            guard.release(ip, me.getLoginId());
        }
        audit.record(accept ? AuditAction.ABUSE_RELEASE : AuditAction.ABUSE_APPEAL, ip, accept ? "이의 인정" : "이의 기각: " + n);
        ra.addFlashAttribute("message", accept ? ip + " 이의를 받아들이고 차단을 풀었습니다." : "이의를 기각했습니다. 차단 시간이 지나면 풀립니다.");
        return "redirect:/admin/blocks";
    }
}
