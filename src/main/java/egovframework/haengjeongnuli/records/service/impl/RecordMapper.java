package egovframework.haengjeongnuli.records.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.records.service.RecordFileVO;
import egovframework.haengjeongnuli.records.service.RecordVO;

@Mapper
public interface RecordMapper {
    // 처리과·연도별 등록일련번호
    Integer lockCounter(@Param("dept") String dept, @Param("year") int year);
    void insertCounter(@Param("dept") String dept, @Param("year") int year, @Param("lastSeq") int lastSeq);
    void updateCounter(@Param("dept") String dept, @Param("year") int year, @Param("lastSeq") int lastSeq);
    /** 카운터가 없던 때 전자결재로 붙인 마지막 번호 */
    int maxApprovalSeq(@Param("dept") String dept, @Param("year") int year);

    long nextId();
    long nextFileId();
    void insert(RecordVO r);
    void insertFile(RecordFileVO f);
    RecordVO select(@Param("id") long id);
    List<RecordFileVO> selectFiles(@Param("regId") long regId);
    RecordFileVO selectFile(@Param("fileId") long fileId);
    List<RecordVO> selectList(@Param("all") boolean all, @Param("dept") String dept, @Param("loginId") String loginId,
                              @Param("year") int year,
                              @Param("type") String type, @Param("q") String q, @Param("limit") int limit);
}
