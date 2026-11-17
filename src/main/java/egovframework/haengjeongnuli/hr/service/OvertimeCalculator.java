package egovframework.haengjeongnuli.hr.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 출퇴근 기록으로 시간외근무 인정 시간을 계산한다 (지방공무원 수당 등에 관한 규정 제15조).
 *
 * <ul>
 *   <li>실제 시간외근무: 출근~퇴근 가운데 정해진 근무시간(기본 09:00~18:00, 유연근무면 그 시간) 밖. 휴일·토요일은 출근~퇴근 전부.</li>
 *   <li>인정: 그 가운데 <b>결재가 끝난 시간외근무 명령(신청) 시간 안</b>에 든 부분만 (근무명령 없는 초과근무는 인정하지 않는다).</li>
 *   <li>하루 인정 시간이 1시간 미만이면 0. 평일(공휴일·토요일이 아닌 날)은 1시간을 뺀다.</li>
 *   <li>하루 상한(기본 4시간)을 넘으면 상한까지만.</li>
 *   <li>한 달은 날마다 분 단위로 더한 뒤 1시간 미만을 버리고, 월 상한(기본 57시간)까지만.</li>
 * </ul>
 * 상한 시간은 기관 지침에 따라 설정에서 바꿀 수 있다.
 */
public final class OvertimeCalculator {

    private OvertimeCalculator() {
    }

    /** 결재가 끝난 시간외근무 명령 한 건 (그날 시작~끝 시각) */
    public record Order(LocalTime from, LocalTime to) {}

    /** 하루 결과 (분 단위) */
    public record Day(LocalDate date, boolean holiday, LocalDateTime in, LocalDateTime out,
                      int actualMinutes, int orderedMinutes, int recognizedMinutes, int unorderedMinutes, String note) {

        public String recognizedText() {
            return hm(recognizedMinutes);
        }

        public String actualText() {
            return hm(actualMinutes);
        }

        public String unorderedText() {
            return hm(unorderedMinutes);
        }
    }

    /** 한 달 결과 */
    public record Month(List<Day> days, int sumMinutes, int recognizedHours, int capHours, boolean capped) {}

    /**
     * 하루 계산.
     *
     * @param holiday 토요일·일요일·공휴일이면 true
     * @param in      출근 (없으면 0)
     * @param out     퇴근 (없으면 0 — 퇴근을 누르지 않은 날은 계산하지 않는다)
     */
    public static Day day(LocalDate date, boolean holiday, LocalTime schedStart, LocalTime schedEnd,
                          LocalDateTime in, LocalDateTime out, List<Order> orders, int dailyCapHours) {
        if (in == null || out == null || !out.isAfter(in)) {
            return new Day(date, holiday, in, out, 0, 0, 0, 0, out == null && in != null ? "퇴근 기록 없음" : "");
        }
        // 실제로 근무시간 밖에 있었던 구간
        List<LocalDateTime[]> outside = new ArrayList<>();
        if (holiday) {
            outside.add(new LocalDateTime[] {in, out});
        } else {
            LocalDateTime s = date.atTime(schedStart);
            LocalDateTime e = date.atTime(schedEnd);
            if (in.isBefore(s)) {
                outside.add(new LocalDateTime[] {in, min(out, s)});
            }
            if (out.isAfter(e)) {
                outside.add(new LocalDateTime[] {max(in, e), out});
            }
        }
        int actual = 0;
        for (LocalDateTime[] r : outside) {
            actual += minutes(r[0], r[1]);
        }
        // 명령 시간과 겹친 부분만
        int ordered = 0;
        int inOrder = 0;
        for (Order o : orders) {
            LocalDateTime os = date.atTime(o.from());
            LocalDateTime oe = o.to().isAfter(o.from()) ? date.atTime(o.to()) : date.plusDays(1).atTime(o.to());
            ordered += minutes(os, oe);
            for (LocalDateTime[] r : outside) {
                inOrder += minutes(max(r[0], os), min(r[1], oe));
            }
        }
        int unordered = Math.max(0, actual - inOrder);
        String note;
        int recognized;
        if (inOrder < 60) {
            recognized = 0;
            note = orders.isEmpty() ? (actual > 0 ? "근무명령 없음" : "") : (inOrder > 0 ? "1시간 미만" : "명령 시간에 근무 없음");
        } else {
            recognized = holiday ? inOrder : inOrder - 60;
            note = holiday ? "휴일 (공제 없음)" : "평일 1시간 공제";
            int cap = dailyCapHours * 60;
            if (recognized > cap) {
                recognized = cap;
                note += " · 하루 " + dailyCapHours + "시간 상한";
            }
        }
        return new Day(date, holiday, in, out, actual, ordered, recognized, unordered, note);
    }

    /** 한 달 합계: 분 단위로 더한 뒤 1시간 미만 버림, 월 상한 */
    public static Month month(List<Day> days, int monthlyCapHours) {
        int sum = 0;
        for (Day d : days) {
            sum += d.recognizedMinutes();
        }
        int hours = sum / 60;
        boolean capped = hours > monthlyCapHours;
        return new Month(days, sum, capped ? monthlyCapHours : hours, monthlyCapHours, capped);
    }

    private static int minutes(LocalDateTime a, LocalDateTime b) {
        return b.isAfter(a) ? (int) ChronoUnit.MINUTES.between(a, b) : 0;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    public static String hm(int minutes) {
        if (minutes <= 0) {
            return "—";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return h == 0 ? m + "분" : m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
