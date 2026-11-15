package egovframework.haengjeongnuli.finance.service;

/** 예산 과목 코드 (기능별 분야·부문 / 성질별 목그룹·편성목·통계목 / 세입 장·관·항·목) */
public class FinCodeVO {
    public static final String FUNC = "FUNC";
    public static final String ITEM = "ITEM";
    public static final String REV = "REV";

    private String codeType;
    private String code;
    private String codeName;
    private String parentCode;
    private String codeLevel;
    private int sortNo;

    public String getLabel() { return code + " " + codeName; }

    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getCodeLevel() { return codeLevel; }
    public void setCodeLevel(String codeLevel) { this.codeLevel = codeLevel; }
    public int getSortNo() { return sortNo; }
    public void setSortNo(int sortNo) { this.sortNo = sortNo; }
}
