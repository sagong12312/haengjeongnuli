package egovframework.haengjeongnuli.cmm.hwp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.reader.HWPReader;

/**
 * 한글 문서(HWP·HWPX)를 내려받지 않고 화면에서 볼 수 있게 문단과 표를 HTML로 바꾼다.
 * 글꼴·쪽 모양·그림은 옮기지 않고 내용(글자·표)만 보여 준다. 글자는 모두 이스케이프한다.
 * HWP 는 hwplib(Apache-2.0)으로 읽고, HWPX 는 압축을 풀어 본문 XML 을 직접 읽는다(외부 엔티티 끔, 크기 제한).
 */
@Component
public class HwpViewer {

    public record Result(boolean ok, String html, String message) {}

    static final long MAX_FILE = 30L * 1024 * 1024;
    static final long MAX_XML = 30L * 1024 * 1024;
    static final int MAX_HTML = 3 * 1024 * 1024;
    static final int MAX_DEPTH = 6;

    public static boolean supports(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".hwp") || n.endsWith(".hwpx");
    }

    public Result render(Path file, String fileName) {
        try {
            if (Files.size(file) > MAX_FILE) {
                return new Result(false, null, "파일이 너무 커서(30MB 넘음) 화면에서 볼 수 없습니다. 내려받아 한글에서 여세요.");
            }
            String n = fileName.toLowerCase(Locale.ROOT);
            StringBuilder out = new StringBuilder("<div class=\"hwp-view\">");
            if (n.endsWith(".hwpx")) {
                hwpx(file, out);
            } else {
                hwp(file, out);
            }
            out.append("</div>");
            if (out.length() > MAX_HTML) {
                return new Result(true, out.substring(0, MAX_HTML) + "</div>", "내용이 길어 앞부분만 보여 줍니다. 전체는 내려받아 보세요.");
            }
            return new Result(true, out.toString(), null);
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            return new Result(false, null, msg.contains("encrypt") || msg.contains("password")
                    ? "암호가 걸린 문서는 화면에서 볼 수 없습니다. 내려받아 한글에서 여세요."
                    : "이 문서는 화면에서 볼 수 없는 형식입니다. 내려받아 한글에서 여세요.");
        }
    }

    // ===== HWP (5.0 바이너리) =====

    private void hwp(Path file, StringBuilder out) throws Exception {
        HWPFile hwp = HWPReader.fromFile(file.toFile());
        if (hwp.getFileHeader().hasPassword() || hwp.getFileHeader().isDRMDocument()) {
            throw new IllegalStateException("encrypted");
        }
        for (Section s : hwp.getBodyText().getSectionList()) {
            paragraphs(s.getParagraphs(), out, 0);
        }
    }

    private void paragraphs(Paragraph[] ps, StringBuilder out, int depth) throws Exception {
        if (ps == null || depth > MAX_DEPTH) {
            return;
        }
        for (Paragraph p : ps) {
            if (out.length() > MAX_HTML) {
                return;
            }
            String text = p.getText() == null ? "" : clean(p.getNormalString());
            para(text, out);
            if (p.getControlList() != null) {
                for (Control c : p.getControlList()) {
                    if (c instanceof ControlTable t) {
                        out.append("<table>");
                        for (Row r : t.getRowList()) {
                            out.append("<tr>");
                            for (Cell cell : r.getCellList()) {
                                out.append("<td>");
                                paragraphs(cell.getParagraphList().getParagraphs(), out, depth + 1);
                                out.append("</td>");
                            }
                            out.append("</tr>");
                        }
                        out.append("</table>");
                    }
                }
            }
        }
    }

    // ===== HWPX (OWPML, ZIP + XML) =====

    private void hwpx(Path file, StringBuilder out) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<ZipEntry> sections = new ArrayList<>();
            zip.stream().filter(e -> e.getName().matches("Contents/section\\d+\\.xml")).forEach(sections::add);
            if (sections.isEmpty()) {
                throw new IllegalStateException("본문이 없습니다");
            }
            sections.sort((a, b) -> Integer.compare(num(a.getName()), num(b.getName())));
            long total = 0;
            for (ZipEntry e : sections) {
                if (e.getSize() > MAX_XML || (total += Math.max(e.getSize(), 0)) > MAX_XML) {
                    throw new IllegalStateException("too large");
                }
                try (InputStream in = new LimitedInputStream(zip.getInputStream(e), MAX_XML)) {
                    section(in, out);
                }
            }
        }
    }

    private static int num(String name) {
        return Integer.parseInt(name.replaceAll("\\D", ""));
    }

    private static void section(InputStream in, StringBuilder out) throws Exception {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = f.createXMLStreamReader(in);
        Deque<StringBuilder> paras = new ArrayDeque<>();
        boolean inText = false;
        int tables = 0;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "p" -> paras.push(new StringBuilder());
                    case "t" -> inText = true;
                    case "lineBreak" -> { if (!paras.isEmpty()) { paras.peek().append('\n'); } }
                    case "tab" -> { if (!paras.isEmpty()) { paras.peek().append('\t'); } }
                    case "tbl" -> {
                        if (++tables > 5000) {
                            throw new IllegalStateException("too many tables");
                        }
                        if (!paras.isEmpty()) {
                            para(clean(paras.peek().toString()), out);
                            paras.peek().setLength(0);
                        }
                        out.append("<table>");
                    }
                    case "tr" -> out.append("<tr>");
                    case "tc" -> out.append("<td>");
                    default -> { }
                }
            } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                if (inText && !paras.isEmpty()) {
                    paras.peek().append(r.getText());
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "p" -> { if (!paras.isEmpty()) { para(clean(paras.pop().toString()), out); } }
                    case "t" -> inText = false;
                    case "tbl" -> out.append("</table>");
                    case "tr" -> out.append("</tr>");
                    case "tc" -> out.append("</td>");
                    default -> { }
                }
            }
            if (out.length() > MAX_HTML) {
                return;
            }
        }
    }

    private static void para(String text, StringBuilder out) {
        if (text.isBlank()) {
            return;
        }
        out.append("<p>").append(HtmlUtils.htmlEscape(text).replace("\n", "<br>")).append("</p>");
    }

    /** 표·그림 자리 등 보이지 않는 제어 문자를 지운다 */
    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\n' || c == '\t' || c >= 0x20) {
                b.append(c == '\t' ? ' ' : c);
            }
        }
        return b.toString().strip();
    }

    /** 압축 폭탄을 막기 위해 읽는 양을 제한한다 */
    static final class LimitedInputStream extends java.io.FilterInputStream {
        private long left;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                throw new IOException("too large");
            }
            int c = super.read();
            if (c >= 0) {
                left--;
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("too large");
            }
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }
}
