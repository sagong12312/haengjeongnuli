package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.AssetVO;
import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.finance.service.PurchaseService;
import egovframework.haengjeongnuli.finance.service.PurchaseVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 구매: 견적 비교 → 업체 선정 → 검수 → 지출결의 → 물품대장 */
@SpringBootTest
class PurchaseTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired FinanceService finance;
    @Autowired PurchaseService purchases;
    @Autowired ApprovalService approvals;

    private record Staff(LoginUser u, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "p" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Purchase-pass-7!");
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

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 견적을_비교해_고르고_검수한_뒤에만_지급하고_물품대장에_올린다() throws Exception {
        int year = LocalDate.now().getYear();
        String dept = "구매" + UUID.randomUUID().toString().substring(0, 4);
        Staff planner = staff("편성", finance.getBudgetDept()), buyer = staff("구매담당", dept), checker = staff("검수담당", dept),
                chief = staff("과장", dept), other = staff("남", "다른" + dept);
        long policy = finance.addProgram(year, "POLICY", null, "정책사업", "016", "청사 관리", null, planner.u()).id();
        long unit = finance.addProgram(year, "UNIT", policy, null, null, "청사 유지", null, planner.u()).id();
        long detail = finance.addProgram(year, "DETAIL", unit, null, null, "비품 구입", dept, planner.u()).id();
        long budget = finance.create(BudgetVO.EXP, year, detail, "405-01", 10_000_000L, "비품", true, buyer.u()).id();
        finance.review(budget, true, null, planner.u());

        // 전체 점검: 다른 부서 직원은 우리 부서의 예산 요청(심사 대기)을 볼 수 없다
        long pending = finance.create(BudgetVO.EXP, year, detail, "201-01", 1_000_000L, "심사대기예산" + dept, true, buyer.u()).id();
        String link = "/finance/budget/" + pending + "\"";
        assertThat(body(other.mvc(), "/finance/review")).doesNotContain(link);
        assertThat(body(buyer.mvc(), "/finance/review")).contains(link);
        assertThat(body(planner.mvc(), "/finance/review")).contains(link);
        other.mvc().perform(get("/finance/budget/" + pending)).andExpect(status().is4xxClientError());
        finance.review(pending, false, "반려", planner.u());

        // 구매 건
        MvcResult made = buyer.mvc().perform(post("/finance/purchases").param("title", "민원실 의자 구매").param("purType", "물품")
                .param("estimatedAmount", "3,000,000").param("budgetId", "" + budget)).andReturn();
        String url = made.getResponse().getRedirectedUrl();
        assertThat(url).matches("/finance/purchases/\\d+");
        long id = Long.parseLong(url.replaceAll("\\D", ""));
        other.mvc().perform(get(url)).andExpect(status().isNotFound());

        // 견적: 한 곳뿐이면 사유 없이 고를 수 없다
        buyer.mvc().perform(multipart(url + "/quotes").file(new MockMultipartFile("file", "견적서.txt", "text/plain", "견적 3,100,000".getBytes()))
                        .param("vendor", "가나가구").param("bizNo", "1234567890").param("amount", "3,100,000").param("quoteDate", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("견적을 넣었습니다")));
        long q1 = purchases.quotes(id).get(0).getQuoteId();
        buyer.mvc().perform(post(url + "/select").param("quoteId", "" + q1))
                .andExpect(flash().attribute("message", containsString("1인 견적")));
        // 같은 사업자는 두 번 못 넣는다
        buyer.mvc().perform(post(url + "/quotes").param("vendor", "가나가구2").param("bizNo", "123-45-67890").param("amount", "1")
                        .param("quoteDate", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("같은 사업자")));
        buyer.mvc().perform(post(url + "/quotes").param("vendor", "다라가구").param("amount", "2,800,000").param("quoteDate", LocalDate.now().toString()));
        // 최저가가 아닌 업체는 사유가 필요
        buyer.mvc().perform(post(url + "/select").param("quoteId", "" + q1))
                .andExpect(flash().attribute("message", containsString("가장 싼 견적이 아닙니다")));
        long q2 = purchases.quotes(id).get(0).getQuoteId(); // 싼 순서
        assertThat(purchases.quote(q2).getVendor()).isEqualTo("다라가구");
        // 고르기 전에는 지출결의도, 검수도 안 된다
        checker.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "PASS"))
                .andExpect(flash().attribute("message", containsString("업체를 고르고 납품을 기다리는")));
        buyer.mvc().perform(post(url + "/select").param("quoteId", "" + q2))
                .andExpect(flash().attribute("message", containsString("업체를 골랐습니다: 다라가구")));
        assertThat(purchases.get(id).getStatus()).isEqualTo(PurchaseVO.SELECTED);

        // 검수 전 지출결의는 막는다
        buyer.mvc().perform(draft("지출결의", "의자 대금", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("budgetId", "" + budget).param("payAmount", "2,800,000").param("payee", "다라가구").param("purchaseId", "" + id))
                .andExpect(content().string(containsString("아직 검수 합격 전입니다")));
        // 다른 부서 직원은 구매 건을 연결할 수 없다
        other.mvc().perform(draft("지출결의", "남의 구매", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("budgetId", "" + budget).param("payAmount", "1").param("payee", "x").param("purchaseId", "" + id))
                .andExpect(content().string(containsString("우리 부서의 취소되지 않은 구매 건")));

        // 검수: 구매 담당은 못 하고, 14일을 넘기면 사유가 필요하다
        buyer.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "PASS"))
                .andExpect(flash().attribute("message", containsString("구매를 맡은 직원은 검수할 수 없습니다")));
        checker.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().minusDays(20).toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "PASS"))
                .andExpect(flash().attribute("message", containsString("14일이 지났습니다(20일)")));
        checker.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().minusDays(3).toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "FAIL"))
                .andExpect(flash().attribute("message", containsString("불합격이면")));
        checker.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().minusDays(3).toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "FAIL").param("note", "다리 흠집 2개"))
                .andExpect(flash().attribute("message", containsString("불합격으로 기록")));
        checker.mvc().perform(post(url + "/inspect").param("notifiedDate", LocalDate.now().minusDays(1).toString())
                        .param("inspectedDate", LocalDate.now().toString()).param("result", "PASS"))
                .andExpect(flash().attribute("message", containsString("검수 합격")));
        assertThat(purchases.get(id).getStatus()).isEqualTo(PurchaseVO.INSPECTED);

        // 지출결의 화면은 구매 건 내용을 가져온다
        String form = body(buyer.mvc(), "/approval/draft?kind=지출결의&purchase=" + id);
        assertThat(form).contains("[지출] 민원실 의자 구매").contains("다라가구").contains("2,800,000");

        // 선정 금액을 넘으면 못 올린다
        buyer.mvc().perform(draft("지출결의", "의자 대금", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("budgetId", "" + budget).param("payAmount", "2,900,000").param("payee", "다라가구").param("purchaseId", "" + id))
                .andExpect(content().string(containsString("더 지급할 수 있는 금액은 2,800,000원")));
        long spend = docId(buyer.mvc().perform(draft("지출결의", "의자 대금", chief.u().getLoginId()).param("spendType", "물품구매")
                .param("budgetId", "" + budget).param("payAmount", "2,800,000").param("payee", "다라가구").param("purchaseId", "" + id)).andReturn());
        chief.mvc().perform(post("/approval/" + spend + "/approve"));
        assertThat(approvals.get(spend).getStatus()).isEqualTo(ApprovalDocVO.DONE);
        assertThat(approvals.get(spend).getPurchaseId()).isEqualTo(id);
        buyer.mvc().perform(draft("지출결의", "또 지급", chief.u().getLoginId()).param("spendType", "물품구매")
                        .param("budgetId", "" + budget).param("payAmount", "1").param("payee", "다라가구").param("purchaseId", "" + id))
                .andExpect(content().string(containsString("더 지급할 수 있는 금액은 0원")));

        // 물품대장: 구매 금액을 넘겨 올릴 수 없다
        buyer.mvc().perform(post("/finance/assets").param("name", "민원실 의자").param("quantity", "10").param("unitPrice", "290,000")
                        .param("acquiredDate", LocalDate.now().toString()).param("purchaseId", "" + id))
                .andExpect(flash().attribute("message", containsString("구매 금액(2,800,000원)을 넘습니다")));
        buyer.mvc().perform(post("/finance/assets").param("name", "민원실 의자").param("quantity", "10").param("unitPrice", "280,000")
                        .param("acquiredDate", LocalDate.now().toString()).param("purchaseId", "" + id).param("location", "민원실"))
                .andExpect(flash().attribute("message", containsString("물품대장에 올렸습니다")));
        AssetVO a = purchases.assets(id).get(0);
        assertThat(a.getTotal()).isEqualTo(2_800_000L);
        other.mvc().perform(post("/finance/assets/" + a.getAssetId() + "/check")).andExpect(status().isNotFound());
        buyer.mvc().perform(post("/finance/assets/" + a.getAssetId() + "/check")).andExpect(flash().attribute("message", containsString("실물을 확인")));
        buyer.mvc().perform(post("/finance/assets/" + a.getAssetId() + "/dispose").param("reason", "폐기"))
                .andExpect(flash().attribute("message", containsString("불용으로 정한")));
        buyer.mvc().perform(post("/finance/assets/" + a.getAssetId() + "/status").param("status", "IDLE"));
        buyer.mvc().perform(post("/finance/assets/" + a.getAssetId() + "/dispose").param("reason", "파손으로 폐기"))
                .andExpect(flash().attribute("message", containsString("처분을 기록")));
        assertThat(purchases.asset(a.getAssetId()).getStatus()).isEqualTo(AssetVO.DISPOSED);

        // 흐름 화면
        String view = body(buyer.mvc(), url);
        assertThat(view).contains("검수 합격").contains("의자 대금").contains("다리 흠집 2개").contains("견적서.txt").contains(a.getAssetNo());
        assertThat(body(buyer.mvc(), "/finance/purchases.csv")).contains("민원실 의자 구매");
        assertThat(body(buyer.mvc(), "/finance/assets.csv?status=DISPOSED")).contains("파손으로 폐기");
        // 결재가 끝난 구매 건은 취소할 수 없다
        buyer.mvc().perform(post(url + "/cancel").param("reason", "x"))
                .andExpect(flash().attribute("message", containsString("검수 전의")));
    }

    @Test
    void 계약을_연결하면_지급_한도는_계약_금액이다() throws Exception {
        String dept = "계구" + UUID.randomUUID().toString().substring(0, 4);
        Staff buyer = staff("담당", dept), checker = staff("검수", dept);
        long id = purchases.create("청사 청소 용역", "용역", 12_000_000L, null, null, buyer.u()).id();
        purchases.addQuote(id, "청소나라", null, 11_000_000L, LocalDate.now(), null, null, buyer.u());
        purchases.addQuote(id, "말끔", null, 11_500_000L, LocalDate.now(), null, null, buyer.u());
        assertThat(purchases.select(id, purchases.quotes(id).get(0).getQuoteId(), null, buyer.u()).ok()).isTrue();
        String today = LocalDate.now().toString();
        buyer.mvc().perform(post("/finance/contracts").param("title", "청사 청소 용역").param("contractKind", "물품").param("method", "수의계약")
                        .param("privateReason", "시행령 제25조").param("party", "청소나라").param("amount", "10,900,000").param("contractDate", today)
                        .param("purchaseId", "" + id))
                .andExpect(flash().attribute("message", containsString("구매 건의 종류(용역)")));
        buyer.mvc().perform(post("/finance/contracts").param("title", "청사 청소 용역").param("contractKind", "용역").param("method", "수의계약")
                        .param("privateReason", "시행령 제25조").param("party", "청소나라").param("amount", "10,900,000").param("contractDate", today)
                        .param("purchaseId", "" + id))
                .andExpect(flash().attribute("message", containsString("계약을 등록했습니다")));
        PurchaseVO p = purchases.get(id);
        assertThat(purchases.payLimit(p)).isEqualTo(10_900_000L);
        // 계약이 있으면 업체 선정을 되돌릴 수 없다
        assertThat(purchases.unselect(id, buyer.u()).message()).contains("되돌릴 수 없습니다");
        assertThat(purchases.inspect(id, LocalDate.now(), LocalDate.now(), "PASS", null, checker.u()).ok()).isTrue();
        assertThat(purchases.checkDoc(id, true, true, 11_000_000L, dept, null)).contains("10,900,000원");
        assertThat(purchases.checkDoc(id, true, true, 10_900_000L, dept, null)).isNull();
    }
}
