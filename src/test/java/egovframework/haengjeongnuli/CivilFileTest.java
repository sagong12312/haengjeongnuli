package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** 민원 구비서류 */
@SpringBootTest
class CivilFileTest {

    @Autowired WebApplicationContext ctx;

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    void 접수할_때_구비서류를_저장하고_볼_수_있는_직원만_내려받는다() throws Exception {
        MockMvc counter = TestUsers.mvcAs(ctx, TestUsers.counter());
        String title = "구비서류 민원 " + UUID.randomUUID();
        String url = counter.perform(multipart("/civil/new")
                        .file(new MockMultipartFile("files", "신청서.pdf", "application/pdf", PDF))
                        .param("kind", "OTHER").param("route", "방문").param("name", "김민원").param("mobile", "010-1111-2222")
                        .param("notify", "문자").param("title", title).param("content", "내용").param("privacyConsent", "true"))
                .andExpect(flash().attribute("message", containsString("구비서류 1개 저장")))
                .andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.replaceAll("\\D", ""));

        String page = counter.perform(get("/civil/" + id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("신청서.pdf");
        Matcher m = Pattern.compile("/civil/files/(\\d+)").matcher(page);
        assertThat(m.find()).isTrue();
        counter.perform(get("/civil/files/" + m.group(1))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment"))).andExpect(content().bytes(PDF));

        // 상세를 볼 수 없는 직원(다른 부서 일반 직원)은 내려받을 수 없다
        LoginUser other = TestUsers.of(LoginUser.Role.GENERAL, "other-c", "남");
        other.setDeptName("다른과");
        TestUsers.mvcAs(ctx, other).perform(get("/civil/files/" + m.group(1))).andExpect(status().isNotFound());

        // 처리 담당이 나중에 추가할 수 있고, 형식이 맞지 않으면 거부
        counter.perform(multipart("/civil/" + id + "/files").file(new MockMultipartFile("files", "위장.pdf", "application/pdf", new byte[] {1, 2})))
                .andExpect(flash().attribute("message", containsString("맞지 않아")));
        counter.perform(multipart("/civil/" + id + "/files").file(new MockMultipartFile("files", "보완서류.pdf", "application/pdf", PDF)))
                .andExpect(flash().attribute("message", "구비서류 1개를 추가했습니다."));
    }

    @Test
    void 형식이_맞지_않는_구비서류가_있으면_접수하지_않는다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.counter()).perform(multipart("/civil/new")
                        .file(new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[] {1}))
                        .param("kind", "OTHER").param("route", "방문").param("name", "박민원").param("mobile", "010-1111-2222")
                        .param("notify", "문자").param("title", "거부").param("content", "내용").param("privacyConsent", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("올릴 수 없는 형식입니다")));
    }
}
