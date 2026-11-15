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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BoardDeleteTest {

    @Autowired
    WebApplicationContext ctx;

    /** counter1 이 글을 쓰고 글 번호를 돌려준다 */
    private long writeAsCounter(MockMvc author, String title) throws Exception {
        author.perform(post("/board/write")
                        .param("board", "suggest").param("scope", "전체 공개")
                        .param("title", title).param("content", "본문"))
                .andExpect(redirectedUrl("/board?tab=suggest"));
        String html = author.perform(get("/board").param("tab", "suggest"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("/board/(\\d+)/delete").matcher(html.substring(html.indexOf(title)));
        assertThat(m.find()).as("글쓴이에게 삭제 버튼이 보여야 한다").isTrue();
        return Long.parseLong(m.group(1));
    }

    @Test
    void 글쓴이는_자기_글을_지울_수_있다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "지울 글 " + UUID.randomUUID();
        long id = writeAsCounter(author, title);

        author.perform(post("/board/" + id + "/delete").param("tab", "suggest"))
                .andExpect(redirectedUrl("/board?tab=suggest"))
                .andExpect(flash().attribute("message", "게시물을 삭제했습니다."));
        author.perform(get("/board").param("tab", "suggest"))
                .andExpect(content().string(not(containsString(title))));

        // 두 번 지우면 '없는 게시물'
        author.perform(post("/board/" + id + "/delete").param("tab", "suggest"))
                .andExpect(flash().attribute("message", "이미 삭제되었거나 없는 게시물입니다."));
    }

    @Test
    void 다른_직원은_지울_수_없고_버튼도_안_보인다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "남의 글 " + UUID.randomUUID();
        long id = writeAsCounter(author, title);

        MockMvc other = TestUsers.mvcAs(ctx, TestUsers.general());
        String html = other.perform(get("/board").param("tab", "suggest"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains(title).doesNotContain("/board/" + id + "/delete");

        other.perform(post("/board/" + id + "/delete")).andExpect(status().isForbidden());
        author.perform(get("/board").param("tab", "suggest")).andExpect(content().string(containsString(title)));
    }

    @Test
    void 관리자는_누구의_글이든_지울_수_있다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "관리자가 지울 글 " + UUID.randomUUID();
        long id = writeAsCounter(author, title);

        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/board/" + id + "/delete").param("tab", "suggest"))
                .andExpect(redirectedUrl("/board?tab=suggest"));
        admin.perform(get("/board").param("tab", "suggest")).andExpect(content().string(not(containsString(title))));
    }

    @Test
    void 지운_자료의_파일은_내려받을_수_없다() throws Exception {
        MockMvc author = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "지울 자료 " + UUID.randomUUID();
        author.perform(multipart("/board/upload")
                        .file(new MockMultipartFile("files", "a.pdf", "application/pdf", "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        .param("scope", "전체 공개").param("title", title))
                .andExpect(redirectedUrl("/board?tab=archive"));
        String html = author.perform(get("/board").param("tab", "archive"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String row = html.substring(html.indexOf(title));
        Matcher fileM = Pattern.compile("/board/files/(\\d+)").matcher(row);
        Matcher delM = Pattern.compile("/board/(\\d+)/delete").matcher(row);
        assertThat(fileM.find() && delM.find()).isTrue();

        author.perform(get("/board/files/" + fileM.group(1))).andExpect(status().isOk());
        author.perform(post("/board/" + delM.group(1) + "/delete").param("tab", "archive"));
        author.perform(get("/board/files/" + fileM.group(1))).andExpect(status().isNotFound());
    }

    @Test
    void 관리자_메뉴는_관리자에게만_보인다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.admin()).perform(get("/"))
                .andExpect(content().string(containsString("계정 관리")));
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/"))
                .andExpect(content().string(not(containsString(">계정 관리<"))));
    }
}
