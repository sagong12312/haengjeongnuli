package egovframework.haengjeongnuli.cmm.audit;

/** 기록하는 작업 종류 */
public enum AuditAction {
    LOGIN_SUCCESS("로그인"),
    LOGIN_FAIL("로그인 실패"),
    LOGOUT("로그아웃"),
    PASSWORD_CHANGE("비밀번호 변경"),

    USER_CREATE("계정 생성"),
    USER_UNLOCK("계정 잠금 해제"),
    USER_ENABLE("계정 사용"),
    USER_DISABLE("계정 사용 중지"),
    USER_PASSWORD_RESET("비밀번호 재설정"),
    USER_DEPT_MOVE("부서 이동"),
    USER_REVENUE("세입 담당 지정·해제"),
    USER_PROFILE("인사정보 수정"),

    POST_CREATE("게시글 등록"),
    POST_UPDATE("게시글 수정"),
    POST_DELETE("게시글 삭제"),
    COMMENT_CREATE("댓글 등록"),
    COMMENT_DELETE("댓글 삭제"),
    FILE_DOWNLOAD("파일 내려받기"),

    CIVIL_RECEIVE("민원 접수"),
    CIVIL_VIEW("민원 상세 열람(개인정보)"),
    CIVIL_ASSIGN("민원 배정"),
    CIVIL_EXTEND("민원 처리기간 연장"),
    CIVIL_PROGRESS("민원 진행상황 통지"),
    CIVIL_DONE("민원 처리 완료"),

    APPROVAL_SAVE("기안 임시저장"),
    APPROVAL_SUBMIT("결재 상신"),
    APPROVAL_APPROVE("결재 승인"),
    APPROVAL_REJECT("결재 반려"),
    APPROVAL_WITHDRAW("결재 회수"),
    APPROVAL_RECEIVE("문서 접수"),
    APPROVAL_DELETE("임시저장 문서 삭제"),

    FIN_PROGRAM("사업 등록·삭제"),
    FIN_BUDGET_SAVE("예산 작성"),
    FIN_BUDGET_REQUEST("예산 승인 요청"),
    FIN_BUDGET_APPROVE("예산 승인"),
    FIN_BUDGET_REJECT("예산 반려"),
    FIN_EXECUTE("예산 집행"),
    FIN_RECEIPT("세입 수납"),
    FIN_TXN_CANCEL("집행·수납 취소"),

    HOLIDAY_ADD("공휴일 추가"),
    HOLIDAY_DELETE("공휴일 삭제"),

    ATTEND_IN("출근"),
    ATTEND_OUT("퇴근");

    private final String label;

    AuditAction(String label) { this.label = label; }

    public String getLabel() { return label; }
}
