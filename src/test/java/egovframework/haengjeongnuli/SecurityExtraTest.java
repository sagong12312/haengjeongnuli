package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.security.SecurityService;
import egovframework.haengjeongnuli.security.SecurityWeb;
import egovframework.haengjeongnuli.security.Totp;
import egovframework.haengjeongnuli.user.service.PasswordPolicy;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 보안 보강: OTP, 흔한·재사용 비밀번호, 로그인 기록·새 주소, 휴면 잠금, 재확인, 내려받기 사유, 권한 알림, 워터마크, 헤더 */
@SpringBootTest
class SecurityExtraTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired SecurityService security;
    @Autowired NotificationService notifications;

    static final String PW = "Staff-pass-77!";

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    LoginUser staff(String name) {
        String id = "s" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword(PW);
        users.create(f);
        LoginUser u = users.findByLoginId(id);
        // 첫 비밀번호 변경 강제를 건너뛰려고 본인이 한 번 바꾼 것으로
        users.changeOwnPassword(u.getUserId(), PW, PW);
        return users.findByLoginId(id);
    }

    @Test
    void TOTP_번호는_RFC_6238_예시와_같고_같은_번호를_두_번_못_쓴다() {
        // RFC 6238 부록 B: 키 "12345678901234567890", 59초 → 94287082 (8자리) 의 끝 6자리 287082
        String secret = egovframework.haengjeongnuli.security.Totp.newSecret();
        assertThat(secret).hasSize(32);
        String rfcKey = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890" 의 Base32
        assertThat(Totp.code(rfcKey, Totp.step(59))).isEqualTo("287082");
        assertThat(Totp.code(rfcKey, Totp.step(1111111109))).isEqualTo("081804");
        long now = Totp.step(1111111109);
        assertThat(Totp.verify(rfcKey, "081804", now, 0)).isEqualTo(now);
        assertThat(Totp.verify(rfcKey, "081804", now, now)).isEqualTo(-1); // 이미 쓴 칸
        assertThat(Totp.verify(rfcKey, "000000", now, 0)).isEqualTo(-1);
        assertThat(Totp.uri("행정누리", "kim", rfcKey)).startsWith("otpauth://totp/").contains("secret=" + rfcKey);
    }

    @Test
    void 두_번째_단계를_마치기_전에는_다른_화면을_못_쓴다() throws Exception {
        LoginUser u = staff("오티피");
        String secret = security.startOtp(u);
        long step = Totp.step(System.currentTimeMillis() / 1000);
        assertThat(security.enableOtp(u, "000000").ok()).isFalse();
        assertThat(security.enableOtp(u, Totp.code(secret, step)).ok()).isTrue();

        MockMvc m = mvc();
        MvcResult login = m.perform(formLogin("/login").user("loginId", u.getLoginId()).password("password", PW)).andReturn();
        MockHttpSession s = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(s.getAttribute(SecurityWeb.OTP_PENDING)).isEqualTo(Boolean.TRUE);
        m.perform(get("/approval").session(s)).andExpect(redirectedUrl("/login/otp"));
        m.perform(post("/login/otp").session(s).with(csrf()).param("code", "123456")).andExpect(status().isOk());
        // 방금 켤 때 쓴 번호는 다시 못 쓴다 → 다음 칸 번호로
        String next = Totp.code(secret, step + 1);
        m.perform(post("/login/otp").session(s).with(csrf()).param("code", next)).andExpect(redirectedUrl("/"));
        m.perform(get("/approval").session(s)).andExpect(status().isOk());

        // 5번 틀리면 로그아웃
        MvcResult l2 = m.perform(formLogin("/login").user("loginId", u.getLoginId()).password("password", PW)).andReturn();
        MockHttpSession s2 = (MockHttpSession) l2.getRequest().getSession(false);
        for (int i = 0; i < 4; i++) {
            m.perform(post("/login/otp").session(s2).with(csrf()).param("code", "111111")).andExpect(status().isOk());
        }
        m.perform(post("/login/otp").session(s2).with(csrf()).param("code", "111111")).andExpect(redirectedUrl("/login?error=otp"));

        // 관리자 초기화
        assertThat(security.resetOtp(u.getLoginId(), u).ok()).isFalse();
        assertThat(security.resetOtp(u.getLoginId(), TestUsers.admin()).ok()).isTrue();
        assertThat(security.otpEnabled(u.getLoginId())).isFalse();
    }

    @Test
    void 흔한_비밀번호와_최근_비밀번호는_못_쓴다() throws Exception {
        assertThat(PasswordPolicy.check("Password1!x", "kim")).contains("흔한");
        assertThat(PasswordPolicy.check("Qwer!5678x", "kim")).contains("흔한");
        assertThat(PasswordPolicy.check("Blue!1234x", "kim")).contains("이어지는");
        assertThat(PasswordPolicy.check("Blue!9876x", "kim")).contains("이어지는");
        assertThat(PasswordPolicy.check("Blue!aaaa7", "kim")).contains("4번");
        assertThat(PasswordPolicy.check("Tq7-Rivers-Blue!", "kim")).isNull();

        LoginUser u = staff("재사용");
        MockMvc m = TestUsers.mvcAs(ctx, u);
        String a = "River-Lamp-71!", b = "Ocean-Desk-52!";
        change(m, PW, a);
        change(TestUsers.mvcAs(ctx, users.findByLoginId(u.getLoginId())), a, b); // 비밀번호가 바뀌면 이전 세션은 끊긴다
        // 바로 전 비밀번호, 두 번 전 비밀번호 모두 막힌다
        assertThat(changeHtml(TestUsers.mvcAs(ctx, users.findByLoginId(u.getLoginId())), b, a)).contains("다시 쓸 수 없습니다");
        assertThat(changeHtml(TestUsers.mvcAs(ctx, users.findByLoginId(u.getLoginId())), b, PW)).contains("다시 쓸 수 없습니다");
    }

    void change(MockMvc m, String cur, String next) throws Exception {
        MvcResult r = m.perform(post("/mypage/password").param("currentPassword", cur).param("newPassword", next).param("confirmPassword", next))
                .andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).as(r.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?s).*class=\"alert[^>]*>([^<]*)<.*", "$1")).isEqualTo("/");
    }

    String changeHtml(MockMvc m, String cur, String next) throws Exception {
        return m.perform(post("/mypage/password").param("currentPassword", cur).param("newPassword", next).param("confirmPassword", next))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    MvcResult login(MockMvc m, String id, String pw, String ip) throws Exception {
        return m.perform(post("/login").with(csrf()).param("loginId", id).param("password", pw).with(r -> { r.setRemoteAddr(ip); return r; }))
                .andReturn();
    }

    @Test
    void 처음_보는_주소_로그인은_알리고_로그인_기록을_본다() throws Exception {
        LoginUser u = staff("주소");
        MockMvc m = mvc();
        login(m, u.getLoginId(), PW, "10.1.1.1");
        Thread.sleep(5100); // 방금 기록과 구분
        login(m, u.getLoginId(), PW, "10.1.1.1");
        assertThat(notifications.recent(u.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).doesNotContain("처음 보는");
        Thread.sleep(5100);
        login(m, u.getLoginId(), PW, "10.9.9.9");
        assertThat(notifications.recent(u.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).contains("처음 보는 주소(10.9.9.9)");
        login(m, u.getLoginId(), "wrong-Pw-12!", "10.9.9.9");
        String page = TestUsers.mvcAs(ctx, users.findByLoginId(u.getLoginId())).perform(get("/mypage/security")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("10.9.9.9").contains("로그인 실패").contains("2단계 인증");
    }

    @Test
    void 오래_쓰지_않은_계정은_사용_중지하고_관리자에게_알린다() {
        LoginUser u = staff("휴면");
        // 마지막 로그인을 100일 전으로
        egovframework.haengjeongnuli.user.service.impl.UserMapper um = ctx.getBean(egovframework.haengjeongnuli.user.service.impl.UserMapper.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        jdbc.update("UPDATE USER_ACCOUNT SET LAST_LOGIN_AT = ? WHERE LOGIN_ID = ?", LocalDateTime.now().minusDays(100), u.getLoginId());
        assertThat(um).isNotNull();
        assertThat(security.lockDormant()).anyMatch(s -> s.contains(u.getLoginId()));
        assertThat(users.findByLoginId(u.getLoginId()).isEnabled()).isFalse();
    }

    @Test
    void 관리_작업은_비밀번호를_다시_확인해야_하고_개인정보_파일은_사유가_있어야_한다() throws Exception {
        SecurityWeb.Reauth reauth = new SecurityWeb.Reauth(10);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/users/1/enabled");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(reauth.preHandle(req, res, null)).isFalse();
        assertThat(res.getRedirectedUrl()).startsWith("/reauth?next=");
        req.getSession(true).setAttribute(SecurityWeb.REAUTH_AT, Instant.now());
        assertThat(reauth.preHandle(req, new MockHttpServletResponse(), null)).isTrue();
        req.getSession().setAttribute(SecurityWeb.REAUTH_AT, Instant.now().minusSeconds(11 * 60));
        assertThat(reauth.preHandle(req, new MockHttpServletResponse(), null)).isFalse();
        assertThat(reauth.preHandle(new MockHttpServletRequest("GET", "/admin/users"), new MockHttpServletResponse(), null)).isTrue();

        // 재확인 화면: 틀린 비밀번호, 맞는 비밀번호
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        assertThat(admin.perform(post("/reauth").param("password", "wrong").param("next", "/admin/users")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("맞지 않습니다");
        // 관리 화면 밖으로는 돌려보내지 않는다
        MockHttpServletResponse rr = admin.perform(post("/reauth").param("password", "still-wrong").param("next", "/approval")).andReturn().getResponse();
        assertThat(rr.getStatus() + " " + rr.getRedirectedUrl() + " " + rr.getContentAsString(StandardCharsets.UTF_8)).contains("value=\"/admin/users\"");

        // 개인정보 내려받기: 사유가 없으면 400
        MockMvc counter = TestUsers.mvcAs(ctx, TestUsers.counter());
        counter.perform(get("/civil/certs.csv")).andExpect(status().isBadRequest());
        counter.perform(get("/civil/certs.csv").param("reason", "월말 보고")).andExpect(status().isOk());
    }

    @Test
    void 권한이_바뀌면_본인과_관리자에게_알리고_화면에는_워터마크와_보안_헤더가_있다() throws Exception {
        LoginUser u = staff("권한");
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/users/" + u.getUserId() + "/welfare").param("value", "true"));
        assertThat(notifications.recent(u.getLoginId(), 5).stream().map(n -> n.getMessage()).toList().toString()).contains("권한이 바뀌었습니다");
        MvcResult r = TestUsers.mvcAs(ctx, u).perform(get("/")).andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin")).andReturn();
        String html = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("class=\"watermark\"").contains("data:image/svg+xml");
        assertThat(SecurityService.watermarkSvg("<b>&")).doesNotContain("<b>").contains("%26amp%3B");
    }
}
