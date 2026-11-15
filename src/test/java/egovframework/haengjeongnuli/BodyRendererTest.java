package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import egovframework.haengjeongnuli.cmm.text.BodyRenderer;

/** 본문 항목 들여쓰기·표, 스크립트 차단 */
class BodyRendererTest {

    private final BodyRenderer r = new BodyRenderer();

    @Test
    void 항목_기호_단계() {
        assertThat(BodyRenderer.level("1. 관련")).isZero();
        assertThat(BodyRenderer.level("  가. 기간")).isEqualTo(1);
        assertThat(BodyRenderer.level("1) 오전")).isEqualTo(2);
        assertThat(BodyRenderer.level("가) 회의")).isEqualTo(3);
        assertThat(BodyRenderer.level("(1) 준비")).isEqualTo(4);
        assertThat(BodyRenderer.level("(가) 자료")).isEqualTo(5);
        assertThat(BodyRenderer.level("① 첫째")).isEqualTo(6);
        assertThat(BodyRenderer.level("㉮ 하나")).isEqualTo(7);
        assertThat(BodyRenderer.level("2026. 12. 1.")).isEqualTo(-1);
        assertThat(BodyRenderer.level("일반 문장")).isEqualTo(-1);
    }

    @Test
    void 들여쓰기와_표를_만들고_HTML은_글자로_보인다() {
        String html = r.render("1. 관련: 계획\n2. 내용\n  가. 기간: 12. 1.\n이어지는 설명\n\n| 구분 | 금액 |\n|---|---|\n| 인건비 | 1,000 |\n<script>alert(1)</script>");
        assertThat(html).contains("padding-left:0em\">1. 관련: 계획")
                .contains("padding-left:2em\">가. 기간: 12. 1.")
                .contains("padding-left:4em\">이어지는 설명")
                .contains("<th>구분</th><th>금액</th>").contains("<td>인건비</td><td>1,000</td>")
                .doesNotContain("---")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>");
        assertThat(r.render("| a | <b>b</b> |")).contains("<td>&lt;b&gt;b&lt;/b&gt;</td>");
        assertThat(r.render(null)).isEmpty();
    }
}
