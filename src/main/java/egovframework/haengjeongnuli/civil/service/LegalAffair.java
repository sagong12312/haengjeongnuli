package egovframework.haengjeongnuli.civil.service;

/**
 * 법정민원 사무와 처리기간.
 * <ul>
 *   <li>주민등록·인감: 즉시(3근무시간) — 정부24 안내·지자체 민원행정서비스헌장 기준</li>
 *   <li>건축신고 5일, 심의·협의 등이 필요하면 20일 — 건축법 제14조 제3항</li>
 *   <li>건축허가 15일 — 법률에 기간이 없어 민원처리기준표(지자체 민원편람)에 따름. 구리시 민원편람(2023)의 법정 처리기간 예시이므로 우리 기관 민원편람으로 확인 필요</li>
 *   <li>식품 영업신고·변경신고 3일, 기간 안에 통지하지 않으면 다음 날 수리 간주 — 식품위생법 제37조 제12항·제13항</li>
 * </ul>
 * 전체 목록은 기관의 민원처리기준표로 채워야 한다.
 */
public enum LegalAffair {

    RESIDENT_COPY("주민등록", "주민등록표 등본(초본) 발급", "즉시 (근무시간 내 3시간)", 0, null, false),
    MOVE_IN("주민등록", "전입신고", "즉시 (근무시간 내 3시간)", 0, null, false),
    SEAL("인감", "인감증명서 발급", "즉시 (3시간 이내)", 0, null, false),
    BUILD_REPORT("건축", "건축신고", "5일", 5, "건축법 제14조 제3항", false),
    BUILD_REPORT_REVIEW("건축", "건축신고 (심의·동의·협의·확인 필요)", "20일 (5일 안에 그 내용을 신고인에게 통지)", 20, "건축법 제14조 제3항·제4항", false),
    BUILD_PERMIT("건축", "건축허가", "15일 (기관 민원편람 확인)", 15, "민원처리기준표", false),
    FOOD_REPORT("식품위생", "식품 영업신고 (휴게·일반음식점, 제과점, 위탁급식 등)", "3일 (기간 안에 통지하지 않으면 수리 간주)", 3, "식품위생법 제37조 제12항·제13항", true),
    FOOD_CHANGE("식품위생", "식품 영업 변경신고", "3일 (기간 안에 통지하지 않으면 수리 간주)", 3, "식품위생법 제37조 제12항·제13항", true);

    private final String group;
    private final String label;
    private final String periodText;
    private final int days;
    private final String basis;
    /** 기간 안에 통지하지 않으면 수리한 것으로 보는 사무 */
    private final boolean deemed;

    LegalAffair(String group, String label, String periodText, int days, String basis, boolean deemed) {
        this.group = group;
        this.label = label;
        this.periodText = periodText;
        this.days = days;
        this.basis = basis;
        this.deemed = deemed;
    }

    public String getGroup() { return group; }
    public String getLabel() { return label; }
    public String getPeriodText() { return periodText; }
    public String getBasis() { return basis; }
    public boolean isDeemed() { return deemed; }

    /** 처리기간 일수 (0 = 즉시) */
    public int periodDays() { return days; }
}
