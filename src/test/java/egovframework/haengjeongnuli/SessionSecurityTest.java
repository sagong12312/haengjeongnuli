package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 로그인 중인 세션 무효화, 로그인 잠금 자동 해제, 주소별 로그인 실패 제한 */
@SpringBootTest
class SessionSecurityTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    private MockMvc anon() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginReq(String id, String pw, String ip) {
        return post("/login").with(csrf()).with(from(ip)).param("loginId", id).param("password", pw);
    }

    private static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    /** 계정을 만들고 첫 비밀번호 변경까지 마친 세션 */
    private record Login(LoginUser user, MockHttpSession session) {}

    private Login login(MockMvc mvc, String ip) throws Exception {
        String id = "x" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("세션직원");
        f.setDeptName("세션과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("First-pass-12!");
        LoginUser u = users.create(f);
        MockHttpSession s = (MockHttpSession) mvc.perform(loginReq(id, "First-pass-12!", ip)).andReturn().getRequest().getSession();
        mvc.perform(post("/mypage/password").session(s).with(csrf()).param("currentPassword", "First-pass-12!")
                .param("newPassword", "Second-pass-34!").param("confirmPassword", "Second-pass-34!"))
                .andExpect(redirectedUrl("/"));
        // 본인이 비밀번호를 바꿔도 세션은 이어진다
        mvc.perform(get("/board").session(s)).andExpect(status().isOk());
        return new Login(u, s);
    }

    @Test
    void 사용_중지하면_로그인_중인_세션이_바로_끊긴다() throws Exception {
        MockMvc mvc = anon();
        Login l = login(mvc, "192.168.20.1");
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(post("/admin/users/" + l.user().getUserId() + "/enabled").param("value", "false"));
        mvc.perform(get("/board").session(l.session())).andExpect(redirectedUrl("/login?error=revoked"));
        // 같은 세션으로 다시 들어올 수 없다
        mvc.perform(get("/board").session(l.session())).andExpect(status().is3xxRedirection());
    }

    @Test
    void 관리자가_비밀번호를_재설정하면_기존_세션이_끊긴다() throws Exception {
        MockMvc mvc = anon();
        Login l = login(mvc, "192.168.20.2");
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(post("/admin/users/" + l.user().getUserId() + "/password")
                .param("newPassword", "Reset-pass-56!"));
        mvc.perform(get("/board").session(l.session())).andExpect(redirectedUrl("/login?error=revoked"));
    }

    @Test
    void 담당을_빼면_다시_로그인하지_않아도_바로_권한이_없어진다() throws Exception {
        MockMvc mvc = anon();
        Login l = login(mvc, "192.168.20.3");
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        mvc.perform(get("/welfare").session(l.session())).andExpect(status().isForbidden());
        admin.perform(post("/admin/users/" + l.user().getUserId() + "/welfare").param("value", "true"));
        mvc.perform(get("/welfare").session(l.session())).andExpect(status().isOk());
        admin.perform(post("/admin/users/" + l.user().getUserId() + "/welfare").param("value", "false"));
        mvc.perform(get("/welfare").session(l.session())).andExpect(status().isForbidden());
    }

    @Test
    void 잠긴_계정은_정해진_시간이_지나면_저절로_풀린다() throws Exception {
        MockMvc mvc = anon();
        String id = "lk" + UUID.randomUUID().toString().substring(0, 6);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("잠김직원");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Right-pass-77!");
        users.create(f);
        for (int i = 0; i < LoginUser.MAX_FAILED; i++) {
            mvc.perform(loginReq(id, "wrong", "192.168.21." + i));
        }
        mvc.perform(loginReq(id, "Right-pass-77!", "192.168.21.9"))
                .andExpect(redirectedUrl("/login?error=locked"));
        // 잠긴 지 잠금 시간이 지나면 맞는 비밀번호로 들어갈 수 있다
        jdbc.update("UPDATE USER_ACCOUNT SET LOCKED_AT = ? WHERE LOGIN_ID = ?",
                LocalDateTime.now().minusMinutes(LoginUser.getLockMinutes() + 1), id);
        mvc.perform(loginReq(id, "Right-pass-77!", "192.168.21.9"))
                .andExpect(redirectedUrl("/"));
        assertThat(jdbc.queryForObject("SELECT FAILED_COUNT FROM USER_ACCOUNT WHERE LOGIN_ID = ?", Integer.class, id)).isZero();
    }

    @Test
    void 한_주소에서_로그인을_너무_많이_틀리면_그_주소를_잠시_막는다() throws Exception {
        MockMvc mvc = anon();
        String ip = "192.168.22.7";
        for (int i = 0; i < 20; i++) {
            mvc.perform(loginReq("nobody" + i, "x", ip));
        }
        String id = "ip" + UUID.randomUUID().toString().substring(0, 6);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("주소직원");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Right-pass-88!");
        users.create(f);
        mvc.perform(loginReq(id, "Right-pass-88!", ip))
                .andExpect(redirectedUrl("/login?error=ip"));
        // 다른 주소에서는 들어갈 수 있고, 막힌 계정 실패 횟수도 늘지 않는다
        mvc.perform(loginReq(id, "Right-pass-88!", "192.168.22.8"))
                .andExpect(redirectedUrl("/"));
    }
}
