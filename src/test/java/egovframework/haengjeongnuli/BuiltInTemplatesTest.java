package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.approval.service.BuiltInTemplatesAccess;

/** 기본 기안 서식 */
@SpringBootTest
class BuiltInTemplatesTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired egovframework.haengjeongnuli.approval.service.ApprovalTemplateService templates;
    @Autowired org.springframework.web.context.WebApplicationContext ctx;

    @Test
    void 서식_찾기는_띄어쓰기_대소문자를_가리지_않고_본문_품목까지_찾는다() throws Exception {
        var me = TestUsers.general();
        var hits = templates.search(me, "usb허브");
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getName()).isEqualTo("[지출품의] 전산 소모품 구매");
        // 이름에 든 것이 먼저, 본문에만 든 것은 뒤에
        assertThat(templates.search(me, "출장").get(0).getName()).contains("출장");
        // 여러 낱말은 모두 들어 있는 것만
        assertThat(templates.search(me, "현수막 제작")).extracting(t -> t.getName()).contains("[지출품의] 현수막 제작");
        assertThat(templates.search(me, "없는낱말zzz")).isEmpty();
        assertThat(templates.search(me, "").size()).isEqualTo(templates.visible(me).size());

        var mvc = TestUsers.mvcAs(ctx, me);
        String page = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/approval/draft").param("tq", "USB 허브"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("전산 소모품 구매").doesNotContain("[지출품의] 공기청정기 구매");
        String list = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/approval/templates").param("q", "USB"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(list).contains("전산 소모품 구매").contains("보안 USB 구매");
    }

    @Test
    void 기본_서식은_100개_이상이고_이름이_겹치지_않으며_모두_들어가_있다() {
        List<String[]> all = BuiltInTemplatesAccess.all();
        assertThat(all.size()).isGreaterThanOrEqualTo(100);
        Set<String> names = new HashSet<>();
        long spend = 0;
        for (String[] t : all) {
            assertThat(names.add(t[0])).as("겹치는 이름 " + t[0]).isTrue();
            assertThat(t[0].length()).isLessThanOrEqualTo(100);
            assertThat(ApprovalForm.isValidKind(t[1])).as(t[0]).isTrue();
            assertThat(ApprovalForm.RETENTIONS).contains(t[2]);
            assertThat(t[4]).as(t[0]).contains("끝.");
            if ("지출품의".equals(t[1])) {
                spend++;
            }
        }
        assertThat(spend).isGreaterThanOrEqualTo(70);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM APPROVAL_TEMPLATE WHERE OWNER_ID = 'system' AND SCOPE = 'ALL'", Integer.class);
        assertThat(n).isEqualTo(all.size());
    }
}
