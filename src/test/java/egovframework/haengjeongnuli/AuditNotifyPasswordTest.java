package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditSearchVO;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class AuditNotifyPasswordTest {

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    UserService userService;

    @Autowired
    AuditService auditService;

    private MockMvc anon() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String newUser(String password) {
        String id = "p" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("비번직원");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword(password);
        userService.create(f);
        return id;
    }

    @Test
    void 처음_받은_비밀번호는_첫_로그인에_바꿔야_한다() throws Exception {
        MockMvc mvc = anon();
        String id = newUser("First-pass-12!");
        MockHttpSession session = (MockHttpSession) mvc
                .perform(formLogin("/login").userParameter("loginId").user(id).password("First-pass-12!"))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/board").session(session)).andExpect(redirectedUrl("/mypage/password?expired"));
        mvc.perform(get("/mypage/password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("처음 받은 비밀번호입니다")));

        mvc.perform(post("/mypage/password").session(session).with(csrf())
                        .param("currentPassword", "First-pass-12!")
                        .param("newPassword", "Second-pass-34!").param("confirmPassword", "Second-pass-34!"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/board").session(session)).andExpect(status().isOk());

        AuditSearchVO s = new AuditSearchVO();
        s.setActorId(id);
        assertThat(auditService.search(s)).extracting(l -> l.getAction())
                .contains(AuditAction.LOGIN_SUCCESS, AuditAction.PASSWORD_CHANGE);
    }

    @Test
    void 기한이_지난_비밀번호도_바꾸게_한다() throws Exception {
        LoginUser old = TestUsers.of(LoginUser.Role.GENERAL, "olduser", "오래된비번");
        old.setPasswordChangedAt(LocalDateTime.now().minusDays(91));
        TestUsers.mvcAs(ctx, old).perform(get("/")).andExpect(redirectedUrl("/mypage/password?expired"));

        LoginUser fresh = TestUsers.of(LoginUser.Role.GENERAL, "freshuser", "새비번");
        fresh.setPasswordChangedAt(LocalDateTime.now().minusDays(89));
        TestUsers.mvcAs(ctx, fresh).perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void 로그인_실패도_기록된다() throws Exception {
        String id = newUser("Right-pass-56!");
        anon().perform(formLogin("/login").userParameter("loginId").user(id).password("wrong"));
        AuditSearchVO s = new AuditSearchVO();
        s.setActorId(id);
        s.setAction(AuditAction.LOGIN_FAIL);
        assertThat(auditService.count(s)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 내_글에_댓글이_달리면_알림이_온다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "알림글 " + UUID.randomUUID();
        author.perform(post("/board/write").param("board", "suggest").param("scope", "전체 공개")
                .param("title", title).param("content", "본문"));
        String list = author.perform(get("/board").param("tab", "suggest"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("/board/(\\d+)\"").matcher(list.substring(list.indexOf(title) - 300));
        assertThat(m.find()).isTrue();
        String postId = m.group(1);

        TestUsers.mvcAs(ctx, TestUsers.general())
                .perform(post("/board/" + postId + "/comments").param("content", "확인했습니다"));

        String page = author.perform(get("/notifications"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("일반직원님이 &#39;" + title + "&#39;에 댓글을 달았습니다.");
        Matcher nm = Pattern.compile("/notifications/(\\d+)\"").matcher(page.substring(page.indexOf(title) - 400));
        assertThat(nm.find()).isTrue();

        // 다른 사람은 내 알림을 열 수 없다
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/notifications/" + nm.group(1)))
                .andExpect(redirectedUrl("/notifications"));
        author.perform(get("/notifications/" + nm.group(1)))
                .andExpect(redirectedUrl("/board/" + postId + "#comments"));
    }

    @Test
    void 작업_기록_화면은_관리자만_보고_게시글_등록이_남는다() throws Exception {
        String title = "기록글 " + UUID.randomUUID();
        TestUsers.mvcAs(ctx, TestUsers.counter()).perform(post("/board/write").param("board", "suggest")
                .param("scope", "전체 공개").param("title", title).param("content", "x"));

        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/audit")).andExpect(status().isForbidden());
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(get("/admin/audit").param("action", "POST_CREATE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("counter1")));
    }
}
