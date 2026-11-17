package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.hr.service.AttendanceVO;
import egovframework.haengjeongnuli.hr.service.DutyKind;
import egovframework.haengjeongnuli.hr.service.DutyService;
import egovframework.haengjeongnuli.hr.service.OvertimeCalculator;
import egovframework.haengjeongnuli.hr.service.OvertimeCalculator.Order;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Area;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Rates;
import egovframework.haengjeongnuli.hr.service.TripExpenseCalculator.Region;
import egovframework.haengjeongnuli.hr.service.WorkPayService;
import egovframework.haengjeongnuli.hr.service.impl.AttendanceMapper;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 시간외근무(출퇴근 기록 기반)·출장 여비(관내·관외)·근태 정정·유연근무 */
@SpringBootTest
class WorkPayTest {

    static final LocalTime S = LocalTime.of(9, 0);
    static final LocalTime E = LocalTime.of(18, 0);
    static final LocalDate WED = LocalDate.of(2026, 11, 11);
    static final LocalDate SAT = LocalDate.of(2026, 11, 14);

    static LocalDateTime at(LocalDate d, int h, int m) {
        return d.atTime(h, m);
    }

    /* ---------------------------------------------------------------- 계산 규칙 */

    @Test
    void 평일은_명령_시간_안에서_1시간을_빼고_하루_상한까지() {
        List<Order> order = List.of(new Order(LocalTime.of(18, 0), LocalTime.of(22, 0)));
        // 18:00~21:30 일함 → 3시간 30분 - 1시간 = 2시간 30분
        OvertimeCalculator.Day d = OvertimeCalculator.day(WED, false, S, E, at(WED, 8, 50), at(WED, 21, 30), order, 4);
        assertThat(d.recognizedMinutes()).isEqualTo(150);
        assertThat(d.actualMinutes()).isEqualTo(220); // 08:50~09:00 10분 + 18:00~21:30
        assertThat(d.unorderedMinutes()).isEqualTo(10); // 아침 10분은 명령 밖
        // 명령보다 늦게까지 있어도 명령 시간까지만
        d = OvertimeCalculator.day(WED, false, S, E, at(WED, 9, 0), at(WED, 23, 30), order, 4);
        assertThat(d.recognizedMinutes()).isEqualTo(180);
        assertThat(d.unorderedMinutes()).isEqualTo(90);
        // 1시간 미만은 0
        d = OvertimeCalculator.day(WED, false, S, E, at(WED, 9, 0), at(WED, 18, 50), order, 4);
        assertThat(d.recognizedMinutes()).isZero();
        assertThat(d.note()).contains("1시간 미만");
        // 명령이 없으면 0
        d = OvertimeCalculator.day(WED, false, S, E, at(WED, 9, 0), at(WED, 21, 0), List.of(), 4);
        assertThat(d.recognizedMinutes()).isZero();
        assertThat(d.note()).isEqualTo("근무명령 없음");
        // 하루 상한
        d = OvertimeCalculator.day(WED, false, S, E, at(WED, 9, 0), at(WED, 23, 59),
                List.of(new Order(LocalTime.of(18, 0), LocalTime.of(23, 59))), 4);
        assertThat(d.recognizedMinutes()).isEqualTo(240);
        // 퇴근 기록이 없으면 계산하지 않는다
        assertThat(OvertimeCalculator.day(WED, false, S, E, at(WED, 9, 0), null, order, 4).note()).isEqualTo("퇴근 기록 없음");
    }

    @Test
    void 휴일은_공제_없이_출근부터() {
        List<Order> order = List.of(new Order(LocalTime.of(10, 0), LocalTime.of(14, 0)));
        OvertimeCalculator.Day d = OvertimeCalculator.day(SAT, true, S, E, at(SAT, 10, 0), at(SAT, 13, 0), order, 4);
        assertThat(d.recognizedMinutes()).isEqualTo(180);
        assertThat(d.note()).contains("공제 없음");
    }

    @Test
    void 한_달은_분을_더해_1시간_미만_버리고_월_상한() {
        List<OvertimeCalculator.Day> days = List.of(
                new OvertimeCalculator.Day(WED, false, null, null, 0, 0, 90, 0, ""),
                new OvertimeCalculator.Day(WED.plusDays(1), false, null, null, 0, 0, 100, 0, ""));
        OvertimeCalculator.Month m = OvertimeCalculator.month(days, 57);
        assertThat(m.sumMinutes()).isEqualTo(190);
        assertThat(m.recognizedHours()).isEqualTo(3);
        assertThat(OvertimeCalculator.month(days, 2).recognizedHours()).isEqualTo(2);
        assertThat(OvertimeCalculator.month(days, 2).capped()).isTrue();
    }

    @Test
    void 출장_여비_관내_관외() {
        Rates r = Rates.defaults();
        // 관내: 4시간 이상 2만원, 미만 1만원, 공용차량 1만원 감액
        assertThat(TripExpenseCalculator.calculate(Area.IN, WED, WED, 4, false, 0, null, null, 0, r).total()).isEqualTo(20_000);
        assertThat(TripExpenseCalculator.calculate(Area.IN, WED, WED, 3.5, false, 0, null, null, 0, r).total()).isEqualTo(10_000);
        assertThat(TripExpenseCalculator.calculate(Area.IN, WED, WED, 5, true, 0, null, null, 0, r).total()).isEqualTo(10_000);
        assertThat(TripExpenseCalculator.calculate(Area.IN, WED, WED, 2, true, 0, null, null, 0, r).total()).isZero();
        // 관외 2일 1박 서울: 일비 2.5만×2 + 식비 2.5만×2 + 숙박 상한 10만 = 20만 (어림)
        TripExpenseCalculator.Result o = TripExpenseCalculator.calculate(Area.OUT, WED, WED.plusDays(1), 0, false, 1, Region.SEOUL, null, 0, r);
        assertThat(o.total()).isEqualTo(200_000);
        // 공용차량이면 일비 절반, 숙박 실비가 상한보다 적으면 실비, 운임 더함
        o = TripExpenseCalculator.calculate(Area.OUT, WED, WED.plusDays(1), 0, true, 1, Region.METRO, 65_000L, 48_000, r);
        assertThat(o.perDiem()).isEqualTo(25_000);
        assertThat(o.lodging()).isEqualTo(65_000);
        assertThat(o.total()).isEqualTo(25_000 + 50_000 + 65_000 + 48_000);
        // 상한을 넘는 숙박비는 상한까지
        assertThat(TripExpenseCalculator.calculate(Area.OUT, WED, WED.plusDays(1), 0, false, 1, Region.OTHER, 90_000L, 0, r).lodging())
                .isEqualTo(70_000);
    }

    /* ---------------------------------------------------------------- 화면·결재와 함께 */

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired DutyService duty;
    @Autowired WorkPayService workPay;
    @Autowired AttendanceMapper attendance;
    @Autowired HolidayService holidays;

    record Staff(LoginUser u, MockMvc mvc) {}

    Staff staff(String name, String position) {
        String id = "w" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName("총무과");
        f.setPositionName(position);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        users.create(f);
        LoginUser u = users.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    void punch(LoginUser u, LocalDate d, LocalTime in, LocalDateTime out) {
        AttendanceVO a = new AttendanceVO();
        a.setAttId(attendance.nextId());
        a.setLoginId(u.getLoginId());
        a.setUserName(u.getUserName());
        a.setDeptName(u.getDeptName());
        a.setWorkDate(d);
        a.setInAt(d.atTime(in));
        attendance.insert(a);
        if (out != null) {
            attendance.close(a.getAttId(), out, "127.0.0.1");
        }
    }

    /** 이번 달 오늘 전의 근무일 (없으면 지난달) */
    LocalDate pastWorkday(int skip) {
        LocalDate d = LocalDate.now().minusDays(1);
        int n = 0;
        while (true) {
            if (holidays.isWorkingDay(d) && n++ >= skip) {
                return d;
            }
            d = d.minusDays(1);
        }
    }

    @Test
    void 출퇴근_기록과_승인된_명령으로_시간외근무와_수당을_계산한다() throws Exception {
        Staff me = staff("시간외", "주무관" + UUID.randomUUID().toString().substring(0, 4));
        Staff boss = staff("과장", "과장");
        LocalDate d = pastWorkday(0);
        YearMonth ym = YearMonth.from(d);
        // 하루 상한을 넘는 명령은 신청할 수 없다
        assertThat(duty.apply(DutyKind.OVERTIME, d, d, null, "18:00", "23:00", null, "행사 준비", boss.u().getLoginId(), me.u()).message())
                .contains("하루 4시간");
        DutyService.Result r = duty.apply(DutyKind.OVERTIME, d, d, null, "18:00", "21:00", null, "예산 자료 작성", boss.u().getLoginId(), me.u());
        assertThat(r.ok()).as(r.message()).isTrue();
        punch(me.u(), d, LocalTime.of(9, 20), d.atTime(22, 10));
        // 결재 전에는 인정하지 않는다
        assertThat(workPay.sheet(me.u(), ym).overtime().recognizedHours()).isZero();
        boss.mvc().perform(post("/approval/" + r.docId() + "/approve"));
        WorkPayService.Sheet s = workPay.sheet(me.u(), ym);
        WorkPayService.Row row = s.rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.late()).isTrue(); // 09:20 출근
        assertThat(row.overtime().recognizedMinutes()).isEqualTo(120); // 18~21 = 3시간 - 1시간
        assertThat(row.overtime().unorderedMinutes()).isEqualTo(70); // 21:00~22:10
        assertThat(s.overtime().recognizedHours()).isEqualTo(2);
        assertThat(s.overtimePay()).isNull(); // 단가 없음

        // 관리자가 직급 단가를 정하면 수당이 나온다
        LoginUser admin = TestUsers.admin();
        assertThat(workPay.saveSettings(java.util.Map.of(), java.util.Map.of(me.u().position(), "12345"), admin).ok()).isTrue();
        assertThat(workPay.sheet(me.u(), ym).overtimePay()).isEqualTo(24_690L);
        assertThat(workPay.saveSettings(java.util.Map.of("ot.dailyCap", "x"), null, admin).ok()).isFalse();
        assertThat(workPay.saveSettings(java.util.Map.of(), null, me.u()).ok()).isFalse();

        String html = me.mvc().perform(get("/hr/sheet").param("month", ym.toString())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("지각").contains("2시간").contains("24,690원");
        String csv = new String(me.mvc().perform(get("/hr/sheet.csv").param("month", ym.toString())).andReturn().getResponse()
                .getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("인정 2시간");
        // 관리자 집계
        String sum = TestUsers.mvcAs(ctx, admin).perform(get("/hr/pay").param("month", ym.toString())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sum).contains("시간외").contains("24,690원");
        me.mvc().perform(get("/hr/pay")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void 근태_정정이_승인되면_반영되고_유연근무_시각으로_지각을_본다() throws Exception {
        Staff me = staff("정정", "주무관");
        Staff boss = staff("팀장", "팀장");
        LocalDate d = pastWorkday(1);
        punch(me.u(), d, LocalTime.of(8, 10), null); // 퇴근을 못 누름
        WorkPayService.Row row = workPay.sheet(me.u(), YearMonth.from(d)).rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.missingOut()).isTrue();

        WorkPayService.Result fr = workPay.applyFix(d, null, "17:10", false, "현장 점검 뒤 바로 퇴근", boss.u().getLoginId(), me.u());
        assertThat(fr.ok()).as(fr.message()).isTrue();
        assertThat(workPay.applyFix(d, null, "17:30", false, "또", boss.u().getLoginId(), me.u()).message()).contains("결재 중");
        row = workPay.sheet(me.u(), YearMonth.from(d)).rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.fixState()).isEqualTo("정정 결재 중");
        boss.mvc().perform(post("/approval/" + fr.id() + "/approve"));
        row = workPay.sheet(me.u(), YearMonth.from(d)).rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.missingOut()).isFalse();
        assertThat(row.outText()).isEqualTo("17:10");
        assertThat(row.early()).isTrue(); // 18:00 전 퇴근

        // 08:00 출근 유연근무면 17:00 퇴근이라 조퇴가 아니다
        assertThat(workPay.setSchedule(me.u().getLoginId(), "08:00", TestUsers.admin()).ok()).isTrue();
        assertThat(workPay.setSchedule(me.u().getLoginId(), "11:00", TestUsers.admin()).ok()).isFalse();
        row = workPay.sheet(me.u(), YearMonth.from(d)).rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.early()).isFalse();
        assertThat(row.late()).isTrue(); // 08:10 출근 > 08:00
        assertThat(workPay.applyFix(LocalDate.now().plusDays(1), "09:00", null, false, "x", boss.u().getLoginId(), me.u()).ok()).isFalse();
    }

    @Test
    void 관내_관외_출장을_신청하고_다녀와서_복명_정산한다() throws Exception {
        Staff me = staff("출장", "주무관");
        Staff boss = staff("국장", "국장");
        LocalDate d = pastWorkday(2);
        // 관내: 여행시간을 적어야 한다
        assertThat(duty.apply(DutyKind.TRIP, d, d, null, null, null, "시청 별관", "회의", boss.u().getLoginId(),
                new DutyService.TripInput("IN", null, false, null, null), me.u()).message()).contains("여행시간");
        DutyService.Result in = duty.apply(DutyKind.TRIP, d, d, null, null, null, "시청 별관", "회의", boss.u().getLoginId(),
                new DutyService.TripInput("IN", 5.0, true, null, null), me.u());
        assertThat(in.ok()).as(in.message()).isTrue();
        LocalDate o1 = d.minusDays(10);
        DutyService.Result out = duty.apply(DutyKind.TRIP, o1, o1.plusDays(1), null, null, null, "세종 정부청사", "교육",
                boss.u().getLoginId(), new DutyService.TripInput("OUT", null, false, 1, "METRO"), me.u());
        assertThat(out.ok()).as(out.message()).isTrue();
        List<egovframework.haengjeongnuli.hr.service.DutyRequestVO> trips = workPay.trips(me.u());
        assertThat(trips).extracting(t -> t.getEstAmount()).contains(10_000L, 180_000L);
        long inId = trips.stream().filter(t -> "IN".equals(t.getTripArea())).findFirst().orElseThrow().getReqId();
        long outId = trips.stream().filter(t -> "OUT".equals(t.getTripArea())).findFirst().orElseThrow().getReqId();
        // 결재 전에는 정산할 수 없다
        assertThat(workPay.settleTrip(outId, "교육 이수", 50_000L, 60_000L, me.u()).message()).contains("승인된");
        boss.mvc().perform(post("/approval/" + in.docId() + "/approve"));
        boss.mvc().perform(post("/approval/" + out.docId() + "/approve"));
        WorkPayService.Result s = workPay.settleTrip(outId, "1. 교육 이수\n2. 자료 공유 예정", 52_000L, 95_000L, me.u());
        assertThat(s.ok()).as(s.message()).isTrue();
        // 일비 5만 + 식비 5만 + 숙박 상한 8만 + 운임 5.2만
        assertThat(((Number) workPay.tripReport(outId).get("amount")).longValue()).isEqualTo(232_000L);
        assertThat(workPay.settleTrip(outId, "다시", 0L, 0L, me.u()).message()).contains("이미");
        assertThat(workPay.settleTrip(inId, "회의 참석", null, null, staff("남", "주무관").u()).ok()).isFalse();
        assertThat(workPay.settleTrip(inId, "회의 참석", null, null, me.u()).message()).contains("10,000원");
        String html = me.mvc().perform(get("/hr/trips/" + outId)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("232,000원").contains("자료 공유 예정");
        // 출장 날은 근무상황에 출장으로 나오고 지각을 보지 않는다
        punch(me.u(), d, LocalTime.of(13, 0), d.atTime(15, 0));
        WorkPayService.Row row = workPay.sheet(me.u(), YearMonth.from(d)).rows().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
        assertThat(row.duty()).contains("출장(관내)");
        assertThat(row.late()).isFalse();
        // 부서 근태: 같은 부서만, 시각은 관리자만
        String team = me.mvc().perform(get("/hr/team")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(team).contains("총무과 오늘 근태").contains(me.u().getUserName()).doesNotContain(">13:00<");
        assertThat(d.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }
}
