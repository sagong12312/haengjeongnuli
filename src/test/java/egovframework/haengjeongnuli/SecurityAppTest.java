package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.secapp.SecurityAppService;
import egovframework.haengjeongnuli.cmm.secapp.SecurityAppVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 보안 프로그램 설치 안내와 직원별 확인 */
@SpringBootTest
class SecurityAppTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired SecurityAppService apps;

    private static String body(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 관리자가_정한_필수_프로그램을_직원이_설치_확인한다() throws Exception {
        String id = "s" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("보안직원");
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Secure-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc staff = TestUsers.mvcAs(ctx, u);
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String name = "백신" + UUID.randomUUID().toString().substring(0, 4);

        staff.perform(get("/admin/security-apps")).andExpect(status().isForbidden());
        // 주소는 http(s)만
        admin.perform(post("/admin/security-apps").param("name", name).param("purpose", "악성코드 검사").param("url", "javascript:alert(1)")
                        .param("required", "true"))
                .andExpect(flash().attribute("message", containsString("http://")));
        admin.perform(post("/admin/security-apps").param("name", name).param("purpose", "악성코드 검사")
                        .param("url", "https://dist.example.go.kr/av").param("required", "true").param("guide", "1. 실행\n2. 재부팅"))
                .andExpect(flash().attribute("message", containsString("저장했습니다")));
        SecurityAppVO a = apps.all().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();

        int before = apps.pending(id);
        assertThat(before).isGreaterThanOrEqualTo(1);
        assertThat(body(staff, "/")).contains("필수 보안 프로그램이");
        assertThat(body(staff, "/security-apps")).contains(name).contains("https://dist.example.go.kr/av").contains("확인 안 함");
        assertThat(body(admin, "/admin/security-apps?app=" + a.getAppId())).contains("보안직원");

        staff.perform(post("/security-apps/" + a.getAppId() + "/ack")).andExpect(flash().attribute("message", containsString("설치를 확인했습니다")));
        assertThat(apps.pending(id)).isEqualTo(before - 1);
        assertThat(body(admin, "/admin/security-apps?app=" + a.getAppId())).doesNotContain("보안직원");

        // 다시 확인 요청 → 다시 눌러야 한다
        admin.perform(post("/admin/security-apps/" + a.getAppId() + "/bump"));
        assertThat(apps.pending(id)).isEqualTo(before);
        // 안내를 멈추면 확인할 필요가 없다
        admin.perform(post("/admin/security-apps/" + a.getAppId() + "/stop"));
        assertThat(apps.pending(id)).isEqualTo(before - 1);
        staff.perform(post("/security-apps/" + a.getAppId() + "/ack")).andExpect(flash().attribute("message", "안내 중인 항목이 아닙니다."));
    }
}
