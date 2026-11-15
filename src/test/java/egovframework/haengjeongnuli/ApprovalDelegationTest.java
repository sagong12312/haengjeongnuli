package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalLineVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 협조·위임전결·대결 (행정업무의 운영 및 혁신에 관한 규정 제10조, 시행규칙 제7조) */
@SpringBootTest
class ApprovalDelegationTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired ApprovalService approvalService;

    private record Staff(String id, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "g" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setPositionName("팀장");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        userService.create(f);
        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(id, TestUsers.mvcAs(ctx, u));
    }

    private static long docId(MvcResult r) {
        Matcher m = Pattern.compile("/approval/(\\d+)").matcher(r.getResponse().getRedirectedUrl());
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 검토_협조_전결_순서로_결재하고_전결_표시가_남는다() throws Exception {
        String dept = "총무" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept), reviewer = staff("검토자", dept), coop = staff("협조자", "회계과"),
                chief = staff("과장", dept);
        String title = "전결 문서 " + UUID.randomUUID();
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", title)
                .param("body", "본문").param("reviewers", reviewer.id()).param("cooperators", coop.id())
                .param("finalApprover", chief.id()).param("delegated", "true")).andReturn());

        ApprovalDocVO d = approvalService.get(id);
        assertThat(d.isDelegated()).isTrue();
        assertThat(d.getLines()).extracting(ApprovalLineVO::getRole).containsExactly("검토", "협조", "결재");

        // 협조자는 검토가 끝난 뒤에 차례가 온다
        coop.mvc().perform(post("/approval/" + id + "/approve"))
                .andExpect(flash().attribute("message", "지금 결재할 차례가 아닙니다."));
        reviewer.mvc().perform(post("/approval/" + id + "/approve"));
        assertThat(body(coop.mvc(), "/notifications")).contains("협조할 차례입니다");
        coop.mvc().perform(post("/approval/" + id + "/approve").param("opinion", "예산 과목 확인했습니다"));
        chief.mvc().perform(post("/approval/" + id + "/approve"));

        assertThat(approvalService.get(id).getStatus()).isEqualTo(ApprovalDocVO.DONE);
        assertThat(body(drafter.mvc(), "/approval/" + id)).contains("sign-mark\">전결").contains("예산 과목 확인했습니다");

        // 다시 기안하면 협조자·전결이 그대로 옮겨진다
        assertThat(body(drafter.mvc(), "/approval/draft?from=" + id)).contains(title);
    }

    @Test
    void 결재권자가_지정한_대결자가_대결하고_사후에_알린다() throws Exception {
        String dept = "민원" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept), reviewer = staff("검토자", dept), chief = staff("과장", dept),
                deputy = staff("팀장", dept), other = staff("남", dept);
        LocalDate today = LocalDate.now();

        chief.mvc().perform(post("/approval/proxy").param("proxyLoginId", deputy.id())
                        .param("startDate", today.toString()).param("endDate", today.plusDays(3).toString()).param("reason", "휴가"))
                .andExpect(flash().attribute("message", containsString("대결자를 지정했습니다")));
        // 기간이 겹치거나 거꾸로면 지정할 수 없다
        chief.mvc().perform(post("/approval/proxy").param("proxyLoginId", other.id())
                        .param("startDate", today.plusDays(2).toString()).param("endDate", today.plusDays(5).toString()).param("reason", "출장"))
                .andExpect(flash().attribute("message", containsString("겹치는")));
        chief.mvc().perform(post("/approval/proxy").param("proxyLoginId", other.id())
                        .param("startDate", today.plusDays(9).toString()).param("endDate", today.plusDays(8).toString()).param("reason", "출장"))
                .andExpect(flash().attribute("message", containsString("기간을 확인하세요")));
        assertThat(body(deputy.mvc(), "/notifications")).contains("대결자로 지정했습니다");

        String title = "대결 문서 " + UUID.randomUUID();
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", title)
                .param("body", "본문").param("reviewers", reviewer.id()).param("finalApprover", chief.id())).andReturn());

        // 검토 차례는 대결하지 않는다 (결재권자 차례가 오기 전에는 문서도 보이지 않는다)
        deputy.mvc().perform(post("/approval/" + id + "/approve")).andExpect(status().isNotFound());
        reviewer.mvc().perform(post("/approval/" + id + "/approve"));

        // 결재권자 차례: 대결자의 결재 대기함에 들어오고 대결할 수 있다. 지정되지 않은 직원은 볼 수도 없다
        assertThat(body(deputy.mvc(), "/approval?box=wait")).contains(title);
        assertThat(body(deputy.mvc(), "/notifications")).contains("부재 중 대결할 문서가 왔습니다");
        assertThat(body(deputy.mvc(), "/approval/" + id)).contains("부재 중 대결할 차례입니다");
        other.mvc().perform(get("/approval/" + id)).andExpect(status().isNotFound());
        other.mvc().perform(post("/approval/" + id + "/approve")).andExpect(status().isNotFound());

        deputy.mvc().perform(post("/approval/" + id + "/approve").param("opinion", "대결합니다"))
                .andExpect(redirectedUrl("/approval?box=wait"));
        ApprovalDocVO d = approvalService.get(id);
        assertThat(d.getStatus()).isEqualTo(ApprovalDocVO.DONE);
        ApprovalLineVO fin = d.getLines().get(d.getLines().size() - 1);
        assertThat(fin.getApproverId()).isEqualTo(chief.id());
        assertThat(fin.getActedById()).isEqualTo(deputy.id());
        assertThat(body(chief.mvc(), "/notifications")).contains("대결했습니다");
        assertThat(body(drafter.mvc(), "/approval/" + id)).contains("sign-mark\">대결").contains("팀장(대결) 의견");
        // 대결한 뒤에도 대결자는 문서를 볼 수 있다
        deputy.mvc().perform(get("/approval/" + id)).andExpect(status().isOk());
    }

    @Test
    void 대결자_지정을_취소하면_대결할_수_없다() throws Exception {
        String dept = "세무" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept), chief = staff("과장", dept), deputy = staff("팀장", dept);
        LocalDate today = LocalDate.now();
        chief.mvc().perform(post("/approval/proxy").param("proxyLoginId", deputy.id())
                .param("startDate", today.toString()).param("endDate", today.toString()).param("reason", "출장"));
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", "취소 시험")
                .param("body", "본문").param("finalApprover", chief.id())).andReturn());
        assertThat(approvalService.get(id).isProxyTurn((LoginUser) userService.loadUserByUsername(deputy.id()))).isTrue();

        long proxyId = approvalService.proxies((LoginUser) userService.loadUserByUsername(chief.id())).get(0).getProxyId();
        // 대결자 본인이나 남은 지정을 취소할 수 없다
        deputy.mvc().perform(post("/approval/proxy/" + proxyId + "/cancel"))
                .andExpect(flash().attribute("message", "취소할 수 없는 지정입니다."));
        chief.mvc().perform(post("/approval/proxy/" + proxyId + "/cancel"))
                .andExpect(flash().attribute("message", "대결자 지정을 취소했습니다."));
        deputy.mvc().perform(post("/approval/" + id + "/approve")).andExpect(status().isNotFound());
        assertThat(body(deputy.mvc(), "/approval?box=wait")).doesNotContain("취소 시험");
        chief.mvc().perform(post("/approval/" + id + "/approve")).andExpect(redirectedUrl("/approval?box=wait"));
    }
}
