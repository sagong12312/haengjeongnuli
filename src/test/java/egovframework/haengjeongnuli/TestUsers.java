package egovframework.haengjeongnuli;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 테스트에서 쓰는 로그인 직원 */
public final class TestUsers {

    private TestUsers() {}

    public static LoginUser of(LoginUser.Role role, String loginId, String name) {
        LoginUser u = new LoginUser();
        u.setUserId((long) loginId.hashCode());
        u.setLoginId(loginId);
        u.setPasswordHash("{noop}x");
        u.setUserName(name);
        u.setPositionName("주무관");
        u.setDeptName("민원봉사과");
        u.setRole(role);
        u.setPasswordChangedAt(java.time.LocalDateTime.now());
        return u;
    }

    public static LoginUser counter() { return of(LoginUser.Role.COUNTER, "counter1", "창구직원"); }
    public static LoginUser general() { return of(LoginUser.Role.GENERAL, "general1", "일반직원"); }
    public static LoginUser admin() { return of(LoginUser.Role.ADMIN, "admin-t", "관리자"); }

    /** 모든 요청이 이 직원으로 로그인한 상태이고 CSRF 토큰을 붙인 MockMvc */
    public static MockMvc mvcAs(WebApplicationContext ctx, LoginUser who) {
        return MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(who)).with(csrf()))
                .build();
    }
}
