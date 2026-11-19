package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
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

import egovframework.haengjeongnuli.biz.BizService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.desk.DeskService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 업무 지원 */
@SpringBootTest
class BizTest {

    @Autowired WebApplicationContext ctx;
    @Autowired BizService biz;
    @Autowired DeskService desk;
    @Autowired UserService users;
    @Autowired NotificationService notifications;
    @Autowired HolidayService holidays;
    @Autowired JdbcTemplate jdbc;

    LoginUser staff(String name, String dept) {
        String id = "z" + UUID.randomUUID().toString().substring(0, 8);
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

    boolean notified(LoginUser u, String text) {
        return notifications.recent(u.getLoginId(), 30).stream().anyMatch(n -> n.getMessage().contains(text));
    }

    String page(LoginUser u, String path) throws Exception {
        return TestUsers.mvcAs(ctx, u).perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static String dept() {
        return "지원과-" + UUID.randomUUID().toString().substring(0, 4);
    }

    @Test
    void 업무_일지는_7일_안만_쓰고_부서원이_함께_본다() throws Exception {
        String d = dept();
        LoginUser a = staff("가일지", d);
        LoginUser b = staff("나일지", d);
        LoginUser x = staff("다일지", "다른과");
        LocalDate today = LocalDate.now();
        assertThat(biz.saveWorkLog(today.minusDays(8), "옛날", a).ok()).isFalse();
        assertThat(biz.saveWorkLog(today.plusDays(1), "내일", a).ok()).isFalse();
        assertThat(biz.saveWorkLog(today, "민원 3건 처리", a).ok()).isTrue();
        assertThat(biz.saveWorkLog(today, "민원 4건 처리", a).ok()).isTrue(); // 고치기
        assertThat(biz.workLog(a, today).get("body")).isEqualTo("민원 4건 처리");
        assertThat(biz.deptWorkLogs(b, today)).extracting(m -> m.get("body")).containsExactly("민원 4건 처리");
        assertThat(biz.deptWorkLogs(x, today)).isEmpty();
        assertThat(page(b, "/biz/worklog")).contains("민원 4건 처리");
    }

    @Test
    void 부서_일정은_부서원_달력에_나오고_올린_사람만_지운다() {
        String d = dept();
        LoginUser a = staff("가일정", d);
        LoginUser b = staff("나일정", d);
        LoginUser x = staff("다일정", "다른과");
        LocalDate day = LocalDate.now().plusDays(3);
        BizService.Result r = biz.addDeptEvent(day, "부서 워크숍", true, a);
        assertThat(r.ok()).isTrue();
        assertThat(notified(b, "부서 워크숍")).isTrue();
        assertThat(desk.calendar(b, YearMonth.from(day)).get(day)).anyMatch(e -> e.text().contains("부서 워크숍"));
        assertThat(desk.calendar(x, YearMonth.from(day)).getOrDefault(day, List.of())).noneMatch(e -> e.text().contains("부서 워크숍"));
        assertThat(biz.deleteDeptEvent(r.id(), b).ok()).isFalse();
        assertThat(biz.deleteDeptEvent(r.id(), x).ok()).isFalse();
        assertThat(biz.deleteDeptEvent(r.id(), a).ok()).isTrue();
    }

    @Test
    void 협조_요청은_받은_부서만_회신하고_기한이_지나면_한_번_알린다() throws Exception {
        String from = dept(), to = dept();
        LoginUser a = staff("가협조", from);
        LoginUser b = staff("나협조", to);
        assertThat(biz.requestCoop(from, "같은 부서", "내용", LocalDate.now(), a).ok()).isFalse();
        assertThat(biz.requestCoop("없는부서-zz", "제목", "내용", LocalDate.now(), a).ok()).isFalse();
        BizService.Result r = biz.requestCoop(to, "예산 자료 협조", "3분기 집행 내역을 보내 주세요.", LocalDate.now().plusDays(2), a);
        assertThat(r.ok()).isTrue();
        assertThat(notified(b, "예산 자료 협조")).isTrue();
        assertThat(biz.replyCoop(r.id(), "보냈습니다", a).ok()).isFalse(); // 보낸 쪽은 회신 못 함
        // 기한 지남 알림
        jdbc.update("UPDATE COOP_REQ SET DUE_ON = ? WHERE REQ_ID = ?", java.sql.Date.valueOf(LocalDate.now().minusDays(1)), r.id());
        assertThat(biz.remindCoops()).isGreaterThanOrEqualTo(1);
        assertThat(notified(b, "회신 기한")).isTrue();
        assertThat(biz.remindCoops()).isZero(); // 한 번만
        assertThat(biz.replyCoop(r.id(), "메일로 보냈습니다.", b).ok()).isTrue();
        assertThat(biz.replyCoop(r.id(), "또", b).ok()).isFalse();
        assertThat(notified(a, "회신했습니다")).isTrue();
        assertThat(page(a, "/biz/coops/" + r.id())).contains("메일로 보냈습니다.");
        LoginUser x = staff("라협조", "다른과");
        assertThat(TestUsers.mvcAs(ctx, x).perform(get("/biz/coops/" + r.id())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void 회의록의_후속_조치는_담당자_할_일로_들어간다() throws Exception {
        String d = dept();
        LoginUser a = staff("가회의록", d);
        LoginUser b = staff("나회의록", "다른과");
        LoginUser x = staff("다회의록", "또다른과");
        assertThat(biz.writeMinutes("주간 회의", LocalDate.now(), List.of(b.getLoginId()), "논의", null, "없는사람: 할 일", a).ok()).isFalse();
        assertThat(biz.writeMinutes("주간 회의", LocalDate.now(), List.of(b.getLoginId()), "논의", null, b.getLoginId() + ": 견적 @12월", a).ok()).isFalse();
        BizService.Result r = biz.writeMinutes("주간 회의", LocalDate.now(), List.of(b.getLoginId()), "사업 진행 논의", "예산 증액 건의",
                b.getLoginId() + ": 업체 견적 받기 @" + LocalDate.now().plusDays(5) + "\n" + a.getLoginId() + ": 회의 결과 보고", a);
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(r.message()).contains("2건");
        assertThat(desk.todos(b)).anyMatch(t -> t.get("title").toString().contains("업체 견적 받기") && LocalDate.now().plusDays(5).equals(t.get("due")));
        assertThat(notified(b, "회의 후속 조치")).isTrue();
        assertThat(biz.minutes(r.id(), b)).isNotNull(); // 참석자
        assertThat(biz.minutes(r.id(), x)).isNull();
        assertThat(page(b, "/biz/minutes/" + r.id())).contains("예산 증액 건의").contains("가회의록");
    }

    @Test
    void 전화_기록과_방문객_개인정보_파기() throws Exception {
        String d = dept();
        LoginUser a = staff("가전화", d);
        assertThat(biz.logCall("홍길동", "abc", "문의", null, "DONE", a).ok()).isFalse();
        assertThat(biz.logCall("홍길동", "010-1111-2222", "여권 발급 기간 문의", "7일 안내", "DONE", a).ok()).isTrue();
        BizService.Result cb = biz.logCall(null, "02-123-4567", "건축 허가 진행 문의", null, "CALLBACK", a);
        assertThat(cb.message()).contains("할 일");
        assertThat(desk.todos(a)).anyMatch(t -> t.get("title").toString().contains("건축 허가 진행 문의"));
        BizService.CallStats st = biz.calls(a, YearMonth.now(), null);
        assertThat(st.total()).isEqualTo(2);
        assertThat(st.byResult().get("CALLBACK")).isEqualTo(1);
        assertThat(biz.calls(a, YearMonth.now(), "여권").total()).isEqualTo(1);

        BizService.Result v = biz.visitIn("김방문", "010-3333-4444", "○○건설", "계약 협의", null, a);
        assertThat(v.ok()).isTrue();
        assertThat(biz.visitOut(v.id()).ok()).isTrue();
        assertThat(biz.visitOut(v.id()).ok()).isFalse();
        jdbc.update("UPDATE VISITOR_LOG SET IN_AT = ? WHERE VISIT_ID = ?", java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(200)), v.id());
        assertThat(biz.purgeVisitors()).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT VISITOR_NAME, VISITOR_PHONE, PURPOSE FROM VISITOR_LOG WHERE VISIT_ID = ?", v.id());
        assertThat(row.get("VISITOR_NAME")).isEqualTo("(파기)");
        assertThat(row.get("VISITOR_PHONE")).isNull();
        assertThat(row.get("PURPOSE")).isEqualTo("계약 협의");
    }

    @Test
    void 습득물은_7일_기한을_보이고_5일째_알린다() {
        LoginUser a = staff("가습득", dept());
        assertThat(biz.addLost(LocalDateTime.now().plusDays(1), "민원실", "지갑", null, "금고", a).ok()).isFalse();
        BizService.Result r = biz.addLost(LocalDateTime.now().minusHours(1), "1층 민원실", "검은 지갑-" + a.getLoginId(), "청원경찰", "총무과 금고", a);
        assertThat(r.ok()).isTrue();
        jdbc.update("UPDATE LOST_ITEM SET FOUND_AT = ? WHERE ITEM_ID = ?", java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(6)), r.id());
        assertThat(biz.remindLost()).isGreaterThanOrEqualTo(1);
        assertThat(notified(a, "곧 습득 7일")).isTrue();
        assertThat(biz.handleLost(r.id(), "RETURNED", null, a).ok()).isFalse(); // 받은 사람 이름 필요
        assertThat(biz.handleLost(r.id(), "RETURNED", "홍길동", a).ok()).isTrue();
        assertThat(biz.handleLost(r.id(), "POLICE", null, a).ok()).isFalse();
    }

    @Test
    void 정보공개_결정_기한은_10일_끝날이_휴일이면_다음_근무일_연장은_한_번() throws Exception {
        LoginUser admin = TestUsers.admin();
        LoginUser a = staff("가정보", dept());
        // 접수일 + 10일이 토요일이 되게
        LocalDate received = LocalDate.now();
        while (received.plusDays(10).getDayOfWeek() != DayOfWeek.SATURDAY || received.plusDays(10).isBefore(LocalDate.now())) {
            received = received.minusDays(1);
        }
        BizService.Result r = biz.receiveInfo("홍청구", "010-0000-0000", "2026년 ○○사업 계약서 사본", received, null, a.getLoginId(), admin);
        assertThat(r.ok()).as(r.message()).isTrue();
        Map<String, Object> i = biz.infoReq(r.id());
        LocalDate due = (LocalDate) i.get("due");
        assertThat(due.getDayOfWeek()).isNotEqualTo(DayOfWeek.SATURDAY).isNotEqualTo(DayOfWeek.SUNDAY);
        assertThat(due).isAfter(received.plusDays(9));
        assertThat(holidays.isWorkingDay(due)).isTrue();
        assertThat(notified(a, "정보공개청구")).isTrue();
        assertThat(biz.extendInfo(r.id(), "짧음").ok()).isFalse();
        assertThat(biz.extendInfo(r.id(), "제3자 의견 청취가 필요함").ok()).isTrue();
        LocalDate extended = (LocalDate) biz.infoReq(r.id()).get("due");
        assertThat(extended).isAfter(due.plusDays(9));
        assertThat(biz.extendInfo(r.id(), "한 번 더 연장 사유").message()).contains("한 번만");
        assertThat(biz.decideInfo(r.id(), "CLOSED", null).ok()).isFalse(); // 비공개는 이유 필요
        assertThat(biz.decideInfo(r.id(), "PARTIAL", "개인정보 부분 제외 (법 제9조제1항제6호)").ok()).isTrue();
        assertThat(biz.infoReq(r.id()).get("status")).isEqualTo("DONE");
        // 담당자·부서만 본다
        LoginUser x = staff("나정보", "다른과");
        assertThat(TestUsers.mvcAs(ctx, x).perform(get("/biz/info/" + r.id())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(page(a, "/biz/info/" + r.id())).contains("부분 공개");
    }

    @Test
    void 비품은_지급할_때_재고에서_빼고_모자라면_지급하지_않는다() {
        LoginUser admin = TestUsers.admin();
        LoginUser a = staff("가비품", dept());
        assertThat(biz.addSupplyItem("볼펜", "자루", 10, a).ok()).isFalse();
        BizService.Result item = biz.addSupplyItem("볼펜-" + a.getLoginId(), "자루", 3, admin);
        assertThat(item.ok()).isTrue();
        BizService.Result r1 = biz.requestSupply(item.id(), 2, "민원실", a);
        BizService.Result r2 = biz.requestSupply(item.id(), 2, null, a);
        assertThat(biz.handleSupply(r1.id(), true, null, a).ok()).isFalse(); // 관리자만
        assertThat(biz.handleSupply(r1.id(), true, null, admin).ok()).isTrue();
        assertThat(biz.handleSupply(r2.id(), true, null, admin).message()).contains("모자랍니다");
        assertThat(biz.addStock(item.id(), 5, admin).ok()).isTrue();
        assertThat(biz.handleSupply(r2.id(), true, null, admin).ok()).isTrue();
        assertThat(biz.supplyItems()).filteredOn(i -> ((Number) i.get("id")).longValue() == item.id())
                .extracting(i -> ((Number) i.get("stock")).intValue()).containsExactly(4);
        BizService.Result r3 = biz.requestSupply(item.id(), 1, null, a);
        assertThat(biz.handleSupply(r3.id(), false, null, admin).ok()).isFalse(); // 반려 이유
        assertThat(biz.handleSupply(r3.id(), false, "다음 달에 신청", admin).ok()).isTrue();
        assertThat(notified(a, "반려했습니다")).isTrue();
    }

    @Test
    void 매뉴얼은_고칠_때_이전_판을_남기고_되돌릴_수_있다() throws Exception {
        LoginUser a = staff("가매뉴얼", dept());
        LoginUser b = staff("나매뉴얼", dept());
        BizService.Result r = biz.saveManual(null, null, "여권 접수 절차", "1. 신분 확인", a);
        assertThat(r.ok()).isTrue();
        assertThat(biz.saveManual(r.id(), 1, "여권 접수 절차", "1. 신분 확인\n2. 사진 확인", a).ok()).isTrue();
        assertThat(biz.saveManual(r.id(), 1, "충돌", "옛 판에서 고침", b).message()).contains("다른 사람이 고쳤습니다");
        assertThat(biz.manualRevs(r.id())).hasSize(1);
        assertThat(biz.restoreManual(r.id(), 1, b).ok()).isTrue();
        Map<String, Object> m = biz.manual(r.id());
        assertThat(m.get("body")).isEqualTo("1. 신분 확인");
        assertThat(((Number) m.get("version")).intValue()).isEqualTo(3);
        assertThat(biz.manualRevs(r.id())).hasSize(2);
        assertThat(biz.manuals("사진 확인")).isEmpty();
        assertThat(page(a, "/biz/manuals/" + r.id())).contains("이전 판").contains("2판");
    }

    @Test
    void 경조사와_칭찬() {
        LoginUser a = staff("가경조", dept());
        LoginUser b = staff("나경조", dept());
        assertThat(biz.postFamilyEvent("NOPE", "제목", null, LocalDate.now(), false, a).ok()).isFalse();
        BizService.Result e = biz.postFamilyEvent("WEDDING", "가경조 주무관 결혼-" + a.getLoginId(), "○○웨딩홀", LocalDate.now().plusDays(10), true, a);
        assertThat(e.ok()).isTrue();
        assertThat(notified(b, "[결혼]")).isTrue();
        assertThat(biz.deleteFamilyEvent(e.id(), b).ok()).isFalse();
        assertThat(biz.deleteFamilyEvent(e.id(), a).ok()).isTrue();

        assertThat(biz.praise(a.getLoginId(), "스스로 칭찬합니다", a).ok()).isFalse();
        assertThat(biz.praise(b.getLoginId(), "짧음", a).ok()).isFalse();
        assertThat(biz.praise(b.getLoginId(), "민원 창구를 끝까지 맡아 주셨습니다.", a).ok()).isTrue();
        assertThat(biz.praise(b.getLoginId(), "또 칭찬합니다 고마워요", a).message()).contains("하루에 한 번");
        assertThat(notified(b, "칭찬했습니다")).isTrue();
        assertThat(biz.praiseTop()).anyMatch(t -> "나경조".equals(t.get("name")));
    }

    @Test
    void 화면이_열린다() throws Exception {
        LoginUser a = staff("가화면", dept());
        LoginUser admin = TestUsers.admin();
        for (String p : List.of("/biz", "/biz/worklog", "/biz/events", "/biz/coops", "/biz/minutes", "/biz/calls", "/biz/visitors", "/biz/lost",
                "/biz/info", "/biz/supplies", "/biz/manuals", "/biz/family", "/biz/praise", "/biz/calls?ym=bad")) {
            MockMvc m = TestUsers.mvcAs(ctx, a);
            assertThat(m.perform(get(p)).andReturn().getResponse().getStatus()).as(p).isEqualTo(200);
        }
        assertThat(TestUsers.mvcAs(ctx, admin).perform(get("/biz/supplies")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("비품·재고 (관리자)");
        assertThat(page(a, "/")).contains("업무 지원");
    }
}
