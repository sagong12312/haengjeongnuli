package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.civil.service.CertIssueVO;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.ContractService;
import egovframework.haengjeongnuli.finance.service.ContractVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 증명 발급대장, 계약대장, 업무 메뉴 */
@SpringBootTest
class CertContractTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired ContractService contracts;

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 창구에서_증명_발급과_수수료를_기록한다() throws Exception {
        assertThat(CertIssueVO.standardFee("주민등록표 등본", "본인", 2)).isEqualTo(800);
        assertThat(CertIssueVO.standardFee("주민등록표 초본", "이해관계인", 1)).isEqualTo(500);
        assertThat(CertIssueVO.standardFee("인감증명서", "본인", 1)).isEqualTo(600);

        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/civil/certs")).andExpect(status().isForbidden());
        MockMvc counter = TestUsers.mvcAs(ctx, TestUsers.counter());
        String name = "발급" + UUID.randomUUID().toString().substring(0, 4);
        counter.perform(post("/civil/certs").param("certType", "인감증명서").param("applicantName", name)
                        .param("applicantType", "본인").param("copies", "2"))
                .andExpect(flash().attribute("message", "신청인 신분증을 확인했는지 표시하세요."));
        counter.perform(post("/civil/certs").param("certType", "인감증명서").param("applicantName", name)
                        .param("applicantType", "본인").param("copies", "2").param("idChecked", "true"))
                .andExpect(flash().attribute("message", containsString("수수료 1,200원")));
        counter.perform(post("/civil/certs").param("certType", "주민등록표 등본").param("applicantName", name)
                        .param("applicantType", "본인").param("copies", "1").param("idChecked", "true").param("exempt", "true"))
                .andExpect(flash().attribute("message", "수수료 면제 사유를 200자 이내로 입력하세요."));
        counter.perform(post("/civil/certs").param("certType", "주민등록표 등본").param("applicantName", name)
                        .param("applicantType", "본인").param("copies", "1").param("idChecked", "true").param("exempt", "true")
                        .param("exemptReason", "기초생활수급자"))
                .andExpect(flash().attribute("message", containsString("수수료 0원")));
        assertThat(body(counter, "/civil/certs?day=" + LocalDate.now())).contains(name).contains("면제: 기초생활수급자");
    }

    @Test
    void 계약을_등록하고_완료_해지를_기록한다() throws Exception {
        String dept = "계약" + UUID.randomUUID().toString().substring(0, 4);
        String id = "k" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("계약담당");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Contract-pass-7!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc m = TestUsers.mvcAs(ctx, u);
        String today = LocalDate.now().toString();

        m.perform(post("/finance/contracts").param("title", "사무용 의자 구매").param("contractKind", "물품").param("method", "수의계약")
                        .param("party", "가나상사").param("amount", "15,000,000").param("contractDate", today))
                .andExpect(flash().attribute("message", containsString("수의계약은 근거")));
        m.perform(post("/finance/contracts").param("title", "사무용 의자 구매").param("contractKind", "물품").param("method", "수의계약")
                        .param("privateReason", "시행령 제25조 해당 사유").param("party", "가나상사").param("partyBizNo", "123-45")
                        .param("amount", "15,000,000").param("contractDate", today))
                .andExpect(flash().attribute("message", containsString("사업자등록번호")));
        m.perform(post("/finance/contracts").param("title", "사무용 의자 구매").param("contractKind", "물품").param("method", "수의계약")
                        .param("privateReason", "시행령 제25조 해당 사유").param("party", "가나상사").param("partyBizNo", "123-45-67890")
                        .param("amount", "15,000,000").param("deposit", "20,000,000").param("contractDate", today))
                .andExpect(flash().attribute("message", containsString("계약보증금")));
        m.perform(post("/finance/contracts").param("title", "사무용 의자 구매").param("contractKind", "물품").param("method", "수의계약")
                        .param("privateReason", "시행령 제25조 해당 사유").param("party", "가나상사").param("partyBizNo", "123-45-67890")
                        .param("amount", "15,000,000").param("contractDate", today).param("endDate", LocalDate.now().plusDays(30).toString()))
                .andExpect(flash().attribute("message", containsString("계약을 등록했습니다. 계약번호 계약-")));

        ContractVO c = contracts.list(u, LocalDate.now().getYear(), null).get(0);
        assertThat(c.getAmount()).isEqualTo(15_000_000L);
        assertThat(c.getDeptName()).isEqualTo(dept);
        // 다른 부서 직원은 보이지 않고 처리할 수 없다
        MockMvc other = TestUsers.mvcAs(ctx, TestUsers.general());
        assertThat(body(other, "/finance/contracts")).doesNotContain("사무용 의자 구매");
        other.perform(post("/finance/contracts/" + c.getContractId() + "/close").param("status", "DONE")).andExpect(status().isNotFound());

        m.perform(post("/finance/contracts/" + c.getContractId() + "/close").param("status", "TERMINATED"))
                .andExpect(flash().attribute("message", "해제·해지 사유를 입력하세요."));
        m.perform(post("/finance/contracts/" + c.getContractId() + "/close").param("status", "DONE").param("note", "납품 완료"))
                .andExpect(flash().attribute("message", "계약을 완료로 기록했습니다."));
        assertThat(contracts.get(c.getContractId()).getStatus()).isEqualTo(ContractVO.DONE);
        assertThat(body(m, "/finance/contracts")).contains("사무용 의자 구매").contains("완료").contains("납품 완료");
    }

    @Test
    void 메인_업무_메뉴와_상단_메뉴에_새_업무가_보인다() throws Exception {
        String main = body(TestUsers.mvcAs(ctx, TestUsers.counter()), "/");
        assertThat(main).contains("id=\"work\"").contains("/civil/certs").contains("/finance/contracts").contains("#work");
        assertThat(body(TestUsers.mvcAs(ctx, TestUsers.general()), "/")).doesNotContain("/civil/certs");
        assertThat(body(TestUsers.mvcAs(ctx, TestUsers.general()), "/search?q=계약")).contains("계약대장");
    }
}
