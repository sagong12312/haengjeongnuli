package egovframework.haengjeongnuli.stats.service.impl;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 업무 통계 (모두 개인을 알아볼 수 없는 합계만 돌려준다) */
@Mapper
public interface StatsMapper {
    List<Map<String, Object>> civilMonthly(@Param("year") int year);
    List<Map<String, Object>> civilByKind(@Param("year") int year);
    List<Map<String, Object>> approvalByStatus(@Param("year") int year);
    List<Map<String, Object>> financeByDept(@Param("year") int year);
    List<Map<String, Object>> welfareByStatus(@Param("year") int year);
    List<Map<String, Object>> hygieneByResult(@Param("year") int year);
    List<Map<String, Object>> buildingByStatus();
    List<Map<String, Object>> dutyByKind(@Param("year") int year);
}
