package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;

@SpringBootTest
class BoardViewCommentTest {

    @Autowired
    WebApplicationContext ctx;

    /** 다른 부서(세무과) 일반 직원 */
    private static LoginUser otherDept() {
        LoginUser u = TestUsers.of(LoginUser.Role.GENERAL, "taxman", "세무직원");
        u.setDeptName("세무과");
        return u;
    }

    private static String html(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static long idOf(String html, String title) {
        Matcher m = Pattern.compile("/board/(\\d+)\"").matcher(html.substring(Math.max(0, html.indexOf(title) - 300)));
        assertThat(m.find()).as("글 링크").isTrue();
        return Long.parseLong(m.group(1));
    }

    private long write(MockMvc mvc, String board, String scope, String title, String content) throws Exception {
        mvc.perform(post("/board/write").param("board", board).param("scope", scope)
                .param("title", title).param("content", content));
        return idOf(html(mvc, "/board?tab=" + board), title);
    }

    @Test
    void 상세_화면에서_내용을_읽고_조회수는_한_번만_센다() throws Exception {
        MockMvc mvc = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "상세 " + UUID.randomUUID();
        long id = write(mvc, "suggest", "전체 공개", title, "첫 줄\n<script>alert(1)</script>");

        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/board/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("첫 줄")))
                // 본문은 HTML로 해석하지 않고 글자로 보여준다
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("조회 1")));
        mvc.perform(get("/board/" + id).session(session)).andExpect(content().string(containsString("조회 1")));
        mvc.perform(get("/board/" + id)).andExpect(content().string(containsString("조회 2")));
    }

    @Test
    void 댓글을_달고_지운다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "댓글 " + UUID.randomUUID();
        long id = write(author, "suggest", "전체 공개", title, "본문");

        MockMvc other = TestUsers.mvcAs(ctx, TestUsers.general());
        other.perform(post("/board/" + id + "/comments").param("content", "좋은 의견입니다"))
                .andExpect(redirectedUrl("/board/" + id + "#comments"))
                .andExpect(flash().attribute("message", "댓글을 달았습니다."));

        String page = html(author, "/board/" + id);
        assertThat(page).contains("좋은 의견입니다").contains("댓글 1");
        assertThat(html(author, "/board?tab=suggest")).contains("[1]");

        Matcher m = Pattern.compile("/comments/(\\d+)/delete").matcher(html(other, "/board/" + id));
        assertThat(m.find()).as("댓글 쓴 사람에게 삭제 버튼").isTrue();
        String cid = m.group(1);
        // 글쓴이라도 남의 댓글은 못 지운다 (관리자는 가능)
        author.perform(post("/board/" + id + "/comments/" + cid + "/delete")).andExpect(status().isForbidden());
        other.perform(post("/board/" + id + "/comments/" + cid + "/delete"))
                .andExpect(flash().attribute("message", "댓글을 삭제했습니다."));
        assertThat(html(author, "/board/" + id)).doesNotContain("좋은 의견입니다");
    }

    @Test
    void 빈_댓글은_받지_않는다() throws Exception {
        MockMvc mvc = TestUsers.mvcAs(ctx, TestUsers.counter());
        long id = write(mvc, "suggest", "전체 공개", "빈댓글 " + UUID.randomUUID(), "본문");
        mvc.perform(post("/board/" + id + "/comments").param("content", "   "))
                .andExpect(flash().attribute("message", "댓글은 1~1000자로 입력하세요."));
    }

    @Test
    void 우리_부서_글은_다른_부서에서_안_보인다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());   // 민원봉사과
        String title = "부서한정 " + UUID.randomUUID();
        author.perform(multipart("/board/upload")
                .file(new MockMultipartFile("files", "secret.pdf", "application/pdf", "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .param("scope", "우리 부서").param("title", title));
        String list = html(author, "/board?tab=archive");
        long id = idOf(list, title);
        Matcher fm = Pattern.compile("/board/files/(\\d+)").matcher(list.substring(list.indexOf(title)));
        assertThat(fm.find()).isTrue();

        MockMvc sameDept = TestUsers.mvcAs(ctx, TestUsers.general());  // 민원봉사과
        assertThat(html(sameDept, "/board?tab=archive")).contains(title);
        sameDept.perform(get("/board/" + id)).andExpect(status().isOk());

        MockMvc other = TestUsers.mvcAs(ctx, otherDept());
        assertThat(html(other, "/board?tab=archive")).doesNotContain(title);
        other.perform(get("/board/" + id)).andExpect(status().isForbidden());
        other.perform(get("/board/files/" + fm.group(1))).andExpect(status().isNotFound());
        other.perform(post("/board/" + id + "/comments").param("content", "몰래")).andExpect(status().isForbidden());

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(get("/board/" + id)).andExpect(status().isOk());
    }

    @Test
    void 목록은_20건씩_나눈다() throws Exception {
        MockMvc mvc = TestUsers.mvcAs(ctx, TestUsers.counter());
        String tag = "PG" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 21; i++) {
            mvc.perform(post("/board/write").param("board", "suggest").param("scope", "전체 공개")
                    .param("title", tag + "-" + i).param("content", "x"));
        }
        String p1 = html(mvc, "/board?tab=suggest&q=" + tag);
        assertThat(p1).contains("전체 <b style=\"color:var(--text)\">21</b>건").contains(tag + "-21").doesNotContain(tag + "-1<");
        assertThat(p1).contains("page=2");
        mvc.perform(get("/board").param("tab", "suggest").param("q", tag).param("page", "2"))
                .andExpect(content().string(containsString(tag + "-1<")))
                .andExpect(content().string(not(containsString(tag + "-21"))));
    }
}
