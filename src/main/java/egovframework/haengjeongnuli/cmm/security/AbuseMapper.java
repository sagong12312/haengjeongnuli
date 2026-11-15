package egovframework.haengjeongnuli.cmm.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AbuseMapper {

    @Select("SELECT SEQ_ABUSE_BLOCK.NEXTVAL FROM DUAL")
    long nextBlockId();

    @Insert("INSERT INTO ABUSE_BLOCK (BLOCK_ID, IP, REASON, BLOCKED_AT, UNTIL_AT) VALUES (#{id}, #{ip}, #{reason}, #{at}, #{until})")
    void insertBlock(@Param("id") long id, @Param("ip") String ip, @Param("reason") String reason,
                     @Param("at") LocalDateTime at, @Param("until") LocalDateTime until);

    /** 지금 유효한 차단 (해제하지 않았고 기간 안) */
    @Select("SELECT BLOCK_ID AS \"blockId\", IP AS \"ip\", REASON AS \"reason\", BLOCKED_AT AS \"blockedAt\", UNTIL_AT AS \"untilAt\" "
            + "FROM ABUSE_BLOCK WHERE RELEASED_AT IS NULL AND UNTIL_AT > #{now} ORDER BY BLOCKED_AT DESC")
    List<Map<String, Object>> selectActive(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM (SELECT BLOCK_ID AS \"blockId\", IP AS \"ip\", REASON AS \"reason\", BLOCKED_AT AS \"blockedAt\", "
            + "UNTIL_AT AS \"untilAt\", RELEASED_BY AS \"releasedBy\", RELEASED_AT AS \"releasedAt\" FROM ABUSE_BLOCK "
            + "ORDER BY BLOCKED_AT DESC) WHERE ROWNUM <= 50")
    List<Map<String, Object>> selectRecent();

    @Update("UPDATE ABUSE_BLOCK SET RELEASED_BY = #{by}, RELEASED_AT = #{at} WHERE IP = #{ip} AND RELEASED_AT IS NULL AND UNTIL_AT > #{at}")
    int release(@Param("ip") String ip, @Param("by") String by, @Param("at") LocalDateTime at);

    @Select("SELECT SEQ_ABUSE_APPEAL.NEXTVAL FROM DUAL")
    long nextAppealId();

    @Insert("INSERT INTO ABUSE_APPEAL (APPEAL_ID, BLOCK_ID, IP, NAME, CONTACT, MESSAGE, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{blockId}, #{ip}, #{name}, #{contact}, #{message}, 'OPEN', #{at})")
    void insertAppeal(@Param("id") long id, @Param("blockId") long blockId, @Param("ip") String ip, @Param("name") String name,
                      @Param("contact") String contact, @Param("message") String message, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM ABUSE_APPEAL WHERE BLOCK_ID = #{blockId}")
    int countAppeals(@Param("blockId") long blockId);

    @Select("SELECT * FROM (SELECT APPEAL_ID AS \"appealId\", BLOCK_ID AS \"blockId\", IP AS \"ip\", NAME AS \"name\", CONTACT AS \"contact\", "
            + "MESSAGE AS \"message\", STATUS AS \"status\", CREATED_AT AS \"createdAt\", HANDLED_BY AS \"handledBy\", HANDLE_NOTE AS \"handleNote\" "
            + "FROM ABUSE_APPEAL ORDER BY CASE WHEN STATUS = 'OPEN' THEN 0 ELSE 1 END, CREATED_AT DESC) WHERE ROWNUM <= 50")
    List<Map<String, Object>> selectAppeals();

    @Select("SELECT IP FROM ABUSE_APPEAL WHERE APPEAL_ID = #{id} AND STATUS = 'OPEN'")
    String openAppealIp(@Param("id") long id);

    @Update("UPDATE ABUSE_APPEAL SET STATUS = #{status}, HANDLED_BY = #{by}, HANDLED_AT = #{at}, HANDLE_NOTE = #{note} "
            + "WHERE APPEAL_ID = #{id} AND STATUS = 'OPEN'")
    int closeAppeal(@Param("id") long id, @Param("status") String status, @Param("by") String by,
                    @Param("at") LocalDateTime at, @Param("note") String note);
}
