package egovframework.haengjeongnuli.hr.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Area;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Rates;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Region;
import egovframework.haengjeongnuli.hr.service.impl.AttendanceMapper;
import egovframework.haengjeongnuli.hr.service.impl.WorkPayMapper;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 근무상황·시간외근무·출장 여비.
 * - 월 근무상황: 출퇴근 기록(승인된 정정 반영) + 승인된 휴가·출장 → 지각·조퇴·퇴근 미기록, 시간외근무 인정 시간
 * - 시간외근무수당: 인정 시간 × 직급별 시간당 단가(설정)
 * - 출장 여비: 관내·관외 계산, 복명서와 정산
 * - 근태 정정 신청(전자결재), 유연근무 시간(관리자), 부서 근태 현황
 */
@Service
public class WorkPayService {

    /** 설정 이름과 기본값 */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("work.start", "09:00");
        DEFAULTS.put("work.end", "18:00");
        DEFAULTS.put("ot.dailyCap", "4");
        DEFAULTS.put("ot.monthlyCap", "57");
        DEFAULTS.put("ot.fixedHours", "0");
        Rates r = Rates.defaults();
        DEFAULTS.put("trip.inOver4h", String.valueOf(r.inOver4h()));
        DEFAULTS.put("trip.inUnder4h", String.valueOf(r.inUnder4h()));
        DEFAULTS.put("trip.inCarCut", String.valueOf(r.inCarCut()));
        DEFAULTS.put("trip.perDiem", String.valueOf(r.perDiem()));
        DEFAULTS.put("trip.meal", String.valueOf(r.meal()));
        DEFAULTS.put("trip.lodging.SEOUL", String.valueOf(r.lodgingSeoul()));
        DEFAULTS.put("trip.lodging.METRO", String.valueOf(r.lodgingMetro()));
        DEFAULTS.put("trip.lodging.OTHER", String.valueOf(r.lodgingOther()));
    }

    public static final String RATE_PREFIX = "ot.rate.";

    public record Result(boolean ok, String message, Long id) {
        static Result fail(String m) { return new Result(false, m, null); }
    }

    /** 월 근무상황 한 줄 */
    public record Row(LocalDate date, String weekday, boolean holiday, String holidayName, LocalDateTime in, LocalDateTime out,
                      String fixState, String duty, boolean late, boolean early, boolean missingOut,
                      OvertimeCalculator.Day overtime) {

        public String inText() { return in == null ? "—" : in.toLocalTime().withNano(0).toString().substring(0, 5); }

        public String outText() {
            if (out == null) {
                return "—";
            }
            return out.toLocalTime().toString().substring(0, 5) + (out.toLocalDate().isAfter(date) ? " (다음 날)" : "");
        }
    }

    /** 월 근무상황 전체 */
    public record Sheet(YearMonth ym, LocalTime start, LocalTime end, boolean flexible, List<Row> rows, int daysWorked,
                        int late, int early, int missingOut, OvertimeCalculator.Month overtime, Long hourlyRate,
                        int fixedHours, Long overtimePay, double orderedHours) {}

    private final WorkPayMapper mapper;
    private final AttendanceMapper attendance;
    private final HolidayService holidays;
    private final ApprovalService approvals;
    private final UserService users;

    public WorkPayService(WorkPayMapper mapper, AttendanceMapper attendance, HolidayService holidays, ApprovalService approvals,
                          UserService users) {
        this.mapper = mapper;
        this.attendance = attendance;
        this.holidays = holidays;
        this.approvals = approvals;
        this.users = users;
    }

    /* ================================================================ 설정 */

    public Map<String, String> settings() {
        Map<String, String> out = new LinkedHashMap<>(DEFAULTS);
        for (Map<String, Object> m : mapper.settings()) {
            out.put((String) m.get("key"), (String) m.get("value"));
        }
        return out;
    }

    private int intSetting(Map<String, String> s, String key) {
        try {
            return Integer.parseInt(s.get(key).trim());
        } catch (RuntimeException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    private long longSetting(Map<String, String> s, String key) {
        try {
            return Long.parseLong(s.get(key).trim());
        } catch (RuntimeException e) {
            return Long.parseLong(DEFAULTS.get(key));
        }
    }

    public Rates tripRates() {
        Map<String, String> s = settings();
        return new Rates(longSetting(s, "trip.inOver4h"), longSetting(s, "trip.inUnder4h"), longSetting(s, "trip.inCarCut"),
                longSetting(s, "trip.perDiem"), longSetting(s, "trip.meal"), longSetting(s, "trip.lodging.SEOUL"),
                longSetting(s, "trip.lodging.METRO"), longSetting(s, "trip.lodging.OTHER"));
    }

    public int dailyCap() {
        return intSetting(settings(), "ot.dailyCap");
    }

    public int monthlyCap() {
        return intSetting(settings(), "ot.monthlyCap");
    }

    /** 직급별 시간외근무 시간당 단가 {직급 이름: 원} */
    public Map<String, Long> rates() {
        Map<String, Long> out = new LinkedHashMap<>();
        settings().forEach((k, v) -> {
            if (k.startsWith(RATE_PREFIX)) {
                try {
                    out.put(k.substring(RATE_PREFIX.length()), Long.parseLong(v));
                } catch (NumberFormatException ignored) {
                    // 잘못 저장된 값은 건너뛴다
                }
            }
        });
        return out;
    }

    /** 관리자: 설정 저장. 숫자 칸은 0 이상 정수, 시각은 HH:mm */
    @Transactional
    public Result saveSettings(Map<String, String> in, Map<String, String> newRates, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 바꿀 수 있습니다.");
        }
        Map<String, String> clean = new LinkedHashMap<>();
        for (String k : DEFAULTS.keySet()) {
            String v = in.get(k);
            if (v == null) {
                continue;
            }
            v = v.trim();
            if (k.startsWith("work.")) {
                try {
                    v = LocalTime.parse(v).toString();
                } catch (DateTimeParseException e) {
                    return Result.fail("근무 시각은 09:00 꼴로 적으세요.");
                }
            } else if (!v.matches("\\d{1,9}")) {
                return Result.fail("금액·시간은 0 이상의 정수로 적으세요.");
            }
            clean.put(k, v);
        }
        if (clean.containsKey("work.start") && clean.containsKey("work.end")
                && !LocalTime.parse(clean.get("work.end")).isAfter(LocalTime.parse(clean.get("work.start")))) {
            return Result.fail("퇴근 시각이 출근 시각보다 늦어야 합니다.");
        }
        if (newRates != null) {
            for (Map.Entry<String, String> e : newRates.entrySet()) {
                String name = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (name.length() > 40) {
                    return Result.fail("직급 이름은 40자 이내로 적으세요.");
                }
                if (v.isEmpty()) {
                    mapper.deleteSetting(RATE_PREFIX + name);
                    continue;
                }
                if (!v.matches("\\d{1,7}")) {
                    return Result.fail("시간당 단가는 원 단위 정수로 적으세요.");
                }
                clean.put(RATE_PREFIX + name, v);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        clean.forEach((k, v) -> {
            mapper.deleteSetting(k);
            mapper.insertSetting(k, v, me.getLoginId(), now);
        });
        return new Result(true, "설정을 저장했습니다.", null);
    }

    /* ================================================================ 근무시간 (유연근무) */

    /** [출근, 퇴근] 정해진 시각. 유연근무를 정해 두었으면 그 시각 */
    public LocalTime[] schedule(String loginId) {
        Map<String, Object> m = mapper.schedule(loginId);
        if (m != null) {
            return new LocalTime[] {LocalTime.parse((String) m.get("start")), LocalTime.parse((String) m.get("end"))};
        }
        Map<String, String> s = settings();
        return new LocalTime[] {LocalTime.parse(s.get("work.start")), LocalTime.parse(s.get("work.end"))};
    }

    public List<Map<String, Object>> schedules() {
        List<Map<String, Object>> out = mapper.schedules();
        for (Map<String, Object> m : out) {
            LoginUser u = users.findByLoginId((String) m.get("loginId"));
            m.put("name", u == null ? "(없는 직원)" : u.getUserName() + " · " + u.dept());
        }
        return out;
    }

    /** 관리자: 시차출퇴근 시각 정하기 (점심 1시간 포함 하루 9시간). start 를 비우면 기본 시각으로 되돌린다 */
    @Transactional
    public Result setSchedule(String loginId, String start, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 정할 수 있습니다.");
        }
        LoginUser u = loginId == null ? null : users.findByLoginId(loginId.trim());
        if (u == null) {
            return Result.fail("직원을 찾을 수 없습니다.");
        }
        if (start == null || start.isBlank()) {
            mapper.deleteSchedule(u.getLoginId());
            return new Result(true, u.getUserName() + " 직원의 근무시간을 기본으로 되돌렸습니다.", null);
        }
        LocalTime s;
        try {
            s = LocalTime.parse(start.trim());
        } catch (DateTimeParseException e) {
            return Result.fail("출근 시각을 08:00 꼴로 적으세요.");
        }
        if (s.isBefore(LocalTime.of(7, 0)) || s.isAfter(LocalTime.of(10, 0)) || s.getMinute() % 30 != 0) {
            return Result.fail("시차출퇴근 출근 시각은 07:00~10:00 사이 30분 단위로 정하세요.");
        }
        LocalTime e = s.plusHours(9);
        mapper.deleteSchedule(u.getLoginId()); // 확인을 마친 뒤에 바꾼다 (틀린 값이면 지금 시각을 그대로 둔다)
        mapper.insertSchedule(u.getLoginId(), s.toString(), e.toString(), me.getLoginId(), LocalDateTime.now());
        return new Result(true, u.getUserName() + " 직원의 근무시간을 " + s + "~" + e + "로 정했습니다.", null);
    }

    /* ================================================================ 월 근무상황 */

    public Sheet sheet(LoginUser u, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();
        LocalTime[] sch = schedule(u.getLoginId());
        boolean flexible = mapper.schedule(u.getLoginId()) != null;
        Map<String, String> s = settings();
        int dailyCap = intSetting(s, "ot.dailyCap");
        int monthlyCap = intSetting(s, "ot.monthlyCap");

        Map<LocalDate, AttendanceVO> att = new HashMap<>();
        for (AttendanceVO a : attendance.selectRange(u.getLoginId(), from, to.plusDays(1))) {
            att.put(a.getWorkDate(), a);
        }
        // 정정 신청: 승인된 것은 반영, 결재 중이면 표시
        Map<LocalDate, Map<String, Object>> fixes = new HashMap<>();
        Map<LocalDate, String> fixState = new HashMap<>();
        for (Map<String, Object> f : mapper.fixes(u.getLoginId(), from, to)) {
            LocalDate d = EPDate.of(f.get("day"));
            String st = (String) f.get("status");
            if ("DONE".equals(st)) {
                fixes.put(d, f);
                fixState.put(d, "정정 반영");
            } else if ("PROG".equals(st) && !fixState.containsKey(d)) {
                fixState.put(d, "정정 결재 중");
            }
        }
        List<DutyRequestVO> duties = mapper.approvedDuty(u.getLoginId(), from, to);
        Map<LocalDate, String> holidayNames = new HashMap<>(holidays.holidays(ym.getYear()));

        List<Row> rows = new ArrayList<>();
        List<OvertimeCalculator.Day> otDays = new ArrayList<>();
        int worked = 0, late = 0, early = 0, missing = 0;
        String[] wd = {"월", "화", "수", "목", "금", "토", "일"};
        for (LocalDate d = from; !d.isAfter(to) && !d.isAfter(today); d = d.plusDays(1)) {
            boolean holiday = !holidays.isWorkingDay(d);
            AttendanceVO a = att.get(d);
            LocalDateTime in = a == null ? null : a.getInAt();
            LocalDateTime out = a == null ? null : a.getOutAt();
            Map<String, Object> fix = fixes.get(d);
            if (fix != null) {
                if (fix.get("inTime") != null) {
                    in = d.atTime(LocalTime.parse((String) fix.get("inTime")));
                }
                if (fix.get("outTime") != null) {
                    out = ("Y".equals(fix.get("nextDay")) ? d.plusDays(1) : d).atTime(LocalTime.parse((String) fix.get("outTime")));
                }
            }
            // 그날 승인된 휴가·출장·시간외 명령
            String dutyText = null;
            boolean fullAway = false, amOff = false, pmOff = false;
            List<OvertimeCalculator.Order> orders = new ArrayList<>();
            for (DutyRequestVO r : duties) {
                if (d.isBefore(r.getStartDate()) || d.isAfter(r.getEndDate())) {
                    continue;
                }
                if (r.getKind() == DutyKind.OVERTIME) {
                    orders.add(new OvertimeCalculator.Order(LocalTime.parse(r.getStartTime()), LocalTime.parse(r.getEndTime())));
                    continue;
                }
                String label = r.getKind().getLabel() + (r.getHalfDay() == null ? "" : "AM".equals(r.getHalfDay()) ? "(오전)" : "(오후)")
                        + (r.getKind() == DutyKind.TRIP ? "(" + r.getTripAreaLabel() + ")" : "");
                dutyText = dutyText == null ? label : dutyText + ", " + label;
                if (r.getKind() == DutyKind.TRIP || r.getHalfDay() == null) {
                    fullAway = true;
                } else if ("AM".equals(r.getHalfDay())) {
                    amOff = true;
                } else {
                    pmOff = true;
                }
            }
            boolean isLate = false, isEarly = false, isMissing = false;
            if (!holiday && !fullAway && in != null) {
                isLate = !amOff && in.toLocalTime().isAfter(sch[0]);
                isEarly = !pmOff && out != null && out.toLocalDate().equals(d) && out.toLocalTime().isBefore(sch[1]);
            }
            if (in != null && out == null && d.isBefore(today)) {
                isMissing = true;
            }
            OvertimeCalculator.Day ot = OvertimeCalculator.day(d, holiday, sch[0], sch[1], in, out, orders, dailyCap);
            otDays.add(ot);
            if (in == null && dutyText == null && holiday) {
                continue; // 쉬는 날에 아무 일도 없으면 줄을 만들지 않는다
            }
            if (in != null) {
                worked++;
            }
            late += isLate ? 1 : 0;
            early += isEarly ? 1 : 0;
            missing += isMissing ? 1 : 0;
            String hname = holidayNames.get(d);
            if (hname == null && holiday) {
                hname = d.getDayOfWeek() == DayOfWeek.SATURDAY ? "토요일" : d.getDayOfWeek() == DayOfWeek.SUNDAY ? "일요일" : "휴일";
            }
            rows.add(new Row(d, wd[d.getDayOfWeek().getValue() - 1], holiday, hname, in, out, fixState.get(d), dutyText,
                    isLate, isEarly, isMissing, ot));
        }
        OvertimeCalculator.Month m = OvertimeCalculator.month(otDays, monthlyCap);
        Long rate = rates().get(u.position());
        int fixedHours = intSetting(s, "ot.fixedHours");
        Long pay = rate == null ? null : rate * (m.recognizedHours() + (worked > 0 ? fixedHours : 0));
        double ordered = mapper.orderedHours(u.getLoginId(), from, to);
        return new Sheet(ym, sch[0], sch[1], flexible, rows, worked, late, early, missing, m, rate, fixedHours, pay, ordered);
    }

    public double orderedHoursOf(String loginId, YearMonth ym) {
        return mapper.orderedHours(loginId, ym.atDay(1), ym.atEndOfMonth());
    }

    /** 관리자: 모든 직원의 그달 시간외근무 인정 시간과 수당 */
    public List<Map<String, Object>> monthSummary(YearMonth ym) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (!u.isEnabled()) {
                continue;
            }
            Sheet sh = sheet(u, ym);
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("loginId", u.getLoginId());
            it.put("name", u.getUserName());
            it.put("dept", u.dept());
            it.put("position", u.position());
            it.put("daysWorked", sh.daysWorked());
            it.put("late", sh.late());
            it.put("early", sh.early());
            it.put("missing", sh.missingOut());
            it.put("ordered", sh.orderedHours());
            it.put("hours", sh.overtime().recognizedHours());
            it.put("capped", sh.overtime().capped());
            it.put("rate", sh.hourlyRate());
            it.put("pay", sh.overtimePay());
            out.add(it);
        }
        return out;
    }

    /* ================================================================ 근태 정정 신청 */

    @Transactional
    public Result applyFix(LocalDate day, String inTime, String outTime, boolean outNextDay, String reason, String approverId,
                           LoginUser me) throws IOException {
        if (day == null || day.isAfter(LocalDate.now())) {
            return Result.fail("정정할 날짜를 오늘이나 그 전으로 고르세요.");
        }
        if (day.isBefore(LocalDate.now().minusDays(60))) {
            return Result.fail("60일이 넘은 기록은 정정 신청할 수 없습니다. 인사 담당에게 문의하세요.");
        }
        LocalTime i = null, o = null;
        try {
            if (inTime != null && !inTime.isBlank()) {
                i = LocalTime.parse(inTime.trim());
            }
            if (outTime != null && !outTime.isBlank()) {
                o = LocalTime.parse(outTime.trim());
            }
        } catch (DateTimeParseException e) {
            return Result.fail("시각을 09:00 꼴로 적으세요.");
        }
        if (i == null && o == null) {
            return Result.fail("고칠 출근 또는 퇴근 시각을 적으세요.");
        }
        if (i != null && o != null && !outNextDay && !o.isAfter(i)) {
            return Result.fail("퇴근 시각이 출근 시각보다 늦어야 합니다 (자정을 넘겼으면 '다음 날'을 고르세요).");
        }
        if (outNextDay && o != null && o.isAfter(LocalTime.of(9, 0))) {
            return Result.fail("다음 날 퇴근은 오전 9시까지로 적으세요. 더 늦으면 그날 기록으로 따로 신청하세요.");
        }
        // 고치지 않는 쪽은 지금 기록과 맞춰 본다
        AttendanceVO cur = attendance.selectDay(me.getLoginId(), day);
        LocalDateTime effIn = i != null ? day.atTime(i) : cur == null ? null : cur.getInAt();
        LocalDateTime effOut = o != null ? (outNextDay ? day.plusDays(1) : day).atTime(o) : cur == null ? null : cur.getOutAt();
        if (effIn == null && o != null) {
            return Result.fail("그날 출근 기록이 없습니다. 출근 시각도 함께 적으세요.");
        }
        if (effIn != null && effOut != null && !effOut.isAfter(effIn)) {
            return Result.fail("고친 뒤 퇴근이 출근보다 이르게 됩니다. 시각을 확인하세요.");
        }
        LocalDateTime nowTime = LocalDateTime.now();
        if ((effIn != null && effIn.isAfter(nowTime)) || (o != null && effOut.isAfter(nowTime))) {
            return Result.fail("아직 오지 않은 시각으로는 정정할 수 없습니다.");
        }

        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty() || r.length() > 500) {
            return Result.fail("정정 사유를 500자 이내로 적으세요 (예: 퇴근 버튼을 누르지 못함).");
        }
        if (mapper.pendingFix(me.getLoginId(), day) > 0) {
            return Result.fail("그날 정정 신청이 이미 결재 중입니다.");
        }
        AttendanceVO now = attendance.selectDay(me.getLoginId(), day);
        String before = now == null ? "기록 없음"
                : "출근 " + now.getInAt().toLocalTime().toString().substring(0, 5) + " / 퇴근 "
                + (now.getOutAt() == null ? "없음" : now.getOutAt().toLocalTime().toString().substring(0, 5));
        String after = (i == null ? "출근 그대로" : "출근 " + i) + " / " + (o == null ? "퇴근 그대로" : "퇴근 " + o + (outNextDay ? " (다음 날)" : ""));
        ApprovalForm f = new ApprovalForm();
        f.setKind(ApprovalForm.DUTY);
        f.setRetention("1년");
        f.setDisclosure("공개");
        f.setReceiverType("internal");
        f.setTitle("[근태 정정] " + me.getUserName() + " " + day);
        f.setBody("1. 신청자: " + me.getUserName() + " " + me.position() + " (" + me.dept() + ")\n"
                + "2. 날짜: " + day + "\n3. 지금 기록: " + before + "\n4. 고칠 기록: " + after + "\n5. 사유: " + r
                + "\n\n승인되면 근무상황과 시간외근무 계산에 반영됩니다.");
        f.setFinalApprover(approverId);
        Map<String, String> errors = approvals.validate(f, true, me, null);
        if (!errors.isEmpty()) {
            return Result.fail(errors.containsKey("finalApprover") || errors.containsKey("reviewers")
                    ? "결재권자를 고르세요 (본인이 아닌 사용 중인 직원)." : errors.values().iterator().next());
        }
        long docId = approvals.create(f, true, null, me);
        mapper.insertFix(mapper.nextFixId(), me.getLoginId(), day, i == null ? null : i.toString(), o == null ? null : o.toString(),
                outNextDay ? "Y" : "N", r, docId, LocalDateTime.now());
        return new Result(true, "근태 정정 신청을 결재에 올렸습니다. 승인되면 반영됩니다.", docId);
    }

    /* ================================================================ 출장 복명·정산 */

    public List<DutyRequestVO> trips(LoginUser me) {
        return mapper.trips(me.getLoginId());
    }

    public DutyRequestVO trip(long id) {
        return mapper.duty(id);
    }

    public Map<String, Object> tripReport(long id) {
        return mapper.tripReport(id);
    }

    /** 출장 다녀온 뒤: 복명(결과 보고)과 실제 운임·숙박비로 여비를 정산한다. 승인된 본인 출장만, 한 번만 */
    @Transactional
    public Result settleTrip(long id, String report, Long fare, Long lodgingPaid, LoginUser me) {
        DutyRequestVO t = mapper.duty(id);
        if (t == null || t.getKind() != DutyKind.TRIP || !t.getLoginId().equals(me.getLoginId())) {
            return Result.fail("본인의 출장만 정산할 수 있습니다.");
        }
        if (!"DONE".equals(t.getDocStatus())) {
            return Result.fail("결재가 끝난(승인된) 출장만 정산할 수 있습니다.");
        }
        if (t.getEndDate().isAfter(LocalDate.now())) {
            return Result.fail("출장이 끝난 뒤에 복명·정산하세요.");
        }
        if (mapper.tripReport(id) != null) {
            return Result.fail("이미 정산한 출장입니다.");
        }
        String rp = report == null ? "" : report.trim();
        if (rp.isEmpty() || rp.length() > 1300) {
            return Result.fail("출장 결과(복명)를 1,300자 이내로 적으세요.");
        }
        long f = fare == null ? 0 : fare;
        if (f < 0 || f > 10_000_000) {
            return Result.fail("운임을 확인하세요.");
        }
        Area area = "IN".equals(t.getTripArea()) ? Area.IN : Area.OUT;
        Region region = region(t.getLodgingRegion());
        int nights = t.getLodgingNights() == null ? 0 : t.getLodgingNights();
        Long lp = area == Area.OUT && nights > 0 ? (lodgingPaid == null ? 0L : lodgingPaid) : null;
        TripExpenseCalculator.Result r = TripExpenseCalculator.calculate(area, t.getStartDate(), t.getEndDate(),
                t.getTripHours() == null ? 0 : t.getTripHours(), "Y".equals(t.getOfficialCar()), nights, region, lp,
                area == Area.OUT ? f : 0, tripRates());
        mapper.insertTripReport(id, rp, area == Area.OUT ? f : 0, lp == null ? 0 : r.lodging(), r.total(), r.detail(),
                me.getLoginId(), LocalDateTime.now());
        return new Result(true, String.format("복명·정산을 마쳤습니다. 여비 %,d원", r.total()), id);
    }

    public static Region region(String s) {
        try {
            return s == null ? Region.OTHER : Region.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Region.OTHER;
        }
    }

    /** 출장 신청 때 어림 여비 */
    public TripExpenseCalculator.Result estimateTrip(Area area, LocalDate start, LocalDate end, double hours, boolean car, int nights,
                                                     Region region) {
        return TripExpenseCalculator.calculate(area, start, end, hours, car, nights, region, null, 0, tripRates());
    }

    /* ================================================================ 부서 근태 현황 (오늘) */

    /**
     * 오늘 부서 사람들. 관리자는 모든 부서, 그 밖은 자기 부서만.
     * 출퇴근 시각은 관리자에게만, 다른 직원에게는 '근무 중·휴가·출장'만 보인다.
     */
    public List<Map<String, Object>> team(LoginUser me, String dept) {
        String d = me.isAdmin() && dept != null && !dept.isBlank() ? dept : me.dept();
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> att = new HashMap<>();
        for (Map<String, Object> a : mapper.attendanceOn(today)) {
            att.put((String) a.get("loginId"), a);
        }
        Map<String, String> duty = new HashMap<>();
        for (Map<String, Object> r : mapper.dutyOn(today)) {
            String k = DutyKind.valueOf((String) r.get("kind")).getLabel()
                    + (r.get("halfDay") == null ? "" : "AM".equals(r.get("halfDay")) ? "(오전)" : "(오후)");
            duty.merge((String) r.get("loginId"), k, (x, y) -> x + ", " + y);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (!u.isEnabled() || !d.equals(u.dept())) {
                continue;
            }
            Map<String, Object> a = att.get(u.getLoginId());
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", u.getUserName());
            it.put("position", u.position());
            it.put("duty", duty.get(u.getLoginId()));
            String state = duty.containsKey(u.getLoginId()) && a == null ? duty.get(u.getLoginId())
                    : a == null ? (holidays.isWorkingDay(today) ? "출근 기록 없음" : "쉬는 날")
                    : a.get("outAt") == null ? "근무 중" : "퇴근";
            it.put("state", state);
            if (me.isAdmin() && a != null) {
                it.put("in", EPDate.hm(a.get("inAt")));
                it.put("out", EPDate.hm(a.get("outAt")));
            }
            out.add(it);
        }
        return out;
    }

    /** DB 값 바꾸기 */
    static final class EPDate {
        private EPDate() {
        }

        static LocalDate of(Object o) {
            if (o instanceof java.sql.Date d) {
                return d.toLocalDate();
            }
            if (o instanceof java.sql.Timestamp t) {
                return t.toLocalDateTime().toLocalDate();
            }
            if (o instanceof LocalDate l) {
                return l;
            }
            if (o instanceof LocalDateTime l) {
                return l.toLocalDate();
            }
            return LocalDate.parse(String.valueOf(o).substring(0, 10));
        }

        static String hm(Object o) {
            LocalDateTime t = o instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : o instanceof LocalDateTime l ? l : null;
            return t == null ? null : t.toLocalTime().toString().substring(0, 5);
        }
    }

    static long days(LocalDate a, LocalDate b) {
        return ChronoUnit.DAYS.between(a, b) + 1;
    }
}
