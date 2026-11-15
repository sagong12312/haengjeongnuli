package egovframework.haengjeongnuli;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.cmm.LoginUser;

/** 게시글 공개 범위 값 검증 (보안 점검 권장 사항) */
@SpringBootTest
class BoardScopeTest {

    @Autowired WebApplicationContext ctx;

    @Test
    void 정해진_공개범위만_받는다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(post("/board/write").param("board", "suggest")
                        .param("scope", "모두").param("title", "조작된 공개 범위").param("content", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("공개 범위는")));
    }

    @Test
    void 알_수_없는_값은_공개로_보지_않는다() {
        BoardPostVO p = new BoardPostVO();
        p.setOpenScope("모두");
        p.setWriterId("someone");
        p.setWriterDept("총무과");
        LoginUser other = TestUsers.of(LoginUser.Role.GENERAL, "other1", "남");
        org.assertj.core.api.Assertions.assertThat(p.canView(other)).isFalse();
        p.setOpenScope("전체 공개");
        org.assertj.core.api.Assertions.assertThat(p.canView(other)).isTrue();
    }
}
