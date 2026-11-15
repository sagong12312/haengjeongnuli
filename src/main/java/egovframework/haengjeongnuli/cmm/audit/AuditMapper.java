package egovframework.haengjeongnuli.cmm.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/** 접속·작업 기록 SQL (egovframework/mapper/cmm/AuditMapper.xml) */
@Mapper
public interface AuditMapper {

    long nextLogId();

    int insertLog(AuditLogVO log);

    java.util.Map<String, Object> lockChain();

    int updateChain(@org.apache.ibatis.annotations.Param("logId") long logId, @org.apache.ibatis.annotations.Param("hash") String hash);

    java.util.Map<String, Object> selectChain();

    List<AuditLogVO> selectChained(@org.apache.ibatis.annotations.Param("afterId") long afterId,
                                   @org.apache.ibatis.annotations.Param("limit") int limit);

    int countUnchained();

    List<AuditLogVO> selectForReview(@org.apache.ibatis.annotations.Param("from") java.time.LocalDateTime from,
                                     @org.apache.ibatis.annotations.Param("to") java.time.LocalDateTime to);

    long nextReviewId();

    void insertReview(AuditReviewVO r);

    List<AuditReviewVO> selectReviews();

    List<AuditLogVO> selectLogs(AuditSearchVO search);

    int selectLogCount(AuditSearchVO search);
}
