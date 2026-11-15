package egovframework.haengjeongnuli.approval.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.approval.service.ApprovalTemplateVO;

@Mapper
public interface ApprovalTemplateMapper {

    String COLS = "TEMPLATE_ID, NAME, SCOPE, OWNER_ID, OWNER_NAME, DEPT_NAME, KIND, RETENTION, TITLE, BODY, CREATED_AT ";

    @Select("SELECT SEQ_APPROVAL_TEMPLATE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO APPROVAL_TEMPLATE (" + COLS + ", DELETED_YN) VALUES (#{templateId}, #{name}, #{scope}, #{ownerId}, #{ownerName}, "
            + "#{deptName}, #{kind}, #{retention}, #{title}, #{body, jdbcType=CLOB}, #{createdAt}, 'N')")
    void insert(ApprovalTemplateVO t);

    /** 내가 쓸 수 있는 서식: 내 것, 우리 부서 것, 기관 전체 */
    @Select("SELECT " + COLS + "FROM APPROVAL_TEMPLATE WHERE DELETED_YN = 'N' AND (OWNER_ID = #{loginId} OR SCOPE = 'ALL' "
            + "OR (SCOPE = 'DEPT' AND DEPT_NAME = #{dept})) ORDER BY CASE SCOPE WHEN 'MINE' THEN 0 WHEN 'DEPT' THEN 1 ELSE 2 END, NAME")
    List<ApprovalTemplateVO> selectVisible(@Param("loginId") String loginId, @Param("dept") String dept);

    @Select("SELECT " + COLS + "FROM APPROVAL_TEMPLATE WHERE TEMPLATE_ID = #{id} AND DELETED_YN = 'N'")
    ApprovalTemplateVO selectById(@Param("id") long id);

    @Update("UPDATE APPROVAL_TEMPLATE SET DELETED_YN = 'Y' WHERE TEMPLATE_ID = #{id}")
    int markDeleted(@Param("id") long id);
}
