package egovframework.haengjeongnuli.office;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.text.Csv;
import egovframework.haengjeongnuli.user.service.UserService;

/** 공통 업무 화면 */
@Controller
public class OfficeController {

    private final OfficeService service;
    private final UserService users;
    private final AuditService audit;

    public OfficeController(OfficeService service, UserService users, AuditService audit) {
        this.service = service;
        this.users = users;
        this.audit = audit;
    }

    private LoginUser fresh(LoginUser me) {
        LoginUser u = users.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    private String done(OfficeService.Result r, RedirectAttributes ra, String target, String auditTarget) {
        ra.addFlashAttribute("message", r.message());
        if (r.ok() && auditTarget != null) {
            audit.record(AuditAction.OFFICE, auditTarget, r.message().length() > 200 ? r.message().substring(0, 200) : r.message());
        }
        return "redirect:" + target;
    }

    private List<LoginUser> colleagues(LoginUser me) {
        List<LoginUser> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && !u.getLoginId().equals(me.getLoginId())) {
                out.add(u);
            }
        }
        return out;
    }

    /* ---------------------------------------------------------------- 모아 보기 */

    @GetMapping("/office")
    public String hub(@AuthenticationPrincipal LoginUser me, Model model) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> mine = new ArrayList<>();
        for (Map<String, Object> r : service.roster(today, today.plusDays(60))) {
            if (me.getLoginId().equals(r.get("loginId"))) {
                mine.add(r);
            }
        }
        model.addAttribute("myDuty", mine);
        model.addAttribute("swaps", service.openSwaps(me));
        List<Map<String, Object>> myResv = new ArrayList<>();
        for (Map<String, Object> r : service.reservations(today, today.plusDays(14))) {
            if (me.getLoginId().equals(r.get("loginId"))) {
                myResv.add(r);
            }
        }
        model.addAttribute("myResv", myResv);
        model.addAttribute("report", service.myReport(me, today));
        model.addAttribute("handovers", service.handovers(me).stream().filter(h -> "SENT".equals(h.get("status"))
                && me.getLoginId().equals(h.get("toId"))).toList());
        model.addAttribute("edu", service.eduStatus(me.getLoginId(), today.getYear()));
        return "office/hub";
    }

    /* ---------------------------------------------------------------- 회의실 */

    @GetMapping("/office/rooms")
    public String rooms(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        LocalDate d = date == null ? LocalDate.now() : date;
        LocalDate monday = OfficeService.weekStart(d);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(monday.plusDays(i));
        }
        model.addAttribute("days", days);
        model.addAttribute("date", d);
        model.addAttribute("prevWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("rooms", service.rooms(true));
        model.addAttribute("allRooms", me.isAdmin() ? service.rooms(false) : List.of());
        List<Map<String, Object>> resvs = service.reservations(monday, monday.plusDays(6));
        Map<String, String> names = new java.util.HashMap<>();
        users.list().forEach(u -> names.put(u.getLoginId(), u.getUserName()));
        List<Map<String, Object>> myFuture = new ArrayList<>();
        for (Map<String, Object> r : resvs) {
            r.put("day", OfficeService.date(r.get("day")));
            r.put("mine", me.getLoginId().equals(r.get("loginId")));
            java.util.Set<String> ids = egovframework.haengjeongnuli.desk.DeskService.attendees(r);
            r.put("attendeeIds", ids);
            r.put("attendeeNames", String.join(", ", ids.stream().map(i -> names.getOrDefault(i, i)).toList()));
            if (Boolean.TRUE.equals(r.get("mine")) && !((LocalDate) r.get("day")).isBefore(LocalDate.now())) {
                myFuture.add(r);
            }
        }
        model.addAttribute("myFuture", myFuture);
        model.addAttribute("colleagues", colleagues(me));
        model.addAttribute("resvs", resvs);
        model.addAttribute("today", LocalDate.now());
        List<String> times = new ArrayList<>();
        for (int m = 7 * 60; m <= 22 * 60; m += 30) {
            times.add(String.format("%02d:%02d", m / 60, m % 60));
        }
        model.addAttribute("times", times);
        return "office/rooms";
    }

    @PostMapping("/office/rooms/reserve")
    public String reserve(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long roomId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                          @RequestParam(required = false) String start, @RequestParam(required = false) String end,
                          @RequestParam(required = false) String title, RedirectAttributes ra) {
        OfficeService.Result r = service.reserve(roomId, day, start, end, title, fresh(me));
        return done(r, ra, "/office/rooms" + (day == null ? "" : "?date=" + day), "회의실 예약 " + r.id());
    }

    @PostMapping("/office/rooms/resv/{id:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String back,
                         RedirectAttributes ra) {
        OfficeService.Result r = service.cancel(id, me);
        return done(r, ra, back != null && back.matches("\\d{4}-\\d{2}-\\d{2}") ? "/office/rooms?date=" + back : "/office/rooms",
                "회의실 예약 " + id + " 취소");
    }

    @PostMapping("/office/rooms/add")
    public String addRoom(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                          @RequestParam(required = false) String place, @RequestParam(required = false) Integer capacity,
                          RedirectAttributes ra) {
        return done(service.addRoom(name, place, capacity, me), ra, "/office/rooms", "회의실 등록");
    }

    @PostMapping("/office/rooms/{id:\\d+}/use")
    public String roomUse(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam boolean use, RedirectAttributes ra) {
        return done(service.setRoomUse(id, use, me), ra, "/office/rooms", "회의실 " + id);
    }

    /* ---------------------------------------------------------------- 당직 */

    private static YearMonth month(String m) {
        try {
            return m == null ? YearMonth.now() : YearMonth.parse(m);
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }

    @GetMapping("/office/duty")
    public String duty(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month, Model model) {
        YearMonth ym = month(month);
        List<Map<String, Object>> roster = service.roster(ym.atDay(1), ym.atEndOfMonth());
        for (Map<String, Object> r : roster) {
            r.put("mine", me.getLoginId().equals(r.get("loginId")));
        }
        model.addAttribute("ym", ym);
        model.addAttribute("prevMonth", ym.minusMonths(1));
        model.addAttribute("nextMonth", ym.plusMonths(1));
        model.addAttribute("roster", roster);
        model.addAttribute("swaps", service.openSwaps(me));
        model.addAttribute("staff", colleagues(me));
        model.addAttribute("today", LocalDate.now());
        // 맞바꿀 수 있는 다른 사람 당직 (다음 60일)
        List<Map<String, Object>> others = new ArrayList<>();
        for (Map<String, Object> r : service.roster(LocalDate.now().plusDays(1), LocalDate.now().plusDays(60))) {
            if (!me.getLoginId().equals(r.get("loginId"))) {
                others.add(r);
            }
        }
        model.addAttribute("others", others);
        return "office/duty";
    }

    @PostMapping("/office/duty/build")
    public String build(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, RedirectAttributes ra) {
        return done(service.buildRoster(from, to, me), ra, "/office/duty" + (from == null ? "" : "?month=" + YearMonth.from(from)),
                "당직 편성 " + from + "~" + to);
    }

    @PostMapping("/office/duty/clear")
    public String clear(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, RedirectAttributes ra) {
        return done(service.clearRoster(from, to, me), ra, "/office/duty" + (from == null ? "" : "?month=" + YearMonth.from(from)),
                "당직 편성 지우기 " + from + "~" + to);
    }

    @PostMapping("/office/duty/{id:\\d+}/swap")
    public String swap(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String toId,
                       @RequestParam(required = false) Long backId, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return done(service.requestSwap(id, toId, backId, reason, fresh(me)), ra, "/office/duty", "당직 교대 신청 " + id);
    }

    @PostMapping("/office/swaps/{id:\\d+}")
    public String decide(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam boolean accept, RedirectAttributes ra) {
        return done(service.decideSwap(id, accept, fresh(me)), ra, "/office/duty", "당직 교대 " + id);
    }

    @PostMapping("/office/duty/{id:\\d+}/log")
    public String log(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String log,
                      RedirectAttributes ra) {
        return done(service.writeLog(id, log, me), ra, "/office/duty", "당직 근무일지 " + id);
    }

    /* ---------------------------------------------------------------- 주간 업무 보고 */

    @GetMapping("/office/weekly")
    public String weekly(@AuthenticationPrincipal LoginUser me,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, Model model) {
        LocalDate w = OfficeService.weekStart(week);
        model.addAttribute("week", w);
        model.addAttribute("weekEnd", w.plusDays(4));
        model.addAttribute("prevWeek", w.minusWeeks(1));
        model.addAttribute("nextWeek", w.isBefore(OfficeService.weekStart(LocalDate.now())) ? w.plusWeeks(1) : null);
        model.addAttribute("report", service.myReport(me, w));
        model.addAttribute("editable", !w.isBefore(OfficeService.weekStart(LocalDate.now()).minusWeeks(4)));
        return "office/weekly";
    }

    @PostMapping("/office/weekly")
    public String saveWeekly(@AuthenticationPrincipal LoginUser me,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             @RequestParam(required = false) String done, @RequestParam(required = false) String plan,
                             @RequestParam(required = false) String issue, RedirectAttributes ra) {
        return done(service.saveReport(week, done, plan, issue, fresh(me)), ra, "/office/weekly?week=" + OfficeService.weekStart(week), null);
    }

    @GetMapping("/office/weekly/dept")
    public String deptWeekly(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String dept,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, Model model) {
        LoginUser u = fresh(me);
        LocalDate w = OfficeService.weekStart(week);
        String d = u.isAdmin() && dept != null && !dept.isBlank() ? dept : u.dept();
        model.addAttribute("week", w);
        model.addAttribute("prevWeek", w.minusWeeks(1));
        model.addAttribute("nextWeek", w.isBefore(OfficeService.weekStart(LocalDate.now())) ? w.plusWeeks(1) : null);
        model.addAttribute("dept", d);
        model.addAttribute("reports", service.deptReports(u, d, w));
        model.addAttribute("missing", service.missingReports(u, d, w));
        if (u.isAdmin()) {
            model.addAttribute("depts", users.list().stream().map(LoginUser::dept).filter(x -> !x.isBlank()).distinct().sorted().toList());
        }
        return "office/weekly-dept";
    }

    @GetMapping("/office/weekly/dept.csv")
    public ResponseEntity<byte[]> deptWeeklyCsv(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String dept,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        LoginUser u = fresh(me);
        LocalDate w = OfficeService.weekStart(week);
        Csv csv = Csv.of("주", "부서", "이름", "이번 주 한 일", "다음 주 할 일", "건의·협조");
        for (Map<String, Object> r : service.deptReports(u, dept, w)) {
            csv.row(w, r.get("dept"), r.get("userName"), r.get("done"), r.get("plan"), r.get("issue"));
        }
        return csv.download("주간업무보고_" + w + ".csv");
    }

    /* ---------------------------------------------------------------- 인계인수 */

    @GetMapping("/office/handovers")
    public String handovers(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("list", service.handovers(me));
        model.addAttribute("me", me.getLoginId());
        return "office/handovers";
    }

    @GetMapping("/office/handovers/new")
    public String handoverForm(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("staff", colleagues(me));
        return "office/handover-form";
    }

    @PostMapping("/office/handovers")
    public String handover(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String toId,
                           @RequestParam(required = false) String title, @RequestParam(required = false) String tasks,
                           @RequestParam(required = false) String docs, @RequestParam(required = false) String notes,
                           RedirectAttributes ra) {
        OfficeService.Result r = service.handover(toId, title, tasks, docs, notes, fresh(me));
        if (!r.ok()) {
            ra.addFlashAttribute("message", r.message());
            return "redirect:/office/handovers/new";
        }
        return done(r, ra, "/office/handovers/" + r.id(), "업무 인계 " + r.id());
    }

    @GetMapping("/office/handovers/{id:\\d+}")
    public String handoverView(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        Map<String, Object> h = service.handoverFor(id, me);
        if (h == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("h", h);
        model.addAttribute("canAccept", "SENT".equals(h.get("status")) && me.getLoginId().equals(h.get("toId")));
        model.addAttribute("canWithdraw", "SENT".equals(h.get("status")) && me.getLoginId().equals(h.get("fromId")));
        return "office/handover";
    }

    @PostMapping("/office/handovers/{id:\\d+}/withdraw")
    public String withdrawHandover(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        OfficeService.Result r = service.withdrawHandover(id, me);
        return done(r, ra, r.ok() ? "/office/handovers" : "/office/handovers/" + id, "업무 인계서 회수 " + id);
    }

    @PostMapping("/office/handovers/{id:\\d+}/accept")
    public String accept(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String note,
                         RedirectAttributes ra) {
        return done(service.acceptHandover(id, note, fresh(me)), ra, "/office/handovers/" + id, "업무 인수 " + id);
    }

    /* ---------------------------------------------------------------- 법정의무교육 */

    @GetMapping("/office/edu")
    public String edu(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year, Model model) {
        int y = year == null || year < 2000 || year > LocalDate.now().getYear() ? LocalDate.now().getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("status", service.eduStatus(me.getLoginId(), y));
        model.addAttribute("records", service.myEduRecords(me.getLoginId(), y));
        model.addAttribute("courses", service.courses(true));
        model.addAttribute("today", LocalDate.now());
        if (me.isAdmin()) {
            model.addAttribute("allCourses", service.courses(false));
            model.addAttribute("missing", service.missingEdu(y));
        }
        return "office/edu";
    }

    @PostMapping("/office/edu")
    public String addEdu(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long courseId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
                         @RequestParam(required = false) Double hours, @RequestParam(required = false) String provider,
                         RedirectAttributes ra) {
        return done(service.addEdu(courseId, on, hours, provider, me), ra, "/office/edu" + (on == null ? "" : "?year=" + on.getYear()), null);
    }

    @PostMapping("/office/edu/{id:\\d+}/delete")
    public String deleteEdu(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteEdu(id, me), ra, "/office/edu", null);
    }

    @PostMapping("/office/edu/courses")
    public String addCourse(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                            @RequestParam(required = false) Double hours, @RequestParam(required = false) String basis,
                            RedirectAttributes ra) {
        return done(service.addCourse(name, hours, basis, me), ra, "/office/edu", "의무교육 과목 추가");
    }

    @PostMapping("/office/edu/courses/{id:\\d+}/use")
    public String courseUse(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam boolean use, RedirectAttributes ra) {
        return done(service.setCourseUse(id, use, me), ra, "/office/edu", "의무교육 과목 " + id);
    }

    @PostMapping("/office/edu/remind")
    public String remind(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year, RedirectAttributes ra) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return done(service.remindEdu(y, me), ra, "/office/edu?year=" + y, "의무교육 미이수 알림 " + y);
    }

    /* ---------------------------------------------------------------- 비상연락망 */

    @GetMapping("/office/contacts")
    public String contacts(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("groups", service.contacts(me));
        model.addAttribute("mine", service.myContact(me));
        model.addAttribute("admin", me.isAdmin());
        audit.record(AuditAction.OFFICE, "비상연락망", "열람");
        return "office/contacts";
    }

    @PostMapping("/office/contacts/me")
    public String saveContact(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String mobile,
                              @RequestParam(defaultValue = "false") boolean share, RedirectAttributes ra) {
        return done(service.saveContact(mobile, share, me), ra, "/office/contacts", "비상연락망 내 번호");
    }
}
