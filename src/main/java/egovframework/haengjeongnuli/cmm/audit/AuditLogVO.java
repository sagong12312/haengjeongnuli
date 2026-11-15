package egovframework.haengjeongnuli.cmm.audit;

import java.time.LocalDateTime;

/** 접속·작업 기록 한 줄 (AUDIT_LOG) */
public class AuditLogVO {

    private Long logId;
    private LocalDateTime occurredAt;
    private String actorId;
    private AuditAction action;
    private String target;
    private String detail;
    private String clientIp;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    /** 앞 기록의 해시, 이 기록의 해시 (위·변조 확인용) */
    private String prevHash;
    private String rowHash;
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getRowHash() { return rowHash; }
    public void setRowHash(String rowHash) { this.rowHash = rowHash; }
}
