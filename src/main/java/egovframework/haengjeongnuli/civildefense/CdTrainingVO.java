package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 민방위 교육·훈련 */
public class CdTrainingVO {

    private Long trainingId;
    private String title;
    private LocalDate heldOn;
    private String place;
    private int hours;
    private String createdBy;
    private LocalDateTime createdAt;
    private Integer invited;
    private Integer attended;

    public Long getTrainingId() { return trainingId; }
    public void setTrainingId(Long trainingId) { this.trainingId = trainingId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getHeldOn() { return heldOn; }
    public void setHeldOn(LocalDate heldOn) { this.heldOn = heldOn; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public int getHours() { return hours; }
    public void setHours(int hours) { this.hours = hours; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getInvited() { return invited; }
    public void setInvited(Integer invited) { this.invited = invited; }
    public Integer getAttended() { return attended; }
    public void setAttended(Integer attended) { this.attended = attended; }
}
