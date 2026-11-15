package egovframework.haengjeongnuli.cmm.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 문서 본문을 화면용 HTML로 바꾼다. 입력한 글자는 모두 이스케이프하므로 HTML·스크립트는 그대로 글자로 보인다.
 * <ul>
 *   <li>항목 기호에 따라 들여쓴다: 1. → 가. → 1) → 가) → (1) → (가) → ① → ㉮
 *       (행정업무규정 시행규칙의 항목 구분 순서)</li>
 *   <li>'|' 로 시작하는 줄이 이어지면 표로 만든다. 둘째 줄이 |---|---| 이면 첫 줄은 제목 줄이다.</li>
 * </ul>
 */
@Component("bodyRenderer")
public class BodyRenderer {

    private static final String HANGUL = "가나다라마바사아자차카타파하";
    private static final Pattern[] LEVELS = {
        Pattern.compile("^\\d{1,3}\\.\\s"),
        Pattern.compile("^[" + HANGUL + "]\\.\\s"),
        Pattern.compile("^\\d{1,3}\\)\\s"),
        Pattern.compile("^[" + HANGUL + "]\\)\\s"),
        Pattern.compile("^\\(\\d{1,3}\\)\\s"),
        Pattern.compile("^\\([" + HANGUL + "]\\)\\s"),
        Pattern.compile("^[\\u2460-\\u2473]\\s?"),
        Pattern.compile("^[\\u326E-\\u327B]\\s?"),
    };
    private static final Pattern SEPARATOR = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$");

    /** 항목 기호의 단계 (0부터), 기호가 없으면 -1 */
    public static int level(String line) {
        String t = line.stripLeading();
        for (int i = 0; i < LEVELS.length; i++) {
            Matcher m = LEVELS[i].matcher(t);
            if (m.find()) {
                return i;
            }
        }
        return -1;
    }

    public String render(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder("<div class=\"doc-body\">");
        int lastLevel = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.stripLeading().startsWith("|")) {
                List<String> rows = new ArrayList<>();
                while (i < lines.length && lines[i].stripLeading().startsWith("|")) {
                    rows.add(lines[i].strip());
                    i++;
                }
                i--;
                table(rows, out, Math.max(lastLevel + 1, 0));
                continue;
            }
            if (line.isBlank()) {
                out.append("<div class=\"b-gap\"></div>");
                continue;
            }
            int lv = level(line);
            // 기호가 없는 줄은 앞 항목의 내용으로 보고 같은 깊이에 이어 쓴다
            int indent = lv >= 0 ? lv : Math.max(lastLevel, -1) + 1;
            if (lv >= 0) {
                lastLevel = lv;
            }
            out.append("<div class=\"b-line\" style=\"padding-left:").append(indent * 2).append("em\">")
                    .append(HtmlUtils.htmlEscape(line.strip())).append("</div>");
        }
        return out.append("</div>").toString();
    }

    private static void table(List<String> rows, StringBuilder out, int indent) {
        boolean header = rows.size() > 1 && SEPARATOR.matcher(rows.get(1)).matches();
        out.append("<div class=\"b-table-wrap\" style=\"margin-left:").append(indent * 2).append("em\"><table class=\"b-table\">");
        for (int r = 0; r < rows.size(); r++) {
            if (header && r == 1) {
                continue;
            }
            String tag = header && r == 0 ? "th" : "td";
            out.append("<tr>");
            for (String c : cells(rows.get(r))) {
                out.append('<').append(tag).append('>').append(HtmlUtils.htmlEscape(c.strip())).append("</").append(tag).append('>');
            }
            out.append("</tr>");
        }
        out.append("</table></div>");
    }

    private static List<String> cells(String row) {
        String t = row;
        if (t.startsWith("|")) {
            t = t.substring(1);
        }
        if (t.endsWith("|")) {
            t = t.substring(0, t.length() - 1);
        }
        List<String> out = new ArrayList<>();
        for (String c : t.split("\\|", -1)) {
            out.add(c);
        }
        return out;
    }
}
