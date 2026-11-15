package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.mock.web.MockMultipartFile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilForm;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.civil.service.LegalAffair;

@SpringBootTest
class DatabaseFlowTest {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestUsers.mvcAs(ctx, TestUsers.counter());
    }

    @Autowired
    CivilService civilService;

    @Test
    void 민원을_접수하면_목록에_나온다() throws Exception {
        String title = "가로등 고장 " + UUID.randomUUID();
        mvc.perform(post("/civil/new")
                        .param("kind", "SUGGESTION").param("route", "방문")
                        .param("name", "김민원").param("mobile", "010-1234-5678").param("notify", "문자")
                        .param("title", title).param("content", "고쳐 주세요")
                        .param("privacyConsent", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("/civil/*"))
                .andExpect(flash().attribute("message", startsWith("민원이 접수되었습니다. 접수번호: ")));

        mvc.perform(get("/civil").param("q", title))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("김민원")));
    }

    @Test
    void 동의가_없으면_접수되지_않는다() throws Exception {
        mvc.perform(post("/civil/new")
                        .param("kind", "OTHER").param("route", "방문")
                        .param("name", "이민원").param("mobile", "010-0000-0000").param("notify", "문자")
                        .param("title", "제목").param("content", "내용"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("개인정보 수집·이용 동의를 확인하세요.")));
    }

    @Test
    void 휴대전화가_없으면_문자_통지는_거부된다() throws Exception {
        mvc.perform(post("/civil/new")
                        .param("kind", "OTHER").param("route", "방문")
                        .param("name", "박민원").param("noMobile", "true").param("noHome", "true")
                        .param("notify", "문자").param("title", "제목").param("content", "내용")
                        .param("privacyConsent", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("휴대전화가 없으면 문자로 통지할 수 없습니다.")));
    }

    @Test
    void 서비스_저장값_확인() {
        CivilForm form = new CivilForm();
        form.setKind(CivilKind.LEGAL);
        form.setAffair(LegalAffair.SEAL);
        form.setName("최민원");
        form.setNoMobile(true);
        form.setHomePhone("02-123-4567");
        form.setNotify("우편");
        form.setTitle("인감 " + UUID.randomUUID());
        form.setContent("발급 요청");
        form.setStaff("담당자A");
        form.setPrivacyConsent(true);

        CivilComplaintVO saved = civilService.receive(form, "테스터");
        assertThat(saved.getReceiptNo()).matches("\\d{4}-\\d{6}");
        // 즉시 처리 민원은 접수 시각부터 3근무시간 (주말·공휴일·근무시간 밖 접수는 다음 근무시간부터)
        assertThat(saved.getDeadlineAt()).isEqualTo(
                new egovframework.haengjeongnuli.civil.service.DeadlineCalculator(
                        egovframework.haengjeongnuli.cmm.holiday.KoreanHolidays.of(saved.getReceivedAt().getYear(),
                                java.util.Map.of(LocalDate.of(2026, 6, 3), "선거")).keySet())
                        .deadlineAt(saved.getReceivedAt(), 0));
        assertThat(saved.getDeadline()).isEqualTo(saved.getDeadlineAt().toLocalDate());
        assertThat(saved.getStatus()).isEqualTo(CivilStatus.IN_PROGRESS);
        assertThat(saved.getMobile()).isNull();
        assertThat(saved.getHomePhone()).isEqualTo("02-123-4567");

        CivilSearchVO search = CivilSearchVO.forViewer(TestUsers.admin());
        search.setQ(form.getTitle());
        assertThat(civilService.search(search)).singleElement()
                .satisfies(c -> {
                    assertThat(c.getLegalAffair()).isEqualTo(LegalAffair.SEAL);
                    assertThat(c.isImminent()).isTrue();
                });
        assertThat(civilService.summary(TestUsers.admin()).getInProgress()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 게시글을_쓰면_목록과_메인에_나온다() throws Exception {
        String title = "공지 " + UUID.randomUUID();
        mvc.perform(post("/board/write")
                        .param("board", "notice").param("scope", "전체 공개")
                        .param("title", title).param("content", "본문").param("pinned", "true"))
                .andExpect(redirectedUrl("/board?tab=notice"));

        mvc.perform(get("/board").param("tab", "notice"))
                .andExpect(content().string(containsString(title)));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString(title)));
    }

    @Test
    void 자료를_올리고_내려받는다() throws Exception {
        String title = "서식 " + UUID.randomUUID();
        // 실제 HWP(OLE2 복합 문서) 표지로 시작하는 내용
        byte[] data = new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("files", "출장 신청서.hwp", "application/octet-stream", data);

        mvc.perform(multipart("/board/upload").file(file)
                        .param("category", "업무 서식").param("scope", "전체 공개").param("title", title))
                .andExpect(redirectedUrl("/board?tab=archive"));

        String html = mvc.perform(get("/board").param("tab", "archive"))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("출장 신청서.hwp")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("/board/files/(\\d+)").matcher(html.substring(html.indexOf(title)));
        assertThat(m.find()).isTrue();
        mvc.perform(get("/board/files/" + m.group(1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("UTF-8''")))
                .andExpect(content().bytes(data));
    }

    @Test
    void 파일_없이_자료를_올릴_수_없다() throws Exception {
        mvc.perform(multipart("/board/upload").param("scope", "전체 공개").param("title", "빈 자료"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("올릴 파일을 하나 이상 선택하세요.")));
    }

    @Test
    void 허용하지_않은_형식은_거부된다() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[]{1, 2});
        mvc.perform(multipart("/board/upload").file(exe).param("scope", "전체 공개").param("title", "실행 파일"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("올릴 수 없는 형식입니다: virus.exe")));
    }

    @Test
    void 없는_파일은_404() throws Exception {
        mvc.perform(get("/board/files/999999")).andExpect(status().isNotFound());
    }
}
