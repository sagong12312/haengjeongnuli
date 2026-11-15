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

@SpringBootTest
class SearchTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;

    private MockMvc staff(String name, LoginUser[] out) {
        String id = "s" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        userService.create(f);
        LoginUser u = userService.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        out[0] = u;
        return TestUsers.mvcAs(ctx, u);
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 볼_수_있는_것만_찾는다() throws Exception {
        LoginUser[] a = new LoginUser[1];
        LoginUser[] b = new LoginUser[1];
        LoginUser[] c = new LoginUser[1];
        MockMvc drafter = staff("기안자", a);
        MockMvc approver = staff("결재자", b);
        MockMvc outsider = staff("남", c);
        String key = "검색어" + UUID.randomUUID().toString().substring(0, 6);

        drafter.perform(post("/approval/draft").param("action", "send").param("title", key + " 보고")
                .param("body", "본문").param("finalApprover", b[0].getLoginId()));
        drafter.perform(post("/board/write").param("board", "suggest").param("scope", "전체 공개")
                .param("title", key + " 건의").param("content", "x"));

        assertThat(body(drafter, "/search?q=" + key)).contains(key + " 보고").contains(key + " 건의");
        assertThat(body(approver, "/search?q=" + key)).contains(key + " 보고");
        // 결재선에 없는 직원에게는 결재 문서가 나오지 않는다 (게시글은 전체 공개라 보인다)
        assertThat(body(outsider, "/search?q=" + key)).doesNotContain(key + " 보고").contains(key + " 건의");
        // 메뉴도 찾는다
        assertThat(body(outsider, "/search?q=휴가")).contains("휴가 신청");
        assertThat(body(outsider, "/search?q=없는말" + UUID.randomUUID())).contains("결과가 없습니다");
    }
}
