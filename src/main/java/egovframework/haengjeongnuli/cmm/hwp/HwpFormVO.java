package egovframework.haengjeongnuli.cmm.hwp;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 한글 서식함의 서식 한 개 */
public class HwpFormVO {

    public static final List<String> CATEGORIES = List.of("민원", "위생", "건축", "증명", "일반");

    private Long formId;
    private String name;
    private String category;
    private String originalName;
    private String storedName;
    private long fileSize;
    /** 누름틀 이름들 (줄바꿈으로 구분) */
    private String fields;
    private String fillableYn;
    private String useYn;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    public List<String> getFieldList() {
        return fields == null || fields.isBlank() ? List.of() : Arrays.stream(fields.split("\n")).filter(s -> !s.isBlank()).toList();
    }
    public boolean isFillable() { return "Y".equals(fillableYn) && !getFieldList().isEmpty(); }
    public boolean isInUse() { return "Y".equals(useYn); }

    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }
    public String getFillableYn() { return fillableYn; }
    public void setFillableYn(String fillableYn) { this.fillableYn = fillableYn; }
    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
