package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.KoreanAmount;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.FinCodeVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.finance.service.ProgramVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class FinanceTest {

    @Autowired WebApplicationContext ctx;
    @Autowired FinanceService finance;
    @Autowired UserService userService;

    private final int year = LocalDate.now().getYear();

    private record Staff(LoginUser u, MockMvc mvc) {}

    /** DB에 계정을 만들고 그 계정으로 로그인한 MockMvc */
    private Staff staff(String name, String dept, boolean revenue) {
        String id = "f" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        LoginUser created = userService.create(f);
        if (revenue) {
            userService.setRevenueStaff(created.getUserId(), true);
        }
        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    private static long idFrom(MvcResult r) {
        Matcher m = Pattern.compile("/finance/budget/(\\d+)").matcher(r.getResponse().getRedirectedUrl());
        assertThat(m.find()).as(r.getResponse().getRedirectedUrl()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 예산과목_코드가_운영기준대로_들어간다() {
        assertThat(finance.codes(FinCodeVO.FUNC).stream().filter(c -> "부문".equals(c.getCodeLevel()))).hasSize(54);
        assertThat(finance.codes(FinCodeVO.ITEM).stream().filter(c -> "편성목".equals(c.getCodeLevel()))).hasSize(38);
        assertThat(finance.codes(FinCodeVO.ITEM).stream().filter(c -> "통계목".equals(c.getCodeLevel()))).hasSize(130);
        assertThat(finance.codeNames(FinCodeVO.ITEM)).containsEntry("201-01", "사무관리비").containsEntry("202-01", "국내여비");
        assertThat(finance.codeNames(FinCodeVO.REV)).containsEntry("111-01", "취득세").containsEntry("611-02", "금융기관채");
        assertThat(finance.codeNames(FinCodeVO.FUNC)).containsEntry("08A", "보육").containsEntry("064", "국가유산");
    }

    @Test
    void 금액_한글_표기() {
        // 행정업무규정 시행규칙 제2조 제2항 예시
        assertThat(KoreanAmount.official(113560)).isEqualTo("금113,560원(금일십일만삼천오백육십원)");
        assertThat(KoreanAmount.hangul(1_0000_0000L)).isEqualTo("일억");
        assertThat(KoreanAmount.hangul(1_0000_5000L)).isEqualTo("일억오천");
        assertThat(KoreanAmount.hangul(12_0034_0000L)).isEqualTo("일십이억삼십사만");
    }

    @Test
    void 편성_승인_집행_흐름() throws Exception {
        String dept = "과" + UUID.randomUUID().toString().substring(0, 5);
        Staff planner = staff("편성담당", finance.getBudgetDept(), false);
        Staff planner2 = staff("편성팀장", finance.getBudgetDept(), false);
        Staff writer = staff("부서담당", dept, false);
        Staff other = staff("다른과", "다른과" + UUID.randomUUID().toString().substring(0, 4), false);

        // 사업 구조는 예산편성과만
        writer.mvc().perform(post("/finance/programs").param("year", "" + year).param("level", "POLICY")
                        .param("type", "정책사업").param("funcCode", "016").param("name", "몰래 만든 사업"))
                .andExpect(flash().attribute("message", containsString("직원과 시스템 관리자만")));
        assertThat(finance.addProgram(year, "POLICY", null, "행정운영경비", "016", "x", null, planner.u()).message())
                .contains("900 기타");
        long policy = finance.addProgram(year, "POLICY", null, "정책사업", "016", "민원서비스 향상", null, planner.u()).id();
        long unit = finance.addProgram(year, "UNIT", policy, null, null, "민원창구 운영", null, planner.u()).id();
        long detail = finance.addProgram(year, "DETAIL", unit, null, null, "민원실 운영", dept, planner.u()).id();

        // 다른 부서는 이 세부사업 예산을 쓸 수 없다
        assertThat(finance.create(BudgetVO.EXP, year, detail, "201-01", 1_000_000L, null, false, other.u()).message())
                .contains("우리 부서 소관");
        // 편성목(201)이 아니라 통계목을 골라야 한다
        assertThat(finance.create(BudgetVO.EXP, year, detail, "201", 1_000_000L, null, false, writer.u()).message())
                .contains("통계목");

        long id = idFrom(writer.mvc().perform(post("/finance/budget").param("kind", "EXP").param("year", "" + year)
                .param("detailId", "" + detail).param("code", "201-01").param("amount", "1,000,000")
                .param("basis", "사무용품").param("action", "request")).andReturn());
        assertThat(finance.get(id).getStatus()).isEqualTo(BudgetVO.REQUESTED);
        assertThat(body(planner.mvc(), "/notifications")).contains("예산 승인을 요청했습니다");
        // 같은 세부사업·통계목은 한 줄만
        assertThat(finance.create(BudgetVO.EXP, year, detail, "201-01", 5L, null, false, writer.u()).message())
                .contains("이미 있습니다");

        // 다른 부서 직원은 볼 수 없고, 담당 부서도 승인할 수 없다
        other.mvc().perform(get("/finance/budget/" + id)).andExpect(status().isNotFound());
        assertThat(finance.review(id, true, null, writer.u()).ok()).isFalse();
        // 반려는 사유 필수
        assertThat(finance.review(id, false, " ", planner.u()).message()).contains("반려 사유");
        planner.mvc().perform(post("/finance/budget/" + id + "/reject").param("note", "산출근거 보완"))
                .andExpect(flash().attribute("message", containsString("반려했습니다")));
        assertThat(body(writer.mvc(), "/notifications")).contains("반려했습니다");

        // 고쳐서 다시 요청 → 승인
        writer.mvc().perform(post("/finance/budget/" + id + "/edit").param("detailId", "" + detail).param("code", "201-01")
                .param("amount", "1000000").param("basis", "사무용품 50,000원 × 20개").param("action", "request"));
        planner.mvc().perform(post("/finance/budget/" + id + "/approve"));
        assertThat(finance.get(id).getStatus()).isEqualTo(BudgetVO.APPROVED);
        assertThat(body(writer.mvc(), "/finance/budget/" + id)).contains("금1,000,000원(금일백만원)");

        // 집행: 잔액을 넘을 수 없다
        writer.mvc().perform(post("/finance/budget/" + id + "/execute").param("date", LocalDate.now().toString())
                .param("amount", "600,000").param("description", "복사용지"))
                .andExpect(flash().attribute("message", "600,000원을 집행했습니다."));
        writer.mvc().perform(post("/finance/budget/" + id + "/execute").param("date", LocalDate.now().toString())
                        .param("amount", "500000").param("description", "토너"))
                .andExpect(flash().attribute("message", "집행 잔액(400,000원)을 넘을 수 없습니다."));
        long txn = finance.txnsOf(id).get(0).getTxnId();
        other.mvc().perform(post("/finance/txn/" + txn + "/cancel"))
                .andExpect(flash().attribute("message", containsString("만 취소할 수 있습니다")));
        writer.mvc().perform(post("/finance/txn/" + txn + "/cancel"));
        assertThat(finance.get(id).getUsed()).isZero();

        // 예산편성과 직원이 자기 부서 예산을 요청하면 다른 직원이 승인해야 한다
        long own = finance.addProgram(year, "DETAIL", unit, null, null, "예산 업무", finance.getBudgetDept(), planner.u()).id();
        long ownBudget = finance.create(BudgetVO.EXP, year, own, "202-01", 300_000L, null, true, planner.u()).id();
        assertThat(finance.review(ownBudget, true, null, planner.u()).message()).contains("자기가 요청한");
        assertThat(finance.review(ownBudget, true, null, planner2.u()).ok()).isTrue();

        // 목록: 담당 부서는 자기 부서만, 예산편성과는 전체
        assertThat(body(writer.mvc(), "/finance?year=" + year)).contains("민원실 운영").doesNotContain("예산 업무");
        assertThat(body(planner.mvc(), "/finance?year=" + year)).contains("민원실 운영").contains("예산 업무");
        // 사업에 예산이 있으면 지울 수 없다
        assertThat(finance.deleteProgram(detail, planner.u()).message()).contains("예산이 있어");
        assertThat(finance.programTree(year)).extracting(ProgramVO::getProgName).contains("민원서비스 향상");
    }

    @Test
    void 세입은_세입_담당만() throws Exception {
        Staff normal = staff("일반", "민원봉사과", false);
        Staff revenue = staff("세입담당", "세무과", true);
        Staff plannerNoRev = staff("편성(세입아님)", finance.getBudgetDept(), false);
        Staff plannerRev = staff("편성(세입)", finance.getBudgetDept(), true);

        normal.mvc().perform(get("/finance?area=rev")).andExpect(status().isForbidden())
                .andExpect(content().string(containsString("세입 담당 직원만 볼 수 있습니다")));
        normal.mvc().perform(get("/finance/budget/new").param("kind", "REV")).andExpect(status().isForbidden());
        normal.mvc().perform(post("/finance/receipts").param("year", "" + year).param("code", "111-01")
                .param("date", LocalDate.now().toString()).param("amount", "100").param("description", "x"))
                .andExpect(status().isForbidden());

        String code = "212-0" + (1 + (int) (Math.random() * 8));
        FinanceService.Result r = finance.create(BudgetVO.REV, year, null, code, 50_000_000L, "추계", true, revenue.u());
        if (!r.ok()) { // 다른 테스트 실행에서 같은 목을 이미 썼으면 다른 목으로
            r = finance.create(BudgetVO.REV, year, null, "224-0" + (1 + (int) (Math.random() * 7)), 50_000_000L, "추계", true, revenue.u());
        }
        assertThat(r.ok()).as(r.message()).isTrue();
        long id = r.id();

        normal.mvc().perform(get("/finance/budget/" + id)).andExpect(status().isNotFound());
        assertThat(finance.review(id, true, null, plannerNoRev.u()).ok()).isFalse();
        assertThat(finance.review(id, true, null, plannerRev.u()).ok()).isTrue();

        BudgetVO b = finance.get(id);
        revenue.mvc().perform(post("/finance/receipts").param("year", "" + year).param("code", b.getCode())
                        .param("date", LocalDate.now().toString()).param("amount", "1,250,000").param("party", "홍길동")
                        .param("description", "11월분"))
                .andExpect(flash().attribute("message", "1,250,000원 수납을 기록했습니다."));
        assertThat(finance.receiptsByCode(year).get(b.getCode())).isGreaterThanOrEqualTo(1_250_000L);
        assertThat(body(revenue.mvc(), "/finance?area=rev&year=" + year)).contains("세입 현황").contains("11월분");
    }
}
