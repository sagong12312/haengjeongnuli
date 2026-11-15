package egovframework.haengjeongnuli.civil.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilSummaryVO;

/** 민원 SQL (egovframework/mapper/civil/CivilMapper.xml) */
@Mapper
public interface CivilMapper {

    long nextId();

    int insertComplaint(CivilComplaintVO vo);

    List<CivilComplaintVO> selectComplaintList(CivilSearchVO search);

    int selectComplaintCount(CivilSearchVO search);

    CivilComplaintVO selectComplaint(@Param("id") long id);

    int updateAssign(@Param("id") long id, @Param("dept") String dept, @Param("assignee") String assignee,
                     @Param("assigneeId") String assigneeId);

    int updateDeadline(@Param("id") long id, @Param("deadline") LocalDate deadline,
                       @Param("deadlineAt") java.time.LocalDateTime deadlineAt, @Param("extendCount") int extendCount);

    int complete(CivilComplaintVO vo);

    long nextHistoryId();

    void insertHistory(egovframework.haengjeongnuli.civil.service.CivilHistoryVO h);

    List<egovframework.haengjeongnuli.civil.service.CivilHistoryVO> selectHistory(@Param("id") long id);

    CivilSummaryVO selectSummary(@Param("imminentUntil") LocalDate imminentUntil,
                                 @Param("monthStart") LocalDate monthStart);
}
