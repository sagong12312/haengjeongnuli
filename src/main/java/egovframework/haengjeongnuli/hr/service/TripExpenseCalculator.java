package egovframework.haengjeongnuli.hr.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 국내 출장 여비 (공무원 여비 규정 제16조·제18조, 별표 2. 지방공무원은 같은 규정을 준용하는 조례를 따른다).
 *
 * <ul>
 *   <li><b>관내(근무지 내)</b>: 출장 여행시간이 4시간 이상이면 2만원, 4시간 미만이면 1만원 (하루마다).
 *       공용차량을 이용하면 1만원을 줄인다. 운임·일비·식비·숙박비는 따로 주지 않는다.</li>
 *   <li><b>관외(근무지 외)</b>: 운임(실비) + 일비 하루 2만5천원(공용차량 이용 시 2분의 1) + 식비 하루 2만5천원
 *       + 숙박비(실비, 1박 상한: 서울 10만원 · 광역시 8만원 · 그 밖 7만원).</li>
 * </ul>
 * 금액은 규정이 바뀌면 설정(HrPaySettings)에서 고친다. 이 클래스는 금액을 받아 계산만 한다.
 */
public final class TripExpenseCalculator {

    private TripExpenseCalculator() {
    }

    public enum Area {
        IN("관내 (근무지 내)"), OUT("관외 (근무지 외)");

        private final String label;

        Area(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    public enum Region {
        SEOUL("서울특별시"), METRO("광역시"), OTHER("그 밖의 지역");

        private final String label;

        Region(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    /** 금액 기준 (원) */
    public record Rates(long inOver4h, long inUnder4h, long inCarCut, long perDiem, long meal,
                        long lodgingSeoul, long lodgingMetro, long lodgingOther) {

        public static Rates defaults() {
            return new Rates(20_000, 10_000, 10_000, 25_000, 25_000, 100_000, 80_000, 70_000);
        }

        public long lodgingCap(Region r) {
            return switch (r == null ? Region.OTHER : r) {
                case SEOUL -> lodgingSeoul;
                case METRO -> lodgingMetro;
                case OTHER -> lodgingOther;
            };
        }
    }

    /** 계산 결과 (원). detail 은 결재 문서·화면에 그대로 쓰는 설명 */
    public record Result(long perDiem, long meal, long lodging, long fare, long fixed, long total, String detail) {}

    /**
     * @param hours        관내: 하루 출장 여행시간 (시간)
     * @param officialCar  공용차량을 이용했는지
     * @param nights       관외: 숙박한 밤 수
     * @param lodgingPaid  관외: 실제 숙박비 합계 (모르면 null → 상한으로 어림)
     * @param fare         관외: 운임 실비 (모르면 0)
     */
    public static Result calculate(Area area, LocalDate start, LocalDate end, double hours, boolean officialCar,
                                   int nights, Region region, Long lodgingPaid, long fare, Rates r) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (area == Area.IN) {
            long per = hours >= 4 ? r.inOver4h() : r.inUnder4h();
            if (officialCar) {
                per = Math.max(0, per - r.inCarCut());
            }
            long total = per * days;
            String d = String.format("관내 출장 %d일 × %,d원 (여행시간 %s시간 %s%s)", days, per,
                    trim(hours), hours >= 4 ? "4시간 이상" : "4시간 미만", officialCar ? ", 공용차량 1만원 감액" : "");
            return new Result(0, 0, 0, 0, total, total, d);
        }
        long perDiemDay = officialCar ? r.perDiem() / 2 : r.perDiem();
        long perDiem = perDiemDay * days;
        long meal = r.meal() * days;
        int n = Math.max(0, nights);
        long cap = r.lodgingCap(region) * n;
        long lodging = lodgingPaid == null ? cap : Math.min(Math.max(0, lodgingPaid), cap);
        long f = Math.max(0, fare);
        long total = perDiem + meal + lodging + f;
        String d = String.format("관외 출장 %d일: 일비 %,d원 × %d일%s, 식비 %,d원 × %d일, 숙박비 %s, 운임 %,d원",
                days, perDiemDay, days, officialCar ? " (공용차량 2분의 1)" : "", r.meal(), days,
                n == 0 ? "없음" : (lodgingPaid == null ? String.format("%s 상한 %,d원 × %d박 (어림)", region == null ? Region.OTHER.getLabel() : region.getLabel(), r.lodgingCap(region), n)
                        : String.format("실비 %,d원 (상한 %,d원)", lodging, cap)),
                f);
        return new Result(perDiem, meal, lodging, f, 0, total, d);
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
