package egovframework.haengjeongnuli.building.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.building.service.BuildingCaseVO;

@Mapper
public interface BuildingMapper {
    long nextId();
    void insert(BuildingCaseVO c);
    BuildingCaseVO select(@Param("id") long id);
    List<BuildingCaseVO> selectList(@Param("q") String q, @Param("limit") int limit);
    int markStarted(@Param("id") long id, @Param("on") LocalDate on);
    int extend(@Param("id") long id, @Param("deadline") LocalDate deadline);
    int updateNote(@Param("id") long id, @Param("note") String note);
    int cancel(@Param("id") long id, @Param("note") String note);
}
