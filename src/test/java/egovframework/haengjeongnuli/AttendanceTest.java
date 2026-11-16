package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditSearchVO;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.hr.service.AttendanceService;
import egovframework.haengjeongnuli.hr.service.AttendanceVO;

@SpringBootTest
class AttendanceTest {

    @Autowired WebApplicationContext ctx;
    @Autowired AttendanceService attendanceService;
    @Autowired AuditService auditService;

    private LoginUser someone() {
        return TestUsers.of(LoginUser.Role.GENERAL, "att" + UUID.randomUUID().toString().substring(0, 8), "근태직원");
    }

    @Test
    void 출근하면_DB에_남고_다시_로그인해도_업무_중이다() throws Exception {
        LoginUser u = someone();
        MockMvc mvc = TestUsers.mvcAs(ctx, u);
        mvc.perform(get("/")).andExpect(content().string(containsString("현재 업무 중이 아닙니다")));
        mvc.perform(post("/attendance").param("action", "in").header("Referer", "http://localhost/hr"))
                .andExpect(redirectedUrl("/hr"))
                .andExpect(flash().attribute("message", org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2} 출근 처리했습니다\\.")));

        // 세션이 다른 새 MockMvc(다시 로그인한 것과 같음)에서도 출근 상태가 유지된다
        TestUsers.mvcAs(ctx, u).perform(get("/")).andExpect(content().string(containsString("현재 업무 중입니다")));

        // 출근을 두 번 눌러도 퇴근으로 바뀌지 않는다
        mvc.perform(post("/attendance").param("action", "in"))
                .andExpect(flash().attribute("message", "이미 업무 중이어서 출근 처리하지 않았습니다."));
        assertThat(attendanceService.isWorking(u.getLoginId())).isTrue();

        mvc.perform(post("/attendance").param("action", "out"))
                .andExpect(flash().attribute("message", org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2} 퇴근 처리했습니다\\.")));
        assertThat(attendanceService.isWorking(u.getLoginId())).isFalse();
        AttendanceVO today = attendanceService.today(u.getLoginId());
        assertThat(today.getInAt()).isNotNull();
        assertThat(today.getOutAt()).isNotNull();
        assertThat(today.getWorkedText()).isNotNull();

        // 퇴근했다가 다시 출근하면 같은 날 기록을 이어 쓴다 (처음 출근 시각 유지)
        mvc.perform(post("/attendance").param("action", "in"));
        AttendanceVO again = attendanceService.today(u.getLoginId());
        assertThat(again.getAttId()).isEqualTo(today.getAttId());
        assertThat(again.getInAt()).isEqualTo(today.getInAt());
        assertThat(again.getOutAt()).isNull();

        AuditSearchVO s = new AuditSearchVO();
        s.setActorId(u.getLoginId());
        assertThat(auditService.search(s)).extracting(l -> l.getAction())
                .contains(AuditAction.ATTEND_IN, AuditAction.ATTEND_OUT);
    }

    @Test
    void 출근_없이_퇴근은_처리하지_않는다() throws Exception {
        LoginUser u = someone();
        TestUsers.mvcAs(ctx, u).perform(post("/attendance").param("action", "out"))
                .andExpect(flash().attribute("message", "업무 중이 아니어서 퇴근 처리하지 않았습니다."));
        assertThat(attendanceService.today(u.getLoginId())).isNull();
    }

    @Test
    void 인사_화면에_이번_달_기록이_나온다() throws Exception {
        LoginUser u = someone();
        MockMvc mvc = TestUsers.mvcAs(ctx, u);
        mvc.perform(post("/attendance").param("action", "in"));
        mvc.perform(post("/attendance").param("action", "out"));
        String today = LocalDate.now().toString().substring(5);
        mvc.perform(get("/hr"))
                .andExpect(content().string(containsString(YearMonth.now().getMonthValue() + "월 출퇴근 기록")))
                .andExpect(content().string(containsString(today)))
                .andExpect(content().string(containsString("출근 1일")));
        // 다른 직원에게는 내 기록이 보이지 않는다
        TestUsers.mvcAs(ctx, someone()).perform(get("/hr"))
                .andExpect(content().string(containsString("이 달에는 출퇴근 기록이 없습니다")));
        // 이상한 달 값은 이번 달로
        mvc.perform(get("/hr").param("month", "abc")).andExpect(content().string(containsString("출근 1일")));
    }
}
