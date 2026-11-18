package egovframework.haengjeongnuli.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 2단계 인증, 비밀번호 이력, 로그인 기록 조회 */
@Mapper
public interface SecurityMapper {

    @Select("SELECT LOGIN_ID AS \"loginId\", SECRET AS \"secret\", ENABLED_YN AS \"enabled\", LAST_STEP AS \"lastStep\" "
            + "FROM USER_OTP WHERE LOGIN_ID = #{loginId}")
    Map<String, Object> otp(@Param("loginId") String loginId);

    @Delete("DELETE FROM USER_OTP WHERE LOGIN_ID = #{loginId}")
    int deleteOtp(@Param("loginId") String loginId);

    @Insert("INSERT INTO USER_OTP (LOGIN_ID, SECRET, ENABLED_YN, LAST_STEP, CREATED_AT) VALUES (#{loginId}, #{secret}, 'N', 0, #{at})")
    int insertOtp(@Param("loginId") String loginId, @Param("secret") String secret, @Param("at") LocalDateTime at);

    @Update("UPDATE USER_OTP SET ENABLED_YN = 'Y', LAST_STEP = #{step} WHERE LOGIN_ID = #{loginId}")
    int enableOtp(@Param("loginId") String loginId, @Param("step") long step);

    /** 쓴 시간 칸을 적는다. 더 앞선 칸으로만 바뀌어 같은 번호를 두 번 쓰지 못한다 */
    @Update("UPDATE USER_OTP SET LAST_STEP = #{step} WHERE LOGIN_ID = #{loginId} AND LAST_STEP < #{step}")
    int useStep(@Param("loginId") String loginId, @Param("step") long step);

    @Insert("INSERT INTO PW_HISTORY (LOGIN_ID, PW_HASH, CHANGED_AT) VALUES (#{loginId}, #{hash}, #{at})")
    int insertPw(@Param("loginId") String loginId, @Param("hash") String hash, @Param("at") LocalDateTime at);

    @Select("SELECT * FROM (SELECT PW_HASH FROM PW_HISTORY WHERE LOGIN_ID = #{loginId} ORDER BY CHANGED_AT DESC) WHERE ROWNUM <= #{n}")
    List<String> recentPw(@Param("loginId") String loginId, @Param("n") int n);

    @Select("SELECT * FROM (SELECT CHANGED_AT FROM PW_HISTORY WHERE LOGIN_ID = #{loginId} ORDER BY CHANGED_AT DESC) WHERE ROWNUM <= #{n}")
    List<LocalDateTime> recentPwTimes(@Param("loginId") String loginId, @Param("n") int n);

    @Delete("DELETE FROM PW_HISTORY WHERE LOGIN_ID = #{loginId} AND CHANGED_AT < #{before}")
    int prunePw(@Param("loginId") String loginId, @Param("before") LocalDateTime before);

    /** 내 로그인 기록 (성공·실패·로그아웃·세션 끊김) */
    @Select("SELECT * FROM (SELECT OCCURRED_AT AS \"at\", ACTION AS \"action\", CLIENT_IP AS \"ip\", DETAIL AS \"detail\" FROM AUDIT_LOG "
            + "WHERE ACTOR_ID = #{loginId} AND ACTION IN ('LOGIN_SUCCESS', 'LOGIN_FAIL', 'LOGOUT', 'SESSION_REVOKED', 'OTP_FAIL', 'OTP_SUCCESS') "
            + "ORDER BY OCCURRED_AT DESC, LOG_ID DESC) WHERE ROWNUM <= #{n}")
    List<Map<String, Object>> loginHistory(@Param("loginId") String loginId, @Param("n") int n);

    /** 그 주소에서 전에 로그인한 적이 있는지 (지금 로그인 기록은 빼고) */
    @Select("SELECT COUNT(*) FROM AUDIT_LOG WHERE ACTOR_ID = #{loginId} AND ACTION = 'LOGIN_SUCCESS' AND CLIENT_IP = #{ip} "
            + "AND OCCURRED_AT >= #{since} AND OCCURRED_AT < #{before}")
    int seenIp(@Param("loginId") String loginId, @Param("ip") String ip, @Param("since") LocalDateTime since,
               @Param("before") LocalDateTime before);

    @Select("SELECT COUNT(*) FROM AUDIT_LOG WHERE ACTOR_ID = #{loginId} AND ACTION = 'LOGIN_SUCCESS' AND OCCURRED_AT < #{before}")
    int pastLogins(@Param("loginId") String loginId, @Param("before") LocalDateTime before);
}
