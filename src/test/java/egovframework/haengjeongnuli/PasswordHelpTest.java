package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.PasswordResetService;
import egovframework.haengjeongnuli.user.service.PasswordResetVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class PasswordHelpTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired PasswordResetService reset;

    private MockMvc anon() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    private String newUser(String name) {
        String id = "h" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Forgot-pass-11!");
        users.create(f);
        return id;
    }

    private PasswordResetVO openRequest(String id) {
        return reset.recent(200).stream().filter(r -> r.getLoginId().equals(id) && "OPEN".equals(r.getStatus()))
                .findFirst().orElse(null);
    }

    @Test
    void 분실_요청을_받아_관리자가_임시_비밀번호를_발급하면_첫_로그인에_바꾸게_한다() throws Exception {
        String id = newUser("분실직원");
        MockMvc anon = anon();
        anon.perform(get("/password-help")).andExpect(status().isOk());
        String msg = "요청을 받았습니다. 아이디와 이름이 맞으면 시스템 관리자가 본인을 확인한 뒤 임시 비밀번호를 직접 전달합니다.";

        // 이름이 틀리거나 없는 아이디도 같은 안내를 보여 주고, 요청은 남기지 않는다
        anon.perform(post("/password-help").with(csrf()).with(from("192.168.7.1")).param("loginId", id).param("userName", "다른사람"))
                .andExpect(redirectedUrl("/password-help")).andExpect(flash().attribute("info", msg));
        anon.perform(post("/password-help").with(csrf()).with(from("192.168.7.1")).param("loginId", "nobody-x").param("userName", "누구"))
                .andExpect(flash().attribute("info", msg));
        assertThat(openRequest(id)).isNull();

        anon.perform(post("/password-help").with(csrf()).with(from("192.168.7.1")).param("loginId", id)
                        .param("userName", "분실직원").param("contact", "내선 1234"))
                .andExpect(flash().attribute("info", msg));
        PasswordResetVO req = openRequest(id);
        assertThat(req).isNotNull();
        assertThat(req.getContact()).isEqualTo("내선 1234");
        // 처리 대기 중이면 또 남기지 않는다
        anon.perform(post("/password-help").with(csrf()).with(from("192.168.7.1")).param("loginId", id).param("userName", "분실직원"));
        assertThat(reset.recent(200).stream().filter(r -> r.getLoginId().equals(id)).count()).isEqualTo(1);

        // 관리자만 발급할 수 있다
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(post("/admin/users/pw-requests/" + req.getReqId() + "/issue"))
                .andExpect(status().isForbidden());
        Object issued = TestUsers.mvcAs(ctx, TestUsers.admin())
                .perform(post("/admin/users/pw-requests/" + req.getReqId() + "/issue"))
                .andExpect(redirectedUrl("/admin/users#pw-requests"))
                .andReturn().getFlashMap().get("tempPassword");
        String temp = ((PasswordResetService.Issued) issued).tempPassword();
        assertThat(PasswordPolicy.check(temp, id)).isNull();
        assertThat(openRequest(id)).isNull();
        // 같은 요청을 두 번 발급하지 않는다
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(post("/admin/users/pw-requests/" + req.getReqId() + "/issue"))
                .andExpect(flash().attribute("message", "이미 처리했거나 없는 요청입니다."));

        // 임시 비밀번호로 로그인하면 비밀번호부터 바꿔야 한다
        var session = anon.perform(formLogin("/login").userParameter("loginId").user(id).password(temp))
                .andReturn().getRequest().getSession();
        anon.perform(get("/").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(redirectedUrl("/mypage/password?expired"));
    }

    @Test
    void 같은_주소에서_요청이_많으면_더_받지_않는다() throws Exception {
        MockMvc anon = anon();
        for (int i = 0; i < 6; i++) {
            anon.perform(post("/password-help").with(csrf()).with(from("192.168.8.8")).param("loginId", "none" + i).param("userName", "x"));
        }
        String id = newUser("많은요청");
        anon.perform(post("/password-help").with(csrf()).with(from("192.168.8.8")).param("loginId", id).param("userName", "많은요청"));
        // 실패한 요청은 남지 않으므로 횟수 제한에 걸리지 않는다
        assertThat(openRequest(id)).isNotNull();
        for (int i = 0; i < 5; i++) {
            String other = newUser("요청" + i);
            anon.perform(post("/password-help").with(csrf()).with(from("192.168.9.9")).param("loginId", other).param("userName", "요청" + i));
        }
        String last = newUser("마지막");
        anon.perform(post("/password-help").with(csrf()).with(from("192.168.9.9")).param("loginId", last).param("userName", "마지막"));
        assertThat(openRequest(last)).isNull();
    }
}
