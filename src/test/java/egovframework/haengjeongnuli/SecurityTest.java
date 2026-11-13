package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class SecurityTest {

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    UserService userService;

    MockMvc anon;

    @BeforeEach
    void setUp() {
        anon = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String newUser(LoginUser.Role role, String password) {
        String id = "u" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("테스트");
        f.setRole(role);
        f.setPassword(password);
        userService.create(f);
        return id;
    }

    @Test
    void 로그인하지_않으면_로그인_화면으로_보낸다() throws Exception {
        anon.perform(get("/civil")).andExpect(redirectedUrlPattern("**/login"));
        anon.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("로그인")));
    }

    @Test
    void 첫_관리자_계정으로_로그인된다() throws Exception {
        anon.perform(formLogin("/login").userParameter("loginId").user("admin").password("Test-admin-1!"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 비밀번호가_틀리면_로그인되지_않는다() throws Exception {
        anon.perform(formLogin("/login").userParameter("loginId").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error=bad"));
    }

    @Test
    void 다섯_번_틀리면_잠긴다() throws Exception {
        String id = newUser(LoginUser.Role.GENERAL, "Good-pass-123!");
        for (int i = 0; i < LoginUser.MAX_FAILED; i++) {
            anon.perform(formLogin("/login").userParameter("loginId").user(id).password("bad"));
        }
        // 맞는 비밀번호여도 잠겨 있으면 안 된다
        anon.perform(formLogin("/login").userParameter("loginId").user(id).password("Good-pass-123!"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error=locked"));

        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        userService.unlock(u.getUserId());
        anon.perform(formLogin("/login").userParameter("loginId").user(id).password("Good-pass-123!"))
                .andExpect(authenticated());
    }

    @Test
    void 사용_중지된_계정은_로그인되지_않는다() throws Exception {
        String id = newUser(LoginUser.Role.GENERAL, "Good-pass-123!");
        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        userService.setEnabled(u.getUserId(), false);
        anon.perform(formLogin("/login").userParameter("loginId").user(id).password("Good-pass-123!"))
                .andExpect(redirectedUrl("/login?error=disabled"));
    }

    @Test
    void 관리_화면은_관리자만() throws Exception {
        MockMvc general = TestUsers.mvcAs(ctx, TestUsers.general());
        general.perform(get("/admin/users")).andExpect(status().isForbidden());
        general.perform(get("/h2-console/")).andExpect(status().isForbidden());

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(get("/admin/users")).andExpect(status().isOk())
                .andExpect(content().string(containsString("직원 계정 관리")));
    }

    @Test
    void 관리자가_계정을_만들면_그_계정으로_로그인된다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/users")
                        .param("loginId", "newbie").param("userName", "신규직원").param("role", "COUNTER")
                        .param("password", "Fresh-start-9!"))
                .andExpect(redirectedUrl("/admin/users"));

        anon.perform(formLogin("/login").userParameter("loginId").user("newbie").password("Fresh-start-9!"))
                .andExpect(authenticated().withRoles("COUNTER"));
    }

    @Test
    void 규칙에_안_맞는_비밀번호로는_계정을_만들_수_없다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/users")
                        .param("loginId", "weak").param("userName", "약한비번").param("role", "GENERAL")
                        .param("password", "12345678"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("9자 이상")));
    }

    @Test
    void CSRF_토큰이_없으면_거부된다() throws Exception {
        anon.perform(post("/login").param("loginId", "admin").param("password", "Test-admin-1!"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그아웃() throws Exception {
        anon.perform(post("/logout").with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void 비밀번호_규칙() {
        assertThat(PasswordPolicy.check("short1!", "a")).isNotNull();
        assertThat(PasswordPolicy.check("onlyletters!!", "a")).isNotNull();
        assertThat(PasswordPolicy.check("Hong1234!x", "hong")).contains("아이디");
        assertThat(PasswordPolicy.check("Good-pass-123!", "user")).isNull();
    }
}
