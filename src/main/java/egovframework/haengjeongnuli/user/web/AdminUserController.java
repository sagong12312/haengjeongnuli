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
import egovframework.haengjeongnuli.dept.service.DeptService;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.PasswordResetService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;

/** 시스템 관리 > 직원 계정 (관리자 전용, SecurityConfig 에서 /admin/** 제한) */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AuditService auditService;
    private final DeptService deptService;
    private final PasswordResetService passwordReset;

    public AdminUserController(UserService userService, AuditService auditService, DeptService deptService,
                               PasswordResetService passwordReset) {
        this.userService = userService;
        this.auditService = auditService;
        this.deptService = deptService;
        this.passwordReset = passwordReset;
    }

    /** 비밀번호 분실 요청: 본인을 확인한 뒤 임시 비밀번호를 발급한다 (한 번만 보여 준다) */
    @PostMapping("/pw-requests/{reqId}/issue")
    public String issueTemp(@PathVariable long reqId, @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        PasswordResetService.Issued r = passwordReset.issue(reqId, me);
        if (r == null) {
            ra.addFlashAttribute("message", "이미 처리했거나 없는 요청입니다.");
            return "redirect:/admin/users";
        }
        auditService.record(AuditAction.USER_PASSWORD_RESET, r.loginId(), "분실 요청 " + reqId + " 임시 비밀번호 발급");
        ra.addFlashAttribute("tempPassword", r);
        ra.addFlashAttribute("message", r.userName() + "(" + r.loginId() + ")의 임시 비밀번호를 발급하고 잠금을 풀었습니다. 본인에게 직접 전달하세요.");
        return "redirect:/admin/users#pw-requests";
    }

    @PostMapping("/pw-requests/{reqId}/reject")
    public String rejectRequest(@PathVariable long reqId, @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        boolean ok = passwordReset.reject(reqId, me);
        if (ok) {
            auditService.record(AuditAction.PASSWORD_HELP_REJECT, "분실 요청 " + reqId, null);
        }
        ra.addFlashAttribute("message", ok ? "요청을 반려했습니다." : "이미 처리했거나 없는 요청입니다.");
        return "redirect:/admin/users#pw-requests";
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
        if (form.getDeptName() != null && !form.getDeptName().isBlank() && !deptService.isActive(form.getDeptName())) {
            result.rejectValue("deptName", "dept", "부서는 부서 관리에 있는 지금 쓰는 부서에서 고르세요.");
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

    /** 인사정보 (이름·직급·사번·직렬·임용일·내선번호) */
    @GetMapping("/{userId}/profile")
    public String profileForm(@PathVariable long userId, Model model) {
        LoginUser u = userService.findById(userId);
        if (u == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("u", u);
        return "user/admin-profile";
    }

    @PostMapping("/{userId}/profile")
    public String profile(@PathVariable long userId, @RequestParam String userName,
                          @RequestParam(required = false) String positionName, @RequestParam(required = false) String empNo,
                          @RequestParam(required = false) String jobSeries,
                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hiredOn,
                          @RequestParam(required = false) String extension, RedirectAttributes ra) {
        LoginUser u = userService.findById(userId);
        if (u == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String name = userName.trim();
        if (name.isEmpty() || name.length() > 100 || len(positionName) > 50 || len(empNo) > 20 || len(jobSeries) > 50
                || len(extension) > 20) {
            ra.addFlashAttribute("message", "입력 길이를 확인하세요 (이름 100자, 직급·직렬 50자, 사번·내선번호 20자).");
            return "redirect:/admin/users/" + userId + "/profile";
        }
        if (hiredOn != null && hiredOn.isAfter(java.time.LocalDate.now())) {
            ra.addFlashAttribute("message", "임용일은 오늘 이전이어야 합니다.");
            return "redirect:/admin/users/" + userId + "/profile";
        }
        u.setUserName(name);
        u.setPositionName(blank(positionName));
        u.setEmpNo(blank(empNo));
        u.setJobSeries(blank(jobSeries));
        u.setHiredOn(hiredOn);
        u.setExtension(blank(extension));
        userService.updateProfile(u);
        auditService.record(AuditAction.USER_PROFILE, u.getLoginId(), null);
        ra.addFlashAttribute("message", u.getUserName() + "(" + u.getLoginId() + ")의 인사정보를 저장했습니다.");
        return "redirect:/admin/users";
    }

    private static int len(String s) {
        return s == null ? 0 : s.trim().length();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 사회복지 담당 지정·해제 (복지 급여 신청 화면은 지정된 직원만 연다) */
    @PostMapping("/{userId}/welfare")
    public String welfare(@PathVariable long userId, @RequestParam boolean value, RedirectAttributes ra) {
        userService.setWelfareStaff(userId, value);
        auditService.record(AuditAction.USER_WELFARE, "사용자 " + userId, value ? "지정" : "해제");
        ra.addFlashAttribute("message", value ? "사회복지 담당으로 지정했습니다. 다시 로그인하면 반영됩니다."
                : "사회복지 담당을 해제했습니다. 다시 로그인하면 반영됩니다.");
        return "redirect:/admin/users";
    }

    /** 처리과 기록물관리책임자 지정·해제 (등록정보 수정을 확인한다, 공공기록물법 시행규칙 제15조) */
    @PostMapping("/{userId}/records-manager")
    public String recordsManager(@PathVariable long userId, @RequestParam boolean value, RedirectAttributes ra) {
        userService.setRecordsManager(userId, value);
        auditService.record(AuditAction.USER_RECORDS_MANAGER, "사용자 " + userId, value ? "지정" : "해제");
        ra.addFlashAttribute("message", value ? "기록물관리책임자로 지정했습니다. 다시 로그인하면 반영됩니다."
                : "기록물관리책임자를 해제했습니다. 다시 로그인하면 반영됩니다.");
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
        if (!deptService.isActive(dept)) {
            ra.addFlashAttribute("message", "부서는 부서 관리에 있는 지금 쓰는 부서에서 고르세요.");
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
        model.addAttribute("pwRequests", passwordReset.recent(20));
        java.util.List<LoginUser> users = userService.list();
        model.addAttribute("users", users);
        model.addAttribute("activeAdmins", users.stream().filter(u -> u.isEnabled() && u.isAdmin()).count());
        model.addAttribute("depts", deptService.activeNames());
        model.addAttribute("roles", LoginUser.Role.values());
        model.addAttribute("rule", PasswordPolicy.DESCRIPTION);
    }
}
