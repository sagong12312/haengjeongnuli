package egovframework.haengjeongnuli.hygiene.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 위생 점검 기록 (식품위생법 제22조 출입·검사) */
public class HygieneCheckVO {
    public static final List<String> ACTIONS = List.of("조치 없음", "현장 시정", "시정명령", "행정처분 의뢰", "고발");

    private Long checkId;
    private Long bizId;
    private LocalDate checkedOn;
    /** 적합 / 부적합 */
    private String result;
    private String violation;
    private String action;
    private String inspectorId;
    private String inspector;
    private LocalDateTime createdAt;

    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public LocalDate getCheckedOn() { return checkedOn; }
    public void setCheckedOn(LocalDate checkedOn) { this.checkedOn = checkedOn; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getViolation() { return violation; }
    public void setViolation(String violation) { this.violation = violation; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getInspectorId() { return inspectorId; }
    public void setInspectorId(String inspectorId) { this.inspectorId = inspectorId; }
    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
