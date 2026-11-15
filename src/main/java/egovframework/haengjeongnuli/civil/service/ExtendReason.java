package egovframework.haengjeongnuli.civil.service;

/**
 * 처리기간 연장 사유 (민원처리법 시행령 제21조 제1항, 2026. 5. 6. 개정).
 * 단순 업무과중, 담당자 지정 지연·부재는 연장 사유가 될 수 없다(같은 조 제2항).
 */
public enum ExtendReason {
    COOPERATION("관계 기관·부서의 협조가 필요한 경우"),
    FACT_CHECK("사실관계 또는 현장 확인이 필요한 경우"),
    FORCE_MAJEURE("천재지변이나 그 밖의 불가항력적인 사유가 발생한 경우"),
    SIMILAR("그 밖에 위의 사유에 준하는 사유가 있는 경우");

    private final String label;

    ExtendReason(String label) { this.label = label; }

    public String getLabel() { return label; }
}
