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

    /** changedAt 이 null 이면 '처음 받은 비밀번호'로 보고 다음 로그인 때 바꾸게 한다 */
    int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash,
                       @Param("changedAt") LocalDateTime changedAt);

    int updateEnabled(@Param("userId") long userId, @Param("enabledYn") String enabledYn);

    int updateProfile(egovframework.haengjeongnuli.cmm.LoginUser u);

    int updateWelfare(@Param("userId") long userId, @Param("welfareYn") String welfareYn);

    int updateRevenue(@Param("userId") long userId, @Param("revenueYn") String revenueYn);

    int updateDept(@Param("userId") long userId, @Param("deptName") String deptName);

    int insertDeptHistory(@Param("userId") long userId, @Param("fromDept") String fromDept,
                          @Param("toDept") String toDept, @Param("movedAt") LocalDateTime movedAt,
                          @Param("movedBy") String movedBy);

    List<egovframework.haengjeongnuli.user.service.DeptMoveVO> selectRecentDeptMoves(@Param("limit") int limit);
}
