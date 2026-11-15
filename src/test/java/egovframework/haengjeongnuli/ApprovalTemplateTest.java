package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.ApprovalTemplateService;
import egovframework.haengjeongnuli.approval.service.ApprovalTemplateVO;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 기안 서식 */
@SpringBootTest
class ApprovalTemplateTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired ApprovalTemplateService templates;

    private LoginUser staff(String dept) {
        String id = "t" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("서식직원");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Template-pass-7!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return u;
    }

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 기안_내용을_서식으로_저장하고_범위에_맞게_불러온다() throws Exception {
        String dept = "서식" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser me = staff(dept), colleague = staff(dept), other = staff("남" + dept);
        MockMvc mine = TestUsers.mvcAs(ctx, me);
        String name = "출장 결과 보고 " + UUID.randomUUID().toString().substring(0, 4);

        // 기안하기 화면(파일 첨부가 되는 multipart 양식)에서 '서식으로 저장'
        mine.perform(multipart("/approval/templates").param("templateName", name).param("templateScope", "DEPT")
                        .param("kind", "보고").param("retention", "3년").param("title", "OO 출장 결과 보고")
                        .param("body", "1. 출장 개요\n  가. 기간:\n  나. 장소:"))
                .andExpect(flash().attribute("message", "'" + name + "' 서식을 저장했습니다."));
        mine.perform(post("/approval/templates").param("templateName", "나만 쓰는 서식").param("templateScope", "MINE")
                .param("kind", "일반 기안").param("title", "개인 서식"));
        mine.perform(post("/approval/templates").param("templateName", "전체").param("templateScope", "ALL")
                        .param("kind", "일반 기안").param("title", "x"))
                .andExpect(flash().attribute("message", "기관 전체 서식은 시스템 관리자만 만들 수 있습니다."));

        ApprovalTemplateVO t = templates.visible(me).stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
        String draft = body(mine, "/approval/draft?template=" + t.getTemplateId());
        assertThat(draft).contains("서식을 불러왔습니다").contains("OO 출장 결과 보고").contains("1. 출장 개요");

        // 같은 부서 직원은 부서 서식을 쓰지만 개인 서식은 못 본다, 다른 부서는 둘 다 못 본다
        assertThat(templates.visible(colleague)).extracting(ApprovalTemplateVO::getName).contains(name).doesNotContain("나만 쓰는 서식");
        assertThat(templates.visible(other)).extracting(ApprovalTemplateVO::getName).doesNotContain(name);
        assertThat(body(TestUsers.mvcAs(ctx, other), "/approval/draft?template=" + t.getTemplateId())).doesNotContain("OO 출장 결과 보고");

        // 만든 사람만 지운다
        TestUsers.mvcAs(ctx, colleague).perform(post("/approval/templates/" + t.getTemplateId() + "/delete"))
                .andExpect(flash().attribute("message", "만든 사람이나 관리자만 지울 수 있습니다."));
        mine.perform(post("/approval/templates/" + t.getTemplateId() + "/delete"))
                .andExpect(flash().attribute("message", "서식을 지웠습니다."));
        assertThat(templates.visible(me)).extracting(ApprovalTemplateVO::getName).doesNotContain(name);
    }
}
