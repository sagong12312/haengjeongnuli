package egovframework.haengjeongnuli.civil.service;

/** 민원 처리 결과 구분 */
public enum ResultType {
    ACCEPT("수용"),
    PARTIAL("일부 수용"),
    ANSWER("답변·안내"),
    REJECT("불수용(거부)"),
    /** 민원인이 처리 완료 전에 스스로 거둬들임 (전자민원). 직원이 고르는 결과가 아니다 */
    WITHDRAWN("취하");

    private final String label;

    ResultType(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** 거부가 들어 있으면 거부 이유와 구제절차를 함께 알려야 한다 (민원처리법 제27조 제3항) */
    public boolean needsRemedy() { return this == REJECT || this == PARTIAL; }

    /** 직원이 처리 완료 때 고를 수 있는 결과 */
    public static ResultType[] staffChoices() {
        return new ResultType[] {ACCEPT, PARTIAL, ANSWER, REJECT};
    }
}
