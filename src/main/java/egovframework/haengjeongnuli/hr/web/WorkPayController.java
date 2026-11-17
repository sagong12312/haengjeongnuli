package egovframework.haengjeongnuli.hr.web;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.text.Csv;
import egovframework.haengjeongnuli.hr.service.DutyRequestVO;
import egovframework.haengjeongnuli.hr.service.OvertimeCalculator;
import egovframework.haengjeongnuli.hr.service.WorkPayService;
import egovframework.haengjeongnuli.user.service.UserService;

/** 근무상황·시간외근무 계산, 근태 정정, 출장 복명·정산, 부서 근태, 수당·여비 설정 */
@Controller
public class WorkPayController {

    private final WorkPayService service;
    private final ApprovalService approvals;
    private final AuditService audit;
    private final UserService users;

    public WorkPayController(WorkPayService service, ApprovalService approvals, AuditService audit, UserService users) {
        this.service = service;
        this.approvals = approvals;
        this.audit = audit;
        this.users = users;
    }

    private LoginUser fresh(LoginUser me) {
        LoginUser u = users.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    private static YearMonth month(String m) {
        YearMonth ym = YearMonth.now();
        if (m != null) {
            try {
                ym = YearMonth.parse(m);
            } catch (DateTimeParseException ignored) {
                // 잘못된 값이면 이번 달
            }
        }
        return ym.isAfter(YearMonth.now()) ? YearMonth.now() : ym;
    }

    /* ---------------------------------------------------------------- 근무상황·시간외근무 */

    @GetMapping("/hr/sheet")
    public String sheet(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month, Model model) {
        YearMonth ym = month(month);
        model.addAttribute("sheet", service.sheet(fresh(me), ym));
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.isBefore(YearMonth.now()) ? ym.plusMonths(1).toString() : null);
        model.addAttribute("profile", fresh(me));
        return "hr/sheet";
    }

    @GetMapping("/hr/sheet.csv")
    public ResponseEntity<byte[]> sheetCsv(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month) {
        WorkPayService.Sheet s = service.sheet(fresh(me), month(month));
        Csv csv = Csv.of("날짜", "요일", "구분", "출근", "퇴근", "휴가·출장", "지각", "조퇴", "퇴근 미기록", "정정",
                "실제 시간외(분)", "명령 시간(분)", "인정(분)", "명령 밖 초과(분)", "비고");
        for (WorkPayService.Row r : s.rows()) {
            OvertimeCalculator.Day d = r.overtime();
            csv.row(r.date(), r.weekday(), r.holiday() ? r.holidayName() : "근무일", r.inText(), r.outText(), r.duty(),
                    r.late() ? "지각" : "", r.early() ? "조퇴" : "", r.missingOut() ? "미기록" : "", r.fixState(),
                    d.actualMinutes(), d.orderedMinutes(), d.recognizedMinutes(), d.unorderedMinutes(), d.note());
        }
        csv.row("합계", "", "", "", "", "", s.late(), s.early(), s.missingOut(), "", "", "", s.overtime().sumMinutes(), "",
                "인정 " + s.overtime().recognizedHours() + "시간" + (s.overtime().capped() ? " (월 상한)" : ""));
        return csv.download("근무상황_" + s.ym() + ".csv");
    }

    /* ---------------------------------------------------------------- 근태 정정 신청 */

    @GetMapping("/hr/fix/new")
    public String fixForm(@AuthenticationPrincipal LoginUser me,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        model.addAttribute("date", date == null ? LocalDate.now().minusDays(1) : date);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("approvers", approvals.approverCandidates(me));
        return "hr/fix-form";
    }

    @PostMapping("/hr/fix")
    public String fix(@AuthenticationPrincipal LoginUser me,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(required = false) String inTime, @RequestParam(required = false) String outTime,
                      @RequestParam(defaultValue = "false") boolean outNextDay, @RequestParam(required = false) String reason,
                      @RequestParam(required = false) String approver, RedirectAttributes ra) throws IOException {
        WorkPayService.Result r = service.applyFix(date, inTime, outTime, outNextDay, reason, approver, fresh(me));
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/hr/fix/new" + (date == null ? "" : "?date=" + date);
        }
        audit.record(AuditAction.APPROVAL_SUBMIT, "결재 " + r.id(), "근태 정정 신청 " + date);
        return "redirect:/hr/sheet?month=" + YearMonth.from(date);
    }

    /* ---------------------------------------------------------------- 출장 복명·정산 */

    @GetMapping("/hr/trips")
    public String trips(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("trips", service.trips(me));
        model.addAttribute("today", LocalDate.now());
        return "hr/trips";
    }

    @GetMapping("/hr/trips/{id:\\d+}")
    public String trip(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        DutyRequestVO t = service.trip(id);
        if (t == null || !t.getLoginId().equals(me.getLoginId()) && !me.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("t", t);
        model.addAttribute("report", service.tripReport(id));
        model.addAttribute("mine", t.getLoginId().equals(me.getLoginId()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("rates", service.tripRates());
        return "hr/trip";
    }

    @PostMapping("/hr/trips/{id:\\d+}/settle")
    public String settle(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String report,
                         @RequestParam(required = false) Long fare, @RequestParam(required = false) Long lodgingPaid,
                         RedirectAttributes ra) {
        WorkPayService.Result r = service.settleTrip(id, report, fare, lodgingPaid, me);
        ra.addFlashAttribute("message", r.message());
        if (r.ok()) {
            audit.record(AuditAction.TRIP_SETTLE, "출장 " + id, r.message());
        }
        return "redirect:/hr/trips/" + id;
    }

    /* ---------------------------------------------------------------- 부서 근태 현황 */

    @GetMapping("/hr/team")
    public String team(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String dept, Model model) {
        LoginUser u = fresh(me);
        model.addAttribute("rows", service.team(u, dept));
        model.addAttribute("dept", u.isAdmin() && dept != null && !dept.isBlank() ? dept : u.dept());
        if (u.isAdmin()) {
            model.addAttribute("depts", users.list().stream().map(LoginUser::dept).filter(d -> !d.isBlank()).distinct().sorted().toList());
        }
        model.addAttribute("admin", u.isAdmin());
        model.addAttribute("today", LocalDate.now());
        return "hr/team";
    }

    /* ---------------------------------------------------------------- 관리자: 수당·여비 설정, 유연근무, 월 집계 */

    private void admin(LoginUser me) {
        if (me == null || !me.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/hr/pay")
    public String pay(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month, Model model) {
        admin(me);
        YearMonth ym = month(month);
        model.addAttribute("settings", service.settings());
        model.addAttribute("rates", service.rates());
        model.addAttribute("positions", users.list().stream().map(LoginUser::position).filter(p -> !p.isBlank()).distinct().sorted().toList());
        model.addAttribute("schedules", service.schedules());
        model.addAttribute("staff", users.list().stream().filter(LoginUser::isEnabled).toList());
        model.addAttribute("ym", ym);
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.isBefore(YearMonth.now()) ? ym.plusMonths(1).toString() : null);
        model.addAttribute("summary", service.monthSummary(ym));
        return "hr/pay";
    }

    @PostMapping("/hr/pay/settings")
    public String saveSettings(@AuthenticationPrincipal LoginUser me, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        admin(me);
        Map<String, String> s = new LinkedHashMap<>();
        Map<String, String> rates = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("s_")) {
                s.put(k.substring(2).replace('_', '.'), v);
            } else if (k.startsWith("rate_")) {
                rates.put(params.getOrDefault("pos_" + k.substring(5), ""), v);
            }
        });
        WorkPayService.Result r = service.saveSettings(s, rates, me);
        if (r.ok()) {
            audit.record(AuditAction.HR_PAY_SETTING, "수당·여비 설정", "저장");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hr/pay";
    }

    @PostMapping("/hr/pay/schedule")
    public String schedule(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String loginId,
                           @RequestParam(required = false) String start, RedirectAttributes ra) {
        admin(me);
        WorkPayService.Result r = service.setSchedule(loginId, start, me);
        if (r.ok()) {
            audit.record(AuditAction.HR_PAY_SETTING, "근무시간 " + loginId, start == null || start.isBlank() ? "기본" : start);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/hr/pay#schedule";
    }

    @GetMapping("/hr/pay/summary.csv")
    public ResponseEntity<byte[]> summaryCsv(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month) {
        admin(me);
        YearMonth ym = month(month);
        List<Map<String, Object>> rows = service.monthSummary(ym);
        Csv csv = Csv.of("아이디", "이름", "부서", "직급", "출근일", "지각", "조퇴", "퇴근 미기록", "명령 시간", "인정 시간", "월 상한 적용",
                "시간당 단가", "시간외근무수당(어림)");
        for (Map<String, Object> r : rows) {
            csv.row(r.get("loginId"), r.get("name"), r.get("dept"), r.get("position"), r.get("daysWorked"), r.get("late"), r.get("early"),
                    r.get("missing"), r.get("ordered"), r.get("hours"), Boolean.TRUE.equals(r.get("capped")) ? "예" : "",
                    r.get("rate"), r.get("pay"));
        }
        audit.record(AuditAction.HR_PAY_SETTING, "시간외근무 집계 " + ym, "CSV 내려받기");
        return csv.download("시간외근무_집계_" + ym + ".csv");
    }
}
