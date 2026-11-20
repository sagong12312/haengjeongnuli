package egovframework.haengjeongnuli.reg;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import egovframework.haengjeongnuli.cmm.holiday.HolidayService;

/** 업무 계산 도구. 결과는 참고용이며, 법령에 특례가 있으면 그 법령을 따른다. */
@Service
public class ToolsService {

    private final HolidayService holidays;

    public ToolsService(HolidayService holidays) {
        this.holidays = holidays;
    }

    private boolean off(LocalDate d) {
        return !holidays.isWorkingDay(d);
    }

    /* ------------------------------------------------------------ 1. 기간 계산 (민법 제157조·제160조·제161조) */

    public record Term(LocalDate start, LocalDate end, LocalDate endShifted, String note) {
        public LocalDate getEnd() { return end; }
        public LocalDate getEndShifted() { return endShifted; }
        public String getNote() { return note; }
    }

    /**
     * unit: D 일, M 월, Y 년. firstDayCounts: 첫날을 넣는지 (보통은 넣지 않음, 민법 제157조).
     * 말일이 토요일·공휴일이면 그 다음 날에 끝난다 (민법 제161조).
     */
    public Term term(LocalDate start, int n, String unit, boolean firstDayCounts) {
        LocalDate base = firstDayCounts ? start.minusDays(1) : start;
        LocalDate end = switch (unit) {
            case "M" -> base.plusMonths(n);
            case "Y" -> base.plusYears(n);
            default -> base.plusDays(n);
        };
        LocalDate shifted = end;
        int guard = 0;
        while ((shifted.getDayOfWeek() == DayOfWeek.SATURDAY || holidays.isHoliday(shifted) || shifted.getDayOfWeek() == DayOfWeek.SUNDAY) && guard++ < 30) {
            shifted = shifted.plusDays(1);
        }
        String note = shifted.equals(end) ? "" : "말일(" + end + ")이 토요일·공휴일이라 다음 날로 넘어갑니다.";
        return new Term(start, end, shifted, note);
    }

    /* ------------------------------------------------------------ 2. 근무일 계산 */

    public LocalDate addWorkingDays(LocalDate start, int n) {
        LocalDate d = start;
        int left = n;
        int guard = 0;
        while (left > 0 && guard++ < 5000) {
            d = d.plusDays(1);
            if (!off(d)) {
                left--;
            }
        }
        return d;
    }

    /* ------------------------------------------------------------ 3. 두 날짜 사이 */

    public Map<String, Object> between(LocalDate a, LocalDate b) {
        LocalDate from = a.isAfter(b) ? b : a;
        LocalDate to = a.isAfter(b) ? a : b;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", ChronoUnit.DAYS.between(from, to));
        m.put("daysInclusive", ChronoUnit.DAYS.between(from, to) + 1);
        long work = from.datesUntil(to.plusDays(1)).limit(4000).filter(Predicate.not(this::off)).count();
        m.put("workDays", work);
        Period p = Period.between(from, to);
        m.put("period", p.getYears() + "년 " + p.getMonths() + "개월 " + p.getDays() + "일");
        return m;
    }

    /* ------------------------------------------------------------ 4. 만 나이 (행정기본법 제7조의2) */

    public Map<String, Object> age(LocalDate birth, LocalDate on) {
        Map<String, Object> m = new LinkedHashMap<>();
        Period p = Period.between(birth, on);
        m.put("years", p.getYears());
        m.put("months", p.getYears() * 12 + p.getMonths());
        m.put("yearAge", on.getYear() - birth.getYear());
        LocalDate next = birth.withYear(on.getYear());
        if (next.isBefore(on) || next.equals(on)) {
            next = birth.plusYears(p.getYears() + 1L);
        }
        m.put("nextBirthday", next);
        return m;
    }

    /* ------------------------------------------------------------ 5. 금액 한글 표기 */

    private static final String[] DIGIT = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL = {"", "십", "백", "천"};
    private static final String[] BIG = {"", "만", "억", "조", "경"};

    public static String koreanAmount(long n) {
        if (n == 0) {
            return "영";
        }
        String sign = n < 0 ? "마이너스 " : "";
        long v = Math.abs(n);
        StringBuilder sb = new StringBuilder();
        int group = 0;
        while (v > 0 && group < BIG.length) {
            int part = (int) (v % 10000);
            if (part > 0) {
                StringBuilder ps = new StringBuilder();
                for (int i = 3; i >= 0; i--) {
                    int dgt = (part / (int) Math.pow(10, i)) % 10;
                    if (dgt > 0) {
                        ps.append(DIGIT[dgt]).append(SMALL[i]);
                    }
                }
                sb.insert(0, ps + BIG[group]);
            }
            v /= 10000;
            group++;
        }
        return sign + sb;
    }

    /* ------------------------------------------------------------ 6. 부가가치세 (세율 10%) */

    public Map<String, Long> vat(long amount, boolean amountIsTotal) {
        Map<String, Long> m = new LinkedHashMap<>();
        long supply;
        long tax;
        if (amountIsTotal) {
            supply = Math.round(amount / 1.1);
            tax = amount - supply;
        } else {
            supply = amount;
            tax = Math.round(amount * 0.1);
        }
        m.put("supply", supply);
        m.put("tax", tax);
        m.put("total", supply + tax);
        return m;
    }

    /* ------------------------------------------------------------ 7. 공문서 날짜·시각 표기 */

    /** 연·월·일 글자를 빼고 온점: 2026. 11. 20. / 시각은 24시각제 쌍점: 14:30 */
    public static String officialDate(LocalDate d) {
        return d.getYear() + ". " + d.getMonthValue() + ". " + d.getDayOfMonth() + ".";
    }

    public static String officialTime(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    public static String officialDateWithDay(LocalDate d) {
        String[] w = {"월", "화", "수", "목", "금", "토", "일"};
        return officialDate(d) + "(" + w[d.getDayOfWeek().getValue() - 1] + ")";
    }

    /* ------------------------------------------------------------ 8. 글자 수·바이트 */

    public Map<String, Object> textCount(String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        String t = s == null ? "" : s.replace("\r\n", "\n");
        m.put("chars", t.codePointCount(0, t.length()));
        m.put("charsNoSpace", t.replaceAll("\\s", "").codePointCount(0, t.replaceAll("\\s", "").length()));
        m.put("lines", t.isEmpty() ? 0 : t.split("\n", -1).length);
        m.put("utf8", t.getBytes(StandardCharsets.UTF_8).length);
        Charset euc = Charset.isSupported("EUC-KR") ? Charset.forName("EUC-KR") : null;
        m.put("euckr", euc == null ? null : t.getBytes(euc).length);
        return m;
    }

    /* ------------------------------------------------------------ 9. 개인정보 가리기 */

    public record Masked(String text, Map<String, Integer> found) {
        public String getText() { return text; }
        public Map<String, Integer> getFound() { return found; }
    }

    private static final List<Object[]> PII = List.of(
            new Object[] {"주민등록·외국인등록번호", Pattern.compile("(?<!\\d)(\\d{6})\\s*-?\\s*([1-8])\\d{6}(?!\\d)"), "$1-$2******"},
            new Object[] {"카드번호", Pattern.compile("(?<!\\d)(\\d{4})[- ](\\d{4})[- ]\\d{4}[- ](\\d{4})(?!\\d)"), "$1-****-****-$3"},
            new Object[] {"휴대전화", Pattern.compile("(?<!\\d)(01[016789])[- .]?(\\d{3,4})[- .]?(\\d{4})(?!\\d)"), "$1-****-$3"},
            new Object[] {"전화번호", Pattern.compile("(?<!\\d)(0(?:2|[3-6][1-5]|70))[- .)](\\d{3,4})[- .](\\d{4})(?!\\d)"), "$1-****-$3"},
            new Object[] {"이메일", Pattern.compile("([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})"), "$1***@$2"},
            new Object[] {"계좌번호", Pattern.compile("(?<![\\d-])(\\d{3,6})-(\\d{2,6})-(\\d{4,8})(?:-(\\d{1,3}))?(?![\\d-])"), "$1-****-****"});

    public Masked mask(String s) {
        String t = s == null ? "" : s;
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Object[] p : PII) {
            Matcher m = ((Pattern) p[1]).matcher(t);
            int n = 0;
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                n++;
                m.appendReplacement(sb, (String) p[2]);
            }
            m.appendTail(sb);
            t = sb.toString();
            if (n > 0) {
                found.put((String) p[0], n);
            }
        }
        return new Masked(t, found);
    }

    /* ------------------------------------------------------------ 10. 면적 환산 */

    /** 1평 = 400/121 ㎡ (약 3.3058㎡) */
    public static final double SQM_PER_PYEONG = 400.0 / 121.0;

    public Map<String, Double> area(double value, boolean fromPyeong) {
        Map<String, Double> m = new LinkedHashMap<>();
        double sqm = fromPyeong ? value * SQM_PER_PYEONG : value;
        m.put("sqm", Math.round(sqm * 100) / 100.0);
        m.put("pyeong", Math.round(sqm / SQM_PER_PYEONG * 100) / 100.0);
        return m;
    }

    /** 법정 기한 바로 계산 (첫날 넣지 않음, 말일 공휴일이면 다음 날) */
    public List<Map<String, Object>> presets(LocalDate from) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object[][] list = {
                {"외부강의 등 신고 (마친 날부터)", 10, "청탁금지법 제10조"},
                {"사적이해관계자 신고·회피 신청 (안 날부터)", 14, "이해충돌방지법 제5조"},
                {"이의신청 결과 통지 (신청 받은 날부터)", 14, "행정기본법 제36조"},
                {"처분 이의신청 (처분 받은 날부터)", 30, "행정기본법 제36조"},
                {"과태료 사전 통지 의견 제출 (최소)", 10, "질서위반행위규제법 제16조"},
                {"정보공개 결정 (청구 받은 날부터)", 10, "공공기관의 정보공개에 관한 법률 제11조"},
                {"행정심판 청구 (처분이 있음을 안 날부터)", 90, "행정심판법 제27조"},
        };
        for (Object[] p : list) {
            Term t = term(from, (Integer) p[1], "D", false);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p[0]);
            m.put("days", p[1]);
            m.put("law", p[2]);
            m.put("end", t.endShifted());
            m.put("note", t.note());
            out.add(m);
        }
        return out;
    }
}
