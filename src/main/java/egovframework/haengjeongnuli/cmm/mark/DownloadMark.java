package egovframework.haengjeongnuli.cmm.mark;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 내려받은 파일에 심는 보이지 않는 워터마크.
 * 추적 번호(40비트 = 16진수 10자리)에 확인용 8비트를 붙여 48비트를 글자로 숨긴다.
 *  - 엑셀(CSV): 폭 없는 글자 (U+200B = 0, U+200C = 1), 앞뒤에 U+2060
 *  - 한글(HWP): 줄 끝의 띄어쓰기 (보통 빈칸 = 0, 붙임 빈칸 U+00A0 = 1), 앞에 정해진 빈칸 무늬
 * 번호만 숨기고, 누가 언제 받았는지는 서버의 DOWNLOAD_MARK 표에 적는다.
 * 사진을 찍거나 손으로 옮겨 적으면 남지 않으므로, 화면·인쇄의 보이는 워터마크와 함께 쓴다.
 */
public final class DownloadMark {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char ZW0 = '​';
    private static final char ZW1 = '‌';
    private static final char ZW_EDGE = '⁠';
    private static final char NB = ' ';
    /** 한글용 시작 무늬: 붙임 빈칸·빈칸·붙임 빈칸·붙임 빈칸·빈칸·붙임 빈칸 */
    private static final String SP_SYNC = "" + NB + ' ' + NB + NB + ' ' + NB;
    private static final Pattern ZW_RE = Pattern.compile(ZW_EDGE + "([" + ZW0 + ZW1 + "]{48})" + ZW_EDGE);
    private static final Pattern SP_RE = Pattern.compile(Pattern.quote(SP_SYNC) + "([ " + NB + "]{48})");
    private static final Pattern CODE_RE = Pattern.compile("[0-9a-f]{10}");

    private DownloadMark() {}

    public static String newCode() {
        long v = RANDOM.nextLong() & 0xFFFFFFFFFFL;
        return String.format(Locale.ROOT, "%010x", v);
    }

    static boolean[] bits(String code) {
        if (code == null || !CODE_RE.matcher(code).matches()) {
            throw new IllegalArgumentException("추적 번호 형식이 아닙니다");
        }
        long v = Long.parseLong(code, 16);
        int check = check(v);
        boolean[] b = new boolean[48];
        for (int i = 0; i < 40; i++) {
            b[i] = ((v >> (39 - i)) & 1) == 1;
        }
        for (int i = 0; i < 8; i++) {
            b[40 + i] = ((check >> (7 - i)) & 1) == 1;
        }
        return b;
    }

    /** 확인용 8비트: 다섯 바이트를 섞은 값 (우연히 같은 무늬가 생겨도 걸러진다) */
    static int check(long v) {
        int c = 0x5A;
        for (int i = 0; i < 5; i++) {
            c = ((c << 1) | (c >>> 7)) & 0xFF;
            c ^= (int) ((v >> (8 * i)) & 0xFF);
        }
        return c;
    }

    static String fromBits(String s, char one) {
        long v = 0;
        for (int i = 0; i < 40; i++) {
            v = (v << 1) | (s.charAt(i) == one ? 1 : 0);
        }
        int c = 0;
        for (int i = 40; i < 48; i++) {
            c = (c << 1) | (s.charAt(i) == one ? 1 : 0);
        }
        return check(v) == c ? String.format(Locale.ROOT, "%010x", v) : null;
    }

    /** 엑셀(CSV)·글용: 폭 없는 글자 50자 */
    public static String zeroWidth(String code) {
        StringBuilder sb = new StringBuilder(50).append(ZW_EDGE);
        for (boolean b : bits(code)) {
            sb.append(b ? ZW1 : ZW0);
        }
        return sb.append(ZW_EDGE).toString();
    }

    /** 한글용: 줄 끝에 붙이는 빈칸 54자 (보이지 않음) */
    public static String spaces(String code) {
        StringBuilder sb = new StringBuilder(54).append(SP_SYNC);
        for (boolean b : bits(code)) {
            sb.append(b ? NB : ' ');
        }
        return sb.toString();
    }

    /** 글에서 숨은 추적 번호를 모두 찾는다 (확인 비트가 맞는 것만) */
    public static Set<String> find(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher m = ZW_RE.matcher(text);
        while (m.find()) {
            String c = fromBits(m.group(1), ZW1);
            if (c != null) {
                out.add(c);
            }
        }
        m = SP_RE.matcher(text);
        while (m.find()) {
            String c = fromBits(m.group(1), NB);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * CSV 에 심는다: 머리줄과 10줄마다 한 번, 그 줄의 첫 '글자 칸'(숫자 칸이 아닌 칸) 맨 앞에.
     * 숫자 칸은 건드리지 않아 엑셀에서 계산이 그대로 된다. 따옴표로 감싼 칸은 따옴표 안쪽에 넣는다.
     */
    public static String markCsv(String csv, String code) {
        String mark = zeroWidth(code);
        StringBuilder out = new StringBuilder(csv.length() + 64);
        int i = 0;
        if (csv.startsWith("﻿")) {
            out.append('﻿');
            i = 1;
        }
        int record = 0;
        while (i < csv.length()) {
            // 한 줄(레코드)의 칸 시작 위치들을 찾는다
            List<int[]> fields = new ArrayList<>();
            int start = i;
            boolean inQuote = false;
            int fieldStart = i;
            int end = csv.length();
            int next = csv.length();
            for (int j = i; j < csv.length(); j++) {
                char ch = csv.charAt(j);
                if (inQuote) {
                    if (ch == '"') {
                        if (j + 1 < csv.length() && csv.charAt(j + 1) == '"') {
                            j++;
                        } else {
                            inQuote = false;
                        }
                    }
                } else if (ch == '"' && j == fieldStart) {
                    inQuote = true;
                } else if (ch == ',') {
                    fields.add(new int[] {fieldStart, j});
                    fieldStart = j + 1;
                } else if (ch == '\r' || ch == '\n') {
                    end = j;
                    next = (ch == '\r' && j + 1 < csv.length() && csv.charAt(j + 1) == '\n') ? j + 2 : j + 1;
                    break;
                }
            }
            fields.add(new int[] {fieldStart, end});
            int insertAt = -1;
            if (record % 10 == 0) {
                for (int[] f : fields) {
                    String v = csv.substring(f[0], f[1]);
                    String inner = v.startsWith("\"") ? v.substring(1, Math.max(1, v.length() - 1)) : v;
                    if (!inner.isBlank() && !inner.matches("-?[0-9][0-9.,]*%?")) {
                        insertAt = v.startsWith("\"") ? f[0] + 1 : f[0];
                        break;
                    }
                }
            }
            if (insertAt >= 0) {
                out.append(csv, start, insertAt).append(mark).append(csv, insertAt, next);
            } else {
                out.append(csv, start, next);
            }
            i = next;
            record++;
        }
        return out.toString();
    }
}
