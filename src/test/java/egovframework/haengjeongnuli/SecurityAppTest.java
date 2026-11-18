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
        // 강제 항목을 설치하지 않으면 첫 화면 대신 설치 안내로 간다
        assertThat(staff.perform(get("/")).andReturn().getResponse().getRedirectedUrl()).isEqualTo("/security-apps?required=1");
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

    @Test
    void 강제_프로그램을_설치하지_않으면_업무_화면을_못_쓰고_권고는_막지_않는다() throws Exception {
        String id = "s" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("강제직원");
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Secure-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc staff = TestUsers.mvcAs(ctx, u);
        String tag = UUID.randomUUID().toString().substring(0, 4);
        // 확인 주소는 이 PC 주소만
        assertThat(apps.save(null, "키보드보안" + tag, "입력 보호", null, null, true, 1, "STAFF", "https://evil.example.com/ok", "admin"))
                .contains("이 PC 주소");
        assertThat(apps.save(null, "권고" + tag, "참고용", null, null, false, 1, "STAFF", null, "admin")).isNull();
        Long rec = apps.all().stream().filter(a -> a.getName().equals("권고" + tag)).findFirst().get().getAppId();
        Long force = null, citizen = null;
        try {
            // 권고만 있으면 막지 않는다
            staff.perform(get("/approval")).andExpect(status().isOk());
            assertThat(apps.save(null, "백신" + tag, "악성코드 검사", null, null, true, 1, "STAFF", "http://127.0.0.1:14440/status", "admin")).isNull();
            assertThat(apps.save(null, "국민보안" + tag, "민원인 입력 보호", null, null, true, 2, "CITIZEN", null, "admin")).isNull();
            force = apps.all().stream().filter(a -> a.getName().equals("백신" + tag)).findFirst().get().getAppId();
            citizen = apps.all().stream().filter(a -> a.getName().equals("국민보안" + tag)).findFirst().get().getAppId();

            MockMvc fresh = TestUsers.mvcAs(ctx, u);
            fresh.perform(get("/approval")).andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/security-apps?required=1"));
            String page = body(fresh, "/security-apps?required=1");
            assertThat(page).contains("강제 보안 프로그램을 설치해야").contains("백신" + tag).contains("권고" + tag)
                    .contains("data-check-url=\"http://127.0.0.1:14440/status\"").doesNotContain("국민보안" + tag);
            // 설치 안내 화면만 그 확인 주소에 접속을 허용한다
            assertThat(fresh.perform(get("/security-apps")).andReturn().getResponse().getHeader("Content-Security-Policy"))
                    .contains("connect-src 'self' http://127.0.0.1:14440");
            assertThat(fresh.perform(get("/desk")).andReturn().getResponse().getHeader("Content-Security-Policy")).contains("connect-src 'self';");
            // 민원인용은 직원이 확인할 수 없다
            fresh.perform(post("/security-apps/" + citizen + "/ack")).andExpect(flash().attribute("message", "안내 중인 항목이 아닙니다."));
            fresh.perform(post("/security-apps/" + force + "/ack")).andExpect(flash().attribute("message", containsString("이제 행정누리를 쓸 수 있습니다")));
            fresh.perform(get("/approval")).andExpect(status().isOk());
            // 전자민원으로는 민원인용만 나간다
            assertThat(apps.forCitizen()).extracting(SecurityAppVO::getName).contains("국민보안" + tag).doesNotContain("백신" + tag);
        } finally {
            apps.setUse(rec, false, "admin");
            if (force != null) {
                apps.setUse(force, false, "admin");
            }
            if (citizen != null) {
                apps.setUse(citizen, false, "admin");
            }
        }
    }
}
