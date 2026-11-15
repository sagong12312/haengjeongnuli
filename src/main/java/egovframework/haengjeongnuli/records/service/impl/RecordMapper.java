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

    // 등록정보 수정 (기록물관리책임자 확인)
    long nextFixId();
    void insertFix(egovframework.haengjeongnuli.records.service.RecordFixVO f);
    List<egovframework.haengjeongnuli.records.service.RecordFixVO> selectFixes(@Param("regId") long regId);
    egovframework.haengjeongnuli.records.service.RecordFixVO selectFix(@Param("fixId") long fixId);
    int countOpenFix(@Param("regId") long regId, @Param("field") String field);
    /** 확인 대기 중인 수정 요청 (처리과 기준) */
    List<egovframework.haengjeongnuli.records.service.RecordFixVO> selectOpenFixes(@Param("dept") String dept);
    int closeFix(@Param("fixId") long fixId, @Param("status") String status, @Param("by") String by,
                 @Param("byName") String byName, @Param("at") java.time.LocalDateTime at);
    /** 확인된 수정을 등록정보에 반영한다 (칸 이름은 XML 에서 정해진 것만) */
    int updateField(@Param("regId") long regId, @Param("field") String field, @Param("value") String value,
                    @Param("pages") Integer pages);
}
