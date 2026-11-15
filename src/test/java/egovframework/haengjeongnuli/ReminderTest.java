package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.cmm.notification.ReminderService;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 매일 아침 기한·대기 알림 */
@SpringBootTest
class ReminderTest {

    @Autowired WebApplicationContext ctx;
    @Autowired ReminderService reminders;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;

    private LoginUser staff(String name, String dept) {
        String id = "m" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Remind-pass-77!");
        users.create(f);
        LoginUser u = (LoginUser) users.loadUserByUsername(id);
        u.setPasswordChangedAt(LocalDateTime.now());
        return u;
    }

    private String latestMessage(String loginId) {
        return jdbc.queryForObject("SELECT MESSAGE FROM (SELECT MESSAGE FROM NOTIFICATION WHERE LOGIN_ID = ? ORDER BY NOTI_ID DESC) WHERE ROWNUM = 1",
                String.class, loginId);
    }

    @Test
    void 기한이_다가온_민원과_오래_기다린_결재를_알린다() throws Exception {
        String dept = "알림" + UUID.randomUUID().toString().substring(0, 4);
        LoginUser a = staff("담당", dept), drafter = staff("기안", dept), approver = staff("결재", dept);

        String title = "기한 임박 민원 " + UUID.randomUUID();
        String url = TestUsers.mvcAs(ctx, TestUsers.counter()).perform(post("/civil/new").param("kind", "OTHER").param("route", "방문")
                .param("name", "김민원").param("mobile", "010-1111-2222").param("notify", "문자").param("title", title)
                .param("content", "내용").param("privacyConsent", "true").param("dept", dept)).andReturn().getResponse().getRedirectedUrl();
        long civilId = Long.parseLong(url.replaceAll("\\D", ""));
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 8, 40);
        jdbc.update("UPDATE CIVIL_COMPLAINT SET DEADLINE_AT = ?, STATUS = 'IN_PROGRESS', ASSIGNEE_ID = NULL WHERE COMPLAINT_ID = ?",
                now.plusHours(5), civilId);

        String docTitle = "오래 기다린 문서 " + UUID.randomUUID();
        String du = TestUsers.mvcAs(ctx, drafter).perform(post("/approval/draft").param("action", "send").param("title", docTitle)
                .param("body", "본문").param("finalApprover", approver.getLoginId())).andReturn().getResponse().getRedirectedUrl();
        long docId = Long.parseLong(du.replaceAll("\\D", ""));
        jdbc.update("UPDATE APPROVAL_DOC SET SUBMITTED_AT = ? WHERE DOC_ID = ?", now.minusDays(5), docId);

        ReminderService.Sent s = reminders.run(now);
        assertThat(s.civil()).isGreaterThanOrEqualTo(1);
        assertThat(s.approval()).isGreaterThanOrEqualTo(1);
        // 담당자가 없으면 처리 부서 직원에게
        assertThat(latestMessage(a.getLoginId())).contains(title).contains("5시간 남았습니다");
        assertThat(latestMessage(approver.getLoginId())).contains(docTitle).contains("결재를 기다리고 있습니다");

        // 기한이 지나면 넘겼다고 알린다
        jdbc.update("UPDATE CIVIL_COMPLAINT SET ASSIGNEE_ID = ? WHERE COMPLAINT_ID = ?", a.getLoginId(), civilId);
        reminders.run(now.plusHours(6));
        assertThat(latestMessage(a.getLoginId())).contains("처리기한").contains("넘겼습니다");
    }
}
