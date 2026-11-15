package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.org.OrgService;

/** 자동 로그아웃 시간 */
@SpringBootTest
class AutoLogoutTest {

    @Autowired WebApplicationContext ctx;
    @Autowired OrgService org;

    @Test
    void 관리자가_정한_시간이_세션과_머리글에_적용된다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        try {
            admin.perform(post("/admin/org/session").param("minutes", "3"))
                    .andExpect(flash().attribute("message", "자동 로그아웃 시간은 5~120분 사이로 정하세요."));
            admin.perform(post("/admin/org/session").param("minutes", "10"))
                    .andExpect(flash().attribute("message", "자동 로그아웃 시간을 10분으로 정했습니다."));
            assertThat(org.sessionTimeoutMinutes()).isEqualTo(10);
            TestUsers.mvcAs(ctx, TestUsers.general()).perform(post("/admin/org/session").param("minutes", "60"))
                    .andExpect(status().isForbidden());

            MockHttpSession session = new MockHttpSession();
            MockMvc user = TestUsers.mvcAs(ctx, TestUsers.general());
            user.perform(get("/").session(session))
                    .andExpect(content().string(containsString("data-timeout=\"600\"")))
                    .andExpect(content().string(containsString("로그아웃까지")));
            assertThat(session.getMaxInactiveInterval()).isEqualTo(600);
            user.perform(get("/session/ping").session(session)).andExpect(status().isNoContent());
        } finally {
            org.saveSessionTimeout("30", "test");
        }
    }

    @Test
    void 시간이_다_되면_로그인_화면에_이유를_보여준다() throws Exception {
        MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build()
                .perform(get("/login").param("error", "timeout"))
                .andExpect(content().string(containsString("자동으로 로그아웃되었습니다")));
        // 로그인하지 않은 사람은 연장 요청도 로그인 화면으로 보낸다
        MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build()
                .perform(get("/session/ping")).andExpect(status().is3xxRedirection());
    }
}
