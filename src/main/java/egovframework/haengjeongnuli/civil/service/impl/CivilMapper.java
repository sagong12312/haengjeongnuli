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

    CivilSummaryVO selectSummary(@Param("imminentUntil") LocalDate imminentUntil,
                                 @Param("monthStart") LocalDate monthStart);
}
