package egovframework.haengjeongnuli.cmm.file;

import java.time.LocalDateTime;

/** 첨부파일 (ATTACH_FILE 테이블) */
public class AttachFileVO {

    private Long fileId;
    private Long postId;
    private String originalName;
    private String storedName;
    private long fileSize;
    private int downloadCount;
    private LocalDateTime createdAt;

    /** 사람이 읽기 쉬운 크기 (예: 1.2MB) */
    public String getSizeText() {
        if (fileSize < 1024) {
            return fileSize + "B";
        }
        if (fileSize < 1024 * 1024) {
            return String.format("%.1fKB", fileSize / 1024.0);
        }
        return String.format("%.1fMB", fileSize / (1024.0 * 1024));
    }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
