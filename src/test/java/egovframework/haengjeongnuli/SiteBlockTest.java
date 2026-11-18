package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.secapp.SecurityAppService;
import egovframework.haengjeongnuli.cmm.siteblock.SiteBlockService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 유해사이트 차단: 목록·DNS 설정, 차단 스크립트, 강제 보안 프로그램 등록 */
@SpringBootTest
class SiteBlockTest {

    @Autowired WebApplicationContext ctx;
    @Autowired SiteBlockService service;
    @Autowired SecurityAppService apps;
    @Autowired UserService users;

    @Test
    void 붙여_넣은_주소에서_도메인만_골라_목록에_넣고_이상한_값은_받지_않는다() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        SiteBlockService.Result r = service.add("https://www.casino-" + tag + ".example/play?x=1\n*.bet-" + tag + ".example\n"
                + "x'; Remove-Item C:\\ -Recurse #.com\n127.0.0.1\n도박-" + tag + ".한국", "GAMBLING", "시험", "admin");
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(r.message()).contains("3개를 더했습니다").contains("알아볼 수 없는 줄");
        assertThat(service.rules()).extracting(m -> m.get("domain"))
                .contains("www.casino-" + tag + ".example", "bet-" + tag + ".example")
                .anyMatch(d -> d.toString().startsWith("xn--"));
        assertThat(service.add("bet-" + tag + ".example", "GAMBLING", null, "admin").message()).contains("건너뜀");
        assertThat(service.add("a.example", "NOPE", null, "admin").ok()).isFalse();
        assertThat(service.remove("bet-" + tag + ".example", "admin").ok()).isTrue();
        assertThat(service.remove("bet-" + tag + ".example", "admin").ok()).isFalse();
        String script = service.script();
        assertThat(script).contains("'www.casino-" + tag + ".example'").doesNotContain("Remove-Item C:").doesNotContain("bet-" + tag);
    }

    @Test
    void DNS_방식에_따라_스크립트에_넣을_주소가_정해진다() {
        assertThat(service.setDns("CUSTOM", "10.0.0.999", null, "admin").ok()).isFalse();
        assertThat(service.setDns("WHAT", null, null, "admin").ok()).isFalse();
        assertThat(service.setDns("CUSTOM", "10.0.0.53", "10.0.0.54", "admin").ok()).isTrue();
        assertThat(service.script()).contains("$dns = @('10.0.0.53', '10.0.0.54')");
        assertThat(service.setDns("OFF", null, null, "admin").ok()).isTrue();
        assertThat(service.script()).contains("$dns = @()");
        assertThat(service.setDns("ADULT", null, null, "admin").ok()).isTrue();
        String s = service.script();
        assertThat(s).contains("$dns = @('1.1.1.3', '1.0.0.3')").contains("DnsOverHttpsMode").contains("URLBlocklist")
                .contains("RunAs").contains("\r\n");
    }

    @Test
    void 강제_보안_프로그램으로_등록하면_스크립트를_설치해야_행정누리를_쓴다() throws Exception {
        String id = "b" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("차단직원");
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Secure-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        TestUsers.mvcAs(ctx, u).perform(get("/admin/site-block")).andExpect(status().isForbidden());
        admin.perform(post("/admin/site-block/register"));
        Long appId = ((Number) service.conf().get("appId")).longValue();
        try {
            assertThat(apps.get(appId).isRequired()).isTrue();
            assertThat(apps.get(appId).getUrl()).isEqualTo("/security-apps/site-block.ps1");
            MockMvc staff = TestUsers.mvcAs(ctx, u);
            assertThat(staff.perform(get("/approval")).andReturn().getResponse().getRedirectedUrl()).isEqualTo("/security-apps?required=1");
            // 막혀 있어도 스크립트는 받을 수 있다 (UTF-8 BOM, 내려받기)
            MockHttpServletResponse r = staff.perform(get("/security-apps/site-block.ps1")).andReturn().getResponse();
            assertThat(r.getStatus()).isEqualTo(200);
            assertThat(r.getHeader("Content-Disposition")).contains("site-block.ps1");
            byte[] b = r.getContentAsByteArray();
            assertThat(new byte[] {b[0], b[1], b[2]}).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
            assertThat(new String(b, StandardCharsets.UTF_8)).contains("행정누리 유해사이트 차단");
            assertThat(staff.perform(get("/security-apps")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("유해사이트 차단").contains("/security-apps/site-block.ps1");
            staff.perform(post("/security-apps/" + appId + "/ack"));
            staff.perform(get("/approval")).andExpect(status().isOk());
            // 목록을 바꾸고 다시 설치를 요청하면 다시 막힌다
            admin.perform(post("/admin/site-block/reinstall"));
            assertThat(TestUsers.mvcAs(ctx, u).perform(get("/approval")).andReturn().getResponse().getRedirectedUrl())
                    .isEqualTo("/security-apps?required=1");
            assertThat(admin.perform(get("/admin/site-block")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("거르는 DNS").contains("강제 보안 프로그램으로 등록");
        } finally {
            apps.setUse(appId, false, "admin");
        }
    }

    @Test
    void 차단_안내에서_해제를_신청하고_관리자가_받아들이면_목록에서_빠진다() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String site = "vendor-" + tag + ".example";
        service.add(site, "ETC", null, "admin");
        String id = "a" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("해제직원");
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Secure-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        MockMvc staff = TestUsers.mvcAs(ctx, u);
        String page = staff.perform(get("/site-blocked").param("site", "www." + site)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("접속이 차단된 사이트").contains("차단 분류: 기타").contains("차단 해제(항의) 신청");
        assertThat(service.appeal(site, "짧음", u).ok()).isFalse();
        assertThat(service.appeal(site, "업체 누리집 확인이 필요합니다.", u).ok()).isTrue();
        assertThat(service.appeal(site, "다시 신청합니다 이유", u).message()).contains("검토하고");
        Long appealId = ((Number) service.myAppeals(id).get(0).get("id")).longValue();
        assertThat(service.decide(appealId, false, null, "admin").ok()).isFalse(); // 거절은 이유 필요
        assertThat(service.decide(appealId, true, "확인했습니다", "admin").ok()).isTrue();
        assertThat(service.reasonFor(site)).isNull();
        assertThat(service.decide(appealId, true, null, "admin").ok()).isFalse();
    }

    @Test
    void 기본_차단_목록을_한꺼번에_넣는다() {
        SiteBlockService.Result r = service.loadPreset("admin");
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(service.rules()).extracting(m -> m.get("domain")).contains("pornhub.com", "kcna.kp", "insecam.org", "donmai.us", "womenonweb.org");
        assertThat(service.reasonFor("danbooru.donmai.us")).isEqualTo("성인·음란");
        assertThat(service.reasonFor("rodong.rep.kp")).isEqualTo("북한·이적 표현물");
        assertThat(service.loadPreset("admin").message()).contains("0개를 더했습니다");
        assertThat(service.script()).contains("'pornhub.com'").contains("'naenara.com.kp'");
    }
}
