package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDateTime;

/** 민원 처리 이력 한 줄 */
public class CivilHistoryVO {
    private Long histId;
    private Long complaintId;
    /** ASSIGN / EXTEND / PROGRESS / DONE / VIEW */
    private String action;
    private String detail;
    private String actorId;
    private String actorName;
    private LocalDateTime actedAt;

    public String getActionLabel() {
        return switch (action) {
            case "ASSIGN" -> "담당 배정";
            case "EXTEND" -> "처리기간 연장";
            case "PROGRESS" -> "진행상황 통지";
            case "DONE" -> "처리 완료";
            default -> action;
        };
    }

    public Long getHistId() { return histId; }
    public void setHistId(Long histId) { this.histId = histId; }
    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
}
