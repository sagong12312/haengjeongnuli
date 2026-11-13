package egovframework.haengjeongnuli.user.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.UserService;

/** 로그인 화면, 내 비밀번호 변경 */
@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
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
    public String passwordForm(Model model) {
        model.addAttribute("rule", PasswordPolicy.DESCRIPTION);
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
            model.addAttribute("rule", PasswordPolicy.DESCRIPTION);
            return "user/password";
        }
        ra.addFlashAttribute("message", "비밀번호를 바꿨습니다.");
        return "redirect:/";
    }
}
