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
