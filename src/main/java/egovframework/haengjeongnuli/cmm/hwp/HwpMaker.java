package egovframework.haengjeongnuli.cmm.hwp;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Component;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.CtrlHeaderGso;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.GsoHeaderProperty;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HeightCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HorzRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.ObjectNumberSort;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.RelativeArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextFlowMethod;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextHorzArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.VertRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.WidthCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.sectiondefine.TextDirection;
import kr.dogfoot.hwplib.object.bodytext.control.gso.textbox.LineChange;
import kr.dogfoot.hwplib.object.bodytext.control.gso.textbox.TextVerticalAlignment;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.DivideAtPageBoundary;
import kr.dogfoot.hwplib.object.bodytext.control.table.ListHeaderForCell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.control.table.Table;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.ParagraphList;
import kr.dogfoot.hwplib.object.bodytext.paragraph.header.ParaHeader;
import kr.dogfoot.hwplib.object.bodytext.paragraph.lineseg.LineSegItem;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharControlExtend;
import kr.dogfoot.hwplib.object.docinfo.BorderFill;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.borderfill.BorderThickness;
import kr.dogfoot.hwplib.object.docinfo.borderfill.BorderType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.EachBorder;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.writer.HWPWriter;

/**
 * 한글(HWP 5.0) 문서를 만든다 (hwplib, Apache-2.0). 빈 문서의 첫 문단(쪽·단 설정)을 그대로 두고
 * 그 뒤에 문단과 표를 붙인다. 글자 모양은 기본·굵게·제목(크게 굵게), 문단 모양은 왼쪽·가운데·오른쪽,
 * 표는 실선 테두리(0.12mm)에 칸 너비를 똑같이 나누고 첫 줄을 제목 줄(굵게, 가운데)로 한다.
 * 한글 문서 구조(HWP 5.0 문서 형식)에 따라 표는 문단 안의 확장 제어 문자(11, 'tbl ')와 표 컨트롤로 넣는다.
 */
@Component
public class HwpMaker {

    public enum Style { NORMAL, BOLD, TITLE }
    public enum Align { LEFT, CENTER, RIGHT }

    /** 문단 한 줄, 또는 표 (rows 가 있으면 표) */
    public record Line(String text, Style style, Align align, List<List<String>> rows) {
        public Line(String text, Style style, Align align) { this(text, style, align, null); }
        public static Line of(String text) { return new Line(text, Style.NORMAL, Align.LEFT); }
        public static Line bold(String text) { return new Line(text, Style.BOLD, Align.LEFT); }
        public static Line title(String text) { return new Line(text, Style.TITLE, Align.CENTER); }
        public static Line center(String text, Style s) { return new Line(text, s, Align.CENTER); }
        public static Line right(String text) { return new Line(text, Style.NORMAL, Align.RIGHT); }
        public static Line blank() { return new Line("", Style.NORMAL, Align.LEFT); }
        /** 표: 첫 줄은 제목 줄 */
        public static Line table(List<List<String>> rows) { return new Line("", Style.NORMAL, Align.LEFT, rows); }
        public boolean isTable() { return rows != null && !rows.isEmpty(); }
    }

    /** 1mm = 283.46 HWPUNIT (1/7200 인치) */
    static final int MM = 283;
    static final int ROW_HEIGHT = 8 * MM;
    static final int CELL_LR = 510;
    static final int CELL_TB = 141;
    static final int MAX_COLS = 20;
    static final int MAX_ROWS = 500;

    /** 문서 하나를 만드는 동안 쓰는 모양 번호와 줄 위치 */
    private static final class Ctx {
        Section section;
        int baseChar, boldChar, titleChar;
        int basePara, centerPara, rightPara;
        int borderFill;
        int baseSize = 1000, titleSize = 1800;
        LineSegItem seg;
        int vpos;
        final SecureRandom random = new SecureRandom();
    }

    public byte[] make(List<Line> lines) throws Exception {
        HWPFile hwp = BlankFileMaker.make();
        Ctx c = new Ctx();
        c.section = hwp.getBodyText().getLastSection();
        Paragraph first = c.section.getParagraph(0);
        c.baseChar = (int) first.getCharShape().getPositonShapeIdPairList().get(0).getShapeId();
        c.basePara = first.getHeader().getParaShapeId();
        c.seg = first.getLineSeg().getLineSegItemList().get(0);

        // 글자 모양: 기본, 굵게, 제목
        List<CharShape> cs = hwp.getDocInfo().getCharShapeList();
        CharShape base = cs.get(c.baseChar);
        c.baseSize = base.getBaseSize() > 0 ? base.getBaseSize() : 1000;
        c.titleSize = 1800;
        CharShape bold = base.clone();
        bold.getProperty().setBold(true);
        cs.add(bold);
        c.boldChar = cs.size() - 1;
        CharShape title = base.clone();
        title.getProperty().setBold(true);
        title.setBaseSize(1800);
        cs.add(title);
        c.titleChar = cs.size() - 1;
        // 문단 모양: 왼쪽(기본), 가운데, 오른쪽
        List<ParaShape> ps = hwp.getDocInfo().getParaShapeList();
        ParaShape center = ps.get(c.basePara).clone();
        center.getProperty1().setAlignment(Alignment.Center);
        ps.add(center);
        c.centerPara = ps.size() - 1;
        ParaShape right = ps.get(c.basePara).clone();
        right.getProperty1().setAlignment(Alignment.Right);
        ps.add(right);
        c.rightPara = ps.size() - 1;
        // 표 테두리: 네 변 실선 0.12mm 검정 (테두리 번호는 1부터)
        BorderFill bf = hwp.getDocInfo().addNewBorderFill();
        for (EachBorder e : new EachBorder[] {bf.getLeftBorder(), bf.getRightBorder(), bf.getTopBorder(), bf.getBottomBorder()}) {
            e.setType(BorderType.Solid);
            e.setThickness(BorderThickness.MM0_12);
            e.getColor().setValue(0);
        }
        bf.getDiagonalBorder().setType(BorderType.None);
        bf.getDiagonalBorder().setThickness(BorderThickness.MM0_1);
        bf.getDiagonalBorder().getColor().setValue(0);
        c.borderFill = hwp.getDocInfo().getBorderFillList().size();

        first.getHeader().setLastInList(lines.isEmpty());
        c.vpos = c.seg.getLineHeight() + c.seg.getLineSpace();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            boolean last = i == lines.size() - 1;
            if (l.isTable()) {
                tableParagraph(c, c.section.addNewParagraph(), l.rows(), last);
            } else {
                int height = l.style() == Style.TITLE ? c.seg.getLineHeight() * 18 / 10 : c.seg.getLineHeight();
                int size = l.style() == Style.TITLE ? c.titleSize : c.baseSize;
                int n = textParagraph(c, c.section.addNewParagraph(), clean(l.text()), charId(c, l.style()), paraId(c, l.align()),
                        last, c.vpos, height, c.seg.getSegmentWidth(), size, l.style() != Style.NORMAL);
                c.vpos += n * (height + c.seg.getLineSpace());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(hwp, out);
        return out.toByteArray();
    }

    private static int charId(Ctx c, Style s) {
        return s == Style.TITLE ? c.titleChar : s == Style.BOLD ? c.boldChar : c.baseChar;
    }

    private static int paraId(Ctx c, Align a) {
        return a == Align.CENTER ? c.centerPara : a == Align.RIGHT ? c.rightPara : c.basePara;
    }

    private static void header(Paragraph p, int paraShape, long chars, boolean last) {
        ParaHeader h = p.getHeader();
        h.setLastInList(last);
        h.setParaShapeId(paraShape);
        h.setStyleId((short) 0);
        h.setCharShapeCount(1);
        h.setRangeTagCount(0);
        h.setLineAlignCount(1);
        h.setInstanceID(0);
        h.setIsMergedByTrack(0);
        h.setCharacterCount(chars);
    }

    private static void lineSeg(Ctx c, Paragraph p, int vpos, int height, int width) {
        p.createLineSeg();
        addSeg(c, p, 0, vpos, height, width);
    }

    private static void addSeg(Ctx c, Paragraph p, int start, int vpos, int height, int width) {
        LineSegItem s = p.getLineSeg().addNewLineSegItem();
        s.setTextStartPosition(start);
        s.setLineVerticalPosition(vpos);
        s.setLineHeight(height);
        s.setTextPartHeight(height);
        s.setDistanceBaseLineToLineVerticalPosition(height * 85 / 100);
        s.setLineSpace(c.seg.getLineSpace());
        s.setStartPositionFromColumn(0);
        s.setSegmentWidth(width);
        s.getTag().setValue(c.seg.getTag().getValue());
    }

    /**
     * 글 문단. 한글은 파일에 적힌 줄 정보대로 그리므로, 줄이 넘치면 직접 나눠 줄마다 위치를 적는다
     * (한 줄로만 적으면 긴 글이 한 줄에 겹쳐 보이고 다음 문단과도 겹친다). 돌려주는 값은 줄 수.
     */
    private static int textParagraph(Ctx c, Paragraph p, String text, int charShape, int paraShape, boolean last,
                                     int vpos, int height, int width, int fontSize, boolean bold) throws Exception {
        header(p, paraShape, text.length() + 1, last);
        p.createText();
        p.getText().addString(text);
        p.createCharShape();
        p.getCharShape().addParaCharShape(0, charShape);
        List<Integer> starts = lineStarts(text, width, fontSize, bold);
        p.createLineSeg();
        for (int i = 0; i < starts.size(); i++) {
            addSeg(c, p, starts.get(i), vpos + i * (height + c.seg.getLineSpace()), height, width);
        }
        return starts.size();
    }

    /** 글자 폭 어림 (HWPUNIT). 실제보다 조금 넓게 잡아 줄 끝이 넘치지 않게 한다 */
    static int charWidth(char ch, int fontSize, boolean bold) {
        double w;
        if (ch == ' ') {
            w = 0.5;
        } else if ((ch >= 0x1100 && ch <= 0x11FF) || (ch >= 0x2E80 && ch <= 0xA4CF) || (ch >= 0xAC00 && ch <= 0xD7A3)
                || (ch >= 0xF900 && ch <= 0xFAFF) || (ch >= 0xFF00 && ch <= 0xFF60) || (ch >= 0x2500 && ch <= 0x257F)
                || (ch >= 0x25A0 && ch <= 0x27BF) || ch == '·' || ch == '…') {
            w = 1.0;
        } else if (Character.isUpperCase(ch) || ch == 'm' || ch == 'w' || ch == 'M' || ch == 'W' || ch == '%' || ch == '@') {
            w = 0.72;
        } else {
            w = 0.58;
        }
        return (int) Math.ceil(fontSize * w * (bold ? 1.06 : 1.0));
    }

    /** 줄마다 시작하는 글자 위치. 가능하면 띄어쓰기에서 끊고, 긴 낱말은 글자 단위로 끊는다 */
    static List<Integer> lineStarts(String text, int width, int fontSize, boolean bold) {
        List<Integer> starts = new java.util.ArrayList<>();
        starts.add(0);
        int avail = Math.max(fontSize * 2, width);
        int used = 0;
        int lastSpace = -1;
        int lineStart = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int w = charWidth(ch, fontSize, bold);
            if (used + w > avail && i > lineStart) {
                int next = lastSpace >= lineStart && lastSpace + 1 < i + 1 && lastSpace + 1 > lineStart ? lastSpace + 1 : i;
                starts.add(next);
                lineStart = next;
                used = 0;
                for (int k = next; k <= i; k++) {
                    used += charWidth(text.charAt(k), fontSize, bold);
                }
                lastSpace = ch == ' ' ? i : -1;
                continue;
            }
            used += w;
            if (ch == ' ') {
                lastSpace = i;
            }
        }
        return starts;
    }

    /** 표를 담는 문단: [표 확장 제어 문자(8칸)][문단 끝] */
    private static void tableParagraph(Ctx c, Paragraph p, List<List<String>> rowsIn, boolean last) throws Exception {
        List<List<String>> rows = rowsIn.size() > MAX_ROWS ? rowsIn.subList(0, MAX_ROWS) : rowsIn;
        int cols = Math.min(MAX_COLS, rows.stream().mapToInt(List::size).max().orElse(1));
        int totalWidth = c.seg.getSegmentWidth();
        int colWidth = totalWidth / cols;
        int textWidth = Math.max(MM * 4, colWidth - CELL_LR * 2);
        int lineH = c.seg.getLineHeight() + c.seg.getLineSpace();
        int[] rowHeights = new int[rows.size()];
        int tableHeight = 0;
        for (int r = 0; r < rows.size(); r++) {
            int maxLines = 1;
            for (int col = 0; col < cols; col++) {
                String text = col < rows.get(r).size() ? clean(rows.get(r).get(col)).strip() : "";
                maxLines = Math.max(maxLines, lineStarts(text, textWidth, c.baseSize, r == 0).size());
            }
            rowHeights[r] = Math.max(ROW_HEIGHT, maxLines * lineH - c.seg.getLineSpace() + CELL_TB * 2);
            tableHeight += rowHeights[r];
        }

        header(p, c.basePara, 8 + 1, last);
        p.createText();
        HWPCharControlExtend ext = p.getText().addNewExtendControlChar();
        ext.setCode(11);
        long id = ControlType.Table.getCtrlId();
        ext.setAddition(new byte[] {(byte) id, (byte) (id >> 8), (byte) (id >> 16), (byte) (id >> 24), 0, 0, 0, 0, 0, 0, 0, 0});
        p.getText().addNewNormalChar().setCode(13);
        p.createCharShape();
        p.getCharShape().addParaCharShape(0, c.baseChar);

        ControlTable t = (ControlTable) p.addNewControl(ControlType.Table);
        CtrlHeaderGso h = t.getHeader();
        GsoHeaderProperty hp = h.getProperty();
        hp.setLikeWord(false);
        hp.setApplyLineSpace(false);
        hp.setVertRelTo(VertRelTo.Para);
        hp.setVertRelativeArrange(RelativeArrange.TopOrLeft);
        hp.setHorzRelTo(HorzRelTo.Para);
        hp.setHorzRelativeArrange(RelativeArrange.TopOrLeft);
        hp.setVertRelToParaLimit(true);
        hp.setAllowOverlap(false);
        hp.setWidthCriterion(WidthCriterion.Absolute);
        hp.setHeightCriterion(HeightCriterion.Absolute);
        hp.setProtectSize(false);
        hp.setTextFlowMethod(TextFlowMethod.TakePlace);
        hp.setTextHorzArrange(TextHorzArrange.BothSides);
        hp.setObjectNumberSort(ObjectNumberSort.Table);
        hp.setHasCaption(false);
        h.setxOffset(0);
        h.setyOffset(0);
        h.setWidth(totalWidth);
        h.setHeight(tableHeight);
        h.setzOrder(0);
        h.setOutterMarginLeft(CELL_TB);
        h.setOutterMarginRight(CELL_TB);
        h.setOutterMarginTop(CELL_TB);
        h.setOutterMarginBottom(CELL_TB);
        h.setInstanceId(c.random.nextInt(Integer.MAX_VALUE));
        h.setPreventPageDivide(false);
        h.getExplanation().setBytes(null);

        Table tb = t.getTable();
        tb.getProperty().setDivideAtPageBoundary(DivideAtPageBoundary.DivideByCell);
        tb.getProperty().setAutoRepeatTitleRow(true);
        tb.setRowCount(rows.size());
        tb.setColumnCount(cols);
        tb.setCellSpacing(0);
        tb.setLeftInnerMargin(CELL_LR);
        tb.setRightInnerMargin(CELL_LR);
        tb.setTopInnerMargin(CELL_TB);
        tb.setBottomInnerMargin(CELL_TB);
        tb.setBorderFillId(c.borderFill);

        for (int r = 0; r < rows.size(); r++) {
            tb.addCellCountOfRow(cols);
            Row row = t.addNewRow();
            for (int col = 0; col < cols; col++) {
                String text = col < rows.get(r).size() ? clean(rows.get(r).get(col)).strip() : "";
                Cell cell = row.addNewCell();
                ListHeaderForCell lh = cell.getListHeader();
                lh.setParaCount(1);
                lh.getProperty().setTextDirection(TextDirection.Horizontal);
                lh.getProperty().setLineChange(LineChange.Normal);
                lh.getProperty().setTextVerticalAlignment(TextVerticalAlignment.Center);
                lh.getProperty().setProtectCell(false);
                lh.getProperty().setTitleCell(r == 0);
                lh.getProperty().setEditableAtFormMode(false);
                lh.setColIndex(col);
                lh.setRowIndex(r);
                lh.setColSpan(1);
                lh.setRowSpan(1);
                lh.setWidth(colWidth);
                lh.setHeight(rowHeights[r]);
                lh.setLeftMargin(CELL_LR);
                lh.setRightMargin(CELL_LR);
                lh.setTopMargin(CELL_TB);
                lh.setBottomMargin(CELL_TB);
                lh.setBorderFillId(c.borderFill);
                lh.setTextWidth(textWidth);
                lh.setFieldName("");
                ParagraphList pl = cell.getParagraphList();
                textParagraph(c, pl.addNewParagraph(), text, r == 0 ? c.boldChar : c.baseChar, r == 0 ? c.centerPara : c.basePara,
                        true, 0, c.seg.getLineHeight(), textWidth, c.baseSize, r == 0);
            }
        }
        int height = tableHeight + CELL_TB * 2;
        lineSeg(c, p, c.vpos, height, c.seg.getSegmentWidth());
        c.vpos += height + c.seg.getLineSpace();
    }

    /** 한글 문단에 넣을 수 없는 제어 문자를 빼고 탭은 공백으로 */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= 0x20) {
                b.append(ch);
            } else if (ch == '\t') {
                b.append("    ");
            }
        }
        return b.toString();
    }
}
