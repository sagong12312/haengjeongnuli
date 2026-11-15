package egovframework.haengjeongnuli.civil.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import egovframework.haengjeongnuli.civil.service.CivilFileVO;

@Mapper
public interface CivilFileMapper {

    @Select("SELECT SEQ_CIVIL_FILE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO CIVIL_FILE (FILE_ID, COMPLAINT_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, UPLOADED_BY, CREATED_AT) "
            + "VALUES (#{fileId}, #{complaintId}, #{originalName}, #{storedName}, #{fileSize}, #{uploadedBy}, #{createdAt})")
    void insert(CivilFileVO f);

    @Select("SELECT FILE_ID, COMPLAINT_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, UPLOADED_BY, CREATED_AT "
            + "FROM CIVIL_FILE WHERE COMPLAINT_ID = #{id} ORDER BY FILE_ID")
    List<CivilFileVO> selectByComplaint(@Param("id") long complaintId);

    @Select("SELECT FILE_ID, COMPLAINT_ID, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, UPLOADED_BY, CREATED_AT "
            + "FROM CIVIL_FILE WHERE FILE_ID = #{id}")
    CivilFileVO selectById(@Param("id") long fileId);
}
