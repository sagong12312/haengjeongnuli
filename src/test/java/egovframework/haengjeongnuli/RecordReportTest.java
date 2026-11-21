package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@SpringBootTest
class RecordReportTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;

    private MockMvc staff(String dept) {
        String id = "p" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("담당");
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Report-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return TestUsers.mvcAs(ctx, u);
    }

    private void register(MockMvc m, String type, String title, String medium, String retention) throws Exception {
        m.perform(post("/records").param("type", type).param("title", title).param("regDate", LocalDate.now().toString())
                .param("counterpart", "OO도청").param("medium", medium).param("retention", retention).param("disclosure", "공개"));
    }

    @Test
    void 처리과별_생산현황을_세고_이관_목록을_CSV로_내려받는다() throws Exception {
        String dept = "현황" + UUID.randomUUID().toString().substring(0, 4);
        MockMvc m = staff(dept);
        MockMvc other = staff("남의" + dept);
        register(m, "PRODUCE", "생산 1", "종이문서", "5년");
        register(m, "RECEIVE", "접수 1", "전자문서", "3년");
        register(m, "RECEIVE", "=HYPERLINK(\"x\"), 접수 2", "팩스", "3년");
        register(other, "RECEIVE", "남의 부서 문서", "팩스", "1년");
        int year = LocalDate.now().getYear();

        String page = m.perform(get("/records/report").param("year", String.valueOf(year)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains(year + "년 기록물 생산현황").contains(dept).contains("비전자기록물(종이문서)")
                .contains("전자기록물").contains("5년");

        // 다른 처리과를 지정해도 내 처리과만 나온다
        String csv = m.perform(get("/records/report.csv").param("year", String.valueOf(year)).param("dept", "남의" + dept))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll("[\u200B\u200C\u2060]", "");
        assertThat(csv).startsWith("﻿처리과,등록번호").contains("생산 1").contains("접수 1")
                .contains("\"'=HYPERLINK(\"\"x\"\"), 접수 2\"").doesNotContain("남의 부서 문서");
        assertThat(csv.split("\r\n")).hasSize(4);

        // 관리자는 처리과를 골라 본다
        String all = TestUsers.mvcAs(ctx, TestUsers.admin())
                .perform(get("/records/report.csv").param("year", String.valueOf(year)).param("dept", "남의" + dept))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(all).contains("남의 부서 문서").doesNotContain("생산 1");
    }
}
