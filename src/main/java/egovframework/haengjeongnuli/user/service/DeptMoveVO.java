package egovframework.haengjeongnuli.user.service;

import java.time.LocalDateTime;

/** 부서 이동 기록 (USER_DEPT_HISTORY) */
public class DeptMoveVO {

    private Long historyId;
    private Long userId;
    private String loginId;
    private String userName;
    private String fromDept;
    private String toDept;
    private LocalDateTime movedAt;
    private String movedBy;

    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getFromDept() { return fromDept; }
    public void setFromDept(String fromDept) { this.fromDept = fromDept; }
    public String getToDept() { return toDept; }
    public void setToDept(String toDept) { this.toDept = toDept; }
    public LocalDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(LocalDateTime movedAt) { this.movedAt = movedAt; }
    public String getMovedBy() { return movedBy; }
    public void setMovedBy(String movedBy) { this.movedBy = movedBy; }
}
