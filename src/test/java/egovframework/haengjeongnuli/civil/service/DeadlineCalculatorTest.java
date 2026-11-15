package egovframework.haengjeongnuli.civil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import egovframework.haengjeongnuli.cmm.holiday.KoreanHolidays;

class DeadlineCalculatorTest {

    private final DeadlineCalculator calc = new DeadlineCalculator();

    @Test
    void 즉시처리는_접수일() {
        LocalDate fri = LocalDate.of(2026, 11, 13);
        assertThat(calc.deadline(fri, 0)).isEqualTo(fri);
    }

    @Test
    void 접수일을_포함해서_센다() {
        LocalDate mon = LocalDate.of(2026, 11, 9);
        assertThat(calc.deadline(mon, 1)).isEqualTo(mon);
        assertThat(calc.deadline(mon, 5)).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void 주말은_빼고_센다() {
        LocalDate fri = LocalDate.of(2026, 11, 13);
        // 금(1) 월(2) 화(3) 수(4) 목(5) 금(6) 월(7)
        assertThat(calc.deadline(fri, 7)).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void 토요일_접수는_다음_근무일부터_센다() {
        LocalDate sat = LocalDate.of(2026, 11, 14);
        assertThat(calc.deadline(sat, 1)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void 공휴일도_뺀다() {
        LocalDate mon = LocalDate.of(2026, 11, 9);
        LocalDate tueHoliday = LocalDate.of(2026, 11, 10);
        DeadlineCalculator withHoliday = new DeadlineCalculator(Set.of(tueHoliday));
        assertThat(withHoliday.deadline(mon, 2)).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void 질의민원은_내용에_따라_기간이_다르다() {
        assertThat(CivilKind.QUERY.periodDays(true)).isEqualTo(14);
        assertThat(CivilKind.QUERY.periodDays(false)).isEqualTo(7);
        assertThat(CivilKind.LEGAL.periodDays(false)).isNull();
    }

    @Test
    void 음수_기간은_거부() {
        assertThatThrownBy(() -> calc.deadline(LocalDate.of(2026, 11, 9), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 민원처리법 제19조: 5일 이하는 시간 단위 ----

    @Test
    void 즉시는_3근무시간_점심시간은_빼고() {
        // 월 11:00 접수 → 11~12시 1시간 + 13~15시 2시간 = 15:00
        assertThat(calc.deadlineAt(LocalDateTime.of(2026, 11, 9, 11, 0), 0))
                .isEqualTo(LocalDateTime.of(2026, 11, 9, 15, 0));
    }

    @Test
    void 퇴근_무렵_접수는_다음_근무일로_넘어간다() {
        // 금 17:00 접수, 즉시(3시간) → 금 1시간 + 월 9~11시 = 월 11:00
        assertThat(calc.deadlineAt(LocalDateTime.of(2026, 11, 13, 17, 0), 0))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 11, 0));
        // 근무시간 전(7시) 접수는 9시부터 센다: 9~12시 3시간
        assertThat(calc.deadlineAt(LocalDateTime.of(2026, 11, 9, 7, 0), 0))
                .isEqualTo(LocalDateTime.of(2026, 11, 9, 12, 0));
    }

    @Test
    void 처리기간_3일은_24근무시간() {
        // 월 14:30 접수 + 24시간 → 월 3.5h, 화 8h, 수 8h, 목 4.5h → 목 14:30
        assertThat(calc.deadlineAt(LocalDateTime.of(2026, 11, 9, 14, 30), 3))
                .isEqualTo(LocalDateTime.of(2026, 11, 12, 14, 30));
    }

    @Test
    void 처리기간_6일_이상은_일_단위_첫날_산입() {
        // 금 접수 7일: 금 월 화 수 목 금 월 → 월 18:00
        assertThat(calc.deadlineAt(LocalDateTime.of(2026, 11, 13, 16, 0), 7))
                .isEqualTo(LocalDateTime.of(2026, 11, 23, 18, 0));
    }

    // ---- 관공서의 공휴일에 관한 규정 ----

    @Test
    void 공휴일과_대체공휴일_계산() {
        Map<LocalDate, String> y2026 = KoreanHolidays.of(2026, Map.of());
        assertThat(y2026).containsEntry(LocalDate.of(2026, 2, 17), "설날")
                .containsEntry(LocalDate.of(2026, 3, 2), "대체공휴일")      // 3·1절 일요일
                .containsEntry(LocalDate.of(2026, 5, 1), "노동절")
                .containsEntry(LocalDate.of(2026, 5, 25), "대체공휴일")     // 부처님 오신 날 일요일
                .containsEntry(LocalDate.of(2026, 7, 17), "제헌절")
                .containsEntry(LocalDate.of(2026, 9, 25), "추석")
                .containsEntry(LocalDate.of(2026, 10, 5), "대체공휴일");    // 개천절 토요일
        // 추석 다음 날이 토요일이어도 설날·추석은 일요일과 겹칠 때만 대체한다
        assertThat(y2026).doesNotContainKey(LocalDate.of(2026, 9, 28));

        Map<LocalDate, String> y2027 = KoreanHolidays.of(2027, Map.of());
        assertThat(y2027.entrySet().stream().filter(e -> e.getValue().equals("대체공휴일")).map(Map.Entry::getKey))
                .containsExactly(LocalDate.of(2027, 2, 9), LocalDate.of(2027, 5, 3), LocalDate.of(2027, 7, 19),
                        LocalDate.of(2027, 8, 16), LocalDate.of(2027, 10, 4), LocalDate.of(2027, 10, 11),
                        LocalDate.of(2027, 12, 27));
        // 2025년은 노동절·제헌절이 관공서 공휴일이 아니었고, 어린이날·부처님 오신 날이 겹쳐 5월 6일이 대체공휴일
        Map<LocalDate, String> y2025 = KoreanHolidays.of(2025, Map.of());
        assertThat(y2025).doesNotContainKey(LocalDate.of(2025, 5, 1))
                .containsEntry(LocalDate.of(2025, 5, 6), "대체공휴일")
                .containsEntry(LocalDate.of(2025, 10, 8), "대체공휴일");
    }

    @Test
    void 선거일도_공휴일로_빼고_센다() {
        LocalDate election = LocalDate.of(2026, 6, 3); // 수요일
        Map<LocalDate, String> m = KoreanHolidays.of(2026, Map.of(election, "지방선거"));
        DeadlineCalculator c = new DeadlineCalculator(m.keySet());
        // 화 17:00 접수 즉시 → 화 1시간, (수 선거일) 목 9~11시
        assertThat(c.deadlineAt(LocalDateTime.of(2026, 6, 2, 17, 0), 0))
                .isEqualTo(LocalDateTime.of(2026, 6, 4, 11, 0));
    }
}
