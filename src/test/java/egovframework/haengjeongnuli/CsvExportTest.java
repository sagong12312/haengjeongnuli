package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.text.Csv;

/** CSV 내려받기: 예산 집행 현황, 계약대장, 증명 발급 일계표·월계표 */
@SpringBootTest
class CsvExportTest {

    @Autowired WebApplicationContext ctx;

    private static String csv(MockMvc m, String url) throws Exception {
        return m.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
                // 내려받은 사람을 추적하는 보이지 않는 글자는 빼고 비교한다
                .replaceAll("[\u200B\u200C\u2060]", "");
    }

    @Test
    void 칸_값_처리() {
        assertThat(Csv.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(Csv.cell("-1")).isEqualTo("'-1");
        assertThat(Csv.cell(-1L)).isEqualTo("-1");
        assertThat(Csv.cell("가, 나")).isEqualTo("\"가, 나\"");
        assertThat(Csv.cell("말 \"따옴표\"")).isEqualTo("\"말 \"\"따옴표\"\"\"");
        assertThat(Csv.of("a", "b").row(1, null).toString()).isEqualTo("﻿a,b\r\n1,\r\n");
    }

    @Test
    void 증명_발급_일계표와_월계표() throws Exception {
        MockMvc counter = TestUsers.mvcAs(ctx, TestUsers.counter());
        String name = "일계" + UUID.randomUUID().toString().substring(0, 4);
        counter.perform(post("/civil/certs").param("certType", "주민등록표 등본").param("applicantName", name)
                .param("applicantType", "본인").param("copies", "3").param("idChecked", "true"));
        String day = csv(counter, "/civil/certs.csv?reason=test&day=" + LocalDate.now());
        assertThat(day).startsWith("﻿발급번호").contains(name).contains("종류별 합계").contains("주민등록표 등본");
        String month = csv(counter, "/civil/certs.csv?reason=test&month=" + LocalDate.now().toString().substring(0, 7));
        assertThat(month).contains(name);
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(get("/civil/certs.csv").param("reason", "시험")).andExpect(status().isForbidden());
    }

    @Test
    void 예산_집행_현황과_계약대장() throws Exception {
        MockMvc admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        assertThat(csv(admin, "/finance/budgets.csv")).startsWith("﻿회계연도,부서,정책사업").contains("합계(승인)");
        assertThat(csv(admin, "/finance/contracts.csv")).startsWith("﻿계약번호,계약명");
    }
}
