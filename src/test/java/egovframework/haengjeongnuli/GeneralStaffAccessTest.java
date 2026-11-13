package egovframework.haengjeongnuli;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

@DisplayName("일반 직원은 민원 접수를 할 수 없다")
@SpringBootTest
class GeneralStaffAccessTest {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestUsers.mvcAs(ctx, TestUsers.general());
    }

    @Test
    void 민원_접수_화면은_막힌다() throws Exception {
        mvc.perform(get("/civil/new")).andExpect(status().isForbidden())
                .andExpect(content().string(containsString("창구 직원만")));
    }

    @Test
    void 민원_접수_요청도_서버에서_막힌다() throws Exception {
        mvc.perform(post("/civil/new")).andExpect(status().isForbidden());
    }

    @Test
    void 민원_목록에_접수_버튼이_없다() throws Exception {
        mvc.perform(get("/civil")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/civil/new\""))));
    }
}
