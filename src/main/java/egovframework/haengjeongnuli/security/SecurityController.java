package egovframework.haengjeongnuli.security;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 2단계 인증 로그인·설정, 내 보안(로그인 기록), 관리 작업 전 비밀번호 재확인 */
@Controller
public class SecurityController {

    static final int OTP_MAX_FAILS = 5;

    private final SecurityService service;
    private final UserService users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SecurityWeb.Reauth reauth;

    public SecurityController(SecurityService service, UserService users, PasswordEncoder encoder, AuditService audit,
                              SecurityWeb.Reauth reauth) {
        this.service = service;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.reauth = reauth;
    }

    /* ---------------------------------------------------------------- 로그인 두 번째 단계 */

    @GetMapping("/login/otp")
    public String otpForm(HttpSession session) {
        return session.getAttribute(SecurityWeb.OTP_PENDING) == null ? "redirect:/" : "user/otp";
    }

    @PostMapping("/login/otp")
    public String otp(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String code, HttpSession session,
                      Model model) {
        if (session.getAttribute(SecurityWeb.OTP_PENDING) == null) {
            return "redirect:/";
        }
        if (service.checkOtp(me.getLoginId(), code)) {
            session.removeAttribute(SecurityWeb.OTP_PENDING);
            session.removeAttribute(SecurityWeb.OTP_FAILS);
            return "redirect:/";
        }
        int fails = session.getAttribute(SecurityWeb.OTP_FAILS) instanceof Integer i ? i + 1 : 1;
        if (fails >= OTP_MAX_FAILS) {
            session.invalidate();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            return "redirect:/login?error=otp";
        }
        session.setAttribute(SecurityWeb.OTP_FAILS, fails);
        model.addAttribute("error", "번호가 맞지 않습니다. 인증 앱에 지금 나온 6자리를 넣으세요. (" + (OTP_MAX_FAILS - fails) + "번 더 틀리면 로그아웃)");
        return "user/otp";
    }

    /* ---------------------------------------------------------------- 내 보안 */

    @GetMapping("/mypage/security")
    public String mySecurity(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("otpOn", service.otpEnabled(me.getLoginId()));
        String secret = service.pendingSecret(me);
        model.addAttribute("secret", secret);
        if (secret != null) {
            model.addAttribute("secretSpaced", secret.replaceAll("(.{4})", "$1 ").trim());
            model.addAttribute("otpUri", Totp.uri("행정누리", me.getLoginId(), secret));
        }
        model.addAttribute("history", service.loginHistory(me.getLoginId()));
        model.addAttribute("required", service.otpRequiredButMissing(me));
        model.addAttribute("pwHistory", service.historySize());
        return "user/security";
    }

    @PostMapping("/mypage/otp/start")
    public String start(@AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        if (service.startOtp(me) == null) {
            ra.addFlashAttribute("message", "이미 2단계 인증을 쓰고 있습니다.");
        }
        return "redirect:/mypage/security#otp";
    }

    @PostMapping("/mypage/otp/enable")
    public String enable(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String code, RedirectAttributes ra) {
        ra.addFlashAttribute("message", service.enableOtp(me, code).message());
        return "redirect:/mypage/security#otp";
    }

    @PostMapping("/mypage/otp/disable")
    public String disable(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String password, RedirectAttributes ra) {
        ra.addFlashAttribute("message", service.disableOtp(me, password).message());
        return "redirect:/mypage/security#otp";
    }

    /* ---------------------------------------------------------------- 관리 작업 전 비밀번호 재확인 */

    private static String safeNext(String next) {
        return next != null && next.startsWith("/admin/") && !next.contains("//") && !next.contains("\\") ? next : "/admin/users";
    }

    @GetMapping("/reauth")
    public String reauthForm(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", safeNext(next));
        model.addAttribute("minutes", reauth.minutes());
        return "user/reauth";
    }

    @PostMapping("/reauth")
    public String reauth(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String password,
                         @RequestParam(required = false) String next, HttpServletRequest request, Model model, RedirectAttributes ra) {
        LoginUser db = users.findByLoginId(me.getLoginId());
        if (db == null || password == null || !encoder.matches(password, db.getPasswordHash())) {
            audit.record(AuditAction.REAUTH, me.getLoginId(), "실패");
            model.addAttribute("error", "비밀번호가 맞지 않습니다.");
            model.addAttribute("next", safeNext(next));
            model.addAttribute("minutes", reauth.minutes());
            return "user/reauth";
        }
        request.getSession(true).setAttribute(SecurityWeb.REAUTH_AT, Instant.now());
        audit.record(AuditAction.REAUTH, me.getLoginId(), "성공");
        ra.addFlashAttribute("message", "확인했습니다. " + reauth.minutes() + "분 동안 관리 작업을 할 수 있습니다. 하려던 작업을 다시 눌러 주세요.");
        return "redirect:" + safeNext(next);
    }

    /* ---------------------------------------------------------------- 관리자: 2단계 인증 초기화 */

    @PostMapping("/admin/users/{userId:\\d+}/otp-reset")
    public String resetOtp(@AuthenticationPrincipal LoginUser me, @PathVariable long userId, RedirectAttributes ra) {
        LoginUser u = users.list().stream().filter(x -> x.getUserId() == userId).findFirst().orElse(null);
        ra.addFlashAttribute("message", u == null ? "직원을 찾을 수 없습니다." : service.resetOtp(u.getLoginId(), me).message());
        return "redirect:/admin/users";
    }
}
