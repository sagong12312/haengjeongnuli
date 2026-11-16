package egovframework.haengjeongnuli.cmm.hwp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.ControlField;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.bookmark.ParameterItem;
import kr.dogfoot.hwplib.object.bodytext.control.bookmark.ParameterType;
import kr.dogfoot.hwplib.object.bodytext.control.bookmark.ParameterSet;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharControlExtend;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharControlInline;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import kr.dogfoot.hwplib.writer.HWPWriter;

/** 누름틀이 있는 서식을 만들어 채워 본다 */
public class HwpFormFillerTest {

    /** "업소명: [누름틀 업소명] / 대표: [누름틀 대표자] / 다시 [누름틀 업소명]" 서식 */
    public static byte[] template() throws Exception {
        HWPFile hwp = BlankFileMaker.make();
        Paragraph p = hwp.getBodyText().getSectionList().get(0).getParagraph(0);
        p.getText().getCharList().removeIf(c -> c.getCode() == 13);
        p.getText().addString("업소명: ");
        field(p, "업소명", "여기에 업소명");
        p.getText().addString(" / 대표: ");
        field(p, "대표자", "대표자 이름");
        p.getText().addString(" / 다시 ");
        field(p, "업소명", "여기에 업소명");
        p.getText().addNewNormalChar().setCode(13);
        p.getHeader().setCharacterCount(p.getText().getCharSize());
        p.getLineSeg().getLineSegItemList().clear();
        p.getLineSeg().addNewLineSegItem();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(hwp, out);
        return out.toByteArray();
    }

    private static void field(Paragraph p, String name, String placeholder) throws Exception {
        byte[] id = {'k', 'l', 'c', '%', 0, 0, 0, 0, 0, 0, 0, 0};
        HWPCharControlExtend start = p.getText().addNewExtendControlChar();
        start.setCode(3);
        start.setAddition(id);
        p.getText().addString(placeholder);
        HWPCharControlInline end = p.getText().addNewInlineControlChar();
        end.setCode(4);
        end.setAddition(id);
        ControlField f = (ControlField) p.addNewControl(ControlType.FIELD_CLICKHERE);
        f.getHeader().getCommand().fromUTF16LEString("Clickhere:set:" + (placeholder.length() * 2) + ":Direction:wstring:"
                + placeholder.length() + ":" + placeholder + " ");
        f.createCtrlData();
        ParameterSet ps = f.getCtrlData().getParameterSet();
        ps.setId(0x21B);
        ParameterItem item = ps.addNewParameterItem();
        item.setId(0x4000);
        item.setType(ParameterType.String);
        item.setValue_BSTR(name);
        if (!name.equals(f.getName())) {
            throw new IllegalStateException("이름 설정 실패: " + f.getName());
        }
    }

    private static String text(byte[] hwp) throws Exception {
        HWPFile f = HWPReader.fromInputStream(new ByteArrayInputStream(hwp));
        return TextExtractor.extract(f, TextExtractMethod.OnlyMainParagraph);
    }

    @Test
    void 누름틀_이름을_찾고_같은_이름은_모두_채운다() throws Exception {
        byte[] t = template();
        assertThat(HwpFormFiller.fieldNames(new ByteArrayInputStream(t))).containsExactly("업소명", "대표자");
        byte[] filled = HwpFormFiller.fill(new ByteArrayInputStream(t), Map.of("업소명", "행복식당", "대표자", "김\n대표"));
        String s = text(filled);
        assertThat(s).contains("업소명: 행복식당").contains("대표: 김 대표").contains("다시 행복식당").doesNotContain("여기에 업소명");
        // 다시 읽어도 누름틀이 남아 있다 (한글에서 또 고칠 수 있게)
        assertThat(HwpFormFiller.fieldNames(new ByteArrayInputStream(filled))).containsExactly("업소명", "대표자");
        // 값이 없는 누름틀은 안내 글자 그대로
        assertThat(text(HwpFormFiller.fill(new ByteArrayInputStream(t), Map.of("업소명", "가게")))).contains("대표자 이름");
    }
}
