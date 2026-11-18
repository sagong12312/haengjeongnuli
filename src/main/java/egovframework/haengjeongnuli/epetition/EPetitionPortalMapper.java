package egovframework.haengjeongnuli.epetition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 전자민원 포털: 공지사항, 자주 묻는 질문, 공개 상담 조회수·공감, 처리 통계, 취하·만족도, 회원 */
@Mapper
public interface EPetitionPortalMapper {

    /* ------------------------------------------------------------ 공지사항 */

    String NOTICE_WHERE = "<where><if test='q != null'>(TITLE LIKE #{q} ESCAPE '\\' OR CONTENT LIKE #{q} ESCAPE '\\')</if></where>";

    @Select("<script>SELECT COUNT(*) FROM EP_NOTICE " + NOTICE_WHERE + "</script>")
    int countNotices(@Param("q") String like);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT NOTICE_ID AS \"id\", TITLE AS \"title\", "
            + "PINNED_YN AS \"pinned\", VIEW_COUNT AS \"views\", CREATED_AT AS \"createdAt\" FROM EP_NOTICE " + NOTICE_WHERE
            + " ORDER BY PINNED_YN DESC, NOTICE_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<Map<String, Object>> selectNotices(@Param("q") String like, @Param("startRow") int startRow, @Param("endRow") int endRow);

    @Select("SELECT NOTICE_ID AS \"id\", TITLE AS \"title\", CONTENT AS \"content\", PINNED_YN AS \"pinned\", VIEW_COUNT AS \"views\", "
            + "CREATED_BY AS \"createdBy\", CREATED_AT AS \"createdAt\", UPDATED_AT AS \"updatedAt\" FROM EP_NOTICE WHERE NOTICE_ID = #{id}")
    Map<String, Object> notice(@Param("id") long id);

    @Select("SELECT SEQ_EP_NOTICE.NEXTVAL FROM DUAL")
    long nextNoticeId();

    @Insert("INSERT INTO EP_NOTICE (NOTICE_ID, TITLE, CONTENT, PINNED_YN, VIEW_COUNT, CREATED_BY, CREATED_AT) "
            + "VALUES (#{id}, #{title}, #{content, jdbcType=CLOB}, #{pinned}, 0, #{by}, #{at})")
    int insertNotice(@Param("id") long id, @Param("title") String title, @Param("content") String content,
                     @Param("pinned") String pinned, @Param("by") String by, @Param("at") LocalDateTime at);

    @Update("UPDATE EP_NOTICE SET TITLE = #{title}, CONTENT = #{content, jdbcType=CLOB}, PINNED_YN = #{pinned}, UPDATED_AT = #{at} "
            + "WHERE NOTICE_ID = #{id}")
    int updateNotice(@Param("id") long id, @Param("title") String title, @Param("content") String content,
                     @Param("pinned") String pinned, @Param("at") LocalDateTime at);

    @Delete("DELETE FROM EP_NOTICE WHERE NOTICE_ID = #{id}")
    int deleteNotice(@Param("id") long id);

    @Update("UPDATE EP_NOTICE SET VIEW_COUNT = VIEW_COUNT + 1 WHERE NOTICE_ID = #{id}")
    int noticeViewed(@Param("id") long id);

    /* ------------------------------------------------------------ 자주 묻는 질문 */

    @Select("<script>SELECT FAQ_ID AS \"id\", CATEGORY AS \"category\", QUESTION AS \"question\", ANSWER AS \"answer\", "
            + "SORT_ORDER AS \"sortOrder\", USE_YN AS \"useYn\" FROM EP_FAQ "
            + "<if test='onlyUsed'>WHERE USE_YN = 'Y' </if>ORDER BY CATEGORY, SORT_ORDER, FAQ_ID</script>")
    List<Map<String, Object>> faqs(@Param("onlyUsed") boolean onlyUsed);

    @Select("SELECT FAQ_ID AS \"id\", CATEGORY AS \"category\", QUESTION AS \"question\", ANSWER AS \"answer\", "
            + "SORT_ORDER AS \"sortOrder\", USE_YN AS \"useYn\" FROM EP_FAQ WHERE FAQ_ID = #{id}")
    Map<String, Object> faq(@Param("id") long id);

    @Select("SELECT SEQ_EP_FAQ.NEXTVAL FROM DUAL")
    long nextFaqId();

    @Insert("INSERT INTO EP_FAQ (FAQ_ID, CATEGORY, QUESTION, ANSWER, SORT_ORDER, USE_YN, CREATED_BY, CREATED_AT) "
            + "VALUES (#{id}, #{category}, #{question}, #{answer}, #{sort}, #{useYn}, #{by}, #{at})")
    int insertFaq(@Param("id") long id, @Param("category") String category, @Param("question") String question,
                  @Param("answer") String answer, @Param("sort") int sort, @Param("useYn") String useYn,
                  @Param("by") String by, @Param("at") LocalDateTime at);

    @Update("UPDATE EP_FAQ SET CATEGORY = #{category}, QUESTION = #{question}, ANSWER = #{answer}, SORT_ORDER = #{sort}, "
            + "USE_YN = #{useYn} WHERE FAQ_ID = #{id}")
    int updateFaq(@Param("id") long id, @Param("category") String category, @Param("question") String question,
                  @Param("answer") String answer, @Param("sort") int sort, @Param("useYn") String useYn);

    @Delete("DELETE FROM EP_FAQ WHERE FAQ_ID = #{id}")
    int deleteFaq(@Param("id") long id);

    /* ------------------------------------------------------------ 공개 상담: 목록(종류·정렬), 조회수, 공감, 비슷한 상담 */

    String PUBLIC = "STATUS = 'DONE' AND PUBLIC_YN = 'Y' AND PUBLISHED_YN = 'Y' AND NVL(RESULT_TYPE, '-') &lt;&gt; 'WITHDRAWN' ";
    String CONSULT_WHERE = "WHERE " + PUBLIC
            + "<if test='q != null'>AND (TITLE LIKE #{q} ESCAPE '\\' OR CONTENT LIKE #{q} ESCAPE '\\') </if>"
            + "<if test='kind != null'>AND KIND = #{kind} </if>"
            + "<if test='category != null'>AND NVL(CATEGORY, 'ETC') = #{category} </if>";

    @Select("<script>SELECT COUNT(*) FROM CIVIL_COMPLAINT " + CONSULT_WHERE + "</script>")
    int countConsultations(@Param("q") String like, @Param("kind") String kind, @Param("category") String category);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", "
            + "KIND AS \"kind\", NVL(CATEGORY, 'ETC') AS \"category\", TITLE AS \"title\", APPLICANT_NAME AS \"name\", RECEIVED_AT AS \"receivedAt\", "
            + "COMPLETED_AT AS \"completedAt\", DEPT AS \"dept\", NVL(VIEW_COUNT, 0) AS \"views\", NVL(LIKE_COUNT, 0) AS \"likes\" "
            + "FROM CIVIL_COMPLAINT " + CONSULT_WHERE
            + "ORDER BY <choose><when test=\"sort == 'views'\">NVL(VIEW_COUNT, 0) DESC, </when>"
            + "<when test=\"sort == 'likes'\">NVL(LIKE_COUNT, 0) DESC, </when></choose>COMPLETED_AT DESC, COMPLAINT_ID DESC"
            + ") X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<Map<String, Object>> selectConsultations(@Param("q") String like, @Param("kind") String kind,
                                                  @Param("category") String category, @Param("sort") String sort,
                                                  @Param("startRow") int startRow, @Param("endRow") int endRow);

    @Select("<script>SELECT NVL(VIEW_COUNT, 0) AS \"views\", NVL(LIKE_COUNT, 0) AS \"likes\", KIND AS \"kind\" "
            + "FROM CIVIL_COMPLAINT WHERE COMPLAINT_ID = #{id} AND " + PUBLIC + "</script>")
    Map<String, Object> consultationCounts(@Param("id") long id);

    @Update("<script>UPDATE CIVIL_COMPLAINT SET VIEW_COUNT = NVL(VIEW_COUNT, 0) + 1 WHERE COMPLAINT_ID = #{id} AND " + PUBLIC + "</script>")
    int consultationViewed(@Param("id") long id);

    @Insert("INSERT INTO EP_LIKE (COMPLAINT_ID, VOTER, CREATED_AT) VALUES (#{id}, #{voter}, #{at})")
    int insertLike(@Param("id") long id, @Param("voter") String voter, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM EP_LIKE WHERE COMPLAINT_ID = #{id} AND VOTER = #{voter}")
    int liked(@Param("id") long id, @Param("voter") String voter);

    @Update("UPDATE CIVIL_COMPLAINT SET LIKE_COUNT = NVL(LIKE_COUNT, 0) + 1 WHERE COMPLAINT_ID = #{id}")
    int addLike(@Param("id") long id);

    @Select("<script>SELECT * FROM (SELECT COMPLAINT_ID AS \"id\", TITLE AS \"title\", COMPLETED_AT AS \"completedAt\" "
            + "FROM CIVIL_COMPLAINT WHERE " + PUBLIC + "AND KIND = #{kind} AND COMPLAINT_ID &lt;&gt; #{id} "
            + "ORDER BY COMPLETED_AT DESC, COMPLAINT_ID DESC) WHERE ROWNUM &lt;= 5</script>")
    List<Map<String, Object>> related(@Param("id") long id, @Param("kind") String kind);

    /* ------------------------------------------------------------ 처리 통계 (최근 12개월) */

    @Select("SELECT KIND AS \"kind\", NVL(CATEGORY, 'ETC') AS \"category\", ROUTE AS \"route\", STATUS AS \"status\", RECEIVED_AT AS \"receivedAt\", "
            + "COMPLETED_AT AS \"completedAt\", DEADLINE AS \"deadline\", DEADLINE_AT AS \"deadlineAt\", RESULT_TYPE AS \"resultType\", "
            + "SATIS_SCORE AS \"satis\", CASE WHEN LOOKUP_PW_HASH IS NULL THEN 'N' ELSE 'Y' END AS \"online\" "
            + "FROM CIVIL_COMPLAINT WHERE RECEIVED_AT >= #{from} OR COMPLETED_AT >= #{from}")
    List<Map<String, Object>> statRows(@Param("from") LocalDateTime from);

    /* ------------------------------------------------------------ 민원인이 하는 일: 취하, 추가 의견, 만족도 */

    @Update("UPDATE CIVIL_COMPLAINT SET STATUS = 'DONE', COMPLETED_AT = #{at}, COMPLETED_BY = #{by}, RESULT_TYPE = 'WITHDRAWN', "
            + "RESULT_TEXT = #{text, jdbcType=CLOB}, RESULT_NOTIFY = '전자민원', PUBLISHED_YN = 'N' "
            + "WHERE COMPLAINT_ID = #{id} AND STATUS <> 'DONE'")
    int withdraw(@Param("id") long id, @Param("text") String text, @Param("by") String by, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM CIVIL_HISTORY WHERE COMPLAINT_ID = #{id} AND ACTION = 'ADDENDUM'")
    int addendumCount(@Param("id") long id);

    @Update("UPDATE CIVIL_COMPLAINT SET SATIS_SCORE = #{score}, SATIS_COMMENT = #{comment}, SATIS_AT = #{at} "
            + "WHERE COMPLAINT_ID = #{id} AND STATUS = 'DONE' AND SATIS_SCORE IS NULL AND NVL(RESULT_TYPE, '-') <> 'WITHDRAWN'")
    int rate(@Param("id") long id, @Param("score") int score, @Param("comment") String comment, @Param("at") LocalDateTime at);

    @Select("SELECT SATIS_SCORE AS \"score\", SATIS_COMMENT AS \"comment\", SATIS_AT AS \"at\", MEMBER_ID AS \"memberId\" "
            + "FROM CIVIL_COMPLAINT WHERE COMPLAINT_ID = #{id}")
    Map<String, Object> satisfaction(@Param("id") long id);

    /** 직원 화면: 최근 만족도 응답 */
    @Select("SELECT * FROM (SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", TITLE AS \"title\", DEPT AS \"dept\", "
            + "SATIS_SCORE AS \"score\", SATIS_COMMENT AS \"comment\", SATIS_AT AS \"at\" FROM CIVIL_COMPLAINT "
            + "WHERE SATIS_SCORE IS NOT NULL ORDER BY SATIS_AT DESC) WHERE ROWNUM <= #{limit}")
    List<Map<String, Object>> recentSatisfaction(@Param("limit") int limit);

    /* ------------------------------------------------------------ 회원 */

    @Select("SELECT SEQ_EP_MEMBER.NEXTVAL FROM DUAL")
    long nextMemberId();

    @Select("SELECT COUNT(*) FROM EP_MEMBER WHERE LOGIN_ID = #{loginId}")
    int loginIdUsed(@Param("loginId") String loginId);

    @Insert("INSERT INTO EP_MEMBER (MEMBER_ID, LOGIN_ID, PW_HASH, NAME, MOBILE, EMAIL, ADDRESS, FAILS, CREATED_AT, PW_CHANGED_AT) "
            + "VALUES (#{id}, #{loginId}, #{hash}, #{name}, #{mobile}, #{email}, #{address}, 0, #{at}, #{at})")
    int insertMember(@Param("id") long id, @Param("loginId") String loginId, @Param("hash") String hash, @Param("name") String name,
                     @Param("mobile") String mobile, @Param("email") String email, @Param("address") String address,
                     @Param("at") LocalDateTime at);

    String MEMBER_COLS = "MEMBER_ID AS \"id\", LOGIN_ID AS \"loginId\", PW_HASH AS \"hash\", NAME AS \"name\", MOBILE AS \"mobile\", "
            + "EMAIL AS \"email\", ADDRESS AS \"address\", FAILS AS \"fails\", LOCKED_UNTIL AS \"lockedUntil\", "
            + "CREATED_AT AS \"createdAt\", LAST_LOGIN_AT AS \"lastLoginAt\", PW_CHANGED_AT AS \"pwChangedAt\" ";

    @Select("SELECT " + MEMBER_COLS + "FROM EP_MEMBER WHERE LOGIN_ID = #{loginId} FOR UPDATE")
    Map<String, Object> memberForLogin(@Param("loginId") String loginId);

    @Select("SELECT " + MEMBER_COLS + "FROM EP_MEMBER WHERE MEMBER_ID = #{id}")
    Map<String, Object> member(@Param("id") long id);

    @Update("UPDATE EP_MEMBER SET FAILS = #{fails}, LOCKED_UNTIL = #{lockedUntil, jdbcType=TIMESTAMP} WHERE MEMBER_ID = #{id}")
    int setMemberFails(@Param("id") long id, @Param("fails") int fails, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Update("UPDATE EP_MEMBER SET FAILS = 0, LOCKED_UNTIL = NULL, LAST_LOGIN_AT = #{at} WHERE MEMBER_ID = #{id}")
    int memberLoggedIn(@Param("id") long id, @Param("at") LocalDateTime at);

    @Update("UPDATE EP_MEMBER SET NAME = #{name}, MOBILE = #{mobile}, EMAIL = #{email}, ADDRESS = #{address} WHERE MEMBER_ID = #{id}")
    int updateMember(@Param("id") long id, @Param("name") String name, @Param("mobile") String mobile,
                     @Param("email") String email, @Param("address") String address);

    @Update("UPDATE EP_MEMBER SET PW_HASH = #{hash}, PW_CHANGED_AT = #{at} WHERE MEMBER_ID = #{id}")
    int setMemberPassword(@Param("id") long id, @Param("hash") String hash, @Param("at") LocalDateTime at);

    @Update("UPDATE CIVIL_COMPLAINT SET MEMBER_ID = NULL WHERE MEMBER_ID = #{id}")
    int detachMember(@Param("id") long id);

    @Delete("DELETE FROM EP_MEMBER WHERE MEMBER_ID = #{id}")
    int deleteMember(@Param("id") long id);

    @Update("UPDATE CIVIL_COMPLAINT SET MEMBER_ID = #{memberId} WHERE COMPLAINT_ID = #{id}")
    int linkMember(@Param("id") long id, @Param("memberId") long memberId);

    /** st: null 이면 모두, OPEN 이면 처리 중(접수 대기 포함), DONE 이면 처리 완료(취하 포함) */
    @Select("SELECT COUNT(*) FROM CIVIL_COMPLAINT WHERE MEMBER_ID = #{memberId} "
            + "AND (#{st, jdbcType=VARCHAR} IS NULL OR (#{st, jdbcType=VARCHAR} = 'DONE' AND STATUS = 'DONE') OR (#{st, jdbcType=VARCHAR} = 'OPEN' AND STATUS <> 'DONE'))")
    int countMemberComplaints(@Param("memberId") long memberId, @Param("st") String st);

    @Select("SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", KIND AS \"kind\", "
            + "TITLE AS \"title\", STATUS AS \"status\", RECEIVED_AT AS \"receivedAt\", DEADLINE_AT AS \"deadlineAt\", "
            + "COMPLETED_AT AS \"completedAt\", RESULT_TYPE AS \"resultType\" FROM CIVIL_COMPLAINT WHERE MEMBER_ID = #{memberId} "
            + "AND (#{st, jdbcType=VARCHAR} IS NULL OR (#{st, jdbcType=VARCHAR} = 'DONE' AND STATUS = 'DONE') OR (#{st, jdbcType=VARCHAR} = 'OPEN' AND STATUS <> 'DONE')) "
            + "ORDER BY COMPLAINT_ID DESC) X WHERE ROWNUM <= #{endRow}) WHERE RN > #{startRow}")
    List<Map<String, Object>> memberComplaints(@Param("memberId") long memberId, @Param("startRow") int startRow, @Param("endRow") int endRow,
                                               @Param("st") String st);

    @Select("SELECT COMPLAINT_ID FROM CIVIL_COMPLAINT WHERE MEMBER_ID = #{memberId} AND RECEIPT_NO = #{receiptNo}")
    Long memberComplaintId(@Param("memberId") long memberId, @Param("receiptNo") String receiptNo);
}
