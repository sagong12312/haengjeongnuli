package egovframework.haengjeongnuli.cmm.hwp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlField;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.tool.objectfinder.FieldFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;

/**
 * 한글(HWP) 서식의 누름틀을 채운다. 서식은 한글에서 [입력 › 개체 › 필드 입력(누름틀)]으로 칸을 만들고,
 * 누름틀 이름을 '업소명'처럼 정해 두면 그 이름의 값이 들어간다. 서식의 글꼴·표·쪽 모양은 그대로 둔다.
 */
public final class HwpFormFiller {

    /** 누름틀 이름 길이·개수 한도 */
    public static final int MAX_FIELDS = 100;
    public static final int MAX_VALUE = 2000;

    private HwpFormFiller() {}

    /** 서식에 들어 있는 누름틀 이름 (나온 순서, 같은 이름은 한 번) */
    public static List<String> fieldNames(InputStream in) throws Exception {
        HWPFile hwp = HWPReader.fromInputStream(in);
        return fieldNames(hwp);
    }

    static List<String> fieldNames(HWPFile hwp) {
        ArrayList<Control> found = ControlFinder.find(hwp, (c, p, s) -> c.getType() == ControlType.FIELD_CLICKHERE);
        Set<String> names = new LinkedHashSet<>();
        for (Control c : found) {
            String n = ((ControlField) c).getName();
            if (n != null && !n.isBlank()) {
                names.add(n.trim());
            }
            if (names.size() >= MAX_FIELDS) {
                break;
            }
        }
        return new ArrayList<>(names);
    }

    /** 이 파일이 암호·배포용(DRM)이라 채울 수 없는지 */
    public static boolean locked(HWPFile hwp) {
        return hwp.getFileHeader().hasPassword() || hwp.getFileHeader().isDRMDocument();
    }

    /**
     * 누름틀을 채운 새 HWP 파일. 값이 없는 누름틀은 서식에 적힌 안내 글자를 그대로 둔다.
     * 같은 이름의 누름틀이 여러 개면 모두 같은 값으로 채운다.
     */
    public static byte[] fill(InputStream template, Map<String, String> values) throws Exception {
        HWPFile hwp = HWPReader.fromInputStream(template);
        if (locked(hwp)) {
            throw new IllegalArgumentException("암호가 걸렸거나 배포용으로 저장된 서식은 채울 수 없습니다.");
        }
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Control c : ControlFinder.find(hwp, (c, p, sec) -> c.getType() == ControlType.FIELD_CLICKHERE)) {
            String n = ((ControlField) c).getName();
            if (n != null) {
                counts.merge(n.trim(), 1, Integer::sum);
            }
        }
        for (String name : fieldNames(hwp)) {
            String v = values.get(name);
            if (v == null || v.isBlank()) {
                continue;
            }
            String value = clean(v);
            int count = counts.getOrDefault(name, 1);
            ArrayList<String> texts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                texts.add(value);
            }
            FieldFinder.setClickHereText(hwp, name, texts);
        }
        // 채운 누름틀이 든 문단은 서식에 적혀 있던 줄 정보를 지운다.
        // 그대로 두면 글이 길어졌을 때 한글이 옛 줄 위치대로 그려 글자가 겹친다 (지우면 한글이 열 때 줄을 다시 계산).
        java.util.Set<kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph> touched =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        ControlFinder.find(hwp, (c, para, sec) -> {
            if (c.getType() == ControlType.FIELD_CLICKHERE && para != null) {
                String n = ((ControlField) c).getName();
                String v = n == null ? null : values.get(n.trim());
                if (v != null && !v.isBlank()) {
                    touched.add(para);
                }
            }
            return false;
        });
        for (kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph para : touched) {
            if (para.getLineSeg() != null) {
                para.deleteLineSeg();
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(hwp, out);
        return out.toByteArray();
    }

    /** 누름틀 안에는 한 줄 글자만 넣는다 (줄바꿈·제어 문자는 띄어쓰기로) */
    static String clean(String v) {
        StringBuilder b = new StringBuilder();
        for (char ch : v.toCharArray()) {
            b.append(ch < 0x20 || ch == 0x7f ? ' ' : ch);
            if (b.length() >= MAX_VALUE) {
                break;
            }
        }
        return b.toString().trim();
    }
}
