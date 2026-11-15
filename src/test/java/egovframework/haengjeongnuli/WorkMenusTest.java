package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.building.service.BuildingCaseVO;
import egovframework.haengjeongnuli.building.service.BuildingService;
import egovframework.haengjeongnuli.civil.service.LegalAffair;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditSearchVO;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.hygiene.service.HygieneService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;
import egovframework.haengjeongnuli.welfare.service.WelfareCaseVO;
import egovframework.haengjeongnuli.welfare.service.WelfareService;

@SpringBootTest
class WorkMenusTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired BuildingService building;
    @Autowired HygieneService hygiene;
    @Autowired WelfareService welfare;
    @Autowired AuditService auditService;

    private LoginUser staff(String dept, LoginUser.Role role, boolean welfareStaff) {
        String id = "w" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("직원" + id.substring(1, 4));
        f.setDeptName(dept);
        f.setRole(role);
        f.setPassword("Staff-pass-77!");
        LoginUser u = userService.create(f);
        if (welfareStaff) {
            userService.setWelfareStaff(u.getUserId(), true);
        }
        LoginUser fresh = userService.findByLoginId(id);
        fresh.setPasswordChangedAt(LocalDateTime.now());
        return fresh;
    }

    @Test
    void 인허가_사무의_법정_처리기간() {
        assertThat(LegalAffair.BUILD_REPORT.periodDays()).isEqualTo(5);          // 건축법 제14조 제3항
        assertThat(LegalAffair.BUILD_REPORT_REVIEW.periodDays()).isEqualTo(20);
        assertThat(LegalAffair.FOOD_REPORT.periodDays()).isEqualTo(3);           // 식품위생법 제37조 제12항
        assertThat(LegalAffair.FOOD_REPORT.isDeemed()).isTrue();
    }

    @Test
    void 영업신고_민원은_수리_간주를_안내한다() throws Exception {
        LoginUser counter = staff("민원봉사과", LoginUser.Role.COUNTER, false);
        MockMvc mvc = TestUsers.mvcAs(ctx, counter);
        String url = mvc.perform(post("/civil/new").param("kind", "LEGAL").param("affair", "FOOD_REPORT").param("route", "방문")
                        .param("name", "김사장").param("mobile", "010-2222-3333").param("notify", "문자")
                        .param("title", "일반음식점 영업신고").param("content", "신고합니다").param("privacyConsent", "true"))
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(url)).andExpect(content().string(containsString("신고를 수리한 것으로 봅니다")));
        mvc.perform(get("/hygiene")).andExpect(content().string(containsString("일반음식점 영업신고")));
    }

    @Test
    void 건축_인허가_대장_착수기한() throws Exception {
        LoginUser builder = staff("건축과", LoginUser.Role.GENERAL, false);
        LoginUser other = staff("총무과", LoginUser.Role.GENERAL, false);
        LocalDate d = LocalDate.now().minusDays(10);

        assertThat(building.register("REPORT", null, "홍길동", "OO동 1-1", "단독주택", 80.0, 1, false, d, null, other).ok()).isFalse();
        assertThat(building.register("REPORT", null, "홍길동", "OO동 1-1", "단독주택", 80.0, 1, false, d, null, builder).ok()).isTrue();
        // 신고 1년, 허가 2년, 공장 허가 3년
        assertThat(BuildingService.startDeadline("REPORT", false, d)).isEqualTo(d.plusYears(1));
        assertThat(BuildingService.startDeadline("PERMIT", false, d)).isEqualTo(d.plusYears(2));
        assertThat(BuildingService.startDeadline("PERMIT", true, d)).isEqualTo(d.plusYears(3));

        BuildingCaseVO c = building.list("OO동 1-1").get(0);
        assertThat(building.extend(c.getCaseId(), 13, "사유", builder).message()).contains("1년");
        assertThat(building.extend(c.getCaseId(), 6, "자재 수급 지연", builder).ok()).isTrue();
        assertThat(building.get(c.getCaseId()).getStartDeadline()).isEqualTo(d.plusYears(1).plusMonths(6));
        assertThat(building.extend(c.getCaseId(), 6, "또", builder).message()).contains("이미 연장");
        assertThat(building.started(c.getCaseId(), LocalDate.now(), builder).ok()).isTrue();
        assertThat(building.get(c.getCaseId()).getStatus()).isEqualTo("STARTED");

        // 착수기한이 지난 건은 표시된다
        building.register("PERMIT", null, "이건축", "OO동 9-9", "근린생활시설", null, null, false,
                LocalDate.now().minusYears(3), null, builder);
        assertThat(building.list("OO동 9-9").get(0).isOverdue()).isTrue();
        TestUsers.mvcAs(ctx, other).perform(get("/building")).andExpect(status().isOk())
                .andExpect(content().string(containsString("허가 취소 대상")));
    }

    @Test
    void 영업소와_위생_점검() {
        LoginUser inspector = staff("위생과", LoginUser.Role.GENERAL, false);
        HygieneService.Result r = hygiene.register("맛있는식당", "박대표", "OO로 10", "일반음식점영업",
                LocalDate.now().minusDays(30), null, inspector);
        assertThat(r.ok()).isTrue();
        assertThat(hygiene.register("x", "y", "z", "유흥주점영업", LocalDate.now(), null, inspector).message()).contains("업종");
        assertThat(hygiene.inspect(r.id(), LocalDate.now(), "부적합", null, "시정명령", inspector).message()).contains("위반 내용");
        assertThat(hygiene.inspect(r.id(), LocalDate.now(), "부적합", "조리장 위생 불량", "조치 없음", inspector).message()).contains("조치");
        assertThat(hygiene.inspect(r.id(), LocalDate.now(), "부적합", "조리장 위생 불량", "시정명령", inspector).ok()).isTrue();
        assertThat(hygiene.get(r.id()).getLastResult()).isEqualTo("부적합");
        assertThat(hygiene.close(r.id(), LocalDate.now(), inspector).ok()).isTrue();
        assertThat(hygiene.inspect(r.id(), LocalDate.now(), "적합", null, "조치 없음", inspector).message()).contains("폐업");
    }

    @Test
    void 기초생활보장_급여_신청_결정() throws Exception {
        LoginUser worker = staff("복지정책과", LoginUser.Role.GENERAL, true);
        LoginUser normal = staff("복지정책과", LoginUser.Role.GENERAL, false);
        TestUsers.mvcAs(ctx, normal).perform(get("/welfare")).andExpect(status().isForbidden())
                .andExpect(content().string(containsString("사회복지 담당 직원만")));

        LocalDate applied = LocalDate.now().minusDays(5);
        WelfareService.Result r = welfare.apply("최수급", LocalDate.of(1960, 5, 5), "OO동", "010-0000-0000", applied,
                List.of("생계급여", "의료급여"), worker);
        assertThat(r.ok()).as(r.message()).isTrue();
        WelfareCaseVO c = welfare.get(r.id());
        assertThat(c.getDeadline()).isEqualTo(applied.plusDays(30));         // 제26조 제4항

        TestUsers.mvcAs(ctx, normal).perform(get("/welfare/" + r.id())).andExpect(status().isForbidden());
        TestUsers.mvcAs(ctx, worker).perform(get("/welfare/" + r.id())).andExpect(status().isOk());
        AuditSearchVO s = new AuditSearchVO();
        s.setActorId(worker.getLoginId());
        s.setAction(AuditAction.WELFARE_VIEW);
        assertThat(auditService.count(s)).isEqualTo(1);

        assertThat(welfare.extend(r.id(), 0, "부양의무자 금융자료 회신 대기", worker).ok()).isTrue();
        assertThat(welfare.get(r.id()).getDeadline()).isEqualTo(applied.plusDays(60));
        assertThat(welfare.extend(r.id(), 1, "또", worker).message()).contains("이미 연장");

        assertThat(welfare.decide(r.id(), true, List.of("생계급여"), " ", "매월 20일 입금", null, LocalDate.now(), worker).message())
                .contains("산출 근거");
        assertThat(welfare.decide(r.id(), true, List.of("주거급여"), "요지", "입금", null, LocalDate.now(), worker).message())
                .contains("신청한 급여");
        assertThat(welfare.decide(r.id(), true, List.of("생계급여"), "소득인정액 OO원, 생계급여 선정기준 이하 — 월 OO원",
                "매월 20일 계좌 입금", null, LocalDate.now(), worker).ok()).isTrue();
        WelfareCaseVO done = welfare.get(r.id());
        assertThat(done.getStatus()).isEqualTo("GRANTED");
        assertThat(done.getBenefitStart()).isEqualTo(applied);              // 제27조 제1항: 신청일부터
        TestUsers.mvcAs(ctx, worker).perform(get("/welfare/" + r.id()))
                .andExpect(content().string(containsString("90일 이내")));
    }

    @Test
    void 통계_화면과_CSV() throws Exception {
        MockMvc mvc = TestUsers.mvcAs(ctx, TestUsers.general());
        mvc.perform(get("/stats")).andExpect(status().isOk()).andExpect(content().string(containsString("월별 민원")));
        byte[] csv = mvc.perform(get("/stats/civil.csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿").contains("월,접수,처리");
    }
}
