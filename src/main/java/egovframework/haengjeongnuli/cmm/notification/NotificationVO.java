package egovframework.haengjeongnuli.cmm.notification;

import java.time.LocalDateTime;

/** 알림 (NOTIFICATION) */
public class NotificationVO {

    private Long notiId;
    private String loginId;
    private String message;
    private String link;
    private LocalDateTime createdAt;
    private String readYn = "N";

    public boolean isRead() { return "Y".equals(readYn); }

    public Long getNotiId() { return notiId; }
    public void setNotiId(Long notiId) { this.notiId = notiId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getReadYn() { return readYn; }
    public void setReadYn(String readYn) { this.readYn = readYn; }
}
