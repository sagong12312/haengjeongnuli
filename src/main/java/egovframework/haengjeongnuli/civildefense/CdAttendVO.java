package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 교육 대상자 한 명의 통지·참석 */
public class CdAttendVO {

    public static final List<String> RESULTS = List.of("통지", "참석", "불참", "면제");

    private Long trainingId;
    private Long memberId;
    private LocalDate notifiedOn;
    private String result;
    private String note;
    private String name;
    private String unitName;
    private String phone;

    public Long getTrainingId() { return trainingId; }
    public void setTrainingId(Long trainingId) { this.trainingId = trainingId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public LocalDate getNotifiedOn() { return notifiedOn; }
    public void setNotifiedOn(LocalDate notifiedOn) { this.notifiedOn = notifiedOn; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
