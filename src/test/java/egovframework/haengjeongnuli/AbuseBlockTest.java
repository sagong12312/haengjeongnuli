package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.security.AbuseGuard;

/** 이상한 요청을 보낸 주소 잠시 차단과 이의 신청 */
@SpringBootTest
class AbuseBlockTest {

    @Autowired WebApplicationContext ctx;
    @Autowired AbuseGuard guard;

    private MockMvc anon() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    @Test
    void 공격_문구() {
        assertThat(AbuseGuard.attackIn("/board?q=1' OR '1'='1")).isNotNull();
        assertThat(AbuseGuard.attackIn("x UNION SELECT password FROM users")).isNotNull();
        assertThat(AbuseGuard.attackIn("<script>alert(1)</script>")).isNotNull();
        assertThat(AbuseGuard.attackIn("/files/../../etc/passwd")).isNotNull();
        assertThat(AbuseGuard.attackIn("2026년 하반기 예산 집행 계획 (1차)")).isNull();
        assertThat(AbuseGuard.isProbe("/wp-admin/setup.php")).isTrue();
        assertThat(AbuseGuard.isProbe("/approval/draft")).isFalse();
    }

    @Test
    void 이상한_요청이_쌓이면_막고_이의를_신청하면_관리자가_푼다() throws Exception {
        MockMvc mvc = anon();
        String ip = "192.168.30.7";
        mvc.perform(get("/login").param("q", "1' or '1'='1").with(from(ip))).andExpect(status().isOk());
        mvc.perform(get("/.env").with(from(ip)));
        assertThat(guard.isBlocked(ip)).isFalse();
        mvc.perform(get("/wp-admin/install.php").with(from(ip)));
        assertThat(guard.isBlocked(ip)).isTrue();

        // 막힌 주소는 어디로 가든 차단 안내로
        mvc.perform(get("/login").with(from(ip))).andExpect(redirectedUrl("/blocked"));
        mvc.perform(post("/login").with(csrf()).with(from(ip)).param("loginId", "admin").param("password", "x"))
                .andExpect(redirectedUrl("/blocked"));
        String page = mvc.perform(get("/blocked").with(from(ip))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("접속을 잠시 막았습니다").contains("탐색 주소").contains("이의 신청");
        // 다른 주소는 그대로
        mvc.perform(get("/login").with(from("192.168.30.8"))).andExpect(status().isOk());

        mvc.perform(post("/blocked/appeal").with(csrf()).with(from(ip)).param("name", "홍길동").param("message", ""))
                .andExpect(flash().attribute("message", containsString("이름과 내용")));
        mvc.perform(post("/blocked/appeal").with(csrf()).with(from(ip)).param("name", "홍길동").param("contact", "내선 1234")
                        .param("message", "자료실에서 파일을 찾다가 주소를 잘못 입력했습니다."))
                .andExpect(flash().attribute("message", containsString("이의 신청을 받았습니다")));
        mvc.perform(post("/blocked/appeal").with(csrf()).with(from(ip)).param("name", "홍길동").param("message", "또"))
                .andExpect(flash().attribute("message", containsString("이미 이의를 신청했습니다")));

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String list = admin.perform(get("/admin/blocks")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(list).contains(ip).contains("자료실에서 파일을 찾다가");
        long appealId = Long.parseLong(list.replaceAll("(?s).*?/admin/blocks/appeals/(\\d+).*", "$1"));
        admin.perform(post("/admin/blocks/appeals/" + appealId).param("accept", "false"))
                .andExpect(flash().attribute("message", "기각 사유를 적으세요."));
        admin.perform(post("/admin/blocks/appeals/" + appealId).param("accept", "true"))
                .andExpect(flash().attribute("message", containsString("차단을 풀었습니다")));
        assertThat(guard.isBlocked(ip)).isFalse();
        mvc.perform(get("/login").with(from(ip))).andExpect(status().isOk());
    }

    @Test
    void 요청_위조와_권한_없는_접근도_점수가_쌓인다() throws Exception {
        MockMvc mvc = anon();
        String ip = "192.168.31.9";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/login").with(from(ip)).param("loginId", "a").param("password", "b")).andExpect(status().isForbidden());
        }
        assertThat(guard.isBlocked(ip)).isTrue();
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(post("/admin/blocks/release").param("ip", ip))
                .andExpect(flash().attribute("message", ip + " 차단을 풀었습니다."));
        assertThat(guard.isBlocked(ip)).isFalse();
    }

    @Test
    void 서버_자신은_막지_않는다() throws Exception {
        MockMvc mvc = anon();
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/wp-login.php"));
        }
        assertThat(guard.isBlocked("127.0.0.1")).isFalse();
        mvc.perform(get("/login")).andExpect(content().string(containsString("로그인")));
    }
}
