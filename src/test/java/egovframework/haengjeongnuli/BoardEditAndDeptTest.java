package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class BoardEditAndDeptTest {

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    UserService userService;

    private static String row(String html, String title) {
        return html.substring(html.indexOf(title));
    }

    private static String first(String regex, String text) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as(regex).isTrue();
        return m.group(1);
    }

    private long write(MockMvc author, String board, String title) throws Exception {
        author.perform(post("/board/write").param("board", board).param("scope", "전체 공개")
                .param("title", title).param("content", "처음 내용"));
        String html = author.perform(get("/board").param("tab", board))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Long.parseLong(first("/board/(\\d+)/edit", row(html, title)));
    }

    @Test
    void 글쓴이는_글을_고칠_수_있다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "고칠 글 " + UUID.randomUUID();
        long id = write(author, "suggest", title);

        author.perform(get("/board/" + id + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("처음 내용")));

        String newTitle = "고친 제목 " + UUID.randomUUID();
        author.perform(post("/board/" + id + "/edit").param("scope", "우리 부서")
                        .param("title", newTitle).param("content", "고친 내용")
                        .param("board", "notice"))   // 게시판 종류는 바꿀 수 없어야 한다
                .andExpect(redirectedUrl("/board/" + id));

        author.perform(get("/board").param("tab", "suggest"))
                .andExpect(content().string(containsString(newTitle)))
                .andExpect(content().string(containsString("(수정됨)")))
                .andExpect(content().string(not(containsString(title))));
    }

    @Test
    void 다른_직원은_고칠_수_없다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "남의 글 " + UUID.randomUUID();
        long id = write(author, "suggest", title);

        MockMvc other = TestUsers.mvcAs(ctx, TestUsers.general());
        other.perform(get("/board/" + id + "/edit")).andExpect(status().isForbidden());
        other.perform(post("/board/" + id + "/edit").param("scope", "전체 공개")
                .param("title", "몰래 고침").param("content", "x")).andExpect(status().isForbidden());

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/board/" + id + "/edit").param("scope", "전체 공개")
                .param("title", "관리자가 고침 " + id).param("content", "x"))
                .andExpect(redirectedUrl("/board/" + id));
    }

    @Test
    void 자료실_파일을_바꾸고_마지막_파일은_못_뺀다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "자료 " + UUID.randomUUID();
        author.perform(multipart("/board/upload")
                .file(new MockMultipartFile("files", "old.pdf", "application/pdf", "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .param("scope", "전체 공개").param("title", title));
        String html = author.perform(get("/board").param("tab", "archive"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String r = row(html, title);
        long id = Long.parseLong(first("/board/(\\d+)/edit", r));
        String oldFileId = first("/board/files/(\\d+)", r);

        // 하나뿐인 파일을 빼기만 하면 거부
        author.perform(multipart("/board/" + id + "/edit").param("scope", "전체 공개").param("title", title)
                        .param("removeFileIds", oldFileId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("파일이 하나 이상")));

        // 빼면서 새 파일을 더하면 된다
        author.perform(multipart("/board/" + id + "/edit")
                        .file(new MockMultipartFile("files", "new.pdf", "application/pdf", "%PDF-1.7 new".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        .param("scope", "전체 공개").param("title", title).param("removeFileIds", oldFileId))
                .andExpect(redirectedUrl("/board/" + id));

        author.perform(get("/board/files/" + oldFileId)).andExpect(status().isNotFound());
        author.perform(get("/board").param("tab", "archive"))
                .andExpect(content().string(containsString("new.pdf")))
                .andExpect(content().string(not(containsString("old.pdf"))));
    }

    @Test
    void 관리자는_부서를_옮기고_기록이_남는다() throws Exception {
        String loginId = "mv" + UUID.randomUUID().toString().substring(0, 6);
        UserForm f = new UserForm();
        f.setLoginId(loginId);
        f.setUserName("이동직원");
        f.setDeptName("민원봉사과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Moving-pass-7!");
        LoginUser u = userService.create(f);

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/users/" + u.getUserId() + "/dept").param("deptName", "세무과"))
                .andExpect(redirectedUrl("/admin/users"));

        LoginUser after = (LoginUser) userService.loadUserByUsername(loginId);
        assertThat(after.getDeptName()).isEqualTo("세무과");
        assertThat(userService.recentDeptMoves(50))
                .anySatisfy(m -> {
                    assertThat(m.getLoginId()).isEqualTo(loginId);
                    assertThat(m.getFromDept()).isEqualTo("민원봉사과");
                    assertThat(m.getToDept()).isEqualTo("세무과");
                    assertThat(m.getMovedBy()).isEqualTo("admin-t");
                });
        admin.perform(get("/admin/users")).andExpect(content().string(containsString("최근 부서 이동")));
    }

    @Test
    void 일반_직원은_부서를_옮길_수_없다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.general())
                .perform(post("/admin/users/1/dept").param("deptName", "아무데나"))
                .andExpect(status().isForbidden());
    }
}
