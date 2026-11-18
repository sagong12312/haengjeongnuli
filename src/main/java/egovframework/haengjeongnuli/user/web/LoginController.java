package egovframework.haengjeongnuli.user.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.security.PasswordAgeInterceptor;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.PasswordResetService;
import egovframework.haengjeongnuli.user.service.UserService;

/** 로그인 화면, 내 비밀번호 변경 */
@Controller
public class LoginController {

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.security.SecurityService securityService;

    private final UserService userService;
    private final AuditService auditService;
    private final PasswordAgeInterceptor passwordAge;
    private final PasswordResetService passwordReset;

    public LoginController(UserService userService, AuditService auditService, PasswordAgeInterceptor passwordAge,
                           PasswordResetService passwordReset) {
        this.userService = userService;
        this.auditService = auditService;
        this.passwordAge = passwordAge;
        this.passwordReset = passwordReset;
    }

    /** 비밀번호 분실: 아이디·이름으로 요청하면 관리자가 본인을 확인하고 임시 비밀번호를 전달한다 */
    @GetMapping("/password-help")
    public String passwordHelp() {
        return "user/password-help";
    }

    @PostMapping("/password-help")
    public String passwordHelpRequest(@RequestParam(required = false) String loginId,
                                      @RequestParam(required = false) String userName,
                                      @RequestParam(required = false) String contact,
                                      jakarta.servlet.http.HttpServletRequest request, RedirectAttributes ra) {
        boolean saved = passwordReset.request(loginId, userName, contact, request.getRemoteAddr());
        if (saved) {
            auditService.recordAs(loginId.trim(), AuditAction.PASSWORD_HELP_REQUEST, loginId.trim(), request.getRemoteAddr());
        }
        // 계정이 있는지 알리지 않도록 결과와 상관없이 같은 안내를 보여 준다
        ra.addFlashAttribute("info", "요청을 받았습니다. 아이디와 이름이 맞으면 시스템 관리자가 본인을 확인한 뒤 임시 비밀번호를 직접 전달합니다.");
        return "redirect:/password-help";
    }

    /** 비밀번호를 바꿔야 하는 이유와 다음 변경 기한 */
    private void addPasswordInfo(LoginUser me, Model model) {
        model.addAttribute("rule", PasswordPolicy.DESCRIPTION);
        model.addAttribute("maxDays", passwordAge.getMaxDays());
        if (me.getPasswordChangedAt() == null) {
            model.addAttribute("mustChange", "처음 받은 비밀번호입니다. 본인만 아는 비밀번호로 바꿔야 다른 화면을 쓸 수 있습니다.");
        } else if (me.isPasswordExpired(passwordAge.getMaxDays())) {
            model.addAttribute("mustChange", "비밀번호를 바꾼 지 " + passwordAge.getMaxDays()
                    + "일이 지났습니다. 새 비밀번호로 바꿔야 다른 화면을 쓸 수 있습니다.");
        } else {
            model.addAttribute("nextChange", me.getPasswordChangedAt().toLocalDate().plusDays(passwordAge.getMaxDays()));
        }
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout, Model model) {
        if (error != null) {
            model.addAttribute("error", switch (error) {
                case "locked" -> "비밀번호를 " + LoginUser.MAX_FAILED + "번 틀려 계정이 " + LoginUser.getLockMinutes()
                        + "분 동안 잠겼습니다. 잠시 뒤 다시 시도하거나 시스템 관리자에게 잠금 해제를 요청하세요.";
                case "timeout" -> "오랫동안 사용하지 않아 자동으로 로그아웃되었습니다. 다시 로그인하세요.";
                case "ip" -> "이 PC에서 로그인을 여러 번 틀려 잠시 로그인을 막았습니다. 몇 분 뒤 다시 시도하세요.";
                case "revoked" -> "계정 상태가 바뀌어(사용 중지·비밀번호 재설정 등) 로그아웃되었습니다. 다시 로그인하세요.";
                case "disabled" -> "사용이 중지된 계정입니다. 오래 쓰지 않은 계정은 자동으로 중지됩니다. 시스템 관리자에게 문의하세요.";
                case "otp" -> "2단계 인증 번호를 여러 번 틀려 로그아웃했습니다. 다시 로그인하세요.";
                case "otp_locked" -> "2단계 인증 번호가 계속 틀려 15분 동안 잠갔습니다. 잠시 뒤에 다시 로그인하세요.";
                case "otp_timeout" -> "2단계 인증 번호를 5분 안에 넣지 않아 로그아웃했습니다. 다시 로그인하세요.";
                case "reauth" -> "비밀번호 재확인을 여러 번 틀려 로그아웃했습니다.";
                default -> "아이디 또는 비밀번호가 맞지 않습니다.";
            });
        }
        if (logout != null) {
            model.addAttribute("info", "로그아웃되었습니다.");
        }
        return "user/login";
    }

    @GetMapping("/mypage/password")
    public String passwordForm(@AuthenticationPrincipal LoginUser me, Model model) {
        addPasswordInfo(me, model);
        return "user/password";
    }

    @PostMapping("/mypage/password")
    public String changePassword(@AuthenticationPrincipal LoginUser me,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model, RedirectAttributes ra,
                                 jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String problem = !newPassword.equals(confirmPassword) ? "새 비밀번호 확인이 맞지 않습니다."
                : newPassword.equals(currentPassword) ? "지금 비밀번호와 다른 비밀번호를 입력하세요."
                : PasswordPolicy.check(newPassword, me.getLoginId());
        if (problem == null && securityService.reused(me, newPassword)) {
            problem = "최근에 쓴 비밀번호(" + securityService.historySize() + "개)는 다시 쓸 수 없습니다.";
        }
        LoginUser before = problem == null ? userService.findByLoginId(me.getLoginId()) : null;
        if (problem == null && !userService.changeOwnPassword(me.getUserId(), currentPassword, newPassword)) {
            problem = "현재 비밀번호가 맞지 않습니다.";
        }
        if (problem != null) {
            model.addAttribute("error", problem);
            addPasswordInfo(me, model);
            return "user/password";
        }
        securityService.remember(me.getLoginId(), before == null ? null : before.getPasswordHash());
        // 지금 로그인 정보도 새 비밀번호·보안 번호로 바꿔서 세션이 끊기거나 변경 화면으로 다시 가지 않게 한다
        LoginUser fresh = userService.findByLoginId(me.getLoginId());
        egovframework.haengjeongnuli.cmm.security.SessionRevalidationFilter.replace(request, response,
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(), fresh);
        auditService.record(AuditAction.PASSWORD_CHANGE, me.getLoginId(), null);
        ra.addFlashAttribute("message", "비밀번호를 바꿨습니다.");
        return "redirect:/";
    }
}
