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
import egovframework.haengjeongnuli.user.service.UserService;

/** 로그인 화면, 내 비밀번호 변경 */
@Controller
public class LoginController {

    private final UserService userService;
    private final AuditService auditService;
    private final PasswordAgeInterceptor passwordAge;

    public LoginController(UserService userService, AuditService auditService, PasswordAgeInterceptor passwordAge) {
        this.userService = userService;
        this.auditService = auditService;
        this.passwordAge = passwordAge;
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
                case "locked" -> "비밀번호를 " + LoginUser.MAX_FAILED + "번 틀려 계정이 잠겼습니다. 시스템 관리자에게 잠금 해제를 요청하세요.";
                case "disabled" -> "사용이 중지된 계정입니다. 시스템 관리자에게 문의하세요.";
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
                                 Model model, RedirectAttributes ra) {
        String problem = !newPassword.equals(confirmPassword) ? "새 비밀번호 확인이 맞지 않습니다."
                : newPassword.equals(currentPassword) ? "지금 비밀번호와 다른 비밀번호를 입력하세요."
                : PasswordPolicy.check(newPassword, me.getLoginId());
        if (problem == null && !userService.changeOwnPassword(me.getUserId(), currentPassword, newPassword)) {
            problem = "현재 비밀번호가 맞지 않습니다.";
        }
        if (problem != null) {
            model.addAttribute("error", problem);
            addPasswordInfo(me, model);
            return "user/password";
        }
        // 지금 로그인 정보에도 바로 반영해서 변경 화면으로 다시 보내지 않게 한다
        me.setPasswordChangedAt(java.time.LocalDateTime.now());
        auditService.record(AuditAction.PASSWORD_CHANGE, me.getLoginId(), null);
        ra.addFlashAttribute("message", "비밀번호를 바꿨습니다.");
        return "redirect:/";
    }
}
