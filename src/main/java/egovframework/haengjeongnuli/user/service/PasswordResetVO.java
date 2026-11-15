package egovframework.haengjeongnuli.user.service;

import java.time.LocalDateTime;

/** 로그인 화면에서 받은 비밀번호 분실 요청 */
public class PasswordResetVO {

    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";

    private Long reqId;
    private String loginId;
    private String userName;
    private String contact;
    private String requestIp;
    private LocalDateTime requestedAt;
    private String status;
    private String handledBy;
    private LocalDateTime handledAt;

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case OPEN -> "처리 대기";
            case DONE -> "임시 비밀번호 발급";
            case REJECTED -> "반려";
            default -> "";
        };
    }

    public Long getReqId() { return reqId; }
    public void setReqId(Long reqId) { this.reqId = reqId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
