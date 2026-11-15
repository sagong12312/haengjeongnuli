package egovframework.haengjeongnuli.cmm.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/** 접속·작업 기록 SQL (egovframework/mapper/cmm/AuditMapper.xml) */
@Mapper
public interface AuditMapper {

    int insertLog(AuditLogVO log);

    List<AuditLogVO> selectLogs(AuditSearchVO search);

    int selectLogCount(AuditSearchVO search);
}
