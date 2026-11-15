package egovframework.haengjeongnuli.hygiene.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.hygiene.service.FoodBusinessVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneCheckVO;

@Mapper
public interface HygieneMapper {
    long nextBizId();
    long nextCheckId();
    void insertBusiness(FoodBusinessVO b);
    FoodBusinessVO selectBusiness(@Param("id") long id);
    List<FoodBusinessVO> selectBusinesses(@Param("q") String q, @Param("type") String type, @Param("limit") int limit);
    int closeBusiness(@Param("id") long id, @Param("on") LocalDate on);
    void insertCheck(HygieneCheckVO c);
    List<HygieneCheckVO> selectChecks(@Param("bizId") long bizId);
}
