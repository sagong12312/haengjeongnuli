package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.file.FileCheckService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 첨부파일 정합성 점검 */
@SpringBootTest
class FileCheckTest {

    @Autowired WebApplicationContext ctx;
    @Autowired FileCheckService check;
    @Autowired FileStorage storage;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 없는_파일과_남는_파일을_찾고_하루_지난_남는_파일만_지운다() throws Exception {
        // 게시판에 올린 파일의 실제 파일이 사라진 경우
        String title = "점검 자료 " + UUID.randomUUID();
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        TestUsers.mvcAs(ctx, TestUsers.counter()).perform(multipart("/board/upload")
                .file(new MockMultipartFile("files", "점검.pdf", "application/pdf", pdf))
                .param("category", "업무 서식").param("scope", "전체 공개").param("title", title));
        String stored = jdbc.queryForObject("SELECT F.STORED_NAME FROM ATTACH_FILE F JOIN BOARD_POST P ON P.POST_ID = F.POST_ID WHERE P.TITLE = ?",
                String.class, title);
        Files.delete(storage.path(stored));

        // DB에 없는 파일: 하루 지난 것과 방금 생긴 것
        String old = UUID.randomUUID().toString().replace("-", ""), fresh = UUID.randomUUID().toString().replace("-", "");
        Path po = storage.path(old), pf = storage.path(fresh);
        Files.writeString(po, "x");
        Files.writeString(pf, "y");
        Files.setLastModifiedTime(po, FileTime.fromMillis(System.currentTimeMillis() - 3L * 24 * 3600 * 1000));

        FileCheckService.Result r = check.check();
        assertThat(r.missing()).anySatisfy(m -> {
            assertThat(m.stored()).isEqualTo(stored);
            assertThat(m.source()).isEqualTo("게시판");
        });
        assertThat(r.orphans()).anySatisfy(o -> { assertThat(o.stored()).isEqualTo(old); assertThat(o.removable()).isTrue(); });
        assertThat(r.orphans()).anySatisfy(o -> { assertThat(o.stored()).isEqualTo(fresh); assertThat(o.removable()).isFalse(); });

        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/files")).andExpect(status().isForbidden());
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        assertThat(admin.perform(get("/admin/files")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(stored).contains("폴더에 파일이 없습니다").contains(old);
        admin.perform(post("/admin/files/orphans/delete"));
        assertThat(Files.exists(po)).isFalse();
        assertThat(Files.exists(pf)).isTrue();
        Files.deleteIfExists(pf);
    }
}
