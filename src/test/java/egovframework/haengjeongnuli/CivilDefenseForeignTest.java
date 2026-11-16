package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.civildefense.CdMemberVO;
import egovframework.haengjeongnuli.civildefense.CdService;
import egovframework.haengjeongnuli.cmm.audit.PrivacyPurgeService;
import egovframework.haengjeongnuli.foreign.ForeignResidentVO;
import egovframework.haengjeongnuli.foreign.ForeignService;

/** 민방위 명부·교육, 외국인주민 지원: 담당 부서만, 등록번호 없이, 보유기간 지나면 파기 */
@SpringBootTest
class CivilDefenseForeignTest {

    @Autowired WebApplicationContext ctx;
    @Autowired CdService cd;
    @Autowired ForeignService foreign;
    @Autowired PrivacyPurgeService purge;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 민방위_명부는_담당부서만_보고_나이를_확인하고_해제하면_파기된다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        staff.perform(get("/civil-defense")).andExpect(status().isForbidden());

        String name = "민방" + UUID.randomUUID().toString().substring(0, 4);
        int year = LocalDate.now().getYear();
        admin.perform(post("/civil-defense").param("name", name).param("birthDate", (year - 50) + "-01-01").param("address", "중앙로 1")
                        .param("unitName", "중앙동 지역대").param("joinedOn", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("편성 대상 나이")));
        admin.perform(post("/civil-defense").param("name", name).param("birthDate", (year - 30) + "-05-05").param("address", "중앙로 1")
                        .param("phone", "010-1111-2222").param("unitName", "중앙동 지역대").param("joinedOn", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("편성했습니다")));
        admin.perform(post("/civil-defense").param("name", name).param("birthDate", (year - 30) + "-05-05").param("address", "중앙로 1")
                        .param("unitName", "중앙동 지역대").param("joinedOn", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("이미 편성")));
        CdMemberVO m = cd.list(true, null, name).get(0);

        // 교육: 부대 단위 통지, 참석 기록
        String loc = admin.perform(post("/civil-defense/trainings").param("title", "기본교육").param("heldOn", LocalDate.now().toString())
                .param("place", "구민회관").param("hours", "4")).andReturn().getResponse().getRedirectedUrl();
        admin.perform(post(loc + "/invite").param("unit", "중앙동 지역대")).andExpect(flash().attribute("message", containsString("명에게 통지")));
        admin.perform(post(loc + "/result").param("memberId", "" + m.getMemberId()).param("result", "면제"))
                .andExpect(flash().attribute("message", containsString("면제 사유")));
        admin.perform(post(loc + "/result").param("memberId", "" + m.getMemberId()).param("result", "참석"))
                .andExpect(flash().attribute("message", containsString("기록했습니다")));
        assertThat(admin.perform(get("/civil-defense/" + m.getMemberId())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("기본교육").contains("참석");

        // 해제 → 보유기간이 지나면 파기
        admin.perform(post("/civil-defense/" + m.getMemberId() + "/release").param("on", LocalDate.now().toString()).param("reason", "전출"))
                .andExpect(flash().attribute("message", containsString("이후 명부에서 파기")));
        jdbc.update("UPDATE CD_MEMBER SET PURGE_AFTER = ? WHERE MEMBER_ID = ?", LocalDate.now().minusDays(1), m.getMemberId());
        PrivacyPurgeService.Purged p = purge.purge("test");
        assertThat(p.civilDefense()).isGreaterThanOrEqualTo(1);
        assertThat(cd.get(m.getMemberId())).isNull();
    }

    @Test
    void 외국인주민은_동의가_있어야_하고_등록번호는_받지_않고_동의_철회는_바로_파기된다() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        String name = "Nguyen " + UUID.randomUUID().toString().substring(0, 4);
        // 통계는 모두 볼 수 있지만 대장은 못 본다
        String page = staff.perform(get("/foreign")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("국적별 지원 현황").doesNotContain("지원 대상 올리기");
        staff.perform(get("/foreign/1")).andExpect(status().isForbidden());

        admin.perform(post("/foreign").param("name", name).param("nationality", "베트남").param("birthDate", "1995-03-03"))
                .andExpect(flash().attribute("message", containsString("동의")));
        admin.perform(post("/foreign").param("name", name).param("nationality", "베트남").param("birthDate", "1995-03-03")
                        .param("stayStatus", "950303-5123456").param("consent", "true").param("consentOn", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("외국인등록번호는 받지 않습니다")));
        admin.perform(post("/foreign").param("name", name).param("nationality", "베트남").param("birthDate", "1995-03-03")
                        .param("stayStatus", "F-6").param("language", "베트남어").param("consent", "true").param("consentOn", LocalDate.now().toString()))
                .andExpect(flash().attribute("message", containsString("올렸습니다")));
        ForeignResidentVO r = foreign.list(true, null, name).get(0);
        admin.perform(post("/foreign/" + r.getResId() + "/support").param("on", LocalDate.now().toString()).param("kind", "통역·번역")
                        .param("content", "주민센터 상담 통역"))
                .andExpect(flash().attribute("message", containsString("기록했습니다")));
        assertThat(foreign.stats()).anySatisfy(s -> assertThat(s.get("nationality")).isEqualTo("베트남"));

        admin.perform(post("/foreign/" + r.getResId() + "/end").param("on", LocalDate.now().toString()).param("reason", "본인 동의 철회"))
                .andExpect(flash().attribute("message", containsString("파기")));
        purge.purge("test");
        assertThat(foreign.get(r.getResId())).isNull();
    }
}
