package egovframework.haengjeongnuli.civil.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 민원 처리기한 계산.
 * 처리기간은 접수일을 포함하고 토요일·일요일과 공휴일은 뺀다 (민원처리법 제19조 취지).
 * 공휴일 목록은 아직 연동하지 않았으므로 생성자로 넘겨받는다.
 * 처리기간 5일 이하를 시간 단위로 세는 규정은 아직 반영하지 않았다.
 */
@Component
public class DeadlineCalculator {

    private final Set<LocalDate> holidays;

    public DeadlineCalculator() {
        this(Set.of());
    }

    public DeadlineCalculator(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /**
     * @param received 접수일
     * @param days     처리기간 일수 (0 이면 즉시 → 접수일)
     * @return 처리기한 날짜
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

    public boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(date);
    }
}
