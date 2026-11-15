package egovframework.haengjeongnuli.dept.service;

import java.time.LocalDateTime;

/** 부서 (조직도의 한 칸) */
public class DeptVO {

    private Long deptId;
    private String deptName;
    private Long parentId;
    private String parentName;
    private String deptCode;
    private int sortOrder;
    private String useYn;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private int memberCount;
    /** 조직도에서 몇 단계 아래인지 (화면 들여쓰기용) */
    private int depth;

    public boolean isActive() { return "Y".equals(useYn); }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public String getDeptCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
