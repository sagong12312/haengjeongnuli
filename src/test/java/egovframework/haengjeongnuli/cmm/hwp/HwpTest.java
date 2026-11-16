package egovframework.haengjeongnuli.cmm.hwp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;

/** HWP 만들기·읽기, HWPX 읽기 (외부 엔티티·압축 폭탄 방어) */
class HwpTest {

    private final HwpMaker maker = new HwpMaker();
    private final HwpViewer viewer = new HwpViewer();

    @Test
    void 만든_HWP를_다시_읽으면_같은_내용이_나온다(@TempDir Path dir) throws Exception {
        byte[] bytes = maker.make(List.of(HwpMaker.Line.title("행정누리시"), HwpMaker.Line.blank(),
                HwpMaker.Line.of("수신  OO시 총무과"), HwpMaker.Line.bold("제목  시설 안전점검 협조 요청"),
                HwpMaker.Line.of("1. 관련: 안전관리계획"), HwpMaker.Line.of("  가. 기간: 12. 1.~12. 12."),
                HwpMaker.Line.right("끝."), HwpMaker.Line.center("행정누리시장", HwpMaker.Style.TITLE)));
        assertThat(bytes.length).isGreaterThan(1000);
        Path f = dir.resolve("a.hwp");
        Files.write(f, bytes);
        HWPFile hwp = HWPReader.fromFile(f.toFile());
        assertThat(hwp.getBodyText().getSectionList().get(0).getParagraphCount()).isEqualTo(9);
        HwpViewer.Result r = viewer.render(f, "a.hwp");
        assertThat(r.ok()).isTrue();
        assertThat(r.html()).contains("<p>행정누리시</p>").contains("제목  시설 안전점검 협조 요청").contains("가. 기간: 12. 1.~12. 12.")
                .contains("행정누리시장");
    }

    @Test
    void 표를_한글_표로_만들고_다시_읽어도_구조가_같다(@TempDir Path dir) throws Exception {
        byte[] bytes = maker.make(List.of(HwpMaker.Line.of("1. 예산"),
                HwpMaker.Line.table(List.of(List.of("구분", "금액", "비고"), List.of("인건비", "1,000", ""), List.of("운영비", "2,000", "추경"))),
                HwpMaker.Line.right("끝.")));
        Path f = dir.resolve("t.hwp");
        Files.write(f, bytes);
        HWPFile hwp = HWPReader.fromFile(f.toFile());
        var paras = hwp.getBodyText().getSectionList().get(0).getParagraphs();
        var table = (kr.dogfoot.hwplib.object.bodytext.control.ControlTable) paras[2].getControlList().get(0);
        assertThat(table.getTable().getRowCount()).isEqualTo(3);
        assertThat(table.getTable().getColumnCount()).isEqualTo(3);
        assertThat(table.getRowList().get(2).getCellList().get(2).getParagraphList().getNormalString()).contains("추경");
        assertThat(table.getRowList().get(0).getCellList().get(0).getListHeader().getProperty().isTitleCell()).isTrue();
        assertThat(viewer.render(f, "t.hwp").html())
                .contains("<table><tr><td><p>구분</p></td><td><p>금액</p></td><td><p>비고</p></td></tr>")
                .contains("<td><p>운영비</p></td><td><p>2,000</p></td><td><p>추경</p></td>").contains("끝.");
        // 한 번 더 저장해도 깨지지 않는다
        java.io.ByteArrayOutputStream again = new java.io.ByteArrayOutputStream();
        kr.dogfoot.hwplib.writer.HWPWriter.toStream(hwp, again);
        Files.write(f, again.toByteArray());
        assertThat(viewer.render(f, "t.hwp").html()).contains("<td><p>운영비</p></td>");
    }

    @Test
    void HWPX_본문과_표를_읽고_글자는_이스케이프한다(@TempDir Path dir) throws Exception {
        String section = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<hs:sec xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\" xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">"
                + "<hp:p><hp:run><hp:t>제목 &lt;script&gt;alert(1)&lt;/script&gt;</hp:t></hp:run></hp:p>"
                + "<hp:p><hp:run><hp:tbl><hp:tr><hp:tc><hp:subList><hp:p><hp:run><hp:t>구분</hp:t></hp:run></hp:p></hp:subList></hp:tc>"
                + "<hp:tc><hp:subList><hp:p><hp:run><hp:t>금액</hp:t></hp:run></hp:p></hp:subList></hp:tc></hp:tr></hp:tbl></hp:run></hp:p>"
                + "<hp:p><hp:run><hp:t>첫 줄</hp:t><hp:lineBreak/><hp:t>둘째 줄</hp:t></hp:run></hp:p></hs:sec>";
        Path f = dir.resolve("b.hwpx");
        Files.write(f, zip("Contents/section0.xml", section));
        HwpViewer.Result r = viewer.render(f, "b.hwpx");
        assertThat(r.ok()).isTrue();
        assertThat(r.html()).contains("&lt;script&gt;").doesNotContain("<script>")
                .contains("<table><tr><td><p>구분</p></td><td><p>금액</p></td></tr></table>")
                .contains("첫 줄<br>둘째 줄");
    }

    @Test
    void 외부_엔티티와_망가진_파일은_보여주지_않는다(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "비밀");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY s SYSTEM \"" + secret.toUri() + "\">]>"
                + "<sec><p><t>&s;</t></p></sec>";
        Path f = dir.resolve("c.hwpx");
        Files.write(f, zip("Contents/section0.xml", xxe));
        HwpViewer.Result r = viewer.render(f, "c.hwpx");
        assertThat(r.ok() && r.html().contains("비밀")).isFalse();

        Path broken = dir.resolve("d.hwp");
        Files.write(broken, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3});
        HwpViewer.Result b = viewer.render(broken, "d.hwp");
        assertThat(b.ok()).isFalse();
        assertThat(b.message()).contains("내려받아 한글에서 여세요");
    }

    private static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("mimetype"));
            z.write("application/hwp+zip".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry(name));
            z.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }
}
