package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.records.service.RecordFixVO;
import egovframework.haengjeongnuli.records.service.RecordService;
import egovframework.haengjeongnuli.records.service.RecordVO;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 등록정보 수정·등록 취소 (공공기록물법 시행규칙 제6조, 제15조) */
@SpringBootTest
class RecordFixTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserService users;
    @Autowired RecordService records;

    private record Staff(String id, MockMvc mvc) {}

    private Staff staff(String name, String dept, boolean manager) {
        String id = "f" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName(name);
        f.setDeptName(dept);
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("Record-fix-77!");
        LoginUser u = users.create(f);
        if (manager) {
            users.setRecordsManager(u.getUserId(), true);
        }
        LoginUser loaded = (LoginUser) users.loadUserByUsername(id);
        loaded.setPasswordChangedAt(LocalDateTime.now());
        return new Staff(id, TestUsers.mvcAs(ctx, loaded));
    }

    private long register(Staff s, String title) throws Exception {
        String url = s.mvc().perform(post("/records").param("type", "RECEIVE").param("title", title)
                        .param("regDate", LocalDate.now().toString()).param("counterpart", "OO시 총무과")
                        .param("medium", "종이문서").param("retention", "3년").param("disclosure", "공개"))
                .andReturn().getResponse().getRedirectedUrl();
        Matcher m = Pattern.compile("/records/(\\d+)$").matcher(url);
        assertThat(m.find()).as(url).isTrue();
        return Long.parseLong(m.group(1));
    }

    private long openFixId(long regId) {
        return records.fixes(regId).stream().filter(f -> RecordFixVO.REQUESTED.equals(f.getStatus()))
                .findFirst().orElseThrow().getFixId();
    }

    private static String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 기록물관리책임자가_확인해야_수정이_반영되고_이력이_남는다() throws Exception {
        String dept = "기록" + UUID.randomUUID().toString().substring(0, 4);
        Staff clerk = staff("담당자", dept, false), manager = staff("책임자", dept, true),
                otherManager = staff("남의책임자", "다른" + dept, true);
        long id = register(clerk, "오타 있는 제목");

        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "title").param("newValue", "바른 제목")
                        .param("reason", "제목 오타"))
                .andExpect(flash().attribute("message", containsString("수정을 요청했습니다")));
        assertThat(records.get(id).getTitle()).isEqualTo("오타 있는 제목");
        assertThat(body(manager.mvc(), "/notifications")).contains("수정을 요청했습니다");
        assertThat(body(manager.mvc(), "/records")).contains("확인할 수정 요청").contains("제목 오타");

        // 같은 항목은 한 번에 하나만, 값 검사
        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "title").param("newValue", "또 다른 제목").param("reason", "x"))
                .andExpect(flash().attribute("message", containsString("이미 확인을 기다리는")));
        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "pages").param("newValue", "0").param("reason", "x"))
                .andExpect(flash().attribute("message", containsString("쪽수는")));
        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "regNo").param("newValue", "X-1").param("reason", "x"))
                .andExpect(flash().attribute("message", "고칠 항목을 고르세요."));

        long fixId = openFixId(id);
        // 요청자 본인이나 다른 처리과 책임자는 확인할 수 없다
        clerk.mvc().perform(post("/records/fixes/" + fixId).param("approve", "true"))
                .andExpect(flash().attribute("message", containsString("기록물관리책임자만")));
        otherManager.mvc().perform(post("/records/fixes/" + fixId).param("approve", "true"))
                .andExpect(flash().attribute("message", containsString("기록물관리책임자만")));
        manager.mvc().perform(post("/records/fixes/" + fixId).param("approve", "true"))
                .andExpect(flash().attribute("message", "제목 수정을 확인해 반영했습니다."));

        RecordVO r = records.get(id);
        assertThat(r.getTitle()).isEqualTo("바른 제목");
        assertThat(r.getRegNo()).startsWith(dept + "-");
        RecordFixVO h = records.fixes(id).get(0);
        assertThat(h.getOldValue()).isEqualTo("오타 있는 제목");
        assertThat(h.getNewValue()).isEqualTo("바른 제목");
        assertThat(h.getReason()).isEqualTo("제목 오타");
        assertThat(h.getConfirmedBy()).isEqualTo(manager.id());
        assertThat(h.getConfirmedAt()).isNotNull();
        assertThat(body(clerk.mvc(), "/records/" + id)).contains("수정 이력").contains("오타 있는 제목 → 바른 제목");
        assertThat(body(clerk.mvc(), "/notifications")).contains("확인해 반영했습니다");

        // 책임자 자신이 요청한 것은 다른 책임자가 확인해야 한다
        manager.mvc().perform(post("/records/" + id + "/fix").param("field", "pages").param("newValue", "4").param("reason", "쪽수 누락"));
        manager.mvc().perform(post("/records/fixes/" + openFixId(id)).param("approve", "true"))
                .andExpect(flash().attribute("message", containsString("자기가 요청한 수정")));
    }

    @Test
    void 직접_등록한_기록물은_확인을_받아_등록_취소로_남긴다() throws Exception {
        String dept = "취소" + UUID.randomUUID().toString().substring(0, 4);
        Staff clerk = staff("담당자", dept, false), manager = staff("책임자", dept, true);
        String title = "중복 등록 " + UUID.randomUUID();
        long id = register(clerk, title);
        String regNo = records.get(id).getRegNo();

        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "canceled").param("newValue", "Y")
                        .param("reason", "같은 공문을 두 번 등록함"))
                .andExpect(flash().attribute("message", containsString("등록 취소를 요청했습니다")));
        manager.mvc().perform(post("/records/fixes/" + openFixId(id)).param("approve", "true"))
                .andExpect(flash().attribute("message", "등록 취소 수정을 확인해 반영했습니다."));

        RecordVO r = records.get(id);
        assertThat(r.isCanceled()).isTrue();
        assertThat(r.getRegNo()).isEqualTo(regNo);
        assertThat(body(clerk.mvc(), "/records")).contains(title).contains("등록 취소");
        clerk.mvc().perform(post("/records/" + id + "/fix").param("field", "title").param("newValue", "새 제목").param("reason", "x"))
                .andExpect(flash().attribute("message", "등록이 취소된 기록물입니다."));

        // 취소된 번호는 다시 쓰지 않는다
        long next = register(clerk, "다음 문서");
        assertThat(records.get(next).getRegNo()).isNotEqualTo(regNo);
    }

    @Test
    void 전자결재로_등록된_기록물은_취소할_수_없다() throws Exception {
        String dept = "결재" + UUID.randomUUID().toString().substring(0, 4);
        Staff drafter = staff("기안자", dept, false), approver = staff("결재자", dept, true);
        String title = "결재 문서 " + UUID.randomUUID();
        String url = drafter.mvc().perform(post("/approval/draft").param("action", "send").param("title", title)
                .param("body", "본문").param("finalApprover", approver.id())).andReturn().getResponse().getRedirectedUrl();
        long docId = Long.parseLong(url.replaceAll("\\D", ""));
        approver.mvc().perform(post("/approval/" + docId + "/approve"));
        LoginUser d = (LoginUser) users.loadUserByUsername(drafter.id());
        RecordVO r = records.list(d, LocalDate.now().getYear(), null, title).get(0);

        drafter.mvc().perform(post("/records/" + r.getRegId() + "/fix").param("field", "canceled").param("newValue", "Y").param("reason", "x"))
                .andExpect(flash().attribute("message", containsString("전자결재로 등록된 기록물은 취소할 수 없습니다")));
        assertThat(body(drafter.mvc(), "/records/" + r.getRegId())).doesNotContain("등록 취소 요청");
    }
}
