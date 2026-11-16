package egovframework.haengjeongnuli.foreign;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 외국인주민 지원 한 건 */
public class ForeignSupportVO {

    public static final List<String> KINDS = List.of("통역·번역", "한국어 교육", "생활 상담", "노동·체류 상담 연계", "긴급 지원", "기타");

    private Long supportId;
    private Long resId;
    private LocalDate supportOn;
    private String kind;
    private String content;
    private String staffName;
    private LocalDateTime createdAt;

    public Long getSupportId() { return supportId; }
    public void setSupportId(Long supportId) { this.supportId = supportId; }
    public Long getResId() { return resId; }
    public void setResId(Long resId) { this.resId = resId; }
    public LocalDate getSupportOn() { return supportOn; }
    public void setSupportOn(LocalDate supportOn) { this.supportOn = supportOn; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
