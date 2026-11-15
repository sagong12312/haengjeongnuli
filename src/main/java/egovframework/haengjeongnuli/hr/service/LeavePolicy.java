package egovframework.haengjeongnuli.hr.service;

import java.time.LocalDate;
import java.time.Period;

/**
 * 연가 일수 (지방공무원 복무규정 제7조 제1항 표, 국가법령정보센터 2026. 10. 2. 시행본에서 확인).
 * <pre>
 * 1개월 이상 1년 미만 11일 / 1년 이상 3년 미만 15일 / 3년 이상 4년 미만 16일
 * 4년 이상 5년 미만 17일 / 5년 이상 6년 미만 20일 / 6년 이상 21일
 * </pre>
 * 해당 연도 1월 1일 현재 재직기간으로 정하고, 그 해에 새로 임용되면 같은 규정 제7조의2 제2항에 따라
 * 근무한 개월 수(15일 이상은 1개월, 미만은 빼고)에 비례해 반올림한다.
 * 휴직 등 재직기간에서 빼는 기간, 가산 연가(제7조 제3항), 저축 연가는 반영하지 않는다.
 */
public final class LeavePolicy {

    /** 병가 연간 한도 (제7조의5 제1항) */
    public static final int SICK_LIMIT_DAYS = 60;

    private LeavePolicy() {}

    /** 재직 년수에 따른 연가 일수 */
    static int byTenure(Period tenure) {
        int y = tenure.getYears();
        if (y >= 6) return 21;
        if (y >= 5) return 20;
        if (y >= 4) return 17;
        if (y >= 3) return 16;
        if (y >= 1) return 15;
        return 11;
    }

    /** 그 해 연가 일수. 임용일을 모르면 null */
    public static Integer granted(LocalDate hiredOn, int year) {
        if (hiredOn == null) {
            return null;
        }
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        if (hiredOn.getYear() > year) {
            return 0;
        }
        if (!hiredOn.isAfter(jan1)) {
            return byTenure(Period.between(hiredOn, jan1));
        }
        // 그 해에 새로 임용: 임용일부터 12월 31일까지 근무 개월 수에 비례
        Period worked = Period.between(hiredOn, LocalDate.of(year + 1, 1, 1));
        int months = worked.getYears() * 12 + worked.getMonths() + (worked.getDays() >= 15 ? 1 : 0);
        return (int) Math.round(11 * months / 12.0);
    }
}
