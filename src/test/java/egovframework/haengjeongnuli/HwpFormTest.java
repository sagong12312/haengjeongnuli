package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.hwp.HwpFormFillerTest;
import egovframework.haengjeongnuli.cmm.hwp.HwpFormService;
import egovframework.haengjeongnuli.cmm.hwp.HwpFormVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

/** 한글 서식함: 올리기, 누름틀 확인, 영업소·직원 정보로 채워 만들기, 법정 증명서 막기 */
@SpringBootTest
class HwpFormTest {

    @Autowired WebApplicationContext ctx;
    @Autowired HwpFormService forms;
    @Autowired HygieneService hygiene;
    @Autowired UserService users;

    private static String text(byte[] hwp) throws Exception {
        return TextExtractor.extract(HWPReader.fromInputStream(new ByteArrayInputStream(hwp)), TextExtractMethod.OnlyMainParagraph);
    }

    @Test
    void 서식을_올리고_영업소_정보로_채워_만든다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        String name = "시정명령서" + UUID.randomUUID().toString().substring(0, 4);
        byte[] tpl = HwpFormFillerTest.template();

        staff.perform(multipart("/admin/forms").file(new MockMultipartFile("files", "a.hwp", "application/octet-stream", tpl)))
                .andExpect(status().isForbidden());
        admin.perform(multipart("/admin/forms").file(new MockMultipartFile("files", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(flash().attribute("message", containsString("한글 파일(.hwp, .hwpx)만")));
        admin.perform(multipart("/admin/forms").file(new MockMultipartFile("files", name + "_템플릿.hwp", "application/octet-stream", tpl))
                        .param("category", "위생"))
                .andExpect(flash().attribute("message", containsString("누름틀 2개: 업소명, 대표자")));
        // 법정 증명서 서식은 채워 만들 수 없다
        admin.perform(multipart("/admin/forms").file(new MockMultipartFile("files", "기본증명서_일반_템플릿.hwp", "application/octet-stream", tpl)))
                .andExpect(flash().attribute("message", containsString("법정 증명서 서식")));
        HwpFormVO f = forms.list(true).stream().filter(x -> x.getName().equals(name + " 템플릿")).findFirst().orElseThrow();
        HwpFormVO cert = forms.list(true).stream().filter(x -> x.getName().startsWith("기본증명서")).reduce((a, b) -> b).orElseThrow();
        assertThat(f.isFillable()).isTrue();
        assertThat(cert.isFillable()).isFalse();
        assertThat(cert.getCategory()).isEqualTo("증명");
        staff.perform(get("/forms/" + cert.getFormId() + "/fill")).andExpect(status().isNotFound());

        // 영업소 정보로 채우기
        long biz = hygiene.register("행복식당" + name, "김대표", "중앙로 1", "일반음식점영업", java.time.LocalDate.now(), null,
                TestUsers.admin()).id();
        String page = staff.perform(get("/forms/" + f.getFormId() + "/fill").param("source", "hygiene").param("sourceId", "" + biz))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("행복식당" + name).contains("김대표");
        byte[] out = staff.perform(post("/forms/" + f.getFormId() + "/fill").param("names", "업소명", "대표자", "없는칸")
                        .param("values", "행복식당" + name, "김대표", "무시").param("source", "hygiene").param("sourceId", "" + biz))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(text(out)).contains("업소명: 행복식당" + name).contains("대표: 김대표");
        assertThat(body(staff, "/forms")).contains(name).contains("법정 증명서");
    }

    @Test
    void 직원_정보는_본인것만_관리자는_모든_직원것을_채운다() throws Exception {
        String id = "f" + UUID.randomUUID().toString().substring(0, 8);
        UserForm uf = new UserForm();
        uf.setLoginId(id);
        uf.setUserName("서식직원");
        uf.setDeptName("총무과");
        uf.setPositionName("주무관");
        uf.setRole(LoginUser.Role.GENERAL);
        uf.setPassword("Form-pass-777!");
        users.create(uf);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc mine = TestUsers.mvcAs(ctx, u);
        LoginUser other = (LoginUser) users.loadUserByUsername("general1");

        assertThat(forms.values("staff", null, u)).containsEntry("직원성명", "서식직원").containsEntry("직원직급", "주무관")
                .containsEntry("직원부서", "총무과");
        assertThat(forms.values("staff", other.getUserId(), u)).isNull(); // 남의 정보는 못 본다
        assertThat(forms.values("staff", u.getUserId(), TestUsers.admin())).containsEntry("직원성명", "서식직원");
        assertThat(HwpFormService.KNOWN).containsKeys("직원성명", "임용일", "근속기간");
        HwpFormVO f = forms.list(true).stream().filter(HwpFormVO::isFillable).findFirst().orElse(null);
        if (f != null) {
            mine.perform(get("/forms/" + f.getFormId() + "/fill").param("source", "staff").param("sourceId", "" + other.getUserId()))
                    .andExpect(status().isNotFound());
        }
    }

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
