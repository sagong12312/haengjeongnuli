package egovframework.haengjeongnuli.user.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;

/** 시스템 관리 > 직원 계정 (관리자 전용, SecurityConfig 에서 /admin/** 제한) */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AuditService auditService;

    public AdminUserController(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("userForm")) {
            model.addAttribute("userForm", new UserForm());
        }
        addCommon(model);
        return "user/admin-users";
    }

    @PostMapping
    public String create(@Validated @ModelAttribute("userForm") UserForm form, BindingResult result,
                         Model model, RedirectAttributes ra, HttpServletResponse response) {
        String pwProblem = PasswordPolicy.check(form.getPassword(), form.getLoginId());
        if (pwProblem != null) {
            result.rejectValue("password", "password", pwProblem);
        }
        if (!result.hasErrors()) {
            try {
                LoginUser u = userService.create(form);
                auditService.record(AuditAction.USER_CREATE, u.getLoginId(), u.getRole().getLabel());
                ra.addFlashAttribute("message", u.getUserName() + "(" + u.getLoginId() + ") 계정을 만들었습니다. 초기 비밀번호를 본인에게 따로 전달하세요.");
                return "redirect:/admin/users";
            } catch (IllegalStateException e) {
                result.rejectValue("loginId", "duplicate", "이미 있는 아이디입니다.");
            }
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        addCommon(model);
        return "user/admin-users";
    }

    @PostMapping("/{userId}/unlock")
    public String unlock(@PathVariable long userId, RedirectAttributes ra) {
        userService.unlock(userId);
        auditService.record(AuditAction.USER_UNLOCK, "사용자 " + userId, null);
        ra.addFlashAttribute("message", "잠금을 풀었습니다.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/enabled")
    public String enabled(@PathVariable long userId, @RequestParam boolean value,
                          @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        if (!value && me.getUserId() == userId) {
            ra.addFlashAttribute("message", "자기 계정은 사용 중지할 수 없습니다.");
            return "redirect:/admin/users";
        }
        userService.setEnabled(userId, value);
        auditService.record(value ? AuditAction.USER_ENABLE : AuditAction.USER_DISABLE, "사용자 " + userId, null);
        ra.addFlashAttribute("message", value ? "계정을 다시 사용하게 했습니다." : "계정 사용을 중지했습니다.");
        return "redirect:/admin/users";
    }

    /** 세입 담당 지정·해제 (세입 화면은 지정된 직원만 연다) */
    @PostMapping("/{userId}/revenue")
    public String revenue(@PathVariable long userId, @RequestParam boolean value, RedirectAttributes ra) {
        userService.setRevenueStaff(userId, value);
        auditService.record(AuditAction.USER_REVENUE, "사용자 " + userId, value ? "지정" : "해제");
        ra.addFlashAttribute("message", value ? "세입 담당으로 지정했습니다. 다시 로그인하면 세입 화면이 열립니다."
                : "세입 담당을 해제했습니다. 다시 로그인하면 세입 화면이 닫힙니다.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/password")
    public String resetPassword(@PathVariable long userId, @RequestParam String newPassword, RedirectAttributes ra) {
        String problem = PasswordPolicy.check(newPassword, null);
        if (problem != null) {
            ra.addFlashAttribute("message", "비밀번호를 바꾸지 못했습니다: " + problem);
            return "redirect:/admin/users";
        }
        userService.resetPassword(userId, newPassword);
        auditService.record(AuditAction.USER_PASSWORD_RESET, "사용자 " + userId, null);
        ra.addFlashAttribute("message", "비밀번호를 새로 정하고 잠금도 풀었습니다. 본인에게 따로 전달하세요.");
        return "redirect:/admin/users";
    }

    /** 부서 이동 */
    @PostMapping("/{userId}/dept")
    public String moveDept(@PathVariable long userId, @RequestParam String deptName,
                           @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        String dept = deptName.trim();
        if (dept.isEmpty() || dept.length() > 100) {
            ra.addFlashAttribute("message", "부서 이름을 1~100자로 입력하세요.");
            return "redirect:/admin/users";
        }
        if (!userService.moveDept(userId, dept, me.getLoginId())) {
            ra.addFlashAttribute("message", "없는 계정입니다.");
            return "redirect:/admin/users";
        }
        auditService.record(AuditAction.USER_DEPT_MOVE, "사용자 " + userId, "→ " + dept);
        ra.addFlashAttribute("message", "부서를 '" + dept + "'(으)로 옮겼습니다."
                + (me.getUserId() == userId ? " 머리글의 부서는 다시 로그인하면 바뀝니다." : ""));
        return "redirect:/admin/users";
    }

    private void addCommon(Model model) {
        model.addAttribute("deptMoves", userService.recentDeptMoves(20));
        java.util.List<LoginUser> users = userService.list();
        model.addAttribute("users", users);
        // 부서 입력칸 자동완성용: 지금 쓰고 있는 부서 이름들
        model.addAttribute("depts", users.stream().map(LoginUser::getDeptName)
                .filter(d -> d != null && !d.isBlank()).distinct().sorted().toList());
        model.addAttribute("roles", LoginUser.Role.values());
        model.addAttribute("rule", PasswordPolicy.DESCRIPTION);
    }
}
