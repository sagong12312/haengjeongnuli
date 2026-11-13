package egovframework.haengjeongnuli.civil.service;

/**
 * 민원처리기준표의 법정민원 사무 (일부).
 * 처리기간은 정부24 안내와 지자체 민원행정서비스헌장 기준이며, 전체 목록은 민원처리기준표로 채워야 한다.
 */
public enum LegalAffair {

    RESIDENT_COPY("주민등록", "주민등록표 등본(초본) 발급", "즉시 (근무시간 내 3시간)"),
    MOVE_IN("주민등록", "전입신고", "즉시 (근무시간 내 3시간)"),
    SEAL("인감", "인감증명서 발급", "즉시 (3시간 이내)");

    private final String group;
    private final String label;
    private final String periodText;

    LegalAffair(String group, String label, String periodText) {
        this.group = group;
        this.label = label;
        this.periodText = periodText;
    }

    public String getGroup() { return group; }
    public String getLabel() { return label; }
    public String getPeriodText() { return periodText; }
}
