package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.reg.RegCatalog;
import egovframework.haengjeongnuli.reg.RegDef;
import egovframework.haengjeongnuli.reg.RegService;
import egovframework.haengjeongnuli.reg.ToolsService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 대장·업무 계산 도구 */
@SpringBootTest
class RegTest {

    @Autowired WebApplicationContext ctx;
    @Autowired RegService reg;
    @Autowired ToolsService tools;
    @Autowired UserService users;

    LoginUser staff(String name, String dept) {
        String id = "r" + UUID.randomUUID().toString().substring(0, 8);
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
        return TestUsers.mvcAs(ctx, u).perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static Map<String, String> f(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put("f_" + kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void 대장은_50종_이상이고_정의가_올바르다() {
        List<RegDef> all = RegCatalog.all();
        assertThat(all).hasSizeGreaterThanOrEqualTo(50);
        for (RegDef d : all) {
            assertThat(d.getCode()).matches("[a-zA-Z]+");
            assertThat(d.getFields()).isNotEmpty();
            assertThat(d.getStatuses()).isNotEmpty();
            assertThat(d.titleField()).isNotNull();
            d.getFields().stream().filter(x -> x.getType().equals("S")).forEach(x -> assertThat(x.getOptions()).as(d.getName() + " " + x.getLabel()).isNotEmpty());
        }
    }

    @Test
    void 등록_검증_기한_상태_이력_복사_삭제() {
        String dept = "감사과-" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser a = staff("가대장", dept);
        LoginUser b = staff("나대장", dept);
        LoginUser x = staff("다대장", "다른과");
        RegDef audit = RegCatalog.get("audit");

        // 필수·형식 확인
        assertThat(reg.save(audit, null, f("finding", "지적"), null, a).ok()).isFalse();
        assertThat(reg.save(audit, null, f("auditName", "정기감사", "finding", "지적", "kind", "없는값"), null, a).ok()).isFalse();
        assertThat(reg.save(audit, null, f("auditName", "정기감사", "finding", "지적", "amount", "12a"), null, a).ok()).isFalse();
        assertThat(reg.save(audit, null, f("auditName", "정기감사", "finding", "지적", "dueOn", "2026-13-01"), null, a).ok()).isFalse();

        LocalDate due = LocalDate.now().minusDays(1);
        RegService.Result r = reg.save(audit, null, f("auditName", "정기감사", "finding", "물품 관리 소홀", "kind", "시정",
                "dueOn", due.toString(), "amount", "1,200,000"), null, a);
        assertThat(r.ok()).isTrue();
        long id = r.id();

        // 같은 부서는 보고 고칠 수 있고, 다른 부서는 못 본다 (부서 안에서만)
        Map<String, Object> e = reg.get(id, new RegService.Viewer(b));
        assertThat(e).isNotNull();
        assertThat(e.get("late")).isEqualTo(true);
        assertThat(e.get("status")).isEqualTo("미조치");
        assertThat(((Map<?, ?>) e.get("values")).get("amount")).isEqualTo("1200000");
        assertThat(reg.get(id, new RegService.Viewer(x))).isNull();
        assertThat(reg.setStatus(id, "조치 완료", "회수 완료", x).ok()).isFalse();

        // 기한 알림에 뜨고, 끝내면 빠진다
        assertThat(reg.dueSoon(new RegService.Viewer(a), 7)).anyMatch(m -> m.get("id").equals(id));
        assertThat(reg.setStatus(id, "없는 상태", null, b).ok()).isFalse();
        assertThat(reg.setStatus(id, "조치 완료", "회수 완료", b).ok()).isTrue();
        assertThat(reg.dueSoon(new RegService.Viewer(a), 7)).noneMatch(m -> m.get("id").equals(id));

        // 통계·검색
        Map<String, Object> st = reg.stats(audit, new RegService.Viewer(a), null);
        assertThat(((Map<?, ?>) st.get("byStatus")).get("조치 완료")).isEqualTo(1L);
        assertThat(st.get("amount")).isEqualTo(1_200_000L);
        assertThat(reg.searchAll(new RegService.Viewer(a), "물품 관리")).anyMatch(m -> m.get("id").equals(id));
        assertThat(reg.searchAll(new RegService.Viewer(x), "물품 관리")).noneMatch(m -> m.get("id").equals(id));
        assertThat(reg.searchAll(new RegService.Viewer(a), "%")).noneMatch(m -> m.get("id").equals(id));

        // 고치면 바뀐 칸이 이력에 남는다
        assertThat(reg.save(audit, id, f("auditName", "정기감사", "finding", "물품 관리 소홀", "kind", "주의", "dueOn", due.toString()), null, b).ok()).isTrue();
        List<?> hist = (List<?>) reg.get(id, new RegService.Viewer(a)).get("history");
        assertThat(hist).hasSize(3);
        assertThat(String.valueOf(((Map<?, ?>) hist.get(0)).get("note"))).contains("처분 종류").contains("회수·추징액");

        // 복사 → 새 항목, 처음 상태
        RegService.Result c = reg.copy(id, a);
        assertThat(c.ok()).isTrue();
        assertThat(reg.get(c.id(), new RegService.Viewer(a)).get("status")).isEqualTo("미조치");

        // 지우기는 사유가 있어야 하고, 지우면 안 보인다
        assertThat(reg.delete(c.id(), "", a).ok()).isFalse();
        assertThat(reg.delete(c.id(), "중복 등록", a).ok()).isTrue();
        assertThat(reg.get(c.id(), new RegService.Viewer(a))).isNull();
    }

    @Test
    void 본인만_보는_신고_대장과_개인정보_가림() {
        LoginUser a = staff("가신고", "총무과");
        LoginUser b = staff("나신고", "총무과");
        RegDef lecture = RegCatalog.get("lecture");
        LocalDate end = LocalDate.of(2026, 3, 2);
        RegService.Result r = reg.save(lecture, null, f("title", "민원 응대 교육", "host", "○○대학교", "endOn", end.toString(), "fee", "300000"), null, a);
        assertThat(r.ok()).isTrue();
        // 마친 날부터 10일 뒤가 기한
        assertThat(reg.get(r.id(), new RegService.Viewer(a)).get("due")).isEqualTo(end.plusDays(10));
        // 같은 부서라도 남의 신고는 못 본다
        assertThat(reg.get(r.id(), new RegService.Viewer(b))).isNull();
        assertThat(reg.get(r.id(), new RegService.Viewer(TestUsers.admin()))).isNotNull();

        RegDef pass = RegCatalog.get("pass");
        RegService.Result p = reg.save(pass, null, f("holder", "홍길동", "org", "시설관리"), null, a);
        Map<String, Object> page = reg.page(pass, new RegService.Viewer(b), null, null, false, false, null, 1);
        Map<?, ?> row = ((List<Map<?, ?>>) page.get("items")).stream().filter(m -> m.get("id").equals(p.id())).findFirst().orElseThrow();
        assertThat(((Map<?, ?>) row.get("shown")).get("holder")).isEqualTo("홍*동");
        assertThat(((Map<?, ?>) reg.get(p.id(), new RegService.Viewer(b)).get("shown")).get("holder")).isEqualTo("홍길동");
    }

    @Test
    void 화면과_엑셀() throws Exception {
        LoginUser a = staff("가화면", "민원과");
        var mvc = TestUsers.mvcAs(ctx, a);
        assertThat(page(a, "/reg")).contains("청렴·감사").contains("선물·금품 신고");
        assertThat(page(a, "/reg/streetlight")).contains("가로등·보안등 고장");
        assertThat(page(a, "/reg/streetlight/new")).contains("f_place");
        mvc.perform(get("/reg/nothere")).andExpect(status().isNotFound());

        String loc = mvc.perform(post("/reg/streetlight").param("f_on", LocalDate.now().toString()).param("f_place", "중앙로 12 앞")
                .param("f_reporter", "01012345678")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(loc).matches("/reg/streetlight/\\d+");
        assertThat(page(a, loc)).contains("중앙로 12 앞").contains("010-1234-5678").contains("변경 이력");
        assertThat(page(a, loc + "/print")).contains("중앙로 12 앞");
        assertThat(mvc.perform(get("/reg/streetlight").param("q", "중앙로")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("중앙로 12 앞");

        // 엑셀은 사유가 있어야 받는다
        mvc.perform(get("/reg/streetlight/export.csv")).andExpect(status().isBadRequest());
        String csv = mvc.perform(get("/reg/streetlight/export.csv").param("reason", "월간 보고")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("위치").contains("중앙로 12 앞");
    }

    @Test
    void 계산_도구() throws Exception {
        // 기간: 첫날 빼고, 말일이 토·일·공휴일이면 다음 날
        LocalDate fri = LocalDate.of(2026, 11, 20);
        assertThat(fri.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(tools.term(fri, 7, "D", false).getEndShifted()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(tools.term(fri, 8, "D", false).getEndShifted()).isEqualTo(LocalDate.of(2026, 11, 30)); // 토 → 월
        assertThat(tools.term(LocalDate.of(2026, 1, 31), 1, "M", false).getEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(tools.term(fri, 7, "D", true).getEnd()).isEqualTo(LocalDate.of(2026, 11, 26));
        // 근무일
        assertThat(tools.addWorkingDays(fri, 1)).isEqualTo(LocalDate.of(2026, 11, 23));
        // 만 나이
        assertThat(tools.age(LocalDate.of(2000, 11, 21), fri).get("years")).isEqualTo(25);
        assertThat(tools.age(LocalDate.of(2000, 11, 20), fri).get("years")).isEqualTo(26);
        // 금액 한글
        assertThat(ToolsService.koreanAmount(1_234_500)).isEqualTo("일백이십삼만사천오백");
        assertThat(ToolsService.koreanAmount(100_000_000)).isEqualTo("일억");
        assertThat(ToolsService.koreanAmount(10_010)).isEqualTo("일만일십");
        // 부가세
        assertThat(tools.vat(11_000, true)).containsEntry("supply", 10_000L).containsEntry("tax", 1_000L);
        assertThat(tools.vat(10_000, false)).containsEntry("total", 11_000L);
        // 공문 날짜
        assertThat(ToolsService.officialDate(LocalDate.of(2026, 3, 5))).isEqualTo("2026. 3. 5.");
        assertThat(ToolsService.officialTime(java.time.LocalTime.of(9, 5))).isEqualTo("09:05");
        // 개인정보 가리기: 날짜·접수번호는 그대로
        ToolsService.Masked m = tools.mask("홍길동 900101-1234567, 010-1234-5678, hong@example.com, 국민 123456-78-901234, 2026-11-20 접수 2026-000123");
        assertThat(m.getText()).contains("900101-1******").contains("010-****-5678").contains("ho***@example.com")
                .contains("123456-****-****").contains("2026-11-20").contains("2026-000123").doesNotContain("1234567");
        assertThat(m.getFound()).containsKeys("주민등록·외국인등록번호", "휴대전화", "이메일", "계좌번호");
        // 면적
        assertThat(tools.area(1, true).get("sqm")).isEqualTo(3.31);
        // 글자 수
        assertThat(tools.textCount("가나 다").get("charsNoSpace")).isEqualTo(3);
        assertThat(tools.textCount("가나 다").get("utf8")).isEqualTo(10);

        LoginUser a = staff("가도구", "민원과");
        assertThat(page(a, "/tools?tool=term&start=2026-11-20&n=8&unit=D")).contains("2026-11-30");
        assertThat(page(a, "/tools?tool=won&amount=1234500")).contains("일금 일백이십삼만사천오백원정");
        assertThat(page(a, "/tools")).contains("행정기본법 제36조");
        String masked = TestUsers.mvcAs(ctx, a).perform(post("/tools/text").param("text", "010-9999-8888").param("action", "mask"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(masked).contains("010-****-8888");
    }
}
