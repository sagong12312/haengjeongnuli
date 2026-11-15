package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.hwp.HwpMaker;
import egovframework.haengjeongnuli.cmm.hwp.HwpViewer;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 첨부 한글 문서 화면 보기, 결재 문서·시행문 한글 파일 만들기 */
@SpringBootTest
class HwpWebTest {

    @Autowired WebApplicationContext ctx;
    @Autowired HwpMaker maker;
    @Autowired HwpViewer viewer;
    @Autowired UserService users;

    private MockMvc staff(String dept) {
        String id = "w" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("한글직원");
        f.setPositionName("주무관");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Hwp-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return TestUsers.mvcAs(ctx, u);
    }

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 게시판에_올린_HWP를_화면에서_보고_볼_수_없는_사람은_못_본다() throws Exception {
        String dept = "한글" + UUID.randomUUID().toString().substring(0, 4);
        MockMvc writer = staff(dept), other = staff("남" + dept);
        byte[] hwp = maker.make(List.of(HwpMaker.Line.title("회의 결과 보고"), HwpMaker.Line.of("1. 일시: 11. 20. 14:00")));
        String title = "회의 자료 " + UUID.randomUUID();
        writer.perform(multipart("/board/upload").file(new MockMultipartFile("files", "회의결과.hwp", "application/x-hwp", hwp))
                .param("category", "업무 서식").param("scope", "우리 부서").param("title", title));
        String list = body(writer, "/board?tab=archive");
        Matcher m = Pattern.compile("/board/files/(\\d+)/view").matcher(list.substring(list.indexOf(title)));
        assertThat(m.find()).isTrue();
        String view = body(writer, "/board/files/" + m.group(1) + "/view");
        assertThat(view).contains("회의 결과 보고").contains("1. 일시: 11. 20. 14:00").contains("내려받기");
        other.perform(get("/board/files/" + m.group(1) + "/view")).andExpect(status().isNotFound());
        // 보기만 해서는 받은 횟수가 늘지 않는다
        assertThat(body(writer, "/board?tab=archive")).contains("받은 횟수 0");
    }

    @Test
    void 결재_문서와_시행문을_한글_파일로_만든다(@TempDir Path dir) throws Exception {
        String dept = "기안" + UUID.randomUUID().toString().substring(0, 4);
        MockMvc drafter = staff(dept), outsider = staff("남" + dept);
        String chiefId = "w" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(chiefId);
        f.setUserName("과장");
        f.setPositionName("과장");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Hwp-pass-77!");
        users.create(f);
        LoginUser chief = (LoginUser) users.loadUserByUsername(chiefId);
        chief.setPasswordChangedAt(LocalDateTime.now());

        String title = "한글 공문 " + UUID.randomUUID().toString().substring(0, 6);
        String url = drafter.perform(post("/approval/draft").param("action", "send").param("title", title)
                .param("body", "1. 관련: 계획\n  가. 기간: 12. 1.\n| 구분 | 금액 |\n|---|---|\n| 인건비 | 1,000 |")
                .param("receiverType", "external").param("receiver", "OO시 총무과, OO군 기획과").param("finalApprover", chiefId)
                .param("delegated", "true")).andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.replaceAll("\\D", ""));
        TestUsers.mvcAs(ctx, chief).perform(post("/approval/" + id + "/approve"));

        byte[] doc = drafter.perform(get("/approval/" + id + "/hwp"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(".hwp")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(doc[0]).isEqualTo((byte) 0xD0); // OLE2 (HWP 5.0)
        Path p = dir.resolve("doc.hwp");
        Files.write(p, doc);
        String text = viewer.render(p, "doc.hwp").html();
        assertThat(text).contains("제목  " + title).contains("가. 기간: 12. 1.").contains("인건비    1,000")
                .contains("전결 과장").contains("수신  OO시 총무과, OO군 기획과");

        byte[] sihaeng = drafter.perform(get("/approval/" + id + "/hwp").param("to", "1")).andReturn().getResponse().getContentAsByteArray();
        Files.write(p, sihaeng);
        assertThat(viewer.render(p, "doc.hwp").html()).contains("수신  OO군 기획과");
        drafter.perform(get("/approval/" + id + "/hwp").param("to", "9")).andExpect(status().isNotFound());
        outsider.perform(get("/approval/" + id + "/hwp")).andExpect(status().isNotFound());
        assertThat(body(drafter, "/approval/" + id)).contains("한글(HWP)로 내려받기");
    }
}
