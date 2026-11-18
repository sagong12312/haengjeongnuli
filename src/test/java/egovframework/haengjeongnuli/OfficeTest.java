package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.office.OfficeService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 공통 업무: 회의실 예약, 당직, 주간 업무 보고, 인계인수, 법정의무교육, 비상연락망 */
@SpringBootTest
class OfficeTest {

    @Autowired WebApplicationContext ctx;
    @Autowired OfficeService office;
    @Autowired UserService users;
    @Autowired HolidayService holidays;
    @Autowired NotificationService notifications;

    LoginUser staff(String name, String dept) {
        String id = "o" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        users.create(f);
        LoginUser u = users.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return u;
    }

    String page(LoginUser u, String path) throws Exception {
        MockMvc m = TestUsers.mvcAs(ctx, u);
        return m.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 회의실은_겹치지_않게_예약하고_예약한_사람만_취소한다() throws Exception {
        LoginUser admin = TestUsers.admin();
        LoginUser a = staff("가예약", "총무과");
        LoginUser b = staff("나예약", "총무과");
        String room = "소회의실-" + UUID.randomUUID().toString().substring(0, 4);
        assertThat(office.addRoom(room, "본관 3층", 8, a).ok()).isFalse();
        OfficeService.Result r = office.addRoom(room, "본관 3층", 8, admin);
        assertThat(r.ok()).isTrue();
        assertThat(office.addRoom(room, null, null, admin).ok()).isFalse(); // 같은 이름
        long roomId = r.id();
        LocalDate d = LocalDate.now().plusDays(2);
        OfficeService.Result first = office.reserve(roomId, d, "10:00", "11:30", "예산 회의", a);
        assertThat(first.ok()).as(first.message()).isTrue();
        assertThat(office.reserve(roomId, d, "11:00", "12:00", "겹침", b).message()).contains("이미 예약");
        assertThat(office.reserve(roomId, d, "11:30", "12:00", "바로 뒤", b).ok()).isTrue(); // 끝과 시작이 같으면 괜찮다
        assertThat(office.reserve(roomId, d, "10:15", "11:00", "30분 단위 아님", b).ok()).isFalse();
        assertThat(office.reserve(roomId, LocalDate.now().minusDays(1), "10:00", "11:00", "지난날", b).ok()).isFalse();
        assertThat(office.reserve(roomId, d, "06:00", "08:00", "너무 이름", b).ok()).isFalse();
        assertThat(office.cancel(first.id(), b).ok()).isFalse();
        assertThat(office.cancel(first.id(), a).ok()).isTrue();
        assertThat(office.reserve(roomId, d, "10:00", "11:00", "다시", b).ok()).isTrue();
        assertThat(page(a, "/office/rooms?date=" + d)).contains(room).contains("다시").contains("바로 뒤");
        assertThat(office.setRoomUse(roomId, false, admin).ok()).isTrue();
        assertThat(office.reserve(roomId, d, "15:00", "16:00", "멈춘 방", b).ok()).isFalse();
    }

    @Test
    void 당직은_공평하게_짜고_휴가자와_전날_당직자를_빼며_교대와_근무일지를_쓴다() throws Exception {
        LoginUser admin = TestUsers.admin();
        for (int i = 0; i < 4; i++) {
            staff("당직" + i, "총무과");
        }
        // 이 테스트만의 먼 미래 기간 (다른 테스트와 겹치지 않게)
        LocalDate from = LocalDate.of(2030, 3, 4);
        LocalDate to = from.plusDays(6);
        assertThat(office.buildRoster(from, to, staff("일반", "총무과")).ok()).isFalse();
        OfficeService.Result b = office.buildRoster(from, to, admin);
        assertThat(b.ok()).as(b.message()).isTrue();
        List<Map<String, Object>> roster = office.roster(from, to);
        long expected = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            expected += holidays.isWorkingDay(d) ? 1 : 2;
        }
        assertThat(roster).hasSize((int) expected);
        // 하루에 같은 사람이 두 번, 이틀 잇달아 서지 않는다
        for (int i = 1; i < roster.size(); i++) {
            Map<String, Object> p = roster.get(i - 1), c = roster.get(i);
            if (!p.get("day").equals(c.get("day")) && ((LocalDate) c.get("day")).minusDays(1).equals(p.get("day"))) {
                assertThat(c.get("loginId")).isNotEqualTo(p.get("loginId"));
            }
            if (p.get("day").equals(c.get("day"))) {
                assertThat(c.get("loginId")).isNotEqualTo(p.get("loginId"));
            }
        }
        // 다시 짜도 그대로
        assertThat(office.buildRoster(from, to, admin).message()).contains("새로 편성할 날이 없습니다");

        // 교대: 다른 사람에게 부탁 → 받아들이면 바뀐다
        Map<String, Object> mine = roster.get(0);
        LoginUser owner = users.findByLoginId((String) mine.get("loginId"));
        Map<String, Object> other = roster.stream().filter(r -> !r.get("loginId").equals(owner.getLoginId())).findFirst().orElseThrow();
        LoginUser helper = users.findByLoginId((String) other.get("loginId"));
        long rid = ((Number) mine.get("id")).longValue();
        assertThat(office.requestSwap(rid, helper.getLoginId(), null, "가족 행사", helper).ok()).isFalse(); // 남의 당직
        OfficeService.Result sw = office.requestSwap(rid, helper.getLoginId(), ((Number) other.get("id")).longValue(), "가족 행사", owner);
        assertThat(sw.ok()).as(sw.message()).isTrue();
        assertThat(office.requestSwap(rid, helper.getLoginId(), null, "또", owner).message()).contains("기다리는 중");
        assertThat(office.decideSwap(sw.id(), true, owner).ok()).isFalse(); // 신청한 사람은 받아들일 수 없다
        assertThat(office.decideSwap(sw.id(), true, helper).ok()).isTrue();
        List<Map<String, Object>> after = office.roster(from, to);
        assertThat(after.stream().filter(r -> ((Number) r.get("id")).longValue() == rid).findFirst().orElseThrow().get("loginId"))
                .isEqualTo(helper.getLoginId());
        assertThat(after.stream().filter(r -> r.get("id").equals(other.get("id"))).findFirst().orElseThrow().get("loginId"))
                .isEqualTo(owner.getLoginId());
        assertThat(notifications.recent(owner.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).contains("받아들였습니다");
        // 미래 당직에는 근무일지를 쓸 수 없다
        assertThat(office.writeLog(rid, "순찰", helper).message()).contains("이틀 안");
        assertThat(office.clearRoster(from, to, admin).ok()).isTrue();
        assertThat(office.roster(from, to)).isEmpty();
    }

    @Test
    void 오늘_당직이면_근무일지를_쓴다() {
        LoginUser admin = TestUsers.admin();
        for (int i = 0; i < 3; i++) {
            staff("오늘당직" + i, "총무과");
        }
        LocalDate today = LocalDate.now();
        office.buildRoster(today, today, admin);
        Map<String, Object> r = office.roster(today, today).get(0);
        LoginUser who = users.findByLoginId((String) r.get("loginId"));
        long id = ((Number) r.get("id")).longValue();
        assertThat(office.writeLog(id, "", who).ok()).isFalse();
        assertThat(office.writeLog(id, "22:00 본관 순찰 이상 없음", staff("남", "총무과")).ok()).isFalse();
        assertThat(office.writeLog(id, "22:00 본관 순찰 이상 없음", who).ok()).isTrue();
        assertThat(office.roster(today, today).get(0).get("log")).isEqualTo("22:00 본관 순찰 이상 없음");
    }

    @Test
    void 주간_업무_보고를_쓰고_부서에서_취합한다() throws Exception {
        String dept = "기획" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser a = staff("가보고", dept);
        LoginUser b = staff("나보고", dept);
        LoginUser x = staff("다른부서", "총무과");
        assertThat(office.saveReport(null, "", "계획", null, a).ok()).isFalse();
        assertThat(office.saveReport(LocalDate.now().plusWeeks(1), "한 일", "계획", null, a).ok()).isFalse();
        assertThat(office.saveReport(null, "예산 요구서 작성", "부서 협의", null, a).ok()).isTrue();
        assertThat(office.saveReport(null, "예산 요구서 작성 완료", "부서 협의", "회의실 필요", a).ok()).isTrue(); // 고치기
        assertThat(office.myReport(a, null).get("done")).isEqualTo("예산 요구서 작성 완료");
        assertThat(office.deptReports(b, null, null)).hasSize(1);
        assertThat(office.missingReports(b, null, null)).containsExactly("나보고");
        assertThat(office.deptReports(x, dept, null)).isEmpty(); // 다른 부서는 볼 수 없다
        assertThat(page(b, "/office/weekly/dept")).contains("예산 요구서 작성 완료").contains("나보고");
        String csv = new String(TestUsers.mvcAs(ctx, b).perform(get("/office/weekly/dept.csv").param("reason", "주간 보고")).andReturn().getResponse()
                .getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("회의실 필요");
    }

    @Test
    void 인계서를_보내고_받은_사람만_인수_확인한다() throws Exception {
        LoginUser from = staff("인계자", "세무과");
        LoginUser to = staff("인수자", "세무과");
        LoginUser x = staff("구경꾼", "세무과");
        assertThat(office.handover(from.getLoginId(), "x", "y", null, null, from).ok()).isFalse(); // 자기 자신
        OfficeService.Result r = office.handover(to.getLoginId(), "세외수입 업무 인계", "1. 체납 고지 — 12월 발송", "결재 세무과-12", null, from);
        assertThat(r.ok()).isTrue();
        assertThat(office.handoverFor(r.id(), x)).isNull();
        assertThat(office.acceptHandover(r.id(), null, from).ok()).isFalse();
        assertThat(office.acceptHandover(r.id(), "확인했습니다", to).ok()).isTrue();
        assertThat(office.acceptHandover(r.id(), null, to).ok()).isFalse();
        assertThat(page(from, "/office/handovers/" + r.id())).contains("확인했습니다").contains("세무과-12");
        TestUsers.mvcAs(ctx, x).perform(get("/office/handovers/" + r.id()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        assertThat(notifications.recent(from.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).contains("인수 확인");
    }

    @Test
    void 법정의무교육_이수와_미이수() throws Exception {
        LoginUser a = staff("교육", "총무과");
        List<Map<String, Object>> courses = office.courses(true);
        assertThat(courses).extracting(c -> c.get("name")).contains("청렴교육");
        long clean = courses.stream().filter(c -> "청렴교육".equals(c.get("name"))).findFirst().map(c -> ((Number) c.get("id")).longValue()).orElseThrow();
        int y = LocalDate.now().getYear();
        assertThat(office.addEdu(clean, LocalDate.now().plusDays(1), 1.0, null, a).ok()).isFalse();
        assertThat(office.addEdu(clean, LocalDate.now(), 1.0, "나라배움터", a).ok()).isTrue();
        assertThat(office.eduStatus(a.getLoginId(), y).stream().filter(s -> "청렴교육".equals(s.course().get("name"))).findFirst().orElseThrow()
                .complete()).isFalse(); // 2시간 필요
        // 같은 날 같은 과목은 한 번만 (두 번째 교육은 다른 날)
        assertThat(office.addEdu(clean, LocalDate.now(), 1.0, null, a).ok()).isFalse();
        LocalDate other = LocalDate.now().getDayOfYear() > 1 ? LocalDate.now().minusDays(1) : LocalDate.now();
        assertThat(office.addEdu(clean, other, 1.0, null, a).ok()).isEqualTo(!other.equals(LocalDate.now()));
        assertThat(office.eduStatus(a.getLoginId(), y).stream().filter(s -> "청렴교육".equals(s.course().get("name"))).findFirst().orElseThrow()
                .complete()).isEqualTo(!other.equals(LocalDate.now()));
        assertThat(office.missingEdu(y).get("청렴교육")).noneMatch(n -> n.startsWith("교육 ("));
        assertThat(office.missingEdu(y).get("장애인 인식개선 교육")).anyMatch(n -> n.startsWith("교육 ("));
        assertThat(office.remindEdu(y, a).ok()).isFalse();
        assertThat(office.remindEdu(y, TestUsers.admin()).ok()).isTrue();
        assertThat(notifications.recent(a.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).contains("법정의무교육 미이수").doesNotContain("청렴교육,");
        assertThat(page(a, "/office/edu")).contains("이수").contains("나라배움터");
    }

    @Test
    void 비상연락망은_동의한_번호만_보이고_관리자는_다_본다() throws Exception {
        LoginUser a = staff("공개함", "안전과");
        LoginUser b = staff("비공개", "안전과");
        assertThat(office.saveContact("123", true, a).ok()).isFalse();
        assertThat(office.saveContact("01011112222", true, a).ok()).isTrue();
        assertThat(office.saveContact("010-3333-4444", false, b).ok()).isTrue();
        String seen = page(a, "/office/contacts");
        assertThat(seen).contains("010-1111-2222").doesNotContain("010-3333-4444").contains("비공개");
        assertThat(page(TestUsers.admin(), "/office/contacts")).contains("010-3333-4444");
        assertThat(page(b, "/office/contacts")).contains("010-3333-4444"); // 본인 번호는 본인에게 보인다
        assertThat(office.saveContact("", false, b).ok()).isTrue();
        assertThat(office.myContact(b)).isNull();
        assertThat(page(a, "/office")).contains("공통 업무").contains("법정의무교육");
    }
}
