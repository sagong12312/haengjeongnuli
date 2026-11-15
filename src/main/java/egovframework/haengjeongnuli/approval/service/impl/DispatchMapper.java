package egovframework.haengjeongnuli.approval.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import egovframework.haengjeongnuli.approval.service.DispatchVO;

@Mapper
public interface DispatchMapper {

    @Select("SELECT SEQ_APPROVAL_DISPATCH.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO APPROVAL_DISPATCH (DISPATCH_ID, DOC_ID, RECEIVER, METHOD, NOTE, DISPATCHED_BY, DISPATCHED_NAME, DISPATCHED_AT) "
            + "VALUES (#{dispatchId}, #{docId}, #{receiver}, #{method}, #{note}, #{dispatchedBy}, #{dispatchedName}, #{dispatchedAt})")
    void insert(DispatchVO v);

    @Select("SELECT DISPATCH_ID, DOC_ID, RECEIVER, METHOD, NOTE, DISPATCHED_BY, DISPATCHED_NAME, DISPATCHED_AT "
            + "FROM APPROVAL_DISPATCH WHERE DOC_ID = #{docId} ORDER BY DISPATCH_ID")
    List<DispatchVO> selectByDoc(@Param("docId") long docId);
}
