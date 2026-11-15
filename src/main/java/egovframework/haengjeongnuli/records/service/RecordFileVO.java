package egovframework.haengjeongnuli.records.service;

import java.time.LocalDateTime;

/** 직접 등록한 문서의 전자 파일 */
public class RecordFileVO {
    private Long fileId;
    private Long regId;
    private String originalName;
    private String storedName;
    private long fileSize;
    private LocalDateTime createdAt;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getRegId() { return regId; }
    public void setRegId(Long regId) { this.regId = regId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
