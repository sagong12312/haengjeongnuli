package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.BudgetChangeService;
import egovframework.haengjeongnuli.finance.service.BudgetChangeVO;
import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 추가경정예산·이월 */
@SpringBootTest
class BudgetChangeTest {

    @Autowired WebApplicationContext ctx;
    @Autowired FinanceService finance;
    @Autowired BudgetChangeService changes;
    @Autowired UserService users;

    private record Staff(LoginUser u, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "b" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Budget-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    private long openChange(long budgetId) {
        return changes.ofBudget(budgetId).stream().filter(c -> BudgetChangeVO.REQUESTED.equals(c.getStatus()))
                .findFirst().orElseThrow().getChangeId();
    }

    @Test
    void 추경으로_증감하고_남은_금액을_다음_해로_이월한다() throws Exception {
        int year = LocalDate.now().getYear();
        String dept = "추경" + UUID.randomUUID().toString().substring(0, 4);
        Staff planner = staff("편성", finance.getBudgetDept()), writer = staff("담당", dept), other = staff("남", "남" + dept);
        long policy = finance.addProgram(year, "POLICY", null, "정책사업", "016", "도로 정비", null, planner.u()).id();
        long unit = finance.addProgram(year, "UNIT", policy, null, null, "보도 정비", null, planner.u()).id();
        long detail = finance.addProgram(year, "DETAIL", unit, null, null, "보도블록 교체", dept, planner.u()).id();
        long id = finance.create(BudgetVO.EXP, year, detail, "401-01", 10_000_000L, "공사비", true, writer.u()).id();
        finance.review(id, true, null, planner.u());

        // 다른 부서는 요청할 수 없다
        other.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "SUPP").param("amount", "1000")
                .param("round", "1").param("reason", "x"));
        assertThat(changes.ofBudget(id)).isEmpty();

        writer.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "SUPP").param("amount", "-20,000,000")
                        .param("round", "1").param("reason", "감액"))
                .andExpect(flash().attribute("message", containsString("적게 줄일 수 없습니다")));
        writer.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "SUPP").param("amount", "5,000,000")
                        .param("round", "1").param("reason", "제1회 추경 의회 의결"))
                .andExpect(flash().attribute("message", "제1회 추가경정 승인을 요청했습니다."));
        writer.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "SUPP").param("amount", "1")
                        .param("round", "1").param("reason", "중복"))
                .andExpect(flash().attribute("message", containsString("승인을 기다리는")));
        assertThat(new String(planner.mvc().perform(get("/finance/review")).andReturn().getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8)).contains("추가경정·이월 승인 대기").contains("제1회 추경 의회 의결");

        // 요청자 본인은 승인할 수 없고, 반려에는 사유가 필요하다
        long c1 = openChange(id);
        writer.mvc().perform(post("/finance/changes/" + c1).param("approve", "true"))
                .andExpect(flash().attribute("message", containsString("직원만 승인·반려")));
        planner.mvc().perform(post("/finance/changes/" + c1).param("approve", "false"))
                .andExpect(flash().attribute("message", "반려 사유를 입력하세요."));
        planner.mvc().perform(post("/finance/changes/" + c1).param("approve", "true"))
                .andExpect(flash().attribute("message", "제1회 추가경정 금액을 예산에 반영했습니다."));
        assertThat(finance.get(id).getAmount()).isEqualTo(15_000_000L);

        // 이월: 남은 금액을 넘을 수 없다
        writer.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "CARRY").param("amount", "16,000,000")
                        .param("carryKind", "사고이월").param("reason", "x"))
                .andExpect(flash().attribute("message", containsString("쓰고 남은")));
        writer.mvc().perform(post("/finance/budget/" + id + "/change").param("type", "CARRY").param("amount", "4,000,000")
                        .param("carryKind", "사고이월").param("reason", "계약 후 공사 지연으로 연내 지급 불가"))
                .andExpect(flash().attribute("message", "사고이월 승인을 요청했습니다."));
        planner.mvc().perform(post("/finance/changes/" + openChange(id)).param("approve", "true"))
                .andExpect(flash().attribute("message", containsString(String.format("4,000,000원을 %d년 예산으로 넘겼습니다.", year + 1))));

        assertThat(finance.get(id).getAmount()).isEqualTo(11_000_000L);
        BudgetChangeVO done = changes.ofBudget(id).get(1);
        BudgetVO next = finance.get(done.getNewBudgetId());
        assertThat(next.getFiscalYear()).isEqualTo(year + 1);
        assertThat(next.getAmount()).isEqualTo(4_000_000L);
        assertThat(next.getCode()).isEqualTo("401-01");
        assertThat(next.isApproved()).isTrue();
        assertThat(next.getBasis()).contains(year + "년도 사고이월");
    }
}
