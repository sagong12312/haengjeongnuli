package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.DispatchService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 기관 정보·관인, 시행문 출력과 발송 기록 */
@SpringBootTest
class SihaengTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired DispatchService dispatch;

    private record Staff(String id, MockMvc mvc) {}

    private Staff staff(String name, String pos, String dept) {
        String id = "s" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setPositionName(pos);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Sihaeng-pass-7!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(id, TestUsers.mvcAs(ctx, u));
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 기관_정보와_관인을_넣으면_시행문에_찍히고_수신자별로_발송을_기록한다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/org").param("orgName", "행정누리시").param("senderTitle", "행정누리시장")
                        .param("zipCode", "12345").param("address", "행정누리시 중앙로 1").param("phone", "02-000-0000")
                        .param("contact", "정보통신과 내선 1234"))
                .andExpect(flash().attribute("message", "기관 정보를 저장했습니다."));
        // 관인: 내용이 PNG가 아니면 거부
        admin.perform(multipart("/admin/org/seal").file(new MockMultipartFile("file", "seal.png", "image/png", new byte[] {1, 2, 3})))
                .andExpect(flash().attribute("message", containsString("맞지 않아")));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        admin.perform(multipart("/admin/org/seal").file(new MockMultipartFile("file", "seal.png", "image/png", png)))
                .andExpect(flash().attribute("message", containsString("관인 이미지를 등록했습니다")));
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/org")).andExpect(status().isForbidden());
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/org/seal"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png")).andExpect(content().bytes(png));
        // 화면 아래쪽 기관명·문의처
        assertThat(body(TestUsers.mvcAs(ctx, TestUsers.general()), "/")).contains("행정누리 · 행정누리시").contains("정보통신과 내선 1234");

        String dept = "발송" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", "주무관", dept), colleague = staff("동료", "주무관", dept),
                chief = staff("과장", "과장", dept), deputy = staff("팀장", "팀장", dept), outsider = staff("남", "주무관", "다른과");
        String title = "협조 요청 공문 " + UUID.randomUUID();
        String url = drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", title)
                .param("body", "다음과 같이 협조를 요청합니다.").param("receiverType", "external")
                .param("receiver", "OO시 총무과, OO군 기획예산과").param("finalApprover", chief.id()).param("delegated", "true"))
                .andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.replaceAll("\\D", ""));
        chief.mvc().perform(post("/approval/proxy").param("proxyLoginId", deputy.id())
                .param("startDate", java.time.LocalDate.now().toString()).param("endDate", java.time.LocalDate.now().toString())
                .param("reason", "출장"));
        deputy.mvc().perform(post("/approval/" + id + "/approve"));

        String view = body(drafter.mvc(), "/approval/" + id);
        assertThat(view).contains("시행문 발송").contains("OO시 총무과").contains("OO군 기획예산과");

        String s0 = body(drafter.mvc(), "/approval/" + id + "/sihaeng?to=0");
        assertThat(s0).contains("행정누리시").contains("<span>OO시 총무과</span>").contains(title).contains("행정누리시장")
                .contains("/org/seal").contains(dept + "-").contains("전결").contains("대결").contains("12345 행정누리시 중앙로 1");
        assertThat(body(drafter.mvc(), "/approval/" + id + "/sihaeng")).contains("수신자 참조").contains("OO시 총무과, OO군 기획예산과");

        // 결재선에 없는 같은 부서 직원은 문서를 볼 수 없다
        colleague.mvc().perform(post("/approval/" + id + "/dispatch").param("receiver", "OO시 총무과").param("method", "우편"))
                .andExpect(status().isNotFound());
        deputy.mvc().perform(post("/approval/" + id + "/dispatch").param("receiver", "OO시 총무과").param("method", "전자우편")
                        .param("note", "chongmu@example.go.kr"))
                .andExpect(flash().attribute("message", "OO시 총무과에 전자우편(으)로 발송했다고 기록했습니다."));
        drafter.mvc().perform(post("/approval/" + id + "/dispatch").param("receiver", "없는 기관").param("method", "우편"))
                .andExpect(flash().attribute("message", "수신자를 목록에서 고르세요."));
        assertThat(dispatch.list(id)).singleElement().satisfies(d -> {
            assertThat(d.getMethod()).isEqualTo("전자우편");
            assertThat(d.getDispatchedBy()).isEqualTo(deputy.id());
        });

        // 관계없는 직원은 시행문을 볼 수 없다
        outsider.mvc().perform(get("/approval/" + id + "/sihaeng")).andExpect(status().isNotFound());
        assertThat(body(chief.mvc(), "/approval/" + id)).contains("시행문 발송").contains("전자우편");
    }

    @Test
    void 내부결재_문서는_시행문이_없다() throws Exception {
        Staff drafter = staff("기안자", "주무관", "내부과"), chief = staff("과장", "과장", "내부과");
        String url = drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", "내부결재")
                .param("body", "본문").param("finalApprover", chief.id())).andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(url.replaceAll("\\D", ""));
        chief.mvc().perform(post("/approval/" + id + "/approve"));
        drafter.mvc().perform(get("/approval/" + id + "/sihaeng")).andExpect(status().isNotFound());
    }
}
