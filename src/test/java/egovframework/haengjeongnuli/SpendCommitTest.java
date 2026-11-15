package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.CommitVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 지출품의(지출원인행위) → 지출결의, 품의·지출 종류, 남은 금액 해제 */
@SpringBootTest
class SpendCommitTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired FinanceService finance;
    @Autowired ApprovalService approvals;

    private record Staff(LoginUser u, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "c" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Commit-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    private static long docId(MvcResult r) {
        String url = r.getResponse().getRedirectedUrl();
        assertThat(url).as(String.valueOf(r.getFlashMap())).matches("/approval/\\d+");
        return Long.parseLong(url.replaceAll("\\D", ""));
    }

    private static MockHttpServletRequestBuilder draft(String kind, String title, String approver) {
        return post("/approval/draft").param("action", "send").param("kind", kind).param("title", title)
                .param("body", "본문").param("finalApprover", approver);
    }

    @Test
    void 품의로_예산을_묶고_그_안에서_지출하고_남은_금액을_푼다() throws Exception {
        int year = LocalDate.now().getYear();
        String dept = "품의" + UUID.randomUUID().toString().substring(0, 4);
        Staff planner = staff("편성", finance.getBudgetDept()), drafter = staff("담당", dept), chief = staff("과장", dept);
        long policy = finance.addProgram(year, "POLICY", null, "정책사업", "016", "청사 관리", null, planner.u()).id();
        long unit = finance.addProgram(year, "UNIT", policy, null, null, "청사 유지", null, planner.u()).id();
        long detail = finance.addProgram(year, "DETAIL", unit, null, null, "사무용품 구입", dept, planner.u()).id();
        long budget = finance.create(BudgetVO.EXP, year, detail, "201-01", 10_000_000L, "사무용품", true, drafter.u()).id();
        finance.review(budget, true, null, planner.u());

        // 지출품의 6,000,000원 → 결재가 끝나면 예산을 묶는다
        long commit = docId(drafter.mvc().perform(draft("지출품의", "사무용 의자 구매 품의", chief.u().getLoginId())
                .param("spendType", "물품구매").param("budgetId", "" + budget).param("payAmount", "6,000,000")
                .param("payee", "가나상사").param("spendTarget", "의자 30개")).andReturn());
        assertThat(finance.available(budget, null)).isEqualTo(4_000_000L); // 결재 중에도 미리 뺀다
        chief.mvc().perform(post("/approval/" + commit + "/approve"))
                .andExpect(flash().attribute("message", containsString("품의 금액 6,000,000원만큼 예산을 묶었습니다")));
        CommitVO c = finance.commitOf(commit);
        assertThat(c.getRemaining()).isEqualTo(6_000_000L);
        assertThat(finance.available(budget, null)).isEqualTo(4_000_000L);

        // 품의 없이 바로 지출은 묶이지 않은 4,000,000원까지
        drafter.mvc().perform(draft("지출결의", "바로 지출", chief.u().getLoginId()).param("spendType", "일반")
                        .param("budgetId", "" + budget).param("payAmount", "5,000,000").param("payee", "다라상사"))
                .andExpect(content().string(containsString("지출 가능 잔액(4,000,000원)을 넘습니다")));

        // 품의 화면의 '지출결의 작성'은 품의 내용을 가져온다
        String form = drafter.mvc().perform(get("/approval/draft").param("kind", "지출결의").param("commit", "" + commit))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(form).contains("[지출] 사무용 의자 구매 품의").contains("가나상사");

        // 품의에 연결한 지출결의: 품의 잔액 안에서만
        drafter.mvc().perform(draft("지출결의", "의자 대금", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("commitDocId", "" + commit).param("payAmount", "7,000,000").param("payee", "가나상사"))
                .andExpect(content().string(containsString("품의 잔액(6,000,000원)을 넘습니다")));
        long spend = docId(drafter.mvc().perform(draft("지출결의", "의자 대금 1차", chief.u().getLoginId()).param("spendType", "물품구매")
                .param("commitDocId", "" + commit).param("payAmount", "2,500,000").param("payee", "가나상사")).andReturn());
        assertThat(approvals.get(spend).getBudgetId()).isEqualTo(budget);
        chief.mvc().perform(post("/approval/" + spend + "/approve"));
        assertThat(approvals.get(spend).getStatus()).isEqualTo(ApprovalDocVO.DONE);
        assertThat(finance.commitOf(commit).getRemaining()).isEqualTo(3_500_000L);
        assertThat(finance.get(budget).getUsed()).isEqualTo(2_500_000L);
        assertThat(finance.available(budget, null)).isEqualTo(4_000_000L); // 품의 안에서 나간 돈은 잔액을 더 줄이지 않는다

        // 품의 화면: 지출 내역과 잔액
        String view = drafter.mvc().perform(get("/approval/" + commit)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(view).contains("지출한 금액 2,500,000원").contains("남은 금액 3,500,000원").contains("의자 대금 1차");

        // 남은 금액 해제 (낙찰 차액 등) → 예산으로 돌아간다
        drafter.mvc().perform(post("/approval/" + commit + "/commit/close"))
                .andExpect(flash().attribute("message", containsString("해제 사유")));
        drafter.mvc().perform(post("/approval/" + commit + "/commit/close").param("note", "납품 완료 후 차액"))
                .andExpect(flash().attribute("message", containsString("해제했습니다")));
        assertThat(finance.available(budget, null)).isEqualTo(7_500_000L);
        drafter.mvc().perform(draft("지출결의", "해제 뒤 지출", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("commitDocId", "" + commit).param("payAmount", "1,000").param("payee", "가나상사"))
                .andExpect(content().string(containsString("잔액을 해제한 품의입니다")));
    }

    @Test
    void 여비와_업무추진비는_집행_대상을_적어야_한다() throws Exception {
        int year = LocalDate.now().getYear();
        String dept = "여비" + UUID.randomUUID().toString().substring(0, 4);
        Staff planner = staff("편성", finance.getBudgetDept()), drafter = staff("담당", dept), chief = staff("과장", dept);
        long policy = finance.addProgram(year, "POLICY", null, "정책사업", "016", "행정 운영", null, planner.u()).id();
        long unit = finance.addProgram(year, "UNIT", policy, null, null, "기본 경비", null, planner.u()).id();
        long detail = finance.addProgram(year, "DETAIL", unit, null, null, "부서 운영", dept, planner.u()).id();
        long budget = finance.create(BudgetVO.EXP, year, detail, "202-01", 5_000_000L, "여비", true, drafter.u()).id();
        finance.review(budget, true, null, planner.u());

        drafter.mvc().perform(draft("지출결의", "출장 여비", chief.u().getLoginId()).param("spendType", "여비")
                        .param("budgetId", "" + budget).param("payAmount", "120,000").param("payee", "담당"))
                .andExpect(content().string(containsString("출장자·출장 기간·출장지")));
        drafter.mvc().perform(draft("지출결의", "간담회", chief.u().getLoginId()).param("spendType", "업무추진비")
                        .param("budgetId", "" + budget).param("payAmount", "600,000").param("payee", "식당"))
                .andExpect(content().string(containsString("집행 대상(상대방 소속·성명 등)")));
        // 50만원 미만은 적지 않아도 된다
        docId(drafter.mvc().perform(draft("지출결의", "소액 간담회", chief.u().getLoginId()).param("spendType", "업무추진비")
                .param("budgetId", "" + budget).param("payAmount", "300,000").param("payee", "식당")).andReturn());
        docId(drafter.mvc().perform(draft("지출결의", "출장 여비 2", chief.u().getLoginId()).param("spendType", "여비")
                .param("budgetId", "" + budget).param("payAmount", "120,000").param("payee", "담당")
                .param("spendTarget", "담당 주무관, 12. 3., 세종 행정안전부")).andReturn());
    }
}
