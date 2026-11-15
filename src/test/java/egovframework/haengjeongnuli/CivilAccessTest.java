package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 민원 목록 개인정보 접근통제 (보안 점검 지적 사항) */
@SpringBootTest
class CivilAccessTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;

    private LoginUser staff(String dept, LoginUser.Role role) {
        String id = "a" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("직원");
        f.setDeptName(dept);
        f.setRole(role);
        f.setPassword("Staff-pass-77!");
        userService.create(f);
        LoginUser u = userService.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return u;
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 관계없는_직원에게는_목록_검색_요약에도_나오지_않는다() throws Exception {
        String dept = "도로" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser counter = staff("민원봉사과", LoginUser.Role.COUNTER);
        LoginUser worker = staff(dept, LoginUser.Role.GENERAL);
        LoginUser colleague = staff(dept, LoginUser.Role.GENERAL);
        LoginUser outsider = staff("다른과" + UUID.randomUUID().toString().substring(0, 4), LoginUser.Role.GENERAL);
        String applicant = "신청인" + UUID.randomUUID().toString().substring(0, 6);
        String title = "보도 파손 " + UUID.randomUUID();

        TestUsers.mvcAs(ctx, counter).perform(post("/civil/new").param("kind", "SUGGESTION").param("route", "방문")
                .param("name", applicant).param("mobile", "010-1111-2222").param("notify", "문자")
                .param("title", title).param("content", "고쳐 주세요").param("staffId", worker.getLoginId())
                .param("privacyConsent", "true"));

        // 창구 직원, 담당자, 같은 처리 부서 직원은 본다
        assertThat(body(TestUsers.mvcAs(ctx, counter), "/civil?q=" + applicant)).contains(title).contains(applicant);
        assertThat(body(TestUsers.mvcAs(ctx, worker), "/civil?q=" + applicant)).contains(title);
        assertThat(body(TestUsers.mvcAs(ctx, colleague), "/civil?q=" + applicant)).contains(title);

        // 관계없는 직원은 목록·통합검색 어디에서도 신청인 이름과 제목을 볼 수 없다
        MockMvc out = TestUsers.mvcAs(ctx, outsider);
        assertThat(body(out, "/civil?q=" + applicant)).doesNotContain(title);  // 검색창에는 검색어가 그대로 보이므로 제목으로 확인
        assertThat(body(out, "/civil")).doesNotContain(applicant);
        assertThat(body(out, "/search?q=" + applicant)).doesNotContain(title);
        // 요청 값으로 범위를 넓힐 수 없다
        assertThat(body(out, "/civil?q=" + applicant + "&viewerAll=true&viewerId=" + counter.getLoginId())).doesNotContain(title);
    }
}
