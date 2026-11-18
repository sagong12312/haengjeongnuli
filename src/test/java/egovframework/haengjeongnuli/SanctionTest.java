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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.haengjeongnuli.cmm.hwp.HwpFormService;
import egovframework.haengjeongnuli.hygiene.service.HygieneService;
import egovframework.haengjeongnuli.hygiene.service.SanctionService;
import egovframework.haengjeongnuli.hygiene.service.SanctionVO;

/** 공중위생 영업소와 행정처분 대장 */
@SpringBootTest
class SanctionTest {

    @Autowired WebApplicationContext ctx;
    @Autowired HygieneService hygiene;
    @Autowired SanctionService sanctions;
    @Autowired HwpFormService forms;

    @Test
    void 영업정지는_6개월_이내이고_정지_중에는_폐업할_수_없고_공개는_고른_것만() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        MockMvc staff = TestUsers.mvcAs(ctx, TestUsers.general());
        String name = "행복이용원" + UUID.randomUUID().toString().substring(0, 4);
        LocalDate today = LocalDate.now();
        long biz = hygiene.register(name, "김이발", "중앙로 5", "이용업", today.minusYears(1), null, TestUsers.admin()).id();
        assertThat(hygiene.get(biz).getLawName()).isEqualTo("공중위생관리법");

        // 위생 부서가 아니면 기록할 수 없다
        staff.perform(post("/hygiene/" + biz + "/sanctions").param("kind", "영업정지").param("violation", "x"))
                .andExpect(flash().attribute("message", containsString("위생 담당 부서")));
        // 6개월을 넘기면 안 된다
        admin.perform(post("/hygiene/" + biz + "/sanctions").param("kind", "영업정지").param("violation", "소독 기준 위반")
                        .param("decidedOn", today.toString()).param("startDate", today.toString()).param("endDate", today.plusMonths(6).toString()))
                .andExpect(flash().attribute("message", containsString("6개월 이내")));
        admin.perform(post("/hygiene/" + biz + "/sanctions").param("kind", "영업정지").param("violation", "소독 기준 위반")
                        .param("decidedOn", today.toString()).param("startDate", today.toString()).param("endDate", today.plusDays(9).toString())
                        .param("publish", "true"))
                .andExpect(flash().attribute("message", containsString("행정처분을 기록했습니다")));
        SanctionVO s = sanctions.byBusiness(biz).get(0);
        assertThat(s.getBasis()).isEqualTo("공중위생관리법 제11조"); // 비우면 기본 조항
        assertThat(s.isInForce()).isTrue();
        // 과징금은 금액이 있어야 한다
        admin.perform(post("/hygiene/" + biz + "/sanctions").param("kind", "과징금").param("violation", "소독 기준 위반")
                        .param("decidedOn", today.toString()))
                .andExpect(flash().attribute("message", containsString("금액")));

        // 공중위생영업은 정지 기간 중 폐업 불가
        admin.perform(post("/hygiene/" + biz + "/close").param("on", today.toString()))
                .andExpect(flash().attribute("message", containsString("영업정지 등의 기간 중")));
        String view = admin.perform(get("/hygiene/" + biz)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(view).contains("영업정지 중").contains("공중위생관리법 제11조");

        // 서식 채우기 값
        assertThat(forms.values("hygiene", biz, TestUsers.admin())).containsEntry("처분종류", "영업정지").containsEntry("근거법률", "공중위생관리법")
                .containsKey("처분기간");

        // 전자민원 공개: 대표자 이름은 없다
        MockMvc api = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String tag = name.substring(name.length() - 4);
        String body = api.perform(EPetitionApiTest.signed("GET", "/api/epetition/v1/dispositions?q=" + tag, null, EPetitionApiTest.nonce()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode d = new ObjectMapper().readTree(body).get("data");
        assertThat(d.get("total").asInt()).isEqualTo(1);
        assertThat(d.get("items").get(0).get("kind").asText()).isEqualTo("영업정지");
        assertThat(body).doesNotContain("김이발");

        // 취소하면 공개에서도 빠지고, 폐업할 수 있다
        admin.perform(post("/hygiene/sanctions/" + s.getSanctionId() + "/cancel").param("reason", "행정심판 인용"))
                .andExpect(flash().attribute("message", containsString("취소로 표시")));
        body = api.perform(EPetitionApiTest.signed("GET", "/api/epetition/v1/dispositions?q=" + tag, null, EPetitionApiTest.nonce()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(new ObjectMapper().readTree(body).get("data").get("total").asInt()).isZero();
        admin.perform(post("/hygiene/" + biz + "/close").param("on", today.toString()))
                .andExpect(flash().attribute("message", containsString("폐업으로 처리")));

        // 식품: 영업소 폐쇄를 기록하면 대장도 폐업
        long food = hygiene.register("폐쇄식당" + tag, "이대표", "시장로 1", "일반음식점영업", today.minusYears(2), null, TestUsers.admin()).id();
        assertThat(sanctions.record(food, "영업소 폐쇄", "무신고 영업장 확장", null, today, null, null, null, null, false, TestUsers.admin())
                .message()).contains("폐업으로 바꿨습니다");
        assertThat(hygiene.get(food).isOpen()).isFalse();
        assertThat(sanctions.byBusiness(food).get(0).getBasis()).isEqualTo("식품위생법 제75조");
        assertThat(admin.perform(get("/hygiene/sanctions.csv?reason=test")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("폐쇄식당" + tag);
    }
}
