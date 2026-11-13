package egovframework.haengjeongnuli.civil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
}
