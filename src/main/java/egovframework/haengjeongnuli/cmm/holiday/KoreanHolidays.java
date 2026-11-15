package egovframework.haengjeongnuli.cmm.holiday;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.DangiCalendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

/**
 * 「관공서의 공휴일에 관한 규정」(대통령령 제36290호, 2026. 5. 11. 시행) 제2조·제3조에 따른 공휴일 계산.
 * 2026년 개정으로 노동절(5월 1일, 2026. 5. 1. 시행)과 국경일 전체(제헌절 7월 17일 포함, 2026. 5. 11. 시행)가
 * 공휴일이 되었으므로 2026년부터 반영한다.
 * <ul>
 *   <li>제2조 제1호 일요일은 {@link #isHoliday}에서 따로 판단한다.</li>
 *   <li>제10호의2 선거일, 제11호 임시공휴일은 정해진 날짜를 {@code extra} 로 받는다.</li>
 *   <li>음력 날짜는 ICU4J 의 한국 음력(단기) 달력으로 계산한다.</li>
 * </ul>
 */
public final class KoreanHolidays {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 대체공휴일 규칙 구분 (제3조 제1항) */
    private enum Sub {
        /** 토·일과 겹치거나 다른 공휴일과 겹치면 대체 (제2호, 제5~7호, 제10호) */
        WEEKEND,
        /** 일요일과 겹치거나 다른 공휴일과 겹치면 대체 (제4호 설날, 제9호 추석) */
        SUNDAY,
        /** 대체공휴일 없음 (제3호 1월 1일, 제8호 현충일) */
        NONE
    }

    private record Base(LocalDate date, String name, Sub sub) {}

    private KoreanHolidays() {}

    /**
     * 그 해의 공휴일(일요일 제외)과 이름.
     *
     * @param extra 선거일·임시공휴일 (날짜 → 이름). 다른 해 날짜가 섞여 있어도 된다.
     */
    public static Map<LocalDate, String> of(int year, Map<LocalDate, String> extra) {
        List<Base> base = new ArrayList<>();
        base.add(new Base(LocalDate.of(year, 1, 1), "1월 1일", Sub.NONE));
        LocalDate seol = lunar(year, 1, 1);
        base.add(new Base(seol.minusDays(1), "설날 전날", Sub.SUNDAY));
        base.add(new Base(seol, "설날", Sub.SUNDAY));
        base.add(new Base(seol.plusDays(1), "설날 다음 날", Sub.SUNDAY));
        base.add(new Base(LocalDate.of(year, 3, 1), "3·1절", Sub.WEEKEND));
        if (year >= 2026) {
            base.add(new Base(LocalDate.of(year, 5, 1), "노동절", Sub.WEEKEND));
        }
        base.add(new Base(LocalDate.of(year, 5, 5), "어린이날", Sub.WEEKEND));
        base.add(new Base(lunar(year, 4, 8), "부처님 오신 날", Sub.WEEKEND));
        base.add(new Base(LocalDate.of(year, 6, 6), "현충일", Sub.NONE));
        if (year >= 2026) {
            base.add(new Base(LocalDate.of(year, 7, 17), "제헌절", Sub.WEEKEND));
        }
        base.add(new Base(LocalDate.of(year, 8, 15), "광복절", Sub.WEEKEND));
        LocalDate chuseok = lunar(year, 8, 15);
        base.add(new Base(chuseok.minusDays(1), "추석 전날", Sub.SUNDAY));
        base.add(new Base(chuseok, "추석", Sub.SUNDAY));
        base.add(new Base(chuseok.plusDays(1), "추석 다음 날", Sub.SUNDAY));
        base.add(new Base(LocalDate.of(year, 10, 3), "개천절", Sub.WEEKEND));
        base.add(new Base(LocalDate.of(year, 10, 9), "한글날", Sub.WEEKEND));
        base.add(new Base(LocalDate.of(year, 12, 25), "기독탄신일", Sub.WEEKEND));

        Map<LocalDate, String> out = new TreeMap<>();
        for (Base b : base) {
            out.merge(b.date(), b.name(), (a, c) -> a + "·" + c);
        }
        extra.forEach((d, n) -> {
            if (d.getYear() == year) {
                out.merge(d, n, (a, c) -> a + "·" + c);
            }
        });

        // 대체공휴일이 생기는 공휴일을 날짜 순으로 센다 (같은 날 두 공휴일이 겹치면 하나는 대체)
        Map<LocalDate, Integer> triggers = new TreeMap<>();
        Map<LocalDate, List<Base>> byDate = new TreeMap<>();
        base.forEach(b -> byDate.computeIfAbsent(b.date(), k -> new ArrayList<>()).add(b));
        for (Map.Entry<LocalDate, List<Base>> e : byDate.entrySet()) {
            LocalDate d = e.getKey();
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            int n = 0;
            if (weekend) {
                for (Base b : e.getValue()) {
                    if (b.sub() == Sub.WEEKEND || (b.sub() == Sub.SUNDAY && dow == DayOfWeek.SUNDAY)) {
                        n++;
                    }
                }
            } else if (e.getValue().size() > 1 && e.getValue().stream().anyMatch(b -> b.sub() != Sub.NONE)) {
                n = e.getValue().size() - 1;
            }
            if (n > 0) {
                triggers.put(d, n);
            }
        }

        Set<LocalDate> taken = new HashSet<>(out.keySet());
        for (Map.Entry<LocalDate, Integer> t : triggers.entrySet()) {
            for (int i = 0; i < t.getValue(); i++) {
                LocalDate c = t.getKey().plusDays(1);
                while (taken.contains(c) || c.getDayOfWeek() == DayOfWeek.SUNDAY
                        || c.getDayOfWeek() == DayOfWeek.SATURDAY) {
                    c = c.plusDays(1);
                }
                taken.add(c);
                if (c.getYear() == year) {
                    out.put(c, "대체공휴일");
                }
            }
        }
        // 지난해 12월 말 공휴일 때문에 올해 1월에 생기는 대체공휴일 (예: 설날 연휴가 해를 넘기는 경우)은
        // 지난해를 계산할 때 생기므로 여기서 함께 붙인다.
        if (year > 1900) {
            Map<LocalDate, String> prev = ofNoCarry(year - 1, extra);
            prev.forEach((d, n) -> {
                if (d.getYear() == year) {
                    out.putIfAbsent(d, n);
                }
            });
        }
        return out;
    }

    private static Map<LocalDate, String> ofNoCarry(int year, Map<LocalDate, String> extra) {
        // 12월 25일 이후 대체공휴일만 확인하면 되므로 같은 계산을 다음 해로 넘기지 않고 한 번 더 한다
        Map<LocalDate, String> m = new TreeMap<>();
        LocalDate xmas = LocalDate.of(year, 12, 25);
        if (xmas.getDayOfWeek() == DayOfWeek.SATURDAY || xmas.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LocalDate c = xmas.plusDays(1);
            while (c.getDayOfWeek() == DayOfWeek.SATURDAY || c.getDayOfWeek() == DayOfWeek.SUNDAY) {
                c = c.plusDays(1);
            }
            m.put(c, "대체공휴일");
        }
        return m;
    }

    /** 일요일이거나 공휴일이면 true. 토요일은 공휴일이 아니다(근무일 판단은 따로). */
    public static boolean isHoliday(LocalDate d, Collection<LocalDate> holidays) {
        return d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d);
    }

    /** 음력 날짜를 양력으로 (윤달이 아닌 달) */
    @SuppressWarnings("deprecation")
    static LocalDate lunar(int gregorianYear, int lunarMonth, int lunarDay) {
        // DangiCalendar 는 ICU 에서 "기술 미리보기" 로 표시되어 있어 사용 경고를 끈다
        DangiCalendar cal = new DangiCalendar(TimeZone.getTimeZone("Asia/Seoul"), ULocale.KOREA);
        // 그 해 7월 1일이 속한 음력 해를 기준으로 한다 (설날은 양력 1~2월이므로 같은 음력 해)
        cal.setTimeInMillis(LocalDate.of(gregorianYear, 7, 1).atStartOfDay(SEOUL).toInstant().toEpochMilli());
        int ey = cal.get(Calendar.EXTENDED_YEAR);
        cal.clear();
        cal.set(Calendar.EXTENDED_YEAR, ey);
        cal.set(Calendar.MONTH, lunarMonth - 1);
        cal.set(DangiCalendar.IS_LEAP_MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, lunarDay);
        return Instant.ofEpochMilli(cal.getTimeInMillis()).atZone(SEOUL).toLocalDate();
    }
}
