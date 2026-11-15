package egovframework.haengjeongnuli.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.user.service.PasswordResetVO;

@Mapper
public interface PasswordResetMapper {

    @Select("SELECT SEQ_PASSWORD_RESET.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO PASSWORD_RESET_REQUEST (REQ_ID, LOGIN_ID, USER_NAME, CONTACT, REQUEST_IP, REQUESTED_AT, STATUS) "
            + "VALUES (#{reqId}, #{loginId}, #{userName}, #{contact}, #{requestIp}, #{requestedAt}, 'OPEN')")
    void insert(PasswordResetVO v);

    @Select("SELECT COUNT(*) FROM PASSWORD_RESET_REQUEST WHERE LOGIN_ID = #{loginId} AND STATUS = 'OPEN'")
    int countOpen(@Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM PASSWORD_RESET_REQUEST WHERE REQUEST_IP = #{ip} AND REQUESTED_AT >= #{since}")
    int countFromIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM (SELECT REQ_ID, LOGIN_ID, USER_NAME, CONTACT, REQUEST_IP, REQUESTED_AT, STATUS, HANDLED_BY, HANDLED_AT "
            + "FROM PASSWORD_RESET_REQUEST ORDER BY CASE WHEN STATUS = 'OPEN' THEN 0 ELSE 1 END, REQUESTED_AT DESC) "
            + "WHERE ROWNUM <= #{limit}")
    List<PasswordResetVO> selectRecent(@Param("limit") int limit);

    @Select("SELECT REQ_ID, LOGIN_ID, USER_NAME, CONTACT, REQUEST_IP, REQUESTED_AT, STATUS, HANDLED_BY, HANDLED_AT "
            + "FROM PASSWORD_RESET_REQUEST WHERE REQ_ID = #{reqId}")
    PasswordResetVO selectById(@Param("reqId") long reqId);

    @Update("UPDATE PASSWORD_RESET_REQUEST SET STATUS = #{status}, HANDLED_BY = #{by}, HANDLED_AT = #{at} "
            + "WHERE REQ_ID = #{reqId} AND STATUS = 'OPEN'")
    int close(@Param("reqId") long reqId, @Param("status") String status, @Param("by") String by,
              @Param("at") LocalDateTime at);

    @Update("UPDATE PASSWORD_RESET_REQUEST SET STATUS = 'DONE', HANDLED_BY = #{by}, HANDLED_AT = #{at} "
            + "WHERE LOGIN_ID = #{loginId} AND STATUS = 'OPEN'")
    int closeAllFor(@Param("loginId") String loginId, @Param("by") String by, @Param("at") LocalDateTime at);
}
