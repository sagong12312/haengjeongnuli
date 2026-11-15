package egovframework.haengjeongnuli.hr.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 하루 출퇴근 기록 */
public class AttendanceVO {
    private Long attId;
    private String loginId;
    private String userName;
    private String deptName;
    private LocalDate workDate;
    private LocalDateTime inAt;
    private LocalDateTime outAt;
    private String inIp;
    private String outIp;

    /** 퇴근을 찍은 날의 근무 시간 ("8시간 30분"). 아직 퇴근 전이면 null */
    public String getWorkedText() {
        if (inAt == null || outAt == null) {
            return null;
        }
        long min = Math.max(0, Duration.between(inAt, outAt).toMinutes());
        return (min / 60) + "시간 " + (min % 60) + "분";
    }

    /** 요일 (월~일) */
    public String getWeekday() {
        return workDate == null ? "" : "월화수목금토일".substring(workDate.getDayOfWeek().getValue() - 1,
                workDate.getDayOfWeek().getValue());
    }

    public Long getAttId() { return attId; }
    public void setAttId(Long attId) { this.attId = attId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public LocalDateTime getInAt() { return inAt; }
    public void setInAt(LocalDateTime inAt) { this.inAt = inAt; }
    public LocalDateTime getOutAt() { return outAt; }
    public void setOutAt(LocalDateTime outAt) { this.outAt = outAt; }
    public String getInIp() { return inIp; }
    public void setInIp(String inIp) { this.inIp = inIp; }
    public String getOutIp() { return outIp; }
    public void setOutIp(String outIp) { this.outIp = outIp; }
}
