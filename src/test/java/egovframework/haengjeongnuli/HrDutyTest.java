package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.hr.service.DutyKind;
import egovframework.haengjeongnuli.hr.service.DutyService;
import egovframework.haengjeongnuli.hr.service.LeavePolicy;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class HrDutyTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService userService;
    @Autowired DutyService dutyService;
    @Autowired ApprovalService approvalService;
    @Autowired HolidayService holidays;

    private record Staff(LoginUser u, MockMvc mvc) {}

    private Staff staff(String name, LocalDate hiredOn) {
        String id = "h" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName("총무과");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Staff-pass-77!");
        LoginUser created = userService.create(f);
        if (hiredOn != null) {
            created.setHiredOn(hiredOn);
            created.setEmpNo("E" + id);
            userService.updateProfile(created);
        }
        LoginUser u = userService.findByLoginId(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(u, TestUsers.mvcAs(ctx, u));
    }

    /** from 이후 첫 근무일 */
    private LocalDate workday(LocalDate from) {
        LocalDate d = from;
        while (!holidays.isWorkingDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void 재직기간별_연가_일수() {
        // 지방공무원 복무규정 제7조 제1항 표
        assertThat(LeavePolicy.granted(LocalDate.of(2025, 12, 1), 2026)).isEqualTo(11);   // 1개월 이상 1년 미만
        assertThat(LeavePolicy.granted(LocalDate.of(2024, 6, 1), 2026)).isEqualTo(15);    // 1년 이상 3년 미만
        assertThat(LeavePolicy.granted(LocalDate.of(2022, 6, 1), 2026)).isEqualTo(16);    // 3년 이상 4년 미만
        assertThat(LeavePolicy.granted(LocalDate.of(2021, 6, 1), 2026)).isEqualTo(17);    // 4년 이상 5년 미만
        assertThat(LeavePolicy.granted(LocalDate.of(2020, 3, 1), 2026)).isEqualTo(20);    // 5년 이상 6년 미만
        assertThat(LeavePolicy.granted(LocalDate.of(2019, 1, 1), 2026)).isEqualTo(21);    // 6년 이상
        // 그 해 7월 1일 신규 임용: 6개월 근무 → 11 × 6/12 = 5.5 → 반올림 6
        assertThat(LeavePolicy.granted(LocalDate.of(2026, 7, 1), 2026)).isEqualTo(6);
        assertThat(LeavePolicy.granted(null, 2026)).isNull();
    }

    @Test
    void 연가_신청부터_결재까지() throws Exception {
        Staff me = staff("신청자", LocalDate.of(2020, 3, 1));
        Staff boss = staff("과장", LocalDate.of(2010, 1, 1));
        LocalDate d1 = workday(LocalDate.now().plusDays(7));
        LocalDate d2 = workday(d1.plusDays(1));
        int year = d1.getYear();
        double before = dutyService.leave(me.u(), year).remaining();

        // 결재권자 없이는 올라가지 않는다
        assertThat(dutyService.apply(DutyKind.ANNUAL, d1, d2, null, null, null, null, "가족 행사", null, me.u()).message())
                .contains("결재권자");
        // 두 근무일을 걸치면(사이 주말·공휴일 제외) 2일
        DutyService.Result r = dutyService.apply(DutyKind.ANNUAL, d1, d2, null, null, null, null, "가족 행사",
                boss.u().getLoginId(), me.u());
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(dutyService.leave(me.u(), year).pending()).isEqualTo(2.0);
        assertThat(dutyService.leave(me.u(), year).remaining()).isEqualTo(before - 2);

        // 같은 날 다시 신청하면 겹친다
        assertThat(dutyService.apply(DutyKind.SPECIAL, d1, d1, null, null, null, null, "x", boss.u().getLoginId(), me.u()).message())
                .contains("겹칩니다");
        // 남은 연가를 넘을 수 없다
        LocalDate far = workday(d2.plusDays(1));
        assertThat(dutyService.apply(DutyKind.ANNUAL, far, LocalDate.of(far.getYear(), 12, 31), null, null, null, null, "긴 휴가",
                boss.u().getLoginId(), me.u()).message()).contains("남은 연가");

        // 결재가 끝나면 사용으로
        boss.mvc().perform(post("/approval/" + r.docId() + "/approve"));
        assertThat(dutyService.leave(me.u(), year).used()).isEqualTo(2.0);
        assertThat(dutyService.leave(me.u(), year).pending()).isZero();
        String hr = me.mvc().perform(get("/hr")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(hr).contains("승인").contains("E" + me.u().getLoginId()).contains("2020-03-01");
    }

    @Test
    void 임용일이_없으면_연가를_계산하지_못하고_시간외근무는_시간으로() throws Exception {
        Staff me = staff("신규", null);
        Staff boss = staff("과장", null);
        LocalDate d = workday(LocalDate.now().plusDays(3));
        assertThat(dutyService.apply(DutyKind.ANNUAL, d, d, null, null, null, null, "x", boss.u().getLoginId(), me.u()).message())
                .contains("임용일");
        // 반일 연가 대신 공가는 임용일과 관계없다, 반일은 0.5일
        DutyService.Result official = dutyService.apply(DutyKind.OFFICIAL, d, d, "AM", null, null, null, "건강검진",
                boss.u().getLoginId(), me.u());
        assertThat(official.ok()).as(official.message()).isTrue();
        assertThat(dutyService.list(me.u(), null, 10).get(0).getDays()).isEqualTo(0.5);

        DutyService.Result ot = dutyService.apply(DutyKind.OVERTIME, d, null, null, "18:00", "20:30", null, "보고서 작성",
                boss.u().getLoginId(), me.u());
        assertThat(ot.ok()).as(ot.message()).isTrue();
        assertThat(dutyService.list(me.u(), null, 10)).anyMatch(x -> x.getKind() == DutyKind.OVERTIME && x.getHours() == 2.5);
        assertThat(dutyService.apply(DutyKind.TRIP, d, d, null, null, null, " ", "회의", boss.u().getLoginId(), me.u()).message())
                .contains("출장지");
        // 결재 문서에 신청 내용이 들어간다
        assertThat(approvalService.get(ot.docId()).getTitle()).startsWith("[시간외근무]");
    }

    @Test
    void 관리자가_인사정보를_고친다() throws Exception {
        Staff s = staff("인사대상", null);
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        admin.perform(post("/admin/users/" + s.u().getUserId() + "/profile").param("userName", "인사대상")
                .param("positionName", "팀장").param("empNo", "2026-0001").param("jobSeries", "행정")
                .param("hiredOn", "2015-03-02").param("extension", "1234"));
        LoginUser u = userService.findByLoginId(s.u().getLoginId());
        assertThat(u.getEmpNo()).isEqualTo("2026-0001");
        assertThat(u.getHiredOn()).isEqualTo(LocalDate.of(2015, 3, 2));
        assertThat(u.getPositionName()).isEqualTo("팀장");
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/admin/users/" + s.u().getUserId() + "/profile"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
