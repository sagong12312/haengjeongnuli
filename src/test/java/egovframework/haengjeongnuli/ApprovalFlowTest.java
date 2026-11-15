package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class ApprovalFlowTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired ApprovalService approvalService;

    /** DB에 계정을 만들고 그 계정으로 로그인한 MockMvc 를 돌려준다 */
    private record Staff(String id, MockMvc mvc) {}

    private Staff staff(String name, String dept) {
        String id = "a" + UUID.randomUUID().toString().substring(0, 8);
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
        return new Staff(id, TestUsers.mvcAs(ctx, u));
    }

    private static long docId(MvcResult r) {
        Matcher m = Pattern.compile("/approval/(\\d+)").matcher(r.getResponse().getRedirectedUrl());
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 기안부터_검토_결재_수신부서_접수까지() throws Exception {
        String dept = "기획" + UUID.randomUUID().toString().substring(0, 4);
        String recvDept = "수신" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept);
        Staff reviewer = staff("검토자", dept);
        Staff approver = staff("결재자", dept);
        Staff recv = staff("수신직원", recvDept);
        Staff outsider = staff("남", "다른과");
        String title = "예산 집행 계획 " + UUID.randomUUID();

        MvcResult r = drafter.mvc().perform(multipart("/approval/draft")
                        .file(new MockMultipartFile("files", "계획서.txt", "text/plain", "내용".getBytes(StandardCharsets.UTF_8)))
                        .param("action", "send").param("kind", "보고").param("retention", "5년").param("disclosure", "공개")
                        .param("receiverType", "external").param("receiver", recvDept)
                        .param("title", title).param("body", "본문입니다")
                        .param("reviewers", reviewer.id(), "", "", "").param("finalApprover", approver.id()))
                .andExpect(status().is3xxRedirection()).andReturn();
        long id = docId(r);

        // 아직 차례가 아닌 결재권자는 결재할 수 없다
        approver.mvc().perform(post("/approval/" + id + "/approve"))
                .andExpect(redirectedUrl("/approval/" + id))
                .andExpect(flash().attribute("message", "지금 결재할 차례가 아닙니다."));

        assertThat(body(reviewer.mvc(), "/approval?box=wait")).contains(title);
        assertThat(body(reviewer.mvc(), "/notifications")).contains("검토할 차례입니다");
        reviewer.mvc().perform(post("/approval/" + id + "/approve").param("opinion", "이상 없습니다"))
                .andExpect(redirectedUrl("/approval?box=wait"));

        assertThat(body(approver.mvc(), "/approval?box=wait")).contains(title);
        approver.mvc().perform(post("/approval/" + id + "/approve")).andExpect(redirectedUrl("/approval?box=wait"));

        ApprovalDocVO d = approvalService.get(id);
        assertThat(d.getStatus()).isEqualTo(ApprovalDocVO.DONE);
        assertThat(d.getDocNo()).startsWith(dept + "-");
        assertThat(body(drafter.mvc(), "/notifications")).contains("결재가 끝났습니다");
        assertThat(body(drafter.mvc(), "/approval?box=done")).contains(title).contains(d.getDocNo());
        assertThat(body(drafter.mvc(), "/approval/" + id)).contains("이상 없습니다").contains("계획서.txt");

        // 수신 부서: 문서함에 들어오고 접수할 수 있다
        assertThat(body(recv.mvc(), "/approval?box=recv")).contains(title).contains("접수 대기");
        assertThat(body(recv.mvc(), "/notifications")).contains("접수해 주세요");
        recv.mvc().perform(post("/approval/" + id + "/receive")).andExpect(flash().attribute("message", "접수했습니다."));
        assertThat(approvalService.get(id).getReceivedAt()).isNotNull();

        // 관계없는 직원은 문서도 붙임도 볼 수 없다
        outsider.mvc().perform(get("/approval/" + id)).andExpect(status().isNotFound());
        long fileId = d.getFiles().get(0).getFileId();
        outsider.mvc().perform(get("/approval/files/" + fileId)).andExpect(status().isNotFound());
        drafter.mvc().perform(get("/approval/files/" + fileId)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void 반려하려면_사유가_필요하고_기안자에게_돌아간다() throws Exception {
        Staff drafter = staff("기안자", "총무과");
        Staff approver = staff("결재자", "총무과");
        String title = "반려될 문서 " + UUID.randomUUID();
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "send")
                .param("title", title).param("body", "본문").param("finalApprover", approver.id())).andReturn());

        approver.mvc().perform(post("/approval/" + id + "/reject").param("opinion", " "))
                .andExpect(flash().attribute("message", "반려 사유를 1,000자 이내로 입력하세요."));
        approver.mvc().perform(post("/approval/" + id + "/reject").param("opinion", "근거 자료를 붙여 주세요"))
                .andExpect(redirectedUrl("/approval?box=wait"));

        assertThat(approvalService.get(id).getStatus()).isEqualTo(ApprovalDocVO.REJECTED);
        assertThat(body(drafter.mvc(), "/approval?box=back")).contains(title);
        assertThat(body(drafter.mvc(), "/notifications")).contains("반려했습니다");
        // 다시 기안: 내용을 옮겨 온다
        assertThat(body(drafter.mvc(), "/approval/draft?from=" + id)).contains(title);
    }

    @Test
    void 아무도_결재하기_전에는_회수할_수_있다() throws Exception {
        Staff drafter = staff("기안자", "총무과");
        Staff approver = staff("결재자", "총무과");
        String title = "회수할 문서 " + UUID.randomUUID();
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "send")
                .param("title", title).param("body", "본문").param("finalApprover", approver.id())).andReturn());

        drafter.mvc().perform(post("/approval/" + id + "/withdraw")).andExpect(redirectedUrl("/approval/" + id + "/edit"));
        assertThat(approvalService.get(id).getStatus()).isEqualTo(ApprovalDocVO.DRAFT);
        assertThat(body(approver.mvc(), "/approval?box=wait")).doesNotContain(title);
        approver.mvc().perform(post("/approval/" + id + "/approve"))
                .andExpect(status().isNotFound()); // 임시저장 문서는 기안자만 본다

        // 다시 상신하고 결재하면 더는 회수할 수 없다
        drafter.mvc().perform(post("/approval/" + id + "/edit").param("action", "send")
                .param("title", title).param("body", "고친 본문").param("finalApprover", approver.id()))
                .andExpect(redirectedUrl("/approval/" + id));
        approver.mvc().perform(post("/approval/" + id + "/approve"));
        drafter.mvc().perform(post("/approval/" + id + "/withdraw"))
                .andExpect(flash().attribute("message", "이미 결재가 진행되어 회수할 수 없습니다."));
    }

    @Test
    void 임시저장_고치기_지우기와_입력_확인() throws Exception {
        Staff drafter = staff("기안자", "총무과");
        Staff other = staff("다른직원", "총무과");

        // 결재권자 없이 상신하거나 자기 자신을 결재선에 넣으면 안 된다
        drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", "t").param("body", "b"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("결재권자를 지정하세요")));
        drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", "t").param("body", "b")
                        .param("finalApprover", drafter.id()))
                .andExpect(status().isBadRequest());
        drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", "t").param("body", "b")
                        .param("disclosure", "비공개").param("finalApprover", other.id()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("비공개 사유")));

        String title = "임시 문서 " + UUID.randomUUID();
        long id = docId(drafter.mvc().perform(post("/approval/draft").param("action", "save").param("title", title))
                .andReturn());
        assertThat(body(drafter.mvc(), "/approval?box=temp")).contains(title);
        other.mvc().perform(get("/approval/" + id + "/edit")).andExpect(status().isNotFound());
        other.mvc().perform(post("/approval/" + id + "/delete")).andExpect(status().isNotFound());

        drafter.mvc().perform(post("/approval/" + id + "/edit").param("action", "save").param("title", title + " 고침"))
                .andExpect(redirectedUrl("/approval/" + id + "/edit"));
        assertThat(approvalService.get(id).getTitle()).isEqualTo(title + " 고침");

        drafter.mvc().perform(post("/approval/" + id + "/delete")).andExpect(redirectedUrl("/approval?box=temp"));
        assertThat(approvalService.get(id)).isNull();
    }

    @Test
    void 메인메뉴에_결재_대기_건수가_보인다() throws Exception {
        Staff drafter = staff("기안자", "총무과");
        Staff approver = staff("결재자", "총무과");
        drafter.mvc().perform(post("/approval/draft").param("action", "send")
                .param("title", "건수 확인").param("body", "본문").param("finalApprover", approver.id()));
        approver.mvc().perform(get("/"))
                .andExpect(content().string(containsString("color:var(--brand)\">1</strong><small>결재 대기")));
        approver.mvc().perform(get("/approval/draft")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("value=\"" + approver.id() + "\""))));
    }
}
