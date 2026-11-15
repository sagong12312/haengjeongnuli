package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditSearchVO;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class CivilProcessTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired CivilProcessService process;
    @Autowired AuditService auditService;

    private record Staff(LoginUser u, MockMvc mvc) {}

    private Staff staff(String name, String dept, LoginUser.Role role) {
        String id = "c" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(role);
        f.setPassword("Staff-pass-77!");
        userService.create(f);
        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    private long receive(Staff counter, String kind, String assignee, String notify) throws Exception {
        String url = counter.mvc().perform(post("/civil/new").param("kind", kind).param("route", "방문")
                        .param("lawInterpretation", "false")
                        .param("name", "홍길동").param("mobile", "010-1111-2222").param("notify", notify)
                        .param("title", "보도 파손 " + UUID.randomUUID()).param("content", "고쳐 주세요")
                        .param("staffId", assignee).param("privacyConsent", "true"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith("/civil/");
        return Long.parseLong(url.substring("/civil/".length()));
    }

    @Test
    void 배정_연장_완료_흐름() throws Exception {
        String dept = "도로" + UUID.randomUUID().toString().substring(0, 4);
        Staff counter = staff("창구", "민원봉사과", LoginUser.Role.COUNTER);
        Staff worker = staff("담당", dept, LoginUser.Role.GENERAL);
        Staff outsider = staff("남", "다른과", LoginUser.Role.GENERAL);

        long id = receive(counter, "SUGGESTION", worker.u().getLoginId(), "우편");
        CivilComplaintVO c = process.get(id);
        assertThat(c.getStatus()).isEqualTo(CivilStatus.IN_PROGRESS);
        assertThat(c.getDept()).isEqualTo(dept);
        assertThat(c.getPeriodDays()).isEqualTo(14);
        assertThat(worker.mvc().perform(get("/notifications")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("담당으로 배정되었습니다");

        // 개인정보가 있는 상세 화면은 관계된 직원만, 열람은 기록
        outsider.mvc().perform(get("/civil/" + id)).andExpect(status().isNotFound());
        worker.mvc().perform(get("/civil/" + id)).andExpect(status().isOk());
        AuditSearchVO s = new AuditSearchVO();
        s.setActorId(worker.u().getLoginId());
        s.setAction(AuditAction.CIVIL_VIEW);
        assertThat(auditService.count(s)).isEqualTo(1);

        // 처리기간 연장 (시행령 제21조)
        assertThat(process.extend(id, null, 3, "x", false, true, worker.u()).message()).contains("연장 사유");
        assertThat(process.extend(id, egovframework.haengjeongnuli.civil.service.ExtendReason.FACT_CHECK, 15, "현장", false, true, worker.u()).message())
                .contains("처리기간(14일)");
        assertThat(process.extend(id, egovframework.haengjeongnuli.civil.service.ExtendReason.FACT_CHECK, 5, "현장", false, false, worker.u()).message())
                .contains("문서로 통지");
        LocalDateTime before = c.getDeadlineAt();
        worker.mvc().perform(post("/civil/" + id + "/extend").param("reason", "FACT_CHECK").param("days", "5")
                        .param("detail", "현장 확인 필요").param("notified", "true"))
                .andExpect(flash().attribute("message", containsString("연장했습니다")));
        assertThat(process.get(id).getDeadlineAt()).isAfter(before);
        assertThat(process.extend(id, egovframework.haengjeongnuli.civil.service.ExtendReason.COOPERATION, 3, "협조", false, true, worker.u()).message())
                .contains("민원인의 동의");
        assertThat(process.extend(id, egovframework.haengjeongnuli.civil.service.ExtendReason.COOPERATION, 3, "협조", true, true, worker.u()).ok()).isTrue();
        assertThat(process.extend(id, egovframework.haengjeongnuli.civil.service.ExtendReason.COOPERATION, 1, "또", true, true, worker.u()).message())
                .contains("더는 연장할 수 없습니다");

        // 처리 완료 (법 제27조)
        assertThat(process.complete(id, egovframework.haengjeongnuli.civil.service.ResultType.REJECT, "어렵습니다", null, "문서(우편·교부)", worker.u()).message())
                .contains("구제절차");
        assertThat(process.complete(id, egovframework.haengjeongnuli.civil.service.ResultType.ACCEPT, "보수했습니다", null, "문자", worker.u()).message())
                .contains("문서로 알리는 것이 원칙");
        worker.mvc().perform(post("/civil/" + id + "/complete").param("type", "ACCEPT").param("result", "보도 블록을 교체했습니다.")
                        .param("notifyMethod", "문서(우편·교부)"))
                .andExpect(flash().attribute("message", containsString("처리를 마쳤습니다")));
        CivilComplaintVO done = process.get(id);
        assertThat(done.getStatus()).isEqualTo(CivilStatus.DONE);
        assertThat(process.history(id)).extracting(h -> h.getAction()).containsExactly("ASSIGN", "EXTEND", "EXTEND", "DONE");
        assertThat(worker.mvc().perform(get("/civil/" + id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("처리 결과").contains("보도 블록을 교체했습니다.").contains("처리기간 연장").contains("2회 연장");
    }

    @Test
    void 즉시_처리_민원은_연장할_수_없고_문자_요청은_문자로_알릴_수_있다() throws Exception {
        Staff counter = staff("창구", "민원봉사과", LoginUser.Role.COUNTER);
        long id = receive(counter, "QUERY", "", "문자");
        assertThat(process.get(id).getStatus()).isEqualTo(CivilStatus.RECEIVED);
        long other = receive(counter, "OTHER", "", "우편");
        assertThat(process.extend(other, egovframework.haengjeongnuli.civil.service.ExtendReason.FACT_CHECK, 1, "x", false, true, counter.u()).message())
                .contains("즉시 처리하는 민원");
        // 민원인이 문자를 원했으면 문자로 결과를 알릴 수 있다
        assertThat(process.complete(id, egovframework.haengjeongnuli.civil.service.ResultType.ANSWER, "답변", null, "문자", counter.u()).ok()).isTrue();
        // 기타민원은 전화로도 알릴 수 있다
        assertThat(process.complete(other, egovframework.haengjeongnuli.civil.service.ResultType.ANSWER, "안내", null, "전화", counter.u()).ok()).isTrue();
    }
}
