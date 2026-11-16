package egovframework.haengjeongnuli.cmm.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 국내 전화번호 확인과 표기 통일 (010-1234-5678 꼴).
 * 띄어쓰기·점·괄호·하이픈은 있어도 없어도 받는다. 받는 번호:
 * 휴대전화 010·011·016·017·018·019, 지역번호 02·031~064, 인터넷전화 070, 평생·안심번호 0502~0508, 대표번호 15xx·16xx·18xx.
 */
public final class PhoneNumbers {

    private static final Pattern MOBILE = Pattern.compile("(010)(\\d{4})(\\d{4})|(01[16789])(\\d{3,4})(\\d{4})");
    private static final Pattern SEOUL = Pattern.compile("(02)(\\d{3,4})(\\d{4})");
    private static final Pattern AREA = Pattern.compile("(0(?:3[1-3]|4[1-4]|5[1-5]|6[1-4]))(\\d{3,4})(\\d{4})");
    private static final Pattern VOIP = Pattern.compile("(070)(\\d{4})(\\d{4})");
    private static final Pattern PERSONAL = Pattern.compile("(050[2-8])(\\d{3,4})(\\d{4})");
    private static final Pattern REPRESENTATIVE = Pattern.compile("(1[568]\\d{2})(\\d{4})");

    private PhoneNumbers() {}

    /** 휴대전화만. 맞지 않으면 null */
    public static String mobile(String raw) {
        String d = digits(raw);
        return d == null ? null : format(MOBILE, d);
    }

    /** 집·사무실 전화, 팩스 (휴대전화 번호도 받는다). 맞지 않으면 null */
    public static String phone(String raw) {
        String d = digits(raw);
        if (d == null) {
            return null;
        }
        for (Pattern p : new Pattern[] {MOBILE, SEOUL, AREA, VOIP, PERSONAL}) {
            String f = format(p, d);
            if (f != null) {
                return f;
            }
        }
        Matcher m = REPRESENTATIVE.matcher(d);
        return m.matches() ? m.group(1) + "-" + m.group(2) : null;
    }

    private static String digits(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.replaceAll("[\\s.()\\-]", "");
        return d.matches("\\d{8,12}") ? d : null;
    }

    private static String format(Pattern p, String d) {
        Matcher m = p.matcher(d);
        if (!m.matches()) {
            return null;
        }
        // 갈래가 둘인 꼴(010 / 011·016~019)은 맞은 쪽 묶음을 쓴다
        int g = m.group(1) != null ? 1 : 4;
        return m.group(g) + "-" + m.group(g + 1) + "-" + m.group(g + 2);
    }
}
