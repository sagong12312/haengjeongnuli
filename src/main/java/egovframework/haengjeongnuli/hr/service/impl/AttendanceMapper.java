package egovframework.haengjeongnuli.hr.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.hr.service.AttendanceVO;

@Mapper
public interface AttendanceMapper {
    long nextId();
    void insert(AttendanceVO a);
    AttendanceVO selectLatest(@Param("loginId") String loginId);
    AttendanceVO selectDay(@Param("loginId") String loginId, @Param("day") LocalDate day);
    int reopen(@Param("attId") long attId);
    int close(@Param("attId") long attId, @Param("at") LocalDateTime at, @Param("ip") String ip);
    List<AttendanceVO> selectRange(@Param("loginId") String loginId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
