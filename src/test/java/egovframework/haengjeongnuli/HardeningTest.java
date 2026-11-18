package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.epetition.EPetitionMemberService;
import egovframework.haengjeongnuli.epetition.EPetitionService;
import egovframework.haengjeongnuli.hr.service.AttendanceVO;
import egovframework.haengjeongnuli.hr.service.DutyKind;
import egovframework.haengjeongnuli.hr.service.DutyService;
import egovframework.haengjeongnuli.hr.service.WorkPayService;
import egovframework.haengjeongnuli.hr.service.impl.AttendanceMapper;
import egovframework.haengjeongnuli.office.OfficeService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 미약한 부분 보강 시험: 동시 예약, 당직 교대 검사, 시간외 자정 넘김·사후 신청, 근태 정정 맞춤, 회원 로그인 IP 제한 등 */
@SpringBootTest
class HardeningTest {

    @Autowired OfficeService office;
    @Autowired UserService users;
    @Autowired DutyService duty;
    @Autowired WorkPayService workPay;
    @Autowired AttendanceMapper attendance;
    @Autowired HolidayService holidays;
    @Autowired NotificationService notifications;
    @Autowired EPetitionMemberService members;

    LoginUser staff(String name) {
        String id = "k" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        users.create(f);
        LoginUser u = users.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return u;
    }

    List<String> messages(LoginUser u) {
        return notifications.recent(u.getLoginId(), 10).stream().map(n -> n.getMessage()).toList();
    }

    @Test
    void 같은_방_같은_시간을_동시에_예약해도_한_건만_된다() throws Exception {
        LoginUser admin = TestUsers.admin();
        long room = office.addRoom("동시" + UUID.randomUUID().toString().substring(0, 4), null, null, admin).id();
        LoginUser a = staff("동시가"), b = staff("동시나");
        LocalDate d = LocalDate.now().plusDays(3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> ta = () -> office.reserve(room, d, "14:00", "15:00", "가", a).ok();
            Callable<Boolean> tb = () -> office.reserve(room, d, "14:00", "15:00", "나", b).ok();
            List<Future<Boolean>> fs = pool.invokeAll(List.of(ta, tb));
            int ok = 0;
            for (Future<Boolean> f : fs) {
                ok += f.get() ? 1 : 0;
            }
            assertThat(ok).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        // 회의실을 멈추면 남은 예약자에게 알린다
        assertThat(office.setRoomUse(room, false, admin).message()).contains("1건");
        assertThat(messages(a).toString() + messages(b)).contains("쓸 수 없게 되었습니다");
    }

    @Test
    void 교대는_받는_사람이_잇달아_서거나_휴가면_안_되고_지난_예약은_취소_못한다() throws Exception {
        LoginUser admin = TestUsers.admin();
        for (int i = 0; i < 4; i++) {
            staff("교대검사" + i);
        }
        LocalDate from = LocalDate.of(2031, 6, 10); // 화요일 (공휴일 아님)
        office.buildRoster(from, from.plusDays(3), admin);
        List<Map<String, Object>> r = office.roster(from, from.plusDays(3));
        Map<String, Object> first = r.get(0);
        // 다음 날 당직자에게 첫날을 부탁하면 이틀 잇달아 서게 된다
        Map<String, Object> nextDay = r.stream().filter(x -> x.get("day").equals(from.plusDays(1))).findFirst().orElseThrow();
        LoginUser owner = users.findByLoginId((String) first.get("loginId"));
        LoginUser near = users.findByLoginId((String) nextDay.get("loginId"));
        OfficeService.Result sw = office.requestSwap(((Number) first.get("id")).longValue(), near.getLoginId(), null, "사정", owner);
        assertThat(sw.ok()).as(sw.message()).isTrue();
        assertThat(office.decideSwap(sw.id(), true, near).message()).contains("잇달아");
        // 그날 휴가인 사람은 받을 수 없다
        LoginUser away = staff("휴가자");
        LoginUser boss = staff("결재자");
        DutyService.Result leave = duty.apply(DutyKind.SPECIAL, from, from, null, null, null, null, "경조사", boss.getLoginId(), away);
        assertThat(leave.ok()).as(leave.message()).isTrue();
        OfficeService.Result sw2 = office.requestSwap(((Number) first.get("id")).longValue(), away.getLoginId(), null, "사정", owner);
        assertThat(office.decideSwap(sw2.id(), true, away).message()).contains("휴가");
        office.clearRoster(from, from.plusDays(3), admin);
    }

    @Test
    void 시간외근무는_자정을_넘겨_신청할_수_있고_오래된_사후_신청은_막는다() throws Exception {
        LoginUser me = staff("야간"), boss = staff("과장");
        LocalDate d = LocalDate.now();
        DutyService.Result r = duty.apply(DutyKind.OVERTIME, d, d, null, "22:00", "01:00", null, "재난 대응", boss.getLoginId(), me);
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(duty.list(me, null, 5).get(0).getPeriodText()).contains("22:00~다음 날 01:00").contains("3시간");
        assertThat(duty.apply(DutyKind.OVERTIME, d.minusDays(10), d.minusDays(10), null, "18:00", "20:00", null, "사후", boss.getLoginId(), me)
                .message()).contains("7일");
        assertThat(duty.apply(DutyKind.OVERTIME, d.plusDays(40), d.plusDays(40), null, "18:00", "20:00", null, "먼 미래", boss.getLoginId(), me)
                .message()).contains("한 달");
    }

    @Test
    void 근태_정정은_지금_기록과_맞춰_보고_미래_시각을_막는다() throws Exception {
        LoginUser me = staff("정정검사"), boss = staff("팀장");
        LocalDate d = LocalDate.now().minusDays(1);
        AttendanceVO a = new AttendanceVO();
        a.setAttId(attendance.nextId());
        a.setLoginId(me.getLoginId());
        a.setUserName(me.getUserName());
        a.setWorkDate(d);
        a.setInAt(d.atTime(9, 0));
        attendance.insert(a);
        assertThat(workPay.applyFix(d, null, "08:00", false, "퇴근 못 누름", boss.getLoginId(), me).message()).contains("퇴근이 출근보다");
        assertThat(workPay.applyFix(d, null, "10:00", true, "자정 넘김", boss.getLoginId(), me).message()).contains("오전 9시");
        assertThat(workPay.applyFix(LocalDate.now(), "23:59", null, false, "미래", boss.getLoginId(), me).ok())
                .isEqualTo(LocalTime.now().isAfter(LocalTime.of(23, 59)));
        LocalDate none = LocalDate.now().minusDays(2);
        assertThat(workPay.applyFix(none, null, "18:00", false, "기록 없음", boss.getLoginId(), me).message()).contains("출근 시각도");
        assertThat(workPay.applyFix(d, null, "18:10", false, "퇴근 못 누름", boss.getLoginId(), me).ok()).isTrue();
    }

    @Test
    void 의무교육_같은_날_중복과_인계서_회수() {
        LoginUser me = staff("중복"), to = staff("받는이");
        long course = ((Number) office.courses(true).get(0).get("id")).longValue();
        assertThat(office.addEdu(course, LocalDate.now(), 1.0, null, me).ok()).isTrue();
        assertThat(office.addEdu(course, LocalDate.now(), 1.0, null, me).message()).contains("같은 날");
        OfficeService.Result h = office.handover(to.getLoginId(), "인계", "업무", null, null, me);
        assertThat(office.withdrawHandover(h.id(), to).ok()).isFalse();
        assertThat(office.withdrawHandover(h.id(), me).ok()).isTrue();
        assertThat(office.handoverFor(h.id(), me)).isNull();
        assertThat(messages(to).toString()).contains("회수");
        OfficeService.Result h2 = office.handover(to.getLoginId(), "인계2", "업무", null, null, me);
        office.acceptHandover(h2.id(), null, to);
        assertThat(office.withdrawHandover(h2.id(), me).message()).contains("인수 확인");
    }

    @Autowired egovframework.haengjeongnuli.office.OfficeMapper officeMapper;

    @Test
    void 지난_회의실_예약은_취소할_수_없다() {
        LoginUser admin = TestUsers.admin();
        long room = office.addRoom("과거" + UUID.randomUUID().toString().substring(0, 4), null, null, admin).id();
        LoginUser a = staff("과거예약");
        long past = officeMapper.nextResvId();
        officeMapper.insertResv(past, room, LocalDate.now().minusDays(1), "09:00", "10:00", "지난 회의", a.getLoginId(), a.getUserName(),
                null, LocalDateTime.now());
        assertThat(office.cancel(past, a).message()).contains("지난 예약");
        assertThat(office.cancel(past, admin).ok()).isFalse();
        OfficeService.Result r = office.reserve(room, LocalDate.now().plusDays(1), "09:00", "10:00", "회의", a);
        assertThat(office.cancel(r.id(), a).ok()).isTrue();
    }

    @Test
    void 회원_로그인은_같은_IP에서_여러_번_틀리면_막고_관리자_비슷한_아이디는_못_쓴다() {
        String ip = "198.51.100." + (int) (Math.random() * 200 + 20);
        for (int i = 0; i < 10; i++) {
            try {
                members.login(new EPetitionMemberService.LoginRequest("nobody" + i, "x"), ip);
            } catch (EPetitionService.Problem p) {
                assertThat(p.code()).isEqualTo("not_matched");
            }
        }
        try {
            members.login(new EPetitionMemberService.LoginRequest("nobody", "x"), ip);
            assertThat(false).as("막혀야 함").isTrue();
        } catch (EPetitionService.Problem p) {
            assertThat(p.status()).isEqualTo(429);
        }
        for (String bad : new String[] {"admin1", "administrator", "root", "system"}) {
            try {
                members.signup(new EPetitionMemberService.SignupRequest(bad, "Blue-River-77!", "김", null, null, null, true, true));
                assertThat(false).as(bad).isTrue();
            } catch (EPetitionService.Problem p) {
                assertThat(p.code()).isEqualTo("invalid_login_id");
            }
        }
    }
}
