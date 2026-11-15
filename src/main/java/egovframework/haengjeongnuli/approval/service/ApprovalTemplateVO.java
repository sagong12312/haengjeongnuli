package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;
import java.util.Map;

/** 기안 서식: 자주 쓰는 문서 종류·제목·본문을 저장해 두고 불러온다 */
public class ApprovalTemplateVO {

    /** 나만 / 우리 부서 / 기관 전체(관리자만 만든다) */
    public static final Map<String, String> SCOPES = Map.of("MINE", "나만", "DEPT", "우리 부서", "ALL", "기관 전체");

    private Long templateId;
    private String name;
    private String scope;
    private String ownerId;
    private String ownerName;
    private String deptName;
    private String kind;
    private String retention;
    private String title;
    private String body;
    private LocalDateTime createdAt;

    public String getScopeLabel() { return SCOPES.getOrDefault(scope, scope); }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getRetention() { return retention; }
    public void setRetention(String retention) { this.retention = retention; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
