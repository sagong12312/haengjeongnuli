package egovframework.haengjeongnuli;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
class PagesTest {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestUsers.mvcAs(ctx, TestUsers.counter());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/approval", "/approval?box=back", "/approval/draft", "/civil", "/civil?kind=QUERY", "/civil?status=DONE&q=x",
            "/civil/new", "/finance", "/finance/programs", "/search", "/search?q=민원", "/building", "/hygiene", "/stats", "/hr/duty", "/hr/duty/new", "/hr/duty/new?kind=OVERTIME", "/hr", "/board", "/board?tab=archive",
            "/board/write", "/board/upload"})
    void 모든_화면이_열린다(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("행정누리")));
    }

    @Test
    void 자료실_탭에서는_자료_올리기_버튼() throws Exception {
        mvc.perform(get("/board?tab=archive")).andExpect(content().string(containsString("자료 올리기")));
    }

    @Test
    void 질의민원_처리기한_계산() throws Exception {
        mvc.perform(get("/civil/api/deadline").param("kind", "QUERY").param("law", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7일"));
    }

    @Test
    void 외부_주소로는_돌아가지_않는다() throws Exception {
        mvc.perform(post("/attendance").header("Referer", "https://evil.example.com//x"))
                .andExpect(redirectedUrl("/"));
    }
}
