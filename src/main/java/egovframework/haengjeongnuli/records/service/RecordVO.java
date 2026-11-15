package egovframework.haengjeongnuli.records.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 기록물 등록대장 한 줄 (생산·접수 등록) */
public class RecordVO {
    public static final String PRODUCE = "PRODUCE";
    public static final String RECEIVE = "RECEIVE";
    /** 기록물 형태 */
    public static final List<String> MEDIA = List.of("전자문서", "종이문서", "팩스", "전자우편", "기타");

    private Long regId;
    private String regType;
    private String regNo;
    private int regYear;
    private String deptName;
    private String title;
    private LocalDate regDate;
    /** 접수: 발신 기관 / 생산: 수신처 */
    private String counterpart;
    /** 접수: 발신 기관의 문서번호 */
    private String counterpartNo;
    private String medium;
    private Integer pages;
    private String retention;
    private String disclosure;
    /** APPROVAL / MANUAL */
    private String source;
    private Long docId;
    private String handlerId;
    private String handlerName;
    private String note;
    private String registeredBy;
    private String registeredName;
    private LocalDateTime registeredAt;
    private List<RecordFileVO> files = new ArrayList<>();

    public String getTypeLabel() { return PRODUCE.equals(regType) ? "생산" : "접수"; }
    public String getSourceLabel() { return "APPROVAL".equals(source) ? "전자결재" : "직접 등록"; }

    public Long getRegId() { return regId; }
    public void setRegId(Long regId) { this.regId = regId; }
    public String getRegType() { return regType; }
    public void setRegType(String regType) { this.regType = regType; }
    public String getRegNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }
    public int getRegYear() { return regYear; }
    public void setRegYear(int regYear) { this.regYear = regYear; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getRegDate() { return regDate; }
    public void setRegDate(LocalDate regDate) { this.regDate = regDate; }
    public String getCounterpart() { return counterpart; }
    public void setCounterpart(String counterpart) { this.counterpart = counterpart; }
    public String getCounterpartNo() { return counterpartNo; }
    public void setCounterpartNo(String counterpartNo) { this.counterpartNo = counterpartNo; }
    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }
    public Integer getPages() { return pages; }
    public void setPages(Integer pages) { this.pages = pages; }
    public String getRetention() { return retention; }
    public void setRetention(String retention) { this.retention = retention; }
    public String getDisclosure() { return disclosure; }
    public void setDisclosure(String disclosure) { this.disclosure = disclosure; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }
    public String getRegisteredName() { return registeredName; }
    public void setRegisteredName(String registeredName) { this.registeredName = registeredName; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public List<RecordFileVO> getFiles() { return files; }
    public void setFiles(List<RecordFileVO> files) { this.files = files; }

    /** 등록 취소 표시 (지우지 않고 남긴다) */
    private String canceledYn = "N";
    public String getCanceledYn() { return canceledYn; }
    public void setCanceledYn(String canceledYn) { this.canceledYn = canceledYn; }
    public boolean isCanceled() { return "Y".equals(canceledYn); }
    public boolean isManual() { return "MANUAL".equals(source); }
}
