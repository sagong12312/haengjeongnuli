package egovframework.haengjeongnuli.cmm.secapp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SecurityAppMapper {

    String COLS = "A.APP_ID, A.NAME, A.PURPOSE, A.GUIDE, A.URL, A.REQUIRED_YN, A.VERSION, A.SORT_NO, A.USE_YN, A.UPDATED_BY, A.UPDATED_AT ";
    /** 사용 중인 직원이 지금 판을 확인했는지 */
    String LIVE_ACK = "FROM SECURITY_APP_ACK K JOIN USER_ACCOUNT U ON U.LOGIN_ID = K.LOGIN_ID AND U.ENABLED_YN = 'Y' "
            + "WHERE K.APP_ID = A.APP_ID AND K.VERSION = A.VERSION";

    @Select("SELECT SEQ_SECURITY_APP.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO SECURITY_APP (APP_ID, NAME, PURPOSE, GUIDE, URL, REQUIRED_YN, VERSION, SORT_NO, USE_YN, UPDATED_BY, UPDATED_AT) "
            + "VALUES (#{appId}, #{name}, #{purpose}, #{guide}, #{url}, #{requiredYn}, 1, #{sortNo}, 'Y', #{updatedBy}, #{updatedAt})")
    void insert(SecurityAppVO a);

    @Update("UPDATE SECURITY_APP SET NAME = #{name}, PURPOSE = #{purpose}, GUIDE = #{guide}, URL = #{url}, REQUIRED_YN = #{requiredYn}, "
            + "SORT_NO = #{sortNo}, UPDATED_BY = #{updatedBy}, UPDATED_AT = #{updatedAt} WHERE APP_ID = #{appId}")
    int update(SecurityAppVO a);

    @Update("UPDATE SECURITY_APP SET VERSION = VERSION + 1, UPDATED_BY = #{by}, UPDATED_AT = #{at} WHERE APP_ID = #{id}")
    int bump(@Param("id") long id, @Param("by") String by, @Param("at") LocalDateTime at);

    @Update("UPDATE SECURITY_APP SET USE_YN = #{useYn}, UPDATED_BY = #{by}, UPDATED_AT = #{at} WHERE APP_ID = #{id}")
    int setUse(@Param("id") long id, @Param("useYn") String useYn, @Param("by") String by, @Param("at") LocalDateTime at);

    @Select("SELECT " + COLS + "FROM SECURITY_APP A WHERE A.APP_ID = #{id}")
    SecurityAppVO selectById(@Param("id") long id);

    /** 직원 화면: 사용 중인 항목과 내가 지금 판을 확인한 때 */
    @Select("SELECT " + COLS + ", K.ACKED_AT FROM SECURITY_APP A LEFT JOIN SECURITY_APP_ACK K ON K.APP_ID = A.APP_ID "
            + "AND K.LOGIN_ID = #{loginId} AND K.VERSION = A.VERSION WHERE A.USE_YN = 'Y' ORDER BY A.SORT_NO, A.APP_ID")
    List<SecurityAppVO> selectForUser(@Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM SECURITY_APP A WHERE A.USE_YN = 'Y' AND A.REQUIRED_YN = 'Y' AND NOT EXISTS ("
            + "SELECT 1 FROM SECURITY_APP_ACK K WHERE K.APP_ID = A.APP_ID AND K.LOGIN_ID = #{loginId} AND K.VERSION = A.VERSION)")
    int countPending(@Param("loginId") String loginId);

    /** 관리자 화면: 모든 항목과 확인한 직원 수 */
    @Select("SELECT " + COLS + ", (SELECT COUNT(*) " + LIVE_ACK + ") AS ACK_COUNT FROM SECURITY_APP A ORDER BY A.USE_YN DESC, A.SORT_NO, A.APP_ID")
    List<SecurityAppVO> selectAll();

    @Select("SELECT COUNT(*) FROM USER_ACCOUNT WHERE ENABLED_YN = 'Y'")
    int countActiveUsers();

    /** 지금 판을 확인하지 않은 사용 중 직원 */
    @Select("SELECT U.LOGIN_ID AS \"loginId\", U.USER_NAME AS \"userName\", U.DEPT_NAME AS \"deptName\" FROM USER_ACCOUNT U "
            + "WHERE U.ENABLED_YN = 'Y' AND NOT EXISTS (SELECT 1 FROM SECURITY_APP_ACK K JOIN SECURITY_APP A ON A.APP_ID = K.APP_ID "
            + "WHERE K.APP_ID = #{id} AND K.LOGIN_ID = U.LOGIN_ID AND K.VERSION = A.VERSION) ORDER BY U.DEPT_NAME, U.USER_NAME")
    List<Map<String, Object>> selectNotAcked(@Param("id") long id);

    @Delete("DELETE FROM SECURITY_APP_ACK WHERE APP_ID = #{id} AND LOGIN_ID = #{loginId}")
    int deleteAck(@Param("id") long id, @Param("loginId") String loginId);

    @Insert("INSERT INTO SECURITY_APP_ACK (APP_ID, LOGIN_ID, VERSION, ACKED_AT) VALUES (#{id}, #{loginId}, #{version}, #{at})")
    void insertAck(@Param("id") long id, @Param("loginId") String loginId, @Param("version") int version, @Param("at") LocalDateTime at);
}
