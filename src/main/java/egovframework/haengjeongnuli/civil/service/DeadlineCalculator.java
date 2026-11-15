package egovframework.haengjeongnuli.civil.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import egovframework.haengjeongnuli.cmm.holiday.HolidayService;

/**
 * 민원 처리기한 계산 (「민원 처리에 관한 법률」 제19조, 같은 법 시행령 제19조).
 * <ul>
 *   <li>처리기간 5일 이하: 접수 시각부터 "시간" 단위로 계산, 1일은 8근무시간</li>
 *   <li>처리기간 6일 이상: "일" 단위로 계산, 첫날 산입</li>
 *   <li>공휴일과 토요일은 산입하지 않는다</li>
 *   <li>'즉시'는 3근무시간 이내 (시행령 제19조)</li>
 * </ul>
 * 근무시간은 「지방공무원 복무규정」 제2조의 9시~18시, 점심시간 12시~13시.
 */
@Component
public class DeadlineCalculator {

    static final LocalTime AM_START = LocalTime.of(9, 0);
    static final LocalTime AM_END = LocalTime.of(12, 0);
    static final LocalTime PM_START = LocalTime.of(13, 0);
    static final LocalTime PM_END = LocalTime.of(18, 0);
    /** 즉시 처리 민원의 처리시간 */
    public static final int IMMEDIATE_HOURS = 3;

    /** 공휴일 여부 (일요일 포함) */
    private final Predicate<LocalDate> holiday;

    @Autowired
    public DeadlineCalculator(HolidayService holidays) {
        this(holidays::isHoliday);
    }

    /** 테스트용: 일요일과 주어진 날짜를 공휴일로 본다 */
    public DeadlineCalculator(Set<LocalDate> holidays) {
        this(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d));
    }

    public DeadlineCalculator() {
        this(Set.of());
    }

    private DeadlineCalculator(Predicate<LocalDate> holiday) {
        this.holiday = holiday;
    }

    public boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && !holiday.test(date);
    }

    /**
     * 처리기한 시각.
     *
     * @param received 접수 시각
     * @param days     처리기간 일수 (0 이면 즉시)
     */
    public LocalDateTime deadlineAt(LocalDateTime received, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("처리기간은 0일 이상이어야 합니다: " + days);
        }
        if (days == 0) {
            return addWorkMinutes(received, IMMEDIATE_HOURS * 60L);
        }
        if (days <= 5) {
            return addWorkMinutes(received, days * 8 * 60L);
        }
        return deadline(received.toLocalDate(), days).atTime(PM_END);
    }

    /**
     * 일 단위 처리기한 날짜 (첫날 산입, 토요일·공휴일 제외).
     * 6일 이상인 처리기간에 쓴다. 0 이면 접수일.
     */
    public LocalDate deadline(LocalDate received, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("처리기간은 0일 이상이어야 합니다: " + days);
        }
        if (days == 0) {
            return received;
        }
        LocalDate date = received;
        int left = days;
        while (true) {
            if (isWorkingDay(date)) {
                left--;
                if (left == 0) {
                    return date;
                }
            }
            date = date.plusDays(1);
        }
    }

    /**
     * 처리기간 연장 후 새 처리기한 (시행령 제21조: 처리기간의 범위에서 연장).
     * 처리기간 5일 이하 민원은 지금 기한에서 근무시간으로, 6일 이상은 기한 다음 근무일부터 일 단위로 센다.
     */
    public LocalDateTime extend(LocalDateTime currentDeadline, int periodDays, int extendDays) {
        if (extendDays < 1) {
            throw new IllegalArgumentException("연장 일수는 1일 이상이어야 합니다: " + extendDays);
        }
        if (periodDays <= 5) {
            return addWorkMinutes(currentDeadline, extendDays * 8 * 60L);
        }
        return deadline(currentDeadline.toLocalDate().plusDays(1), extendDays).atTime(PM_END);
    }

    /** 근무시간만 세어 minutes 분 뒤의 시각 */
    LocalDateTime addWorkMinutes(LocalDateTime from, long minutes) {
        LocalDateTime t = from.withSecond(0).withNano(0);
        long left = minutes;
        while (true) {
            t = nextWorkMoment(t);
            LocalTime end = t.toLocalTime().isBefore(AM_END) ? AM_END : PM_END;
            long avail = java.time.Duration.between(t.toLocalTime(), end).toMinutes();
            if (left <= avail) {
                return t.plusMinutes(left);
            }
            left -= avail;
            t = t.toLocalDate().atTime(end);
        }
    }

    /** t 이후 처음 근무시간에 들어가는 시각 (근무시간 안이면 그대로) */
    private LocalDateTime nextWorkMoment(LocalDateTime t) {
        while (true) {
            LocalDate d = t.toLocalDate();
            LocalTime tm = t.toLocalTime();
            if (!isWorkingDay(d) || !tm.isBefore(PM_END)) {
                t = d.plusDays(1).atTime(AM_START);
                continue;
            }
            if (tm.isBefore(AM_START)) {
                return d.atTime(AM_START);
            }
            if (!tm.isBefore(AM_END) && tm.isBefore(PM_START)) {
                return d.atTime(PM_START);
            }
            return t;
        }
    }
}
