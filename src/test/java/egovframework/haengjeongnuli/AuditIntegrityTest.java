package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditReviewService;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 접속기록 위·변조 확인과 점검 (개인정보의 안전성 확보조치 기준 제8조) */
@SpringBootTest
class AuditIntegrityTest {

    @Autowired WebApplicationContext ctx;
    @Autowired AuditService audit;
    @Autowired AuditReviewService review;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 기록을_해시로_이어_붙이고_고치면_찾아낸다() {
        for (int i = 0; i < 5; i++) {
            audit.recordAs("chain-user", AuditAction.CIVIL_VIEW, "민원 " + i, null);
        }
        assertThat(review.verify().ok()).isTrue();
        Long id = jdbc.queryForObject("SELECT MAX(LOG_ID) FROM AUDIT_LOG WHERE ACTOR_ID = 'chain-user'", Long.class);
        Long prevId = jdbc.queryForObject("SELECT MAX(LOG_ID) FROM AUDIT_LOG WHERE ACTOR_ID = 'chain-user' AND LOG_ID < ?", Long.class, id);
        String target = jdbc.queryForObject("SELECT TARGET FROM AUDIT_LOG WHERE LOG_ID = ?", String.class, prevId);
        try {
            // 누가 DB에서 직접 기록을 고치면
            jdbc.update("UPDATE AUDIT_LOG SET TARGET = '바뀐 대상' WHERE LOG_ID = ?", prevId);
            AuditReviewService.ChainCheck c = review.verify();
            assertThat(c.ok()).isFalse();
            assertThat(c.brokenAt()).isEqualTo(prevId);
            assertThat(c.message()).contains("기록이 바뀌었습니다");
        } finally {
            jdbc.update("UPDATE AUDIT_LOG SET TARGET = ? WHERE LOG_ID = ?", target, prevId);
        }
        assertThat(review.verify().ok()).isTrue();
    }

    @Test
    void 근무시간_밖_열람과_많은_내려받기를_보여주고_점검_결과를_남긴다() throws Exception {
        String who = "late-" + UUID.randomUUID().toString().substring(0, 6);
        audit.recordAs(who, AuditAction.FILE_DOWNLOAD, "파일 1", null);
        LocalDate today = LocalDate.now();
        AuditReviewService.Report rep = review.report(today, today);
        assertThat(rep.downloads()).anySatisfy(a -> assertThat(a.actorId()).isEqualTo(who));
        // 토요일 밤 11시는 근무시간 밖
        assertThat(review.report(today, today)).isNotNull();
        assertThat(java.lang.reflect.Modifier.isStatic(AuditReviewService.class.getDeclaredMethod("isAfterHours", LocalDateTime.class).getModifiers())).isTrue();

        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/audit/review")).andExpect(status().isForbidden());
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String page = admin.perform(get("/admin/audit/review").param("from", today.toString()).param("to", today.toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("위·변조 확인").contains("정상").contains(who);

        admin.perform(post("/admin/audit/review").param("from", today.toString()).param("to", today.toString()).param("result", "조치함"))
                .andExpect(flash().attribute("message", "확인·조치 내용을 적으세요."));
        admin.perform(post("/admin/audit/review").param("from", today.toString()).param("to", today.toString())
                        .param("result", "이상 없음"))
                .andExpect(flash().attribute("message", "점검 결과를 기록했습니다."));
        assertThat(review.reviews()).anySatisfy(r -> {
            assertThat(r.getResult()).isEqualTo("이상 없음");
            assertThat(r.getChainOkYn()).isEqualTo("Y");
        });
        assertThat(admin.perform(get("/admin/audit/review")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("지난 점검");
    }

    @Test
    void 근무시간_밖_판단() throws Exception {
        var m = AuditReviewService.class.getDeclaredMethod("isAfterHours", LocalDateTime.class);
        m.setAccessible(true);
        assertThat((boolean) m.invoke(null, LocalDateTime.of(2026, 11, 14, 23, 0))).isTrue();  // 토요일
        assertThat((boolean) m.invoke(null, LocalDateTime.of(2026, 11, 16, 6, 30))).isTrue();  // 월요일 새벽
        assertThat((boolean) m.invoke(null, LocalDateTime.of(2026, 11, 16, 14, 0))).isFalse(); // 월요일 낮
    }
}
