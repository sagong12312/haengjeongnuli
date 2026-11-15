package egovframework.haengjeongnuli.civil.service;

/** 민원 처리 결과 구분 */
public enum ResultType {
    ACCEPT("수용"),
    PARTIAL("일부 수용"),
    ANSWER("답변·안내"),
    REJECT("불수용(거부)");

    private final String label;

    ResultType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** 거부가 들어 있으면 거부 이유와 구제절차를 함께 알려야 한다 (민원처리법 제27조 제3항) */
    public boolean needsRemedy() { return this == REJECT || this == PARTIAL; }
}
