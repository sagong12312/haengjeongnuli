package egovframework.haengjeongnuli.cmm.siteblock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 유해사이트 차단 목록과 설정 */
@Mapper
public interface SiteBlockMapper {

    @Select("SELECT DOMAIN AS \"domain\", CATEGORY AS \"category\", MEMO AS \"memo\", ADDED_BY AS \"addedBy\", ADDED_AT AS \"addedAt\" "
            + "FROM SITE_BLOCK_RULE ORDER BY CATEGORY, DOMAIN")
    List<Map<String, Object>> rules();

    @Select("SELECT COUNT(*) FROM SITE_BLOCK_RULE")
    int countRules();

    @Select("SELECT COUNT(*) FROM SITE_BLOCK_RULE WHERE DOMAIN = #{domain}")
    int exists(@Param("domain") String domain);

    @Insert("INSERT INTO SITE_BLOCK_RULE (DOMAIN, CATEGORY, MEMO, ADDED_BY, ADDED_AT) VALUES (#{domain}, #{category}, #{memo}, #{by}, #{at})")
    int insertRule(@Param("domain") String domain, @Param("category") String category, @Param("memo") String memo,
                   @Param("by") String by, @Param("at") LocalDateTime at);

    @Delete("DELETE FROM SITE_BLOCK_RULE WHERE DOMAIN = #{domain}")
    int deleteRule(@Param("domain") String domain);

    @Select("SELECT DNS_MODE AS \"dnsMode\", DNS1 AS \"dns1\", DNS2 AS \"dns2\", APP_ID AS \"appId\", VERSION AS \"version\", "
            + "UPDATED_BY AS \"updatedBy\", UPDATED_AT AS \"updatedAt\" FROM SITE_BLOCK_CONF WHERE CONF_ID = 1")
    Map<String, Object> conf();

    @Insert("INSERT INTO SITE_BLOCK_CONF (CONF_ID, DNS_MODE, VERSION, UPDATED_BY, UPDATED_AT) VALUES (1, 'ADULT', 1, 'system', #{at})")
    int insertConf(@Param("at") LocalDateTime at);

    @Update("UPDATE SITE_BLOCK_CONF SET DNS_MODE = #{mode}, DNS1 = #{dns1, jdbcType=VARCHAR}, DNS2 = #{dns2, jdbcType=VARCHAR}, "
            + "VERSION = VERSION + 1, UPDATED_BY = #{by}, UPDATED_AT = #{at} WHERE CONF_ID = 1")
    int updateConf(@Param("mode") String mode, @Param("dns1") String dns1, @Param("dns2") String dns2, @Param("by") String by,
                   @Param("at") LocalDateTime at);

    @Update("UPDATE SITE_BLOCK_CONF SET VERSION = VERSION + 1, UPDATED_BY = #{by}, UPDATED_AT = #{at} WHERE CONF_ID = 1")
    int bump(@Param("by") String by, @Param("at") LocalDateTime at);

    @Update("UPDATE SITE_BLOCK_CONF SET APP_ID = #{appId} WHERE CONF_ID = 1")
    int setApp(@Param("appId") long appId);

    /* ---------------------------------------------------------------- 차단 항의(이의) 신청 */

    @Select("SELECT CATEGORY FROM SITE_BLOCK_RULE WHERE DOMAIN = #{domain}")
    String category(@Param("domain") String domain);

    @Select("SELECT SEQ_SITE_BLOCK_APPEAL.NEXTVAL FROM DUAL")
    long nextAppealId();

    @Insert("INSERT INTO SITE_BLOCK_APPEAL (APPEAL_ID, DOMAIN, LOGIN_ID, USER_NAME, DEPT_NAME, REASON, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{domain}, #{loginId}, #{userName}, #{dept, jdbcType=VARCHAR}, #{reason}, 'WAIT', #{at})")
    int insertAppeal(@Param("id") long id, @Param("domain") String domain, @Param("loginId") String loginId,
                     @Param("userName") String userName, @Param("dept") String dept, @Param("reason") String reason,
                     @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM SITE_BLOCK_APPEAL WHERE DOMAIN = #{domain} AND LOGIN_ID = #{loginId} AND STATUS = 'WAIT'")
    int waiting(@Param("domain") String domain, @Param("loginId") String loginId);

    String APPEAL_COLS = "APPEAL_ID AS \"id\", DOMAIN AS \"domain\", LOGIN_ID AS \"loginId\", USER_NAME AS \"userName\", "
            + "DEPT_NAME AS \"dept\", REASON AS \"reason\", STATUS AS \"status\", ANSWER AS \"answer\", CREATED_AT AS \"createdAt\", "
            + "DECIDED_BY AS \"decidedBy\", DECIDED_AT AS \"decidedAt\" ";

    @Select("SELECT * FROM (SELECT " + APPEAL_COLS + "FROM SITE_BLOCK_APPEAL ORDER BY CASE WHEN STATUS = 'WAIT' THEN 0 ELSE 1 END, APPEAL_ID DESC) "
            + "WHERE ROWNUM <= 100")
    List<Map<String, Object>> appeals();

    @Select("SELECT * FROM (SELECT " + APPEAL_COLS + "FROM SITE_BLOCK_APPEAL WHERE LOGIN_ID = #{loginId} ORDER BY APPEAL_ID DESC) WHERE ROWNUM <= 20")
    List<Map<String, Object>> myAppeals(@Param("loginId") String loginId);

    @Select("SELECT " + APPEAL_COLS + "FROM SITE_BLOCK_APPEAL WHERE APPEAL_ID = #{id}")
    Map<String, Object> appeal(@Param("id") long id);

    @Update("UPDATE SITE_BLOCK_APPEAL SET STATUS = #{status}, ANSWER = #{answer, jdbcType=VARCHAR}, DECIDED_BY = #{by}, DECIDED_AT = #{at} "
            + "WHERE APPEAL_ID = #{id} AND STATUS = 'WAIT'")
    int decide(@Param("id") long id, @Param("status") String status, @Param("answer") String answer, @Param("by") String by,
               @Param("at") LocalDateTime at);
}
