package egovframework.haengjeongnuli.epetition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EPetitionMapper {

    @Update("UPDATE CIVIL_COMPLAINT SET LOOKUP_PW_HASH = #{hash}, PUBLIC_YN = #{publicYn}, LOOKUP_FAILS = 0 WHERE COMPLAINT_ID = #{id}")
    int setLookup(@Param("id") long id, @Param("hash") String hash, @Param("publicYn") String publicYn);

    @Select("SELECT COMPLAINT_ID AS \"id\", LOOKUP_PW_HASH AS \"hash\", NVL(LOOKUP_FAILS, 0) AS \"fails\", "
            + "LOOKUP_LOCKED_UNTIL AS \"lockedUntil\" FROM CIVIL_COMPLAINT WHERE RECEIPT_NO = #{no} FOR UPDATE")
    Map<String, Object> lockForLookup(@Param("no") String receiptNo);

    @Update("UPDATE CIVIL_COMPLAINT SET LOOKUP_FAILS = #{fails}, LOOKUP_LOCKED_UNTIL = #{lockedUntil, jdbcType=TIMESTAMP} WHERE COMPLAINT_ID = #{id}")
    int setFails(@Param("id") long id, @Param("fails") int fails, @Param("lockedUntil") LocalDateTime lockedUntil);

    String PUBLIC_WHERE = "WHERE STATUS = 'DONE' AND PUBLIC_YN = 'Y' AND PUBLISHED_YN = 'Y' "
            + "<if test='q != null'>AND (TITLE LIKE #{q} ESCAPE '\\' OR CONTENT LIKE #{q} ESCAPE '\\') </if>";

    @Select("<script>SELECT COUNT(*) FROM CIVIL_COMPLAINT " + PUBLIC_WHERE + "</script>")
    int countPublic(@Param("q") String likeQuery);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", "
            + "KIND AS \"kind\", TITLE AS \"title\", APPLICANT_NAME AS \"name\", RECEIVED_AT AS \"receivedAt\", "
            + "COMPLETED_AT AS \"completedAt\", DEPT AS \"dept\" FROM CIVIL_COMPLAINT " + PUBLIC_WHERE
            + "ORDER BY COMPLETED_AT DESC, COMPLAINT_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<Map<String, Object>> selectPublic(@Param("q") String likeQuery, @Param("startRow") int startRow, @Param("endRow") int endRow);

    @Select("SELECT COMPLAINT_ID FROM CIVIL_COMPLAINT WHERE COMPLAINT_ID = #{id} AND STATUS = 'DONE' AND PUBLIC_YN = 'Y' AND PUBLISHED_YN = 'Y'")
    Long publicId(@Param("id") long id);

    /** 직원이 조회 비밀번호를 새로 정한다 (전자민원으로 접수한 민원만), 잠금도 푼다 */
    @Update("UPDATE CIVIL_COMPLAINT SET LOOKUP_PW_HASH = #{hash}, LOOKUP_FAILS = 0, LOOKUP_LOCKED_UNTIL = NULL "
            + "WHERE COMPLAINT_ID = #{id} AND LOOKUP_PW_HASH IS NOT NULL")
    int resetLookup(@Param("id") long id, @Param("hash") String hash);

    @Update("UPDATE CIVIL_COMPLAINT SET PUBLISHED_YN = #{yn} WHERE COMPLAINT_ID = #{id} AND STATUS = 'DONE' AND PUBLIC_YN = 'Y'")
    int setPublished(@Param("id") long id, @Param("yn") String yn);

    @Select("SELECT PUBLIC_YN AS \"publicYn\", PUBLISHED_YN AS \"publishedYn\", "
            + "CASE WHEN LOOKUP_PW_HASH IS NULL THEN 'N' ELSE 'Y' END AS \"online\" FROM CIVIL_COMPLAINT WHERE COMPLAINT_ID = #{id}")
    Map<String, Object> flags(@Param("id") long id);
}
