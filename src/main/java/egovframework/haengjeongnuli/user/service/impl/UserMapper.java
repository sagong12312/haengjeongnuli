package egovframework.haengjeongnuli.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 직원 계정 SQL (egovframework/mapper/user/UserMapper.xml) */
@Mapper
public interface UserMapper {

    long nextId();

    int countUsers();

    LoginUser selectByLoginId(@Param("loginId") String loginId);

    LoginUser selectById(@Param("userId") long userId);

    List<LoginUser> selectAll();

    int insertUser(LoginUser user);

    int increaseFailedCount(@Param("loginId") String loginId);

    int markLoginSuccess(@Param("loginId") String loginId, @Param("now") LocalDateTime now);

    int resetFailedCount(@Param("userId") long userId);

    int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);

    int updateEnabled(@Param("userId") long userId, @Param("enabledYn") String enabledYn);

    int updateDept(@Param("userId") long userId, @Param("deptName") String deptName);

    int insertDeptHistory(@Param("userId") long userId, @Param("fromDept") String fromDept,
                          @Param("toDept") String toDept, @Param("movedAt") LocalDateTime movedAt,
                          @Param("movedBy") String movedBy);

    List<egovframework.haengjeongnuli.user.service.DeptMoveVO> selectRecentDeptMoves(@Param("limit") int limit);
}
