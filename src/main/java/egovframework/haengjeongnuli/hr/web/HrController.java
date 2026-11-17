package egovframework.haengjeongnuli.hr.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.hr.service.AttendanceService;
import egovframework.haengjeongnuli.hr.service.AttendanceVO;

/** 인사: 내 인사정보, 오늘 근태, 월별 출퇴근 기록, 복무 신청 */
@Controller
public class HrController {

    private final AttendanceService attendanceService;
    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.hr.service.WorkPayService workPay;
    private final egovframework.haengjeongnuli.hr.service.DutyService dutyService;
    private final egovframework.haengjeongnuli.user.service.UserService userService;
    private final egovframework.haengjeongnuli.approval.service.ApprovalService approvalService;
    private final egovframework.haengjeongnuli.cmm.audit.AuditService auditService;

    public HrController(AttendanceService attendanceService, egovframework.haengjeongnuli.hr.service.DutyService dutyService,
                        egovframework.haengjeongnuli.user.service.UserService userService,
                        egovframework.haengjeongnuli.approval.service.ApprovalService approvalService,
                        egovframework.haengjeongnuli.cmm.audit.AuditService auditService) {
        this.attendanceService = attendanceService;
        this.dutyService = dutyService;
        this.userService = userService;
        this.approvalService = approvalService;
        this.auditService = auditService;
    }

    /** 관리자가 바꾼 인사정보가 바로 보이도록 DB에서 다시 읽는다 */
    private LoginUser fresh(LoginUser me) {
        LoginUser u = userService.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    @GetMapping("/hr")
    public String index(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) String month, Model model) {
        AttendanceVO current = attendanceService.current(me.getLoginId());
        AttendanceVO today = current != null ? current : attendanceService.today(me.getLoginId());
        model.addAttribute("working", current != null);
        model.addAttribute("today", today);

        YearMonth ym = YearMonth.now();
        if (month != null) {
            try {
                ym = YearMonth.parse(month);
            } catch (DateTimeParseException ignored) {
                // 잘못된 값이면 이번 달
            }
        }
        if (ym.isAfter(YearMonth.now())) {
            ym = YearMonth.now();
        }
        List<AttendanceVO> records = attendanceService.month(me.getLoginId(), ym);
        model.addAttribute("ym", ym);
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.isBefore(YearMonth.now()) ? ym.plusMonths(1).toString() : null);
        model.addAttribute("records", records);
        model.addAttribute("daysWorked", records.size());
        LoginUser profile = fresh(me);
        int thisYear = YearMonth.now().getYear();
        model.addAttribute("profile", profile);
        model.addAttribute("leave", dutyService.leave(profile, thisYear));
        model.addAttribute("sickUsed", dutyService.sickUsed(profile, thisYear));
        model.addAttribute("requests", dutyService.list(profile, null, 5));
        model.addAttribute("kinds", egovframework.haengjeongnuli.hr.service.DutyKind.values());
        return "hr/index";
    }

    @GetMapping("/hr/duty")
    public String dutyList(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year, Model model) {
        int y = year == null ? YearMonth.now().getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("requests", dutyService.list(me, y, 500));
        return "hr/duty-list";
    }

    @GetMapping("/hr/duty/new")
    public String dutyForm(@AuthenticationPrincipal LoginUser me,
                           @RequestParam(defaultValue = "ANNUAL") egovframework.haengjeongnuli.hr.service.DutyKind kind, Model model) {
        LoginUser profile = fresh(me);
        model.addAttribute("kind", kind);
        model.addAttribute("kinds", egovframework.haengjeongnuli.hr.service.DutyKind.values());
        model.addAttribute("leave", dutyService.leave(profile, YearMonth.now().getYear()));
        model.addAttribute("sickUsed", dutyService.sickUsed(profile, YearMonth.now().getYear()));
        model.addAttribute("approvers", approvalService.approverCandidates(me));
        model.addAttribute("today", java.time.LocalDate.now());
        model.addAttribute("tripRates", workPay.tripRates());
        model.addAttribute("otDailyCap", workPay.dailyCap());
        model.addAttribute("otMonthlyCap", workPay.monthlyCap());
        return "hr/duty-form";
    }

    @org.springframework.web.bind.annotation.PostMapping("/hr/duty")
    public String apply(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) egovframework.haengjeongnuli.hr.service.DutyKind kind,
                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate start,
                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate end,
                        @RequestParam(required = false) String halfDay, @RequestParam(required = false) String startTime,
                        @RequestParam(required = false) String endTime, @RequestParam(required = false) String destination,
                        @RequestParam(required = false) String reason, @RequestParam(required = false) String approver,
                        @RequestParam(required = false) String tripArea, @RequestParam(required = false) Double tripHours,
                        @RequestParam(defaultValue = "false") boolean officialCar, @RequestParam(required = false) Integer nights,
                        @RequestParam(required = false) String region,
                        org.springframework.web.servlet.mvc.support.RedirectAttributes ra) throws java.io.IOException {
        egovframework.haengjeongnuli.hr.service.DutyService.Result r = dutyService.apply(kind, start, end, halfDay, startTime,
                endTime, destination, reason, approver,
                new egovframework.haengjeongnuli.hr.service.DutyService.TripInput(tripArea, tripHours, officialCar, nights, region), fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/hr/duty/new" + (kind == null ? "" : "?kind=" + kind);
        }
        auditService.record(egovframework.haengjeongnuli.cmm.audit.AuditAction.APPROVAL_SUBMIT, "결재 " + r.docId(),
                kind.getLabel() + " 신청");
        return "redirect:/hr";
    }
}
