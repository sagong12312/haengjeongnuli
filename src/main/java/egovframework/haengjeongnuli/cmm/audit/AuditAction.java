package egovframework.haengjeongnuli.cmm.audit;

/** 기록하는 작업 종류 */
public enum AuditAction {
    LOGIN_SUCCESS("로그인"),
    LOGIN_FAIL("로그인 실패"),
    LOGOUT("로그아웃"),
    SESSION_REVOKED("세션 강제 종료"),
    ABUSE_BLOCK("이상 요청 주소 차단"),
    ABUSE_RELEASE("차단 해제"),
    ABUSE_APPEAL("차단 이의 신청"),
    PASSWORD_CHANGE("비밀번호 변경"),

    USER_CREATE("계정 생성"),
    USER_UNLOCK("계정 잠금 해제"),
    USER_ENABLE("계정 사용"),
    USER_DISABLE("계정 사용 중지"),
    USER_PASSWORD_RESET("비밀번호 재설정"),
    USER_RECORDS_MANAGER("기록물관리책임자 지정"),
    ORG_UPDATE("기관 정보 변경"),
    AUDIT_REVIEW("접속기록 점검"),
    FILE_ORPHAN_DELETE("남는 첨부파일 정리"),
    ORG_SEAL("관인 이미지 변경"),
    RECORD_FIX_REQUEST("등록정보 수정 요청"),
    RECORD_FIX_CONFIRM("등록정보 수정 확인"),
    PASSWORD_HELP_REQUEST("비밀번호 분실 요청"),
    PASSWORD_HELP_REJECT("비밀번호 분실 요청 반려"),
    USER_DEPT_MOVE("부서 이동"),
    DEPT_CREATE("부서 만들기"),
    DEPT_UPDATE("부서 정보 변경"),
    DEPT_CLOSE("부서 폐지"),
    DEPT_REOPEN("부서 다시 사용"),
    USER_REVENUE("세입 담당 지정·해제"),
    USER_PROFILE("인사정보 수정"),
    USER_WELFARE("사회복지 담당 지정·해제"),

    POST_CREATE("게시글 등록"),
    POST_UPDATE("게시글 수정"),
    POST_DELETE("게시글 삭제"),
    COMMENT_CREATE("댓글 등록"),
    COMMENT_DELETE("댓글 삭제"),
    FILE_DOWNLOAD("파일 내려받기"),
    FILE_VIEW("한글 문서 화면 보기"),

    CIVIL_RECEIVE("민원 접수"),
    CIVIL_VIEW("민원 상세 열람(개인정보)"),
    CIVIL_FILE_ADD("민원 구비서류 추가"),
    CERT_ISSUE("증명 발급 기록"),
    CONTRACT_SAVE("계약 등록·변경"),
    PURCHASE_SAVE("구매·견적·검수 기록"),
    ASSET_SAVE("물품대장 기록"),
    FIN_EXPORT("재정 자료 내려받기"),
    CERT_EXPORT("증명 발급 일계표 내려받기"),
    CIVIL_ASSIGN("민원 배정"),
    CIVIL_EXTEND("민원 처리기간 연장"),
    CIVIL_PROGRESS("민원 진행상황 통지"),
    CIVIL_DONE("민원 처리 완료"),

    APPROVAL_SAVE("기안 임시저장"),
    APPROVAL_SUBMIT("결재 상신"),
    APPROVAL_APPROVE("결재 승인"),
    APPROVAL_REJECT("결재 반려"),
    APPROVAL_PROXY("대결자 지정"),
    APPROVAL_SIHAENG("시행문 출력"),
    APPROVAL_DISPATCH("시행문 발송 기록"),
    APPROVAL_HWP("한글 문서 만들기"),
    APPROVAL_TEMPLATE("기안 서식"),
    APPROVAL_WITHDRAW("결재 회수"),
    APPROVAL_RECEIVE("문서 접수"),
    APPROVAL_DELETE("임시저장 문서 삭제"),

    FIN_PROGRAM("사업 등록·삭제"),
    FIN_BUDGET_SAVE("예산 작성"),
    FIN_BUDGET_REQUEST("예산 승인 요청"),
    FIN_BUDGET_APPROVE("예산 승인"),
    FIN_BUDGET_REJECT("예산 반려"),
    FIN_BUDGET_CHANGE("추가경정·이월"),
    FIN_COMMIT_CLOSE("품의 잔액 해제"),
    FIN_EXECUTE("예산 집행"),
    FIN_RECEIPT("세입 수납"),
    FIN_TXN_CANCEL("집행·수납 취소"),

    BUILDING_EDIT("건축 인허가 대장 수정"),
    HYGIENE_EDIT("영업소·위생 점검 기록"),
    WELFARE_VIEW("복지 신청 열람(개인정보)"),
    WELFARE_EDIT("복지 신청 처리"),

    RECORD_REGISTER("기록물 직접 등록"),
    RECORD_TRANSFER_LIST("이관 목록 내려받기"),

    HOLIDAY_ADD("공휴일 추가"),
    HOLIDAY_DELETE("공휴일 삭제"),

    ATTEND_IN("출근"),
    ATTEND_OUT("퇴근");

    private final String label;

    AuditAction(String label) { this.label = label; }

    public String getLabel() { return label; }
}
