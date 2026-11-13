package egovframework.haengjeongnuli.civil.service;

/** 민원 처리 상태 */
public enum CivilStatus {
    RECEIVED("접수 대기", "badge-gray"),
    IN_PROGRESS("처리 중", "badge-blue"),
    DONE("처리 완료", "badge-green");

    private final String label;
    private final String badge;

    CivilStatus(String label, String badge) {
        this.label = label;
        this.badge = badge;
    }

    public String getLabel() { return label; }
    public String getBadge() { return badge; }
}
