package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.hwp.HwpMaker;
import egovframework.haengjeongnuli.cmm.mark.DownloadMark;

/** 내려받은 파일의 보이지 않는 워터마크와 유출 추적 */
@SpringBootTest
class DownloadMarkTest {

    @Autowired WebApplicationContext ctx;

    @Test
    void 숨긴_번호는_보이지_않고_다시_찾을_수_있다() {
        String code = DownloadMark.newCode();
        assertThat(code).matches("[0-9a-f]{10}");
        String zw = DownloadMark.zeroWidth(code);
        assertThat(zw).hasSize(50);
        assertThat(zw.chars().allMatch(c -> c == 0x200B || c == 0x200C || c == 0x2060)).isTrue();
        assertThat(DownloadMark.find("앞글" + zw + "뒷글")).containsExactly(code);
        String sp = DownloadMark.spaces(code);
        assertThat(sp.chars().allMatch(c -> c == ' ' || c == 0xA0)).isTrue(); // 빈칸뿐이라 보이지 않는다
        assertThat(DownloadMark.find("기관명\n" + sp + "\n수신")).containsExactly(code);
        // 한 글자라도 바뀌면 확인 비트가 맞지 않아 엉뚱한 사람을 가리키지 않는다
        char[] broken = zw.toCharArray();
        broken[5] = broken[5] == '​' ? '‌' : '​';
        assertThat(DownloadMark.find(new String(broken))).isEmpty();
        assertThat(DownloadMark.find("그냥 글  띄어쓰기   많이")).isEmpty();
    }

    @Test
    void CSV는_글자_칸에만_심고_숫자_칸은_그대로() {
        String code = DownloadMark.newCode();
        StringBuilder csv = new StringBuilder("﻿번호,이름,금액\r\n");
        for (int i = 1; i <= 25; i++) {
            csv.append(i).append(",\"홍길동, ").append(i).append("\",").append(i * 1000).append("\r\n");
        }
        String marked = DownloadMark.markCsv(csv.toString(), code);
        assertThat(marked).startsWith("﻿번호".replace("번호", "")); // BOM 그대로
        assertThat(DownloadMark.find(marked)).containsExactly(code);
        // 숨긴 글자를 지우면 원래 파일과 똑같다
        assertThat(marked.replaceAll("[​‌⁠]", "")).isEqualTo(csv.toString());
        // 숫자 칸은 숫자로 남고, 따옴표 칸은 따옴표 안쪽에 들어간다
        String[] lines = marked.split("\r\n");
        assertThat(lines[10]).startsWith("10,\"\u2060").endsWith(",10000");
        assertThat(lines[20]).startsWith("20,\"\u2060");
        assertThat(lines[1]).doesNotContain("\u2060");
        // 머리줄·10·20번째 줄 = 3곳 (앞뒤 표시 2자씩)
        assertThat(marked.chars().filter(c -> c == 0x2060).count()).isEqualTo(6);
    }

    @Test
    void 한글_파일에_심은_번호는_파일을_다시_읽어도_남는다() throws Exception {
        String code = DownloadMark.newCode();
        byte[] hwp = new HwpMaker().make(List.of(HwpMaker.Line.title("행정누리시"), HwpMaker.Line.of(DownloadMark.spaces(code)),
                HwpMaker.Line.of("수신  내부결재"), HwpMaker.Line.bold("제목  시험")));
        String text = kr.dogfoot.hwplib.tool.textextractor.TextExtractor.extract(
                kr.dogfoot.hwplib.reader.HWPReader.fromInputStream(new ByteArrayInputStream(hwp)),
                kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText);
        assertThat(DownloadMark.find(text)).containsExactly(code);
    }

    @Test
    void 엑셀로_내려받으면_번호가_심기고_유출_추적에서_사람을_찾는다() throws Exception {
        LoginUser staff = TestUsers.of(LoginUser.Role.GENERAL, "mark-" + System.nanoTime() % 100000, "김추적");
        var mvc = TestUsers.mvcAs(ctx, staff);
        mvc.perform(post("/reg/streetlight").param("f_on", LocalDate.now().toString()).param("f_place", "추적 시험 장소"))
                .andExpect(status().is3xxRedirection());
        byte[] csv = mvc.perform(get("/reg/streetlight/export.csv").param("reason", "유출 추적 시험")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        Set<String> codes = DownloadMark.find(text);
        assertThat(codes).hasSize(1);
        assertThat(text.replaceAll("[​‌⁠]", "")).contains("추적 시험 장소");

        // 두 번 받으면 번호가 다르다 (받은 때마다 따로 추적)
        byte[] again = mvc.perform(get("/reg/streetlight/export.csv").param("reason", "다시")).andReturn().getResponse().getContentAsByteArray();
        assertThat(DownloadMark.find(new String(again, StandardCharsets.UTF_8))).doesNotContainAnyElementsOf(codes);

        // 일반 직원은 유출 추적 화면을 못 쓴다
        mvc.perform(get("/admin/download-marks")).andExpect(status().isForbidden());

        // 관리자: 파일을 넣으면 받은 사람이 나온다
        var admin = TestUsers.mvcAs(ctx, TestUsers.admin());
        String page = admin.perform(multipart("/admin/download-marks/trace")
                        .file(new MockMultipartFile("file", "유출.csv", "text/csv", csv)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("김추적").contains("유출 추적 시험").contains(codes.iterator().next());
        // 붙여 넣은 글로도 찾는다
        String pasted = admin.perform(post("/admin/download-marks/trace").param("text", text.substring(0, Math.min(400, text.length()))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(pasted).contains("김추적");
        // 아무것도 없으면 못 찾았다고 알려 준다
        assertThat(admin.perform(post("/admin/download-marks/trace").param("text", "평범한 글")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("찾지 못했습니다");
        assertThat(admin.perform(get("/admin/download-marks").param("q", "김추적")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("김추적");
    }
}
