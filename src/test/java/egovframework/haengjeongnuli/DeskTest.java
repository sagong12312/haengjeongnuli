package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.civil.service.CivilCategory;
import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilForm;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.desk.DeskService;
import egovframework.haengjeongnuli.office.OfficeService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 내 업무 도구 */
@SpringBootTest
class DeskTest {

    @Autowired WebApplicationContext ctx;
    @Autowired DeskService desk;
    @Autowired OfficeService office;
    @Autowired UserService users;
    @Autowired CivilService civil;
    @Autowired NotificationService notifications;
    @Autowired JdbcTemplate jdbc;

    LoginUser staff(String name, String dept) {
        String id = "d" + UUID.randomUUID().toString().substring(0, 8);
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

    boolean notified(LoginUser u, String text) {
        return notifications.recent(u.getLoginId(), 20).stream().anyMatch(n -> n.getMessage().contains(text));
    }

    @Test
    void 할_일은_본인만_보고_기한_지남을_표시한다() throws Exception {
        LoginUser a = staff("가할일", "총무과");
        LoginUser b = staff("나할일", "총무과");
        assertThat(desk.addTodo(" ", null, a).ok()).isFalse();
        DeskService.Result late = desk.addTodo("보고서 제출", LocalDate.now().minusDays(1), a);
        DeskService.Result today = desk.addTodo("회의 자료", LocalDate.now(), a);
        assertThat(late.ok()).isTrue();
        List<Map<String, Object>> list = desk.todos(a);
        assertThat(list).anyMatch(t -> "보고서 제출".equals(t.get("title")) && Boolean.TRUE.equals(t.get("overdue")));
        assertThat(desk.todos(b)).isEmpty();
        assertThat(desk.toggleTodo(late.id(), true, b).ok()).isFalse(); // 남의 것
        assertThat(desk.deleteTodo(late.id(), b).ok()).isFalse();
        assertThat(desk.toggleTodo(late.id(), true, a).ok()).isTrue();
        assertThat(desk.todos(a)).anyMatch(t -> "보고서 제출".equals(t.get("title")) && "Y".equals(t.get("done")));
        assertThat(page(a, "/desk")).contains("회의 자료").doesNotContain("보고서 제출");
        assertThat(page(a, "/desk/todos")).contains("보고서 제출").contains("마침");
        assertThat(desk.deleteTodo(today.id(), a).ok()).isTrue();
    }

    @Test
    void 즐겨찾기는_정해진_메뉴만_받는다() throws Exception {
        LoginUser a = staff("가즐겨", "총무과");
        assertThat(desk.saveFavorites(List.of("/civil", "https://evil.example", "/desk/todos"), a).ok()).isTrue();
        assertThat(desk.favorites(a)).extracting(m -> m.get("path")).containsExactly("/civil", "/desk/todos");
        assertThat(page(a, "/desk")).contains("민원 목록").doesNotContain("evil.example");
        List<String> many = DeskService.MENUS.stream().map(DeskService.Menu::path).toList();
        assertThat(desk.saveFavorites(many, a).ok()).isFalse();
        assertThat(desk.favorites(a)).hasSize(2); // 실패하면 그대로
    }

    @Test
    void 쪽지는_보낸_사람과_받는_사람만_보고_읽음이_남는다() throws Exception {
        LoginUser a = staff("가쪽지", "민원과-" + UUID.randomUUID().toString().substring(0, 4));
        LoginUser b = staff("나쪽지", a.getDeptName());
        LoginUser c = staff("다쪽지", a.getDeptName());
        LoginUser x = staff("라남", "다른과");
        assertThat(desk.send(a.getLoginId(), "나에게", a).ok()).isFalse();
        assertThat(desk.send("없는사람", "안녕", a).ok()).isFalse();
        DeskService.Result r = desk.send(b.getLoginId(), "오후 3시 회의 자료 부탁드립니다", a);
        assertThat(r.ok()).isTrue();
        assertThat(notified(b, "가쪽지님의 쪽지")).isTrue();
        assertThat(desk.unread(b)).isEqualTo(1);
        assertThat(desk.open(r.id(), x)).isNull();
        assertThat(TestUsers.mvcAs(ctx, x).perform(get("/desk/messages/" + r.id())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(desk.open(r.id(), a).get("readAt")).isNull(); // 보낸 사람이 열어도 읽음 아님
        assertThat(page(b, "/desk/messages/" + r.id())).contains("오후 3시 회의 자료");
        assertThat(desk.unread(b)).isZero();
        // 받는 사람이 지워도 보낸 쪽에는 남는다
        assertThat(desk.deleteMessage(r.id(), b).ok()).isTrue();
        assertThat(desk.inbox(b)).isEmpty();
        assertThat(desk.sent(a)).hasSize(1);
        // 부서 전체
        DeskService.Result all = desk.sendDept("금요일 부서 회식", users.findByLoginId(a.getLoginId()));
        assertThat(all.message()).contains("2명");
        assertThat(desk.inbox(c)).anyMatch(m -> m.get("body").toString().contains("금요일 부서 회식"));
        assertThat(desk.inbox(x)).isEmpty();
        // 통합검색에서 내 쪽지·직원을 찾는다
        assertThat(page(c, "/search?q=" + "회식")).contains("금요일 부서 회식");
        assertThat(page(x, "/search?q=" + "회식")).doesNotContain("금요일 부서 회식");
    }

    @Test
    void 업무_분장으로_직원을_찾고_민원_담당자를_추천한다() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 4);
        LoginUser a = staff("가분장", "교통과");
        LoginUser b = staff("나분장", "교통과");
        assertThat(desk.saveDuty(b.getLoginId(), "가로등", a).ok()).isFalse(); // 남의 것
        assertThat(desk.saveDuty(null, "불법주정차" + tag + ", 견인, 불법주정차" + tag, a).ok()).isTrue();
        assertThat(desk.dutyMap().get(a.getLoginId())).isEqualTo("불법주정차" + tag + ", 견인");
        assertThat(desk.saveDuty(null, "가로등" + tag, b).ok()).isTrue();
        assertThat(desk.staff("불법주정차" + tag)).extracting(s -> s.user().getLoginId()).containsExactly(a.getLoginId());
        assertThat(page(b, "/desk/staff?q=" + "불법주정차" + tag)).contains("가분장");

        CivilComplaintVO c = receive("홍추천", "010-5555-1234", "우리 집 앞 불법주정차" + tag + " 단속 요청");
        List<DeskService.Suggestion> s = desk.suggest(c);
        assertThat(s).isNotEmpty();
        assertThat(s.get(0).user().getLoginId()).isEqualTo(a.getLoginId());
        assertThat(s.get(0).matched()).containsExactly("불법주정차" + tag);
    }

    CivilComplaintVO receive(String name, String mobile, String title) {
        CivilForm form = new CivilForm();
        form.setKind(CivilKind.OTHER);
        form.setCategory(CivilCategory.PARKING);
        form.setName(name);
        form.setMobile(mobile);
        form.setNoHome(true);
        form.setNotify("문자");
        form.setTitle(title);
        form.setContent(title + " 내용");
        form.setStaff("담당자A");
        form.setPrivacyConsent(true);
        return civil.receive(form, "테스터");
    }

    @Test
    void 같은_민원인의_민원을_묶고_세_번째부터_반복_민원으로_표시한다() throws Exception {
        String mobile = "010-7" + String.format("%03d", (int) (Math.random() * 1000)) + "-" + String.format("%04d", (int) (Math.random() * 10000));
        String name = "반복" + UUID.randomUUID().toString().substring(0, 4);
        CivilComplaintVO one = receive(name, mobile, "첫 민원");
        CivilComplaintVO other = receive(name, "010-0000-0001", "이름만 같은 다른 사람");
        assertThat(desk.sameApplicantCount(one)).isZero();
        CivilComplaintVO two = receive(name, mobile, "두 번째 민원");
        assertThat(DeskService.isRepeat(desk.sameApplicantCount(two))).isFalse();
        CivilComplaintVO three = receive(name, mobile, "세 번째 민원");
        assertThat(desk.sameApplicantCount(three)).isEqualTo(2);
        assertThat(DeskService.isRepeat(desk.sameApplicantCount(three))).isTrue();
        LoginUser admin = TestUsers.admin();
        assertThat(desk.sameApplicant(three, admin)).extracting(m -> m.get("title")).containsExactly("두 번째 민원", "첫 민원");
        String html = page(admin, "/civil/" + three.getComplaintId());
        assertThat(html).contains("반복 민원").contains("같은 민원인의 다른 민원").contains("첫 민원").doesNotContain("이름만 같은 다른 사람");
        assertThat(other.getComplaintId()).isNotNull();
    }

    @Test
    void 답변_문구는_내_것과_부서_공용만_보이고_민원_처리_화면에_나온다() throws Exception {
        String dept = "문구과-" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser a = staff("가문구", dept);
        LoginUser b = staff("나문구", dept);
        LoginUser x = staff("다문구", "다른과");
        assertThat(desk.addTemplate("인사", "", false, a).ok()).isFalse();
        DeskService.Result mine = desk.addTemplate("내 인사말", "안녕하십니까. 민원 주셔서 감사합니다.", false, a);
        DeskService.Result shared = desk.addTemplate("부서 안내", "담당 부서는 " + dept + "입니다.", true, a);
        assertThat(desk.templates(a)).hasSize(2);
        assertThat(desk.templates(b)).extracting(t -> t.get("title")).containsExactly("부서 안내");
        assertThat(desk.templates(x)).isEmpty();
        assertThat(desk.deleteTemplate(mine.id(), b).ok()).isFalse();
        assertThat(desk.deleteTemplate(shared.id(), x).ok()).isFalse();
        assertThat(desk.deleteTemplate(shared.id(), b).ok()).isTrue(); // 같은 부서 공용은 부서원이 지운다
        // 민원 처리 화면 (관리자는 처리할 수 있다)
        LoginUser admin = TestUsers.admin();
        desk.addTemplate("관리자 문구", "검토하겠습니다.", false, admin);
        CivilComplaintVO c = receive("문구민원", "010-3333-4444", "문구 시험");
        assertThat(page(admin, "/civil/" + c.getComplaintId())).contains("답변 문구 넣기").contains("관리자 문구");
    }

    @Test
    void 물품은_한_사람만_빌리고_빌린_사람이나_관리자만_반납한다() throws Exception {
        LoginUser admin = TestUsers.admin();
        LoginUser a = staff("가대여", "총무과");
        LoginUser b = staff("나대여", "총무과");
        assertThat(desk.addItem("노트북", null, a).ok()).isFalse();
        String name = "노트북-" + UUID.randomUUID().toString().substring(0, 4);
        DeskService.Result item = desk.addItem(name, "총무과 캐비닛", admin);
        assertThat(item.ok()).isTrue();
        assertThat(desk.borrow(item.id(), "발표", LocalDate.now().plusDays(31), a).ok()).isFalse();
        assertThat(desk.borrow(item.id(), "", LocalDate.now().plusDays(3), a).ok()).isFalse();
        DeskService.Result loan = desk.borrow(item.id(), "주민설명회 발표", LocalDate.now().plusDays(3), a);
        assertThat(loan.ok()).as(loan.message()).isTrue();
        assertThat(desk.borrow(item.id(), "교육", LocalDate.now().plusDays(1), b).message()).contains("이미");
        assertThat(desk.giveBack(loan.id(), b).ok()).isFalse();
        assertThat(page(b, "/desk/loans")).contains(name).contains("대여 중");
        // 반납 기한이 지나면 알린다
        jdbc.update("UPDATE LOAN SET DUE_ON = ? WHERE LOAN_ID = ?", java.sql.Date.valueOf(LocalDate.now().minusDays(1)), loan.id());
        assertThat(desk.remindOverdue()).isGreaterThanOrEqualTo(1);
        assertThat(notified(a, name)).isTrue();
        assertThat(page(a, "/desk")).contains("기한 지남");
        assertThat(desk.giveBack(loan.id(), a).ok()).isTrue();
        assertThat(desk.giveBack(loan.id(), a).ok()).isFalse();
        assertThat(desk.borrow(item.id(), "교육", LocalDate.now().plusDays(1), b).ok()).isTrue();
    }

    @Test
    void 설문은_한_번만_참여하고_누가_무엇을_골랐는지_남지_않는다() throws Exception {
        LoginUser a = staff("가설문", "총무과");
        LoginUser b = staff("나설문", "총무과");
        assertThat(desk.addSurvey("교육 날짜", "언제?", "하나뿐", LocalDate.now().plusDays(3), a).ok()).isFalse();
        assertThat(desk.addSurvey("교육 날짜", "언제?", "가\n나", LocalDate.now().plusDays(61), a).ok()).isFalse();
        DeskService.Result s = desk.addSurvey("교육 날짜", "언제가 좋으십니까?", "12월 3일\n12월 10일\n12월 3일", LocalDate.now().plusDays(3), a);
        assertThat(s.ok()).isTrue();
        assertThat(desk.survey(s.id(), a).options()).containsExactly("12월 3일", "12월 10일");
        assertThat(desk.vote(s.id(), 3, a).ok()).isFalse();
        assertThat(desk.vote(s.id(), 2, a).ok()).isTrue();
        assertThat(desk.vote(s.id(), 1, a).message()).contains("이미");
        assertThat(desk.vote(s.id(), 2, b).ok()).isTrue();
        DeskService.SurveyView v = desk.survey(s.id(), b);
        assertThat(v.counts()).containsExactly(0, 2);
        assertThat(v.total()).isEqualTo(2);
        assertThat(page(b, "/desk/surveys/" + s.id())).contains("결과").contains("100%");
        // 참여자 표에는 고른 보기가 없다
        assertThat(jdbc.queryForList("SELECT * FROM SURVEY_VOTER WHERE SURVEY_ID = ?", s.id()).get(0).keySet())
                .containsExactlyInAnyOrder("SURVEY_ID", "LOGIN_ID", "VOTED_AT");
        jdbc.update("UPDATE SURVEY SET CLOSES_ON = ? WHERE SURVEY_ID = ?", java.sql.Date.valueOf(LocalDate.now().minusDays(1)), s.id());
        LoginUser c = staff("다설문", "총무과");
        assertThat(desk.vote(s.id(), 1, c).message()).contains("마감");
    }

    @Test
    void 회의_참석자를_정하면_알리고_참석자_일정에_나온다() throws Exception {
        LoginUser admin = TestUsers.admin();
        LoginUser a = staff("가회의", "총무과");
        LoginUser b = staff("나회의", "총무과");
        LoginUser c = staff("다회의", "총무과");
        long roomId = office.addRoom("참석실-" + UUID.randomUUID().toString().substring(0, 4), null, null, admin).id();
        LocalDate d = LocalDate.now().plusDays(3);
        long resv = office.reserve(roomId, d, "14:00", "15:00", "예산 협의", a).id();
        assertThat(desk.setAttendees(resv, List.of(b.getLoginId()), c).ok()).isFalse(); // 예약자 아님
        DeskService.Result r = desk.setAttendees(resv, List.of(b.getLoginId(), a.getLoginId(), "없는사람"), a);
        assertThat(r.ok()).isTrue();
        assertThat(r.message()).contains("1명");
        assertThat(notified(b, "예산 협의")).isTrue();
        assertThat(desk.myMeetings(b, d, d)).hasSize(1);
        assertThat(desk.myMeetings(c, d, d)).isEmpty();
        Map<LocalDate, List<DeskService.Event>> cal = desk.calendar(b, YearMonth.from(d));
        assertThat(cal.get(d)).anyMatch(e -> e.text().contains("예산 협의") && e.text().contains("참석"));
        assertThat(page(b, "/desk/calendar?ym=" + YearMonth.from(d))).contains("예산 협의");
        assertThat(page(a, "/office/rooms?date=" + d)).contains("나회의").contains("내 예약 참석자 정하기");
        // 화면에서 저장
        MockMvc m = TestUsers.mvcAs(ctx, a);
        m.perform(post("/office/rooms/resv/" + resv + "/attendees").with(csrf()).param("attendee", c.getLoginId()));
        assertThat(notified(c, "예산 협의")).isTrue();
        assertThat(desk.myMeetings(b, d, d)).isEmpty();
    }

    @Test
    void 화면이_열린다() throws Exception {
        LoginUser a = staff("가화면", "총무과");
        for (String p : List.of("/desk", "/desk/todos", "/desk/favorites", "/desk/messages", "/desk/messages?box=sent", "/desk/staff",
                "/desk/templates", "/desk/loans", "/desk/surveys", "/desk/calendar", "/desk/calendar?ym=bad")) {
            int st = TestUsers.mvcAs(ctx, a).perform(get(p)).andReturn().getResponse().getStatus();
            assertThat(st).as(p).isEqualTo(200);
        }
        assertThat(page(a, "/")).contains("내 업무");
        assertThat(page(a, "/search?q=" + "쪽지")).contains("/desk/messages");
    }
}
