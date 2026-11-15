package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.records.service.RecordService;
import egovframework.haengjeongnuli.records.service.RecordVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class RecordRegistryTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired ApprovalService approvalService;
    @Autowired RecordService recordService;

    private record Staff(String id, LoginUser user, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "r" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setPositionName("주무관");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        userService.create(f);
        LoginUser u = (LoginUser) userService.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(id, u, TestUsers.mvcAs(ctx, u));
    }

    private static long id(MvcResult r, String prefix) {
        Matcher m = Pattern.compile(prefix + "(\\d+)$").matcher(r.getResponse().getRedirectedUrl());
        assertThat(m.find()).as(r.getResponse().getRedirectedUrl()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 결재가_끝나거나_접수하면_등록대장에_오르고_직접_등록은_번호를_이어_쓴다() throws Exception {
        String dept = "기록" + UUID.randomUUID().toString().substring(0, 4);
        String recvDept = "받는" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept);
        Staff approver = staff("결재자", dept);
        Staff recv = staff("수신직원", recvDept);
        String title = "등록대장 시험 " + UUID.randomUUID();

        long docId = id(drafter.mvc().perform(post("/approval/draft").param("action", "send")
                .param("retention", "5년").param("disclosure", "공개")
                .param("receiverType", "external").param("receiver", recvDept)
                .param("title", title).param("body", "본문").param("finalApprover", approver.id())).andReturn(), "/approval/");
        approver.mvc().perform(post("/approval/" + docId + "/approve"));
        recv.mvc().perform(post("/approval/" + docId + "/receive"));

        int year = LocalDate.now().getYear();
        assertThat(recordService.list(drafter.user(), year, null, title)).singleElement().satisfies(r -> {
            assertThat(r.getRegType()).isEqualTo(RecordVO.PRODUCE);
            assertThat(r.getRegNo()).isEqualTo(dept + "-1");
            assertThat(r.getDocId()).isEqualTo(docId);
            assertThat(r.getRetention()).isEqualTo("5년");
        });
        assertThat(recordService.list(recv.user(), year, null, title)).singleElement().satisfies(r -> {
            assertThat(r.getRegType()).isEqualTo(RecordVO.RECEIVE);
            assertThat(r.getRegNo()).isEqualTo(recvDept + "-1");
            assertThat(r.getCounterpart()).isEqualTo(dept);
        });
        assertThat(body(drafter.mvc(), "/records")).contains(title).contains(dept + "-1");

        // 시스템 밖에서 받은 종이 문서를 직접 등록하면 같은 번호대의 다음 번호가 붙는다
        String paper = "종이 공문 " + UUID.randomUUID();
        byte[] pdf = "%PDF-1.4\n%âã\n1 0 obj<<>>endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        MvcResult r = recv.mvc().perform(multipart("/records")
                        .file(new MockMultipartFile("files", "스캔본.pdf", "application/pdf", pdf))
                        .param("type", "RECEIVE").param("title", paper).param("regDate", LocalDate.now().toString())
                        .param("counterpart", "OO도 기획조정실").param("counterpartNo", "기획조정실-1234")
                        .param("medium", "종이문서").param("pages", "3").param("retention", "3년").param("disclosure", "공개")
                        .param("handlerId", drafter.id()))
                .andExpect(flash().attribute("message", startsWith("접수 등록했습니다. 등록번호 " + recvDept + "-2")))
                .andReturn();
        long recId = id(r, "/records/");
        assertThat(body(recv.mvc(), "/records/" + recId)).contains(paper).contains("기획조정실-1234").contains("스캔본.pdf");

        // 다른 처리과라도 처리 담당자로 지정되면 볼 수 있다
        assertThat(body(drafter.mvc(), "/records")).contains(paper);
        drafter.mvc().perform(get("/records/" + recId)).andExpect(status().isOk());

        long fileId = recordService.get(recId).getFiles().get(0).getFileId();
        recv.mvc().perform(get("/records/files/" + fileId)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // 관계없는 직원은 목록·상세·파일 모두 볼 수 없다
        Staff outsider = staff("남", "다른과" + UUID.randomUUID().toString().substring(0, 4));
        assertThat(body(outsider.mvc(), "/records")).doesNotContain(paper).doesNotContain(title);
        outsider.mvc().perform(get("/records/" + recId)).andExpect(status().isNotFound());
        outsider.mvc().perform(get("/records/files/" + fileId)).andExpect(status().isNotFound());

        // 등록정보는 고치거나 지울 수 없다 (공공기록물법 시행령 제20조 ②)
        recv.mvc().perform(post("/records/" + recId)).andExpect(status().is4xxClientError());
        recv.mvc().perform(post("/records/" + recId + "/delete")).andExpect(status().is4xxClientError());
        recv.mvc().perform(delete("/records/" + recId)).andExpect(status().is4xxClientError());
        assertThat(recordService.get(recId).getTitle()).isEqualTo(paper);
    }

    @Test
    void 직접_등록_입력을_검사한다() throws Exception {
        Staff s = staff("등록자", "검사과" + UUID.randomUUID().toString().substring(0, 4));
        String today = LocalDate.now().toString();

        s.mvc().perform(post("/records").param("type", "RECEIVE").param("title", "발신처 없음").param("regDate", today))
                .andExpect(redirectedUrl("/records/new?type=RECEIVE"))
                .andExpect(flash().attribute("message", containsString("발신 기관")));
        s.mvc().perform(post("/records").param("type", "PRODUCE").param("title", "미래 날짜")
                        .param("regDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(redirectedUrl("/records/new?type=PRODUCE"));
        s.mvc().perform(post("/records").param("type", "PRODUCE").param("title", " ").param("regDate", today))
                .andExpect(redirectedUrl("/records/new?type=PRODUCE"));
        s.mvc().perform(multipart("/records")
                        .file(new MockMultipartFile("files", "위장.pdf", "application/pdf", new byte[] {'P', 'K', 3, 4, 0, 0}))
                        .param("type", "PRODUCE").param("title", "가짜 파일").param("regDate", today))
                .andExpect(redirectedUrl("/records/new?type=PRODUCE"));

        // 생산 문서는 수신처 없이(내부결재) 등록할 수 있다
        s.mvc().perform(post("/records").param("type", "PRODUCE").param("title", "종이 내부결재").param("regDate", today)
                        .param("medium", "종이문서").param("retention", "1년").param("disclosure", "공개"))
                .andExpect(flash().attribute("message", containsString("생산 등록했습니다")));
        assertThat(body(s.mvc(), "/records/new?type=RECEIVE")).contains("접수 문서 등록");
    }
}
