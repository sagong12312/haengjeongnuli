package egovframework.haengjeongnuli.desk;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 개인 업무 도구: 할 일, 즐겨찾기, 쪽지, 업무 분장, 상용 답변 문구, 물품 대여, 설문, 같은 민원인 */
@Mapper
public interface DeskMapper {

    /* ---------------------------------------------------------------- 할 일 */

    @Select("SELECT SEQ_TODO_ITEM.NEXTVAL FROM DUAL")
    long nextTodoId();

    @Insert("INSERT INTO TODO_ITEM (TODO_ID, LOGIN_ID, TITLE, DUE_ON, DONE_YN, CREATED_AT) VALUES (#{id}, #{loginId}, #{title}, "
            + "#{due, jdbcType=DATE}, 'N', #{at})")
    int insertTodo(@Param("id") long id, @Param("loginId") String loginId, @Param("title") String title, @Param("due") LocalDate due,
                   @Param("at") LocalDateTime at);

    @Select("SELECT TODO_ID AS \"id\", TITLE AS \"title\", DUE_ON AS \"due\", DONE_YN AS \"done\", DONE_AT AS \"doneAt\" FROM TODO_ITEM "
            + "WHERE LOGIN_ID = #{loginId} AND (DONE_YN = 'N' OR DONE_AT >= #{since}) ORDER BY DONE_YN, CASE WHEN DUE_ON IS NULL THEN 1 ELSE 0 END, DUE_ON, TODO_ID")
    List<Map<String, Object>> todos(@Param("loginId") String loginId, @Param("since") LocalDateTime since);

    @Update("UPDATE TODO_ITEM SET DONE_YN = #{yn}, DONE_AT = #{at, jdbcType=TIMESTAMP} WHERE TODO_ID = #{id} AND LOGIN_ID = #{loginId}")
    int setTodoDone(@Param("id") long id, @Param("loginId") String loginId, @Param("yn") String yn, @Param("at") LocalDateTime at);

    @Delete("DELETE FROM TODO_ITEM WHERE TODO_ID = #{id} AND LOGIN_ID = #{loginId}")
    int deleteTodo(@Param("id") long id, @Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM TODO_ITEM WHERE LOGIN_ID = #{loginId} AND DONE_YN = 'N'")
    int openTodos(@Param("loginId") String loginId);

    /* ---------------------------------------------------------------- 즐겨찾기 */

    @Select("SELECT PATH AS \"path\", LABEL AS \"label\" FROM FAVORITE_MENU WHERE LOGIN_ID = #{loginId} ORDER BY SORT_ORDER, LABEL")
    List<Map<String, Object>> favorites(@Param("loginId") String loginId);

    @Delete("DELETE FROM FAVORITE_MENU WHERE LOGIN_ID = #{loginId}")
    int clearFavorites(@Param("loginId") String loginId);

    @Insert("INSERT INTO FAVORITE_MENU (LOGIN_ID, PATH, LABEL, SORT_ORDER) VALUES (#{loginId}, #{path}, #{label}, #{sort})")
    int insertFavorite(@Param("loginId") String loginId, @Param("path") String path, @Param("label") String label, @Param("sort") int sort);

    /* ---------------------------------------------------------------- 쪽지 */

    @Select("SELECT SEQ_MESSAGE.NEXTVAL FROM DUAL")
    long nextMessageId();

    @Insert("INSERT INTO MESSAGE (MSG_ID, FROM_ID, FROM_NAME, TO_ID, TO_NAME, BODY, SENT_AT, TO_DEL, FROM_DEL) "
            + "VALUES (#{id}, #{fromId}, #{fromName}, #{toId}, #{toName}, #{body}, #{at}, 'N', 'N')")
    int insertMessage(@Param("id") long id, @Param("fromId") String fromId, @Param("fromName") String fromName, @Param("toId") String toId,
                      @Param("toName") String toName, @Param("body") String body, @Param("at") LocalDateTime at);

    String MSG_COLS = "MSG_ID AS \"id\", FROM_ID AS \"fromId\", FROM_NAME AS \"fromName\", TO_ID AS \"toId\", TO_NAME AS \"toName\", "
            + "BODY AS \"body\", SENT_AT AS \"sentAt\", READ_AT AS \"readAt\" ";

    @Select("SELECT * FROM (SELECT " + MSG_COLS + "FROM MESSAGE WHERE TO_ID = #{loginId} AND TO_DEL = 'N' ORDER BY MSG_ID DESC) WHERE ROWNUM <= 100")
    List<Map<String, Object>> inbox(@Param("loginId") String loginId);

    @Select("SELECT * FROM (SELECT " + MSG_COLS + "FROM MESSAGE WHERE FROM_ID = #{loginId} AND FROM_DEL = 'N' ORDER BY MSG_ID DESC) WHERE ROWNUM <= 100")
    List<Map<String, Object>> sent(@Param("loginId") String loginId);

    @Select("SELECT " + MSG_COLS + "FROM MESSAGE WHERE MSG_ID = #{id}")
    Map<String, Object> message(@Param("id") long id);

    @Update("UPDATE MESSAGE SET READ_AT = #{at} WHERE MSG_ID = #{id} AND TO_ID = #{loginId} AND READ_AT IS NULL")
    int markRead(@Param("id") long id, @Param("loginId") String loginId, @Param("at") LocalDateTime at);

    @Update("UPDATE MESSAGE SET TO_DEL = 'Y' WHERE MSG_ID = #{id} AND TO_ID = #{loginId}")
    int deleteForTo(@Param("id") long id, @Param("loginId") String loginId);

    @Update("UPDATE MESSAGE SET FROM_DEL = 'Y' WHERE MSG_ID = #{id} AND FROM_ID = #{loginId}")
    int deleteForFrom(@Param("id") long id, @Param("loginId") String loginId);

    @Select("SELECT COUNT(*) FROM MESSAGE WHERE TO_ID = #{loginId} AND TO_DEL = 'N' AND READ_AT IS NULL")
    int unreadMessages(@Param("loginId") String loginId);

    /* ---------------------------------------------------------------- 업무 분장 */

    @Select("SELECT LOGIN_ID AS \"loginId\", DUTIES AS \"duties\" FROM WORK_DUTY")
    List<Map<String, Object>> duties();

    @Delete("DELETE FROM WORK_DUTY WHERE LOGIN_ID = #{loginId}")
    int deleteDuty(@Param("loginId") String loginId);

    @Insert("INSERT INTO WORK_DUTY (LOGIN_ID, DUTIES, UPDATED_AT) VALUES (#{loginId}, #{duties}, #{at})")
    int insertDuty(@Param("loginId") String loginId, @Param("duties") String duties, @Param("at") LocalDateTime at);

    /* ---------------------------------------------------------------- 상용 답변 문구 */

    @Select("SELECT SEQ_REPLY_TEMPLATE.NEXTVAL FROM DUAL")
    long nextTemplateId();

    @Insert("INSERT INTO REPLY_TEMPLATE (TPL_ID, OWNER_ID, DEPT_NAME, TITLE, BODY, CREATED_AT) VALUES (#{id}, #{ownerId}, #{dept}, #{title}, #{body}, #{at})")
    int insertTemplate(@Param("id") long id, @Param("ownerId") String ownerId, @Param("dept") String dept, @Param("title") String title,
                       @Param("body") String body, @Param("at") LocalDateTime at);

    /** 내 것 + 우리 부서 공용 */
    @Select("<script>SELECT TPL_ID AS \"id\", OWNER_ID AS \"ownerId\", DEPT_NAME AS \"dept\", TITLE AS \"title\", BODY AS \"body\" FROM REPLY_TEMPLATE "
            + "WHERE OWNER_ID = #{loginId} <if test='dept != null'>OR (OWNER_ID IS NULL AND DEPT_NAME = #{dept})</if> ORDER BY TITLE</script>")
    List<Map<String, Object>> templates(@Param("loginId") String loginId, @Param("dept") String dept);

    @Select("SELECT TPL_ID AS \"id\", OWNER_ID AS \"ownerId\", DEPT_NAME AS \"dept\" FROM REPLY_TEMPLATE WHERE TPL_ID = #{id}")
    Map<String, Object> template(@Param("id") long id);

    @Delete("DELETE FROM REPLY_TEMPLATE WHERE TPL_ID = #{id}")
    int deleteTemplate(@Param("id") long id);

    /* ---------------------------------------------------------------- 물품 대여 */

    @Select("SELECT SEQ_LOAN_ITEM.NEXTVAL FROM DUAL")
    long nextItemId();

    @Insert("INSERT INTO LOAN_ITEM (ITEM_ID, NAME, PLACE, USE_YN, CREATED_AT) VALUES (#{id}, #{name}, #{place}, 'Y', #{at})")
    int insertItem(@Param("id") long id, @Param("name") String name, @Param("place") String place, @Param("at") LocalDateTime at);

    @Select("SELECT I.ITEM_ID AS \"id\", I.NAME AS \"name\", I.PLACE AS \"place\", I.USE_YN AS \"useYn\", L.LOAN_ID AS \"loanId\", "
            + "L.LOGIN_ID AS \"borrowerId\", L.USER_NAME AS \"borrower\", L.DUE_ON AS \"due\", L.PURPOSE AS \"purpose\" "
            + "FROM LOAN_ITEM I LEFT JOIN LOAN L ON L.ITEM_ID = I.ITEM_ID AND L.STATUS = 'ON' ORDER BY I.NAME")
    List<Map<String, Object>> items();

    @Select("SELECT ITEM_ID FROM LOAN_ITEM WHERE ITEM_ID = #{id} AND USE_YN = 'Y' FOR UPDATE")
    Long lockItem(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM LOAN WHERE ITEM_ID = #{id} AND STATUS = 'ON'")
    int itemOut(@Param("id") long id);

    @Select("SELECT SEQ_LOAN.NEXTVAL FROM DUAL")
    long nextLoanId();

    @Insert("INSERT INTO LOAN (LOAN_ID, ITEM_ID, LOGIN_ID, USER_NAME, PURPOSE, DUE_ON, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{itemId}, #{loginId}, #{userName}, #{purpose}, #{due, jdbcType=DATE}, 'ON', #{at})")
    int insertLoan(@Param("id") long id, @Param("itemId") long itemId, @Param("loginId") String loginId, @Param("userName") String userName,
                   @Param("purpose") String purpose, @Param("due") LocalDate due, @Param("at") LocalDateTime at);

    @Select("SELECT LOAN_ID AS \"id\", ITEM_ID AS \"itemId\", LOGIN_ID AS \"loginId\", STATUS AS \"status\" FROM LOAN WHERE LOAN_ID = #{id}")
    Map<String, Object> loan(@Param("id") long id);

    @Update("UPDATE LOAN SET STATUS = 'RETURNED', RETURNED_AT = #{at} WHERE LOAN_ID = #{id} AND STATUS = 'ON'")
    int returnLoan(@Param("id") long id, @Param("at") LocalDateTime at);

    @Select("SELECT L.LOAN_ID AS \"id\", I.NAME AS \"item\", L.LOGIN_ID AS \"loginId\", L.DUE_ON AS \"due\" FROM LOAN L "
            + "JOIN LOAN_ITEM I ON I.ITEM_ID = L.ITEM_ID WHERE L.STATUS = 'ON' AND L.DUE_ON < #{today, jdbcType=DATE}")
    List<Map<String, Object>> overdueLoans(@Param("today") LocalDate today);

    /* ---------------------------------------------------------------- 직원 설문 */

    @Select("SELECT SEQ_SURVEY.NEXTVAL FROM DUAL")
    long nextSurveyId();

    @Insert("INSERT INTO SURVEY (SURVEY_ID, TITLE, QUESTION, OPTIONS, CLOSES_ON, CREATED_BY, CREATED_AT) "
            + "VALUES (#{id}, #{title}, #{question}, #{options}, #{closes, jdbcType=DATE}, #{by}, #{at})")
    int insertSurvey(@Param("id") long id, @Param("title") String title, @Param("question") String question, @Param("options") String options,
                     @Param("closes") LocalDate closes, @Param("by") String by, @Param("at") LocalDateTime at);

    @Insert("INSERT INTO SURVEY_COUNT (SURVEY_ID, OPT_NO, CNT) VALUES (#{id}, #{no}, 0)")
    int insertCount(@Param("id") long id, @Param("no") int no);

    @Select("SELECT * FROM (SELECT SURVEY_ID AS \"id\", TITLE AS \"title\", QUESTION AS \"question\", OPTIONS AS \"options\", "
            + "CLOSES_ON AS \"closes\", CREATED_BY AS \"by\" FROM SURVEY ORDER BY SURVEY_ID DESC) WHERE ROWNUM <= 50")
    List<Map<String, Object>> surveys();

    @Select("SELECT SURVEY_ID AS \"id\", TITLE AS \"title\", QUESTION AS \"question\", OPTIONS AS \"options\", CLOSES_ON AS \"closes\" "
            + "FROM SURVEY WHERE SURVEY_ID = #{id}")
    Map<String, Object> survey(@Param("id") long id);

    @Insert("INSERT INTO SURVEY_VOTER (SURVEY_ID, LOGIN_ID, VOTED_AT) VALUES (#{id}, #{loginId}, #{at})")
    int insertVoter(@Param("id") long id, @Param("loginId") String loginId, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM SURVEY_VOTER WHERE SURVEY_ID = #{id} AND LOGIN_ID = #{loginId}")
    int voted(@Param("id") long id, @Param("loginId") String loginId);

    @Update("UPDATE SURVEY_COUNT SET CNT = CNT + 1 WHERE SURVEY_ID = #{id} AND OPT_NO = #{no}")
    int addVote(@Param("id") long id, @Param("no") int no);

    @Select("SELECT OPT_NO AS \"no\", CNT AS \"cnt\" FROM SURVEY_COUNT WHERE SURVEY_ID = #{id} ORDER BY OPT_NO")
    List<Map<String, Object>> counts(@Param("id") long id);

    /* ---------------------------------------------------------------- 회의 참석자 */

    @Update("UPDATE ROOM_RESV SET ATTENDEES = #{attendees, jdbcType=VARCHAR} WHERE RESV_ID = #{id}")
    int setAttendees(@Param("id") long id, @Param("attendees") String attendees);

    /* ---------------------------------------------------------------- 같은 민원인 */

    /** 이름이 같고 휴대전화나 이메일이 같은 다른 민원 (최근 1년) */
    @Select("<script>SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", TITLE AS \"title\", STATUS AS \"status\", "
            + "RECEIVED_AT AS \"receivedAt\" FROM CIVIL_COMPLAINT WHERE APPLICANT_NAME = #{name} AND COMPLAINT_ID &lt;&gt; #{id} "
            + "AND RECEIVED_AT &gt;= #{since} AND (1 = 0 <if test='mobile != null'>OR MOBILE = #{mobile} </if><if test='email != null'>OR EMAIL = #{email} </if>) "
            + "ORDER BY COMPLAINT_ID DESC</script>")
    List<Map<String, Object>> sameApplicant(@Param("id") long id, @Param("name") String name, @Param("mobile") String mobile,
                                            @Param("email") String email, @Param("since") LocalDateTime since);
}
