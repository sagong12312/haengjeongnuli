package egovframework.haengjeongnuli.cmm.hwp;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.header.ParaHeader;
import kr.dogfoot.hwplib.object.bodytext.paragraph.lineseg.LineSegItem;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.writer.HWPWriter;

/**
 * 한글(HWP 5.0) 문서를 만든다 (hwplib, Apache-2.0). 빈 문서의 첫 문단(쪽·단 설정)을 그대로 두고
 * 그 뒤에 한 줄씩 문단을 붙인다. 글자 모양은 기본·굵게·제목(크게 굵게), 문단 모양은 왼쪽·가운데·오른쪽.
 */
@Component
public class HwpMaker {

    public enum Style { NORMAL, BOLD, TITLE }
    public enum Align { LEFT, CENTER, RIGHT }

    /** 문단 한 줄 */
    public record Line(String text, Style style, Align align) {
        public static Line of(String text) { return new Line(text, Style.NORMAL, Align.LEFT); }
        public static Line bold(String text) { return new Line(text, Style.BOLD, Align.LEFT); }
        public static Line title(String text) { return new Line(text, Style.TITLE, Align.CENTER); }
        public static Line center(String text, Style s) { return new Line(text, s, Align.CENTER); }
        public static Line right(String text) { return new Line(text, Style.NORMAL, Align.RIGHT); }
        public static Line blank() { return new Line("", Style.NORMAL, Align.LEFT); }
    }

    public byte[] make(List<Line> lines) throws Exception {
        HWPFile hwp = BlankFileMaker.make();
        Section section = hwp.getBodyText().getLastSection();
        Paragraph first = section.getParagraph(0);
        int baseChar = (int) first.getCharShape().getPositonShapeIdPairList().get(0).getShapeId();
        int basePara = first.getHeader().getParaShapeId();
        LineSegItem seg = first.getLineSeg().getLineSegItemList().get(0);

        // 글자 모양: 기본, 굵게, 제목
        List<CharShape> cs = hwp.getDocInfo().getCharShapeList();
        CharShape base = cs.get(baseChar);
        CharShape bold = base.clone();
        bold.getProperty().setBold(true);
        cs.add(bold);
        int boldId = cs.size() - 1;
        CharShape title = base.clone();
        title.getProperty().setBold(true);
        title.setBaseSize(1800);
        cs.add(title);
        int titleId = cs.size() - 1;
        // 문단 모양: 왼쪽(기본), 가운데, 오른쪽
        List<ParaShape> ps = hwp.getDocInfo().getParaShapeList();
        ParaShape center = ps.get(basePara).clone();
        center.getProperty1().setAlignment(Alignment.Center);
        ps.add(center);
        int centerId = ps.size() - 1;
        ParaShape right = ps.get(basePara).clone();
        right.getProperty1().setAlignment(Alignment.Right);
        ps.add(right);
        int rightId = ps.size() - 1;

        first.getHeader().setLastInList(lines.isEmpty());
        int vpos = seg.getLineHeight() + seg.getLineSpace();
        List<Line> all = new ArrayList<>(lines);
        for (int i = 0; i < all.size(); i++) {
            Line l = all.get(i);
            String text = clean(l.text());
            Paragraph p = section.addNewParagraph();
            ParaHeader h = p.getHeader();
            h.setLastInList(i == all.size() - 1);
            h.setParaShapeId(l.align() == Align.CENTER ? centerId : l.align() == Align.RIGHT ? rightId : basePara);
            h.setStyleId((short) 0);
            h.setCharShapeCount(1);
            h.setRangeTagCount(0);
            h.setLineAlignCount(1);
            h.setInstanceID(0);
            h.setIsMergedByTrack(0);
            p.createText();
            p.getText().addString(text);
            h.setCharacterCount(text.length() + 1);
            p.createCharShape();
            p.getCharShape().addParaCharShape(0, l.style() == Style.TITLE ? titleId : l.style() == Style.BOLD ? boldId : baseChar);
            p.createLineSeg();
            LineSegItem s = p.getLineSeg().addNewLineSegItem();
            int height = l.style() == Style.TITLE ? seg.getLineHeight() * 18 / 10 : seg.getLineHeight();
            s.setTextStartPosition(0);
            s.setLineVerticalPosition(vpos);
            s.setLineHeight(height);
            s.setTextPartHeight(height);
            s.setDistanceBaseLineToLineVerticalPosition(height * 85 / 100);
            s.setLineSpace(seg.getLineSpace());
            s.setStartPositionFromColumn(0);
            s.setSegmentWidth(seg.getSegmentWidth());
            s.getTag().setValue(seg.getTag().getValue());
            vpos += height + seg.getLineSpace();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(hwp, out);
        return out.toByteArray();
    }

    /** 한글 문단에 넣을 수 없는 제어 문자를 빼고 탭은 공백으로 */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0x20) {
                b.append(c);
            } else if (c == '\t') {
                b.append("    ");
            }
        }
        return b.toString();
    }
}
