package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 전체 점검: 모든 목록·입력 화면이 권한마다 오류(500) 없이 열리는지 */
@SpringBootTest
class ScreenSmokeTest {

    @Autowired WebApplicationContext ctx;

    static final List<String> PAGES = List.of("/", "/approval", "/approval/draft", "/approval/proxy", "/approval/templates",
            "/approval?box=done", "/approval?box=temp", "/approval?box=receive",
            "/civil", "/civil/new", "/civil/certs", "/building", "/hygiene", "/welfare",
            "/finance", "/finance?area=rev", "/finance/budget/new", "/finance/programs", "/finance/review",
            "/finance/contracts", "/finance/purchases", "/finance/assets", "/finance/assets?status=DISPOSED&q=%25_",
            "/hr", "/hr/duty", "/hr/duty/new", "/board", "/board/write", "/board/upload", "/records", "/records/new",
            "/records/report", "/search?q=민원", "/stats", "/notifications", "/mypage/password", "/security-apps",
            "/admin/users", "/admin/holidays", "/admin/audit", "/admin/audit/review", "/admin/depts", "/admin/org",
            "/admin/files", "/admin/blocks", "/admin/security-apps", "/forms", "/forms?source=staff", "/admin/forms", "/hygiene/sanctions", "/hygiene/sanctions?law=공중위생관리법");

    static final List<String> CSV = List.of("/finance/budgets.csv", "/finance/contracts.csv", "/finance/purchases.csv",
            "/finance/assets.csv", "/civil/certs.csv", "/records/report.csv", "/stats/civil.csv", "/hygiene/sanctions.csv");

    @Test
    void 모든_화면이_권한별로_오류_없이_열린다() throws Exception {
        List<String> problems = new ArrayList<>();
        LoginUser noDept = TestUsers.of(LoginUser.Role.GENERAL, "nodept-t", "부서없음");
        noDept.setDeptName(null);
        for (LoginUser u : List.of(TestUsers.admin(), TestUsers.general(), TestUsers.counter(), noDept)) {
            MockMvc m = TestUsers.mvcAs(ctx, u);
            for (String p : PAGES) {
                int s = m.perform(get(p)).andReturn().getResponse().getStatus();
                if (s >= 500) {
                    problems.add(u.getLoginId() + " " + p + " → " + s);
                }
                if (System.getenv("SMOKE_MATRIX") != null) {
                    System.out.println("MATRIX " + u.getLoginId() + " " + s + " " + p);
                }
            }
            for (String p : CSV) {
                int s = m.perform(get(p)).andReturn().getResponse().getStatus();
                if (s >= 500) {
                    problems.add(u.getLoginId() + " " + p + " → " + s);
                }
            }
        }
        assertThat(problems).isEmpty();
    }
}
