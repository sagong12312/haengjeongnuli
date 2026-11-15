package egovframework.haengjeongnuli.hr.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.hr.service.DutyRequestVO;

@Mapper
public interface DutyMapper {
    long nextId();
    void insert(DutyRequestVO r);
    List<DutyRequestVO> selectByUser(@Param("loginId") String loginId, @Param("year") Integer year, @Param("limit") int limit);
    /** 결재 중·승인된 휴가 중 기간이 겹치는 건수 */
    int countOverlap(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                     @Param("overtime") boolean overtime);
    /** 그 해 종류별 일수 합계 (status: DONE / PROG) */
    Double sumDays(@Param("loginId") String loginId, @Param("kind") String kind, @Param("year") int year,
                   @Param("status") String status);
}
