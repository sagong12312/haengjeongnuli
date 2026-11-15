package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 사업: 정책사업(POLICY) > 단위사업(UNIT) > 세부사업(DETAIL) */
public class ProgramVO {
    public static final String POLICY = "POLICY";
    public static final String UNIT = "UNIT";
    public static final String DETAIL = "DETAIL";
    /** 정책사업 구분 (운영기준: 정책사업, 행정운영경비, 재무활동) */
    public static final List<String> TYPES = List.of("정책사업", "행정운영경비", "재무활동");

    private Long progId;
    private int fiscalYear;
    private String progLevel;
    private Long parentId;
    private String progType;
    private String funcCode;
    private String funcName;
    private String progName;
    private String deptName;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<ProgramVO> children = new ArrayList<>();

    public String getLevelLabel() {
        return POLICY.equals(progLevel) ? "정책사업" : UNIT.equals(progLevel) ? "단위사업" : "세부사업";
    }

    public Long getProgId() { return progId; }
    public void setProgId(Long progId) { this.progId = progId; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public String getProgLevel() { return progLevel; }
    public void setProgLevel(String progLevel) { this.progLevel = progLevel; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getProgType() { return progType; }
    public void setProgType(String progType) { this.progType = progType; }
    public String getFuncCode() { return funcCode; }
    public void setFuncCode(String funcCode) { this.funcCode = funcCode; }
    public String getFuncName() { return funcName; }
    public void setFuncName(String funcName) { this.funcName = funcName; }
    public String getProgName() { return progName; }
    public void setProgName(String progName) { this.progName = progName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<ProgramVO> getChildren() { return children; }
    public void setChildren(List<ProgramVO> children) { this.children = children; }
}
