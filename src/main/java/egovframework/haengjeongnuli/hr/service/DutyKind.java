package egovframework.haengjeongnuli.hr.service;

/** 복무 신청 종류 (지방공무원 복무규정: 연가 제7조, 병가 제7조의5, 공가 제7조의6, 특별휴가 제7조의7 이하) */
public enum DutyKind {
    ANNUAL("연가", true),
    SICK("병가", true),
    OFFICIAL("공가", true),
    SPECIAL("특별휴가", true),
    TRIP("출장", false),
    OVERTIME("시간외근무", false);

    private final String label;
    private final boolean leave;

    DutyKind(String label, boolean leave) {
        this.label = label;
        this.leave = leave;
    }

    public String getLabel() { return label; }

    /** 근무일 수로 세는 휴가인지 */
    public boolean isLeave() { return leave; }
}
