package egovframework.haengjeongnuli.cmm;

/**
 * 금액을 한글로 적는다.
 * 「행정업무의 운영 및 혁신에 관한 규정 시행규칙」 제2조 제2항: 아라비아 숫자로 쓰고 괄호 안에 한글로 적는다.
 * (예시) 금113,560원(금일십일만삼천오백육십원)
 */
public final class KoreanAmount {

    private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL = {"", "십", "백", "천"};
    private static final String[] BIG = {"", "만", "억", "조", "경"};

    private KoreanAmount() {}

    /** 113560 → "일십일만삼천오백육십" (0 → "영") */
    public static String hangul(long amount) {
        if (amount == 0) {
            return "영";
        }
        if (amount < 0) {
            return "마이너스" + hangul(-amount);
        }
        StringBuilder sb = new StringBuilder();
        String s = Long.toString(amount);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int d = s.charAt(i) - '0';
            int pos = len - 1 - i;
            if (d != 0) {
                sb.append(DIGITS[d]).append(SMALL[pos % 4]);
            }
            if (pos % 4 == 0 && pos > 0) {
                // 이 네 자리 묶음에 숫자가 하나라도 있으면 만·억 단위를 붙인다
                int from = Math.max(0, i - 3);
                if (!s.substring(from, i + 1).chars().allMatch(c -> c == '0')) {
                    sb.append(BIG[pos / 4]);
                }
            }
        }
        return sb.toString();
    }

    /** "금113,560원(금일십일만삼천오백육십원)" */
    public static String official(long amount) {
        return String.format("금%,d원(금%s원)", amount, hangul(amount));
    }
}
