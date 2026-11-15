package egovframework.haengjeongnuli.civil.service;

/**
 * 민원 종류 (민원 처리에 관한 법률 제2조) 와 처리기간.
 * 처리기간은 같은 법 시행령(2026. 5. 6. 개정) 제14조(질의 14일·7일), 제15조(건의 14일), 제16조(기타 즉시),
 * 제17조(고충 7일, 현장조사 14일은 기간에 넣지 않음)를 국가법령정보센터 원문으로 확인했다.
 */
public enum CivilKind {

    LEGAL("법정민원", "법정",
            "관계 법령에서 정한 요건에 따라 인가·허가·등록 등을 신청하거나, 사실·법률관계의 확인·증명을 신청하는 민원",
            "민원별 법정 기간 [민원처리기준표]"),
    QUERY("질의민원", "질의",
            "법령·제도·절차 등 행정업무에 관하여 행정기관의 설명이나 해석을 요구하는 민원",
            "법령 해석 14일 · 제도·절차 7일"),
    SUGGESTION("건의민원", "건의",
            "행정제도 및 운영의 개선을 요구하는 민원",
            "14일"),
    OTHER("기타민원", "기타",
            "단순한 행정절차·형식요건 상담이나 일상생활 불편사항을 알리는 민원",
            "즉시"),
    GRIEVANCE("고충민원", "고충",
            "위법·부당하거나 소극적인 처분, 불합리한 행정제도로 권리를 침해받거나 불편·부담을 겪는 민원",
            "7일 (현장조사 14일 별도)");

    private final String label;
    private final String shortLabel;
    private final String description;
    private final String periodText;

    CivilKind(String label, String shortLabel, String description, String periodText) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.description = description;
        this.periodText = periodText;
    }

    public String getLabel() { return label; }
    public String getShortLabel() { return shortLabel; }
    public String getDescription() { return description; }
    public String getPeriodText() { return periodText; }

    /**
     * 처리기간 일수. 즉시는 0, 법정민원처럼 사무마다 다르면 null.
     *
     * @param lawInterpretation 질의민원일 때 법령 설명·해석 요구인지 여부
     */
    public Integer periodDays(boolean lawInterpretation) {
        return switch (this) {
            case LEGAL -> null;
            case QUERY -> lawInterpretation ? 14 : 7;
            case SUGGESTION -> 14;
            case OTHER -> 0;
            case GRIEVANCE -> 7;
        };
    }
}
