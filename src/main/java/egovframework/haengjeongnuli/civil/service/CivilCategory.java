package egovframework.haengjeongnuli.civil.service;

/**
 * 민원 분야 (무슨 일에 관한 민원인지). 민원 종류(CivilKind: 법정·질의·건의·기타·고충)는
 * 민원 처리에 관한 법률 제2조가 정한 것이라 그대로 두고, 담당 부서를 빨리 찾도록 분야를 따로 고른다.
 */
public enum CivilCategory {
    ROAD("도로·교통", "도로 파손, 신호등·가로등, 교통 시설"),
    PARKING("주차", "불법 주정차, 공영주차장"),
    TRANSIT("대중교통", "버스 노선·정류장, 택시"),
    CLEAN("환경·청소", "생활 쓰레기, 무단 투기, 소음·악취"),
    WATER("상하수도", "수돗물, 누수, 하수구 막힘"),
    PARK("공원·녹지", "공원 시설, 가로수, 산림"),
    BUILDING("주택·건축", "건축 허가·신고, 공동주택, 불법 건축물"),
    LAND("도시계획·토지", "도시계획, 지적, 토지 이용"),
    WELFARE("복지", "기초생활보장, 노인·장애인·아동 복지"),
    HEALTH("보건·위생", "보건소, 감염병, 식품·공중위생 업소"),
    TAX("세무", "지방세 부과·납부, 세외수입"),
    SAFETY("안전·재난", "재난·재해, 시설물 안전, 민방위"),
    CULTURE("문화·체육·관광", "도서관, 체육시설, 축제·관광"),
    ECONOMY("경제·일자리", "일자리, 소상공인, 전통시장"),
    ANIMAL("동물", "반려동물 등록, 유기동물, 동물 피해"),
    ADMIN("행정·민원 서비스", "증명 발급, 공무원 친절, 행정 절차"),
    ETC("기타", "위 분야에 없는 일");

    private final String label;
    private final String examples;

    CivilCategory(String label, String examples) {
        this.label = label;
        this.examples = examples;
    }

    public String getLabel() { return label; }
    public String getExamples() { return examples; }

    /** 이름으로 찾기 (없거나 틀리면 null) */
    public static CivilCategory of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
