package egovframework.haengjeongnuli.welfare.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.welfare.service.WelfareCaseVO;

@Mapper
public interface WelfareMapper {
    long nextId();
    void insert(WelfareCaseVO c);
    WelfareCaseVO select(@Param("id") long id);
    List<WelfareCaseVO> selectList(@Param("status") String status, @Param("q") String q, @Param("limit") int limit);
    int updateSurvey(@Param("id") long id, @Param("note") String note);
    int extend(WelfareCaseVO c);
    int decide(WelfareCaseVO c);
    int withdraw(@Param("id") long id, @Param("note") String note);
}
