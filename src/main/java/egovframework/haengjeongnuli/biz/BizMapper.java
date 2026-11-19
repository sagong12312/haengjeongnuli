package egovframework.haengjeongnuli.biz;

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

/** 업무 지원 */
@Mapper
public interface BizMapper {

    @Select("SELECT SEQ_BIZ.NEXTVAL FROM DUAL")
    long nextId();

    /* ---------------------------------------------------------------- 업무 일지 */

    @Select("SELECT LOG_ID AS \"id\", LOGIN_ID AS \"loginId\", USER_NAME AS \"userName\", DEPT_NAME AS \"dept\", WORK_DATE AS \"day\", "
            + "BODY AS \"body\", UPDATED_AT AS \"updatedAt\" FROM WORK_LOG WHERE LOGIN_ID = #{loginId} AND WORK_DATE = #{day, jdbcType=DATE}")
    Map<String, Object> workLog(@Param("loginId") String loginId, @Param("day") LocalDate day);

    @Insert("INSERT INTO WORK_LOG (LOG_ID, LOGIN_ID, USER_NAME, DEPT_NAME, WORK_DATE, BODY, UPDATED_AT) "
            + "VALUES (#{id}, #{loginId}, #{userName}, #{dept, jdbcType=VARCHAR}, #{day, jdbcType=DATE}, #{body}, #{at})")
    int insertWorkLog(@Param("id") long id, @Param("loginId") String loginId, @Param("userName") String userName, @Param("dept") String dept,
                      @Param("day") LocalDate day, @Param("body") String body, @Param("at") LocalDateTime at);

    @Update("UPDATE WORK_LOG SET BODY = #{body}, UPDATED_AT = #{at} WHERE LOGIN_ID = #{loginId} AND WORK_DATE = #{day, jdbcType=DATE}")
    int updateWorkLog(@Param("loginId") String loginId, @Param("day") LocalDate day, @Param("body") String body, @Param("at") LocalDateTime at);

    @Select("SELECT LOGIN_ID AS \"loginId\", USER_NAME AS \"userName\", WORK_DATE AS \"day\", BODY AS \"body\" FROM WORK_LOG "
            + "WHERE DEPT_NAME = #{dept} AND WORK_DATE = #{day, jdbcType=DATE} ORDER BY USER_NAME")
    List<Map<String, Object>> deptWorkLogs(@Param("dept") String dept, @Param("day") LocalDate day);

    @Select("SELECT WORK_DATE AS \"day\", BODY AS \"body\" FROM WORK_LOG WHERE LOGIN_ID = #{loginId} "
            + "AND WORK_DATE >= #{from, jdbcType=DATE} AND WORK_DATE <= #{to, jdbcType=DATE} ORDER BY WORK_DATE DESC")
    List<Map<String, Object>> myWorkLogs(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /* ---------------------------------------------------------------- 부서 일정 */

    @Insert("INSERT INTO DEPT_EVENT (EVENT_ID, DEPT_NAME, EVENT_DATE, TITLE, CREATED_BY, CREATED_NAME, CREATED_AT) "
            + "VALUES (#{id}, #{dept}, #{day, jdbcType=DATE}, #{title}, #{by}, #{name}, #{at})")
    int insertDeptEvent(@Param("id") long id, @Param("dept") String dept, @Param("day") LocalDate day, @Param("title") String title,
                        @Param("by") String by, @Param("name") String name, @Param("at") LocalDateTime at);

    @Select("SELECT EVENT_ID AS \"id\", DEPT_NAME AS \"dept\", EVENT_DATE AS \"day\", TITLE AS \"title\", CREATED_BY AS \"by\", "
            + "CREATED_NAME AS \"byName\" FROM DEPT_EVENT WHERE DEPT_NAME = #{dept} AND EVENT_DATE >= #{from, jdbcType=DATE} "
            + "AND EVENT_DATE <= #{to, jdbcType=DATE} ORDER BY EVENT_DATE, EVENT_ID")
    List<Map<String, Object>> deptEvents(@Param("dept") String dept, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT EVENT_ID AS \"id\", DEPT_NAME AS \"dept\", CREATED_BY AS \"by\" FROM DEPT_EVENT WHERE EVENT_ID = #{id}")
    Map<String, Object> deptEvent(@Param("id") long id);

    @Delete("DELETE FROM DEPT_EVENT WHERE EVENT_ID = #{id}")
    int deleteDeptEvent(@Param("id") long id);

    /* ---------------------------------------------------------------- 협조 요청 */

    @Insert("INSERT INTO COOP_REQ (REQ_ID, FROM_ID, FROM_NAME, FROM_DEPT, TO_DEPT, TITLE, BODY, DUE_ON, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{fromId}, #{fromName}, #{fromDept, jdbcType=VARCHAR}, #{toDept}, #{title}, #{body}, #{due, jdbcType=DATE}, 'OPEN', #{at})")
    int insertCoop(@Param("id") long id, @Param("fromId") String fromId, @Param("fromName") String fromName, @Param("fromDept") String fromDept,
                   @Param("toDept") String toDept, @Param("title") String title, @Param("body") String body, @Param("due") LocalDate due,
                   @Param("at") LocalDateTime at);

    String COOP_COLS = "REQ_ID AS \"id\", FROM_ID AS \"fromId\", FROM_NAME AS \"fromName\", FROM_DEPT AS \"fromDept\", TO_DEPT AS \"toDept\", "
            + "TITLE AS \"title\", BODY AS \"body\", DUE_ON AS \"due\", STATUS AS \"status\", REPLY AS \"reply\", REPLIED_BY AS \"repliedBy\", "
            + "REPLIED_AT AS \"repliedAt\", CREATED_AT AS \"createdAt\" ";

    @Select("SELECT * FROM (SELECT " + COOP_COLS + "FROM COOP_REQ WHERE TO_DEPT = #{dept} OR FROM_DEPT = #{dept} OR FROM_ID = #{loginId} "
            + "ORDER BY CASE WHEN STATUS = 'OPEN' THEN 0 ELSE 1 END, DUE_ON, REQ_ID DESC) WHERE ROWNUM <= 100")
    List<Map<String, Object>> coops(@Param("dept") String dept, @Param("loginId") String loginId);

    @Select("SELECT " + COOP_COLS + "FROM COOP_REQ WHERE REQ_ID = #{id}")
    Map<String, Object> coop(@Param("id") long id);

    @Update("UPDATE COOP_REQ SET STATUS = 'DONE', REPLY = #{reply}, REPLIED_BY = #{by}, REPLIED_AT = #{at} WHERE REQ_ID = #{id} AND STATUS = 'OPEN'")
    int replyCoop(@Param("id") long id, @Param("reply") String reply, @Param("by") String by, @Param("at") LocalDateTime at);

    @Select("SELECT " + COOP_COLS + "FROM COOP_REQ WHERE STATUS = 'OPEN' AND REMINDED_YN = 'N' AND DUE_ON < #{today, jdbcType=DATE}")
    List<Map<String, Object>> overdueCoops(@Param("today") LocalDate today);

    @Update("UPDATE COOP_REQ SET REMINDED_YN = 'Y' WHERE REQ_ID = #{id}")
    int coopReminded(@Param("id") long id);

    /* ---------------------------------------------------------------- 회의록 */

    @Insert("INSERT INTO MEETING_MINUTES (MIN_ID, TITLE, MEET_DATE, ATTENDEES, BODY, DECISIONS, WRITER_ID, WRITER_NAME, DEPT_NAME, CREATED_AT) "
            + "VALUES (#{id}, #{title}, #{day, jdbcType=DATE}, #{attendees, jdbcType=VARCHAR}, #{body}, #{decisions, jdbcType=VARCHAR}, "
            + "#{by}, #{name}, #{dept, jdbcType=VARCHAR}, #{at})")
    int insertMinutes(@Param("id") long id, @Param("title") String title, @Param("day") LocalDate day, @Param("attendees") String attendees,
                      @Param("body") String body, @Param("decisions") String decisions, @Param("by") String by, @Param("name") String name,
                      @Param("dept") String dept, @Param("at") LocalDateTime at);

    String MIN_COLS = "MIN_ID AS \"id\", TITLE AS \"title\", MEET_DATE AS \"day\", ATTENDEES AS \"attendees\", BODY AS \"body\", "
            + "DECISIONS AS \"decisions\", WRITER_ID AS \"writerId\", WRITER_NAME AS \"writerName\", DEPT_NAME AS \"dept\", CREATED_AT AS \"createdAt\" ";

    /** 내가 쓴 것, 우리 부서 것, 내가 참석자인 것 */
    @Select("SELECT * FROM (SELECT " + MIN_COLS + "FROM MEETING_MINUTES WHERE WRITER_ID = #{loginId} OR DEPT_NAME = #{dept} "
            + "OR ATTENDEES LIKE #{like} ORDER BY MEET_DATE DESC, MIN_ID DESC) WHERE ROWNUM <= 100")
    List<Map<String, Object>> minutesList(@Param("loginId") String loginId, @Param("dept") String dept, @Param("like") String like);

    @Select("SELECT " + MIN_COLS + "FROM MEETING_MINUTES WHERE MIN_ID = #{id}")
    Map<String, Object> minutes(@Param("id") long id);

    /* ---------------------------------------------------------------- 전화 응대 */

    @Insert("INSERT INTO CALL_LOG (CALL_ID, CALLED_AT, CALLER_NAME, CALLER_PHONE, TOPIC, SUMMARY, RESULT, STAFF_ID, STAFF_NAME, DEPT_NAME) "
            + "VALUES (#{id}, #{at}, #{name, jdbcType=VARCHAR}, #{phone, jdbcType=VARCHAR}, #{topic}, #{summary, jdbcType=VARCHAR}, #{result}, "
            + "#{staffId}, #{staffName}, #{dept, jdbcType=VARCHAR})")
    int insertCall(@Param("id") long id, @Param("at") LocalDateTime at, @Param("name") String name, @Param("phone") String phone,
                   @Param("topic") String topic, @Param("summary") String summary, @Param("result") String result,
                   @Param("staffId") String staffId, @Param("staffName") String staffName, @Param("dept") String dept);

    @Select("<script>SELECT * FROM (SELECT CALL_ID AS \"id\", CALLED_AT AS \"at\", CALLER_NAME AS \"name\", CALLER_PHONE AS \"phone\", "
            + "TOPIC AS \"topic\", SUMMARY AS \"summary\", RESULT AS \"result\", STAFF_NAME AS \"staffName\", DEPT_NAME AS \"dept\" FROM CALL_LOG "
            + "WHERE CALLED_AT &gt;= #{from} AND CALLED_AT &lt; #{to} <if test='dept != null'>AND DEPT_NAME = #{dept} </if>"
            + "<if test='q != null'>AND (TOPIC LIKE #{q} OR SUMMARY LIKE #{q} OR CALLER_NAME LIKE #{q}) </if>"
            + "ORDER BY CALLED_AT DESC) WHERE ROWNUM &lt;= 300</script>")
    List<Map<String, Object>> calls(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("dept") String dept,
                                    @Param("q") String q);

    /* ---------------------------------------------------------------- 방문객 */

    @Insert("INSERT INTO VISITOR_LOG (VISIT_ID, VISITOR_NAME, VISITOR_PHONE, ORG_NAME, PURPOSE, HOST_ID, HOST_NAME, IN_AT, PURGED_YN) "
            + "VALUES (#{id}, #{name}, #{phone, jdbcType=VARCHAR}, #{org, jdbcType=VARCHAR}, #{purpose}, #{hostId}, #{hostName}, #{at}, 'N')")
    int insertVisit(@Param("id") long id, @Param("name") String name, @Param("phone") String phone, @Param("org") String org,
                    @Param("purpose") String purpose, @Param("hostId") String hostId, @Param("hostName") String hostName,
                    @Param("at") LocalDateTime at);

    @Select("SELECT * FROM (SELECT VISIT_ID AS \"id\", VISITOR_NAME AS \"name\", VISITOR_PHONE AS \"phone\", ORG_NAME AS \"org\", "
            + "PURPOSE AS \"purpose\", HOST_NAME AS \"hostName\", IN_AT AS \"inAt\", OUT_AT AS \"outAt\", PURGED_YN AS \"purged\" FROM VISITOR_LOG "
            + "WHERE IN_AT >= #{from} ORDER BY CASE WHEN OUT_AT IS NULL THEN 0 ELSE 1 END, IN_AT DESC) WHERE ROWNUM <= 300")
    List<Map<String, Object>> visits(@Param("from") LocalDateTime from);

    @Update("UPDATE VISITOR_LOG SET OUT_AT = #{at} WHERE VISIT_ID = #{id} AND OUT_AT IS NULL")
    int visitOut(@Param("id") long id, @Param("at") LocalDateTime at);

    /** 보존기간이 지난 방문 기록의 이름·연락처를 지운다 (방문 사실과 목적은 남김) */
    @Update("UPDATE VISITOR_LOG SET VISITOR_NAME = '(파기)', VISITOR_PHONE = NULL, PURGED_YN = 'Y' WHERE PURGED_YN = 'N' AND IN_AT < #{before}")
    int purgeVisits(@Param("before") LocalDateTime before);

    /* ---------------------------------------------------------------- 습득물 */

    @Insert("INSERT INTO LOST_ITEM (ITEM_ID, FOUND_AT, PLACE, ITEM_DESC, FINDER, STORE_PLACE, STATUS, CREATED_BY) "
            + "VALUES (#{id}, #{at}, #{place}, #{desc}, #{finder, jdbcType=VARCHAR}, #{store}, 'KEEP', #{by})")
    int insertLost(@Param("id") long id, @Param("at") LocalDateTime at, @Param("place") String place, @Param("desc") String desc,
                   @Param("finder") String finder, @Param("store") String store, @Param("by") String by);

    String LOST_COLS = "ITEM_ID AS \"id\", FOUND_AT AS \"foundAt\", PLACE AS \"place\", ITEM_DESC AS \"desc\", FINDER AS \"finder\", "
            + "STORE_PLACE AS \"store\", STATUS AS \"status\", OWNER_NAME AS \"owner\", HANDLED_AT AS \"handledAt\", HANDLED_BY AS \"handledBy\", "
            + "CREATED_BY AS \"createdBy\" ";

    @Select("SELECT * FROM (SELECT " + LOST_COLS + "FROM LOST_ITEM ORDER BY CASE WHEN STATUS = 'KEEP' THEN 0 ELSE 1 END, FOUND_AT DESC) WHERE ROWNUM <= 300")
    List<Map<String, Object>> lostItems();

    @Update("UPDATE LOST_ITEM SET STATUS = #{status}, OWNER_NAME = #{owner, jdbcType=VARCHAR}, HANDLED_AT = #{at}, HANDLED_BY = #{by} "
            + "WHERE ITEM_ID = #{id} AND STATUS = 'KEEP'")
    int handleLost(@Param("id") long id, @Param("status") String status, @Param("owner") String owner, @Param("at") LocalDateTime at,
                   @Param("by") String by);

    @Select("SELECT " + LOST_COLS + "FROM LOST_ITEM WHERE STATUS = 'KEEP' AND REMINDED_YN = 'N' AND FOUND_AT < #{before}")
    List<Map<String, Object>> lostToRemind(@Param("before") LocalDateTime before);

    @Update("UPDATE LOST_ITEM SET REMINDED_YN = 'Y' WHERE ITEM_ID = #{id}")
    int lostReminded(@Param("id") long id);

    /* ---------------------------------------------------------------- 정보공개청구 */

    @Select("SELECT COUNT(*) FROM INFO_REQ WHERE RECEIPT_NO LIKE #{prefix}")
    int infoCount(@Param("prefix") String prefix);

    @Insert("INSERT INTO INFO_REQ (REQ_ID, RECEIPT_NO, REQUESTER, CONTACT, CONTENT, RECEIVED_ON, DUE_ON, EXTENDED_YN, DEPT_NAME, ASSIGNEE_ID, "
            + "STATUS, CREATED_BY, CREATED_AT) VALUES (#{id}, #{no}, #{requester}, #{contact, jdbcType=VARCHAR}, #{content}, "
            + "#{received, jdbcType=DATE}, #{due, jdbcType=DATE}, 'N', #{dept, jdbcType=VARCHAR}, #{assignee, jdbcType=VARCHAR}, 'WAIT', #{by}, #{at})")
    int insertInfo(@Param("id") long id, @Param("no") String no, @Param("requester") String requester, @Param("contact") String contact,
                   @Param("content") String content, @Param("received") LocalDate received, @Param("due") LocalDate due,
                   @Param("dept") String dept, @Param("assignee") String assignee, @Param("by") String by, @Param("at") LocalDateTime at);

    String INFO_COLS = "REQ_ID AS \"id\", RECEIPT_NO AS \"no\", REQUESTER AS \"requester\", CONTACT AS \"contact\", CONTENT AS \"content\", "
            + "RECEIVED_ON AS \"received\", DUE_ON AS \"due\", EXTENDED_YN AS \"extended\", EXTEND_REASON AS \"extendReason\", "
            + "DEPT_NAME AS \"dept\", ASSIGNEE_ID AS \"assignee\", STATUS AS \"status\", DECISION AS \"decision\", DECISION_ON AS \"decisionOn\", "
            + "DECISION_NOTE AS \"decisionNote\" ";

    @Select("SELECT * FROM (SELECT " + INFO_COLS + "FROM INFO_REQ ORDER BY CASE WHEN STATUS = 'WAIT' THEN 0 ELSE 1 END, DUE_ON, REQ_ID DESC) "
            + "WHERE ROWNUM <= 300")
    List<Map<String, Object>> infoReqs();

    @Select("SELECT " + INFO_COLS + "FROM INFO_REQ WHERE REQ_ID = #{id}")
    Map<String, Object> infoReq(@Param("id") long id);

    @Update("UPDATE INFO_REQ SET DUE_ON = #{due, jdbcType=DATE}, EXTENDED_YN = 'Y', EXTEND_REASON = #{reason} WHERE REQ_ID = #{id} "
            + "AND STATUS = 'WAIT' AND EXTENDED_YN = 'N'")
    int extendInfo(@Param("id") long id, @Param("due") LocalDate due, @Param("reason") String reason);

    @Update("UPDATE INFO_REQ SET STATUS = 'DONE', DECISION = #{decision}, DECISION_ON = #{on, jdbcType=DATE}, DECISION_NOTE = #{note, jdbcType=VARCHAR} "
            + "WHERE REQ_ID = #{id} AND STATUS = 'WAIT'")
    int decideInfo(@Param("id") long id, @Param("decision") String decision, @Param("on") LocalDate on, @Param("note") String note);

    @Select("SELECT " + INFO_COLS + "FROM INFO_REQ WHERE STATUS = 'WAIT' AND DUE_ON <= #{until, jdbcType=DATE}")
    List<Map<String, Object>> infoDueSoon(@Param("until") LocalDate until);

    /* ---------------------------------------------------------------- 비품 */

    @Insert("INSERT INTO SUPPLY_ITEM (ITEM_ID, NAME, UNIT, STOCK, USE_YN) VALUES (#{id}, #{name}, #{unit}, #{stock}, 'Y')")
    int insertSupplyItem(@Param("id") long id, @Param("name") String name, @Param("unit") String unit, @Param("stock") int stock);

    @Select("SELECT ITEM_ID AS \"id\", NAME AS \"name\", UNIT AS \"unit\", STOCK AS \"stock\" FROM SUPPLY_ITEM WHERE USE_YN = 'Y' ORDER BY NAME")
    List<Map<String, Object>> supplyItems();

    @Select("SELECT ITEM_ID AS \"id\", NAME AS \"name\", UNIT AS \"unit\", STOCK AS \"stock\" FROM SUPPLY_ITEM WHERE ITEM_ID = #{id} FOR UPDATE")
    Map<String, Object> lockSupplyItem(@Param("id") long id);

    @Update("UPDATE SUPPLY_ITEM SET STOCK = STOCK + #{delta} WHERE ITEM_ID = #{id} AND STOCK + #{delta} >= 0")
    int changeStock(@Param("id") long id, @Param("delta") int delta);

    @Insert("INSERT INTO SUPPLY_REQ (REQ_ID, ITEM_ID, QTY, LOGIN_ID, USER_NAME, DEPT_NAME, STATUS, NOTE, CREATED_AT) "
            + "VALUES (#{id}, #{itemId}, #{qty}, #{loginId}, #{userName}, #{dept, jdbcType=VARCHAR}, 'WAIT', #{note, jdbcType=VARCHAR}, #{at})")
    int insertSupplyReq(@Param("id") long id, @Param("itemId") long itemId, @Param("qty") int qty, @Param("loginId") String loginId,
                        @Param("userName") String userName, @Param("dept") String dept, @Param("note") String note, @Param("at") LocalDateTime at);

    String SREQ_COLS = "R.REQ_ID AS \"id\", R.ITEM_ID AS \"itemId\", I.NAME AS \"item\", I.UNIT AS \"unit\", R.QTY AS \"qty\", R.LOGIN_ID AS \"loginId\", "
            + "R.USER_NAME AS \"userName\", R.DEPT_NAME AS \"dept\", R.STATUS AS \"status\", R.NOTE AS \"note\", R.CREATED_AT AS \"createdAt\", "
            + "R.HANDLED_BY AS \"handledBy\" ";

    @Select("<script>SELECT * FROM (SELECT " + SREQ_COLS + "FROM SUPPLY_REQ R JOIN SUPPLY_ITEM I ON I.ITEM_ID = R.ITEM_ID "
            + "<if test='loginId != null'>WHERE R.LOGIN_ID = #{loginId} </if>"
            + "ORDER BY CASE WHEN R.STATUS = 'WAIT' THEN 0 ELSE 1 END, R.REQ_ID DESC) WHERE ROWNUM &lt;= 200</script>")
    List<Map<String, Object>> supplyReqs(@Param("loginId") String loginId);

    @Select("SELECT " + SREQ_COLS + "FROM SUPPLY_REQ R JOIN SUPPLY_ITEM I ON I.ITEM_ID = R.ITEM_ID WHERE R.REQ_ID = #{id}")
    Map<String, Object> supplyReq(@Param("id") long id);

    @Update("UPDATE SUPPLY_REQ SET STATUS = #{status}, NOTE = #{note, jdbcType=VARCHAR}, HANDLED_BY = #{by}, HANDLED_AT = #{at} "
            + "WHERE REQ_ID = #{id} AND STATUS = 'WAIT'")
    int handleSupplyReq(@Param("id") long id, @Param("status") String status, @Param("note") String note, @Param("by") String by,
                        @Param("at") LocalDateTime at);

    /* ---------------------------------------------------------------- 업무 매뉴얼 */

    @Insert("INSERT INTO MANUAL_DOC (DOC_ID, DEPT_NAME, TITLE, BODY, VERSION, UPDATED_BY, UPDATED_AT) "
            + "VALUES (#{id}, #{dept, jdbcType=VARCHAR}, #{title}, #{body}, 1, #{by}, #{at})")
    int insertManual(@Param("id") long id, @Param("dept") String dept, @Param("title") String title, @Param("body") String body,
                     @Param("by") String by, @Param("at") LocalDateTime at);

    String MAN_COLS = "DOC_ID AS \"id\", DEPT_NAME AS \"dept\", TITLE AS \"title\", BODY AS \"body\", VERSION AS \"version\", "
            + "UPDATED_BY AS \"updatedBy\", UPDATED_AT AS \"updatedAt\" ";

    @Select("<script>SELECT " + MAN_COLS + "FROM MANUAL_DOC <if test='q != null'>WHERE TITLE LIKE #{q} OR BODY LIKE #{q} </if>"
            + "ORDER BY DEPT_NAME, TITLE</script>")
    List<Map<String, Object>> manuals(@Param("q") String q);

    @Select("SELECT " + MAN_COLS + "FROM MANUAL_DOC WHERE DOC_ID = #{id}")
    Map<String, Object> manual(@Param("id") long id);

    @Insert("INSERT INTO MANUAL_REV (DOC_ID, VERSION, TITLE, BODY, UPDATED_BY, UPDATED_AT) "
            + "SELECT DOC_ID, VERSION, TITLE, BODY, UPDATED_BY, UPDATED_AT FROM MANUAL_DOC WHERE DOC_ID = #{id}")
    int keepManualRev(@Param("id") long id);

    @Update("UPDATE MANUAL_DOC SET TITLE = #{title}, BODY = #{body}, VERSION = VERSION + 1, UPDATED_BY = #{by}, UPDATED_AT = #{at} "
            + "WHERE DOC_ID = #{id} AND VERSION = #{version}")
    int updateManual(@Param("id") long id, @Param("version") int version, @Param("title") String title, @Param("body") String body,
                     @Param("by") String by, @Param("at") LocalDateTime at);

    @Select("SELECT DOC_ID AS \"id\", VERSION AS \"version\", TITLE AS \"title\", BODY AS \"body\", UPDATED_BY AS \"updatedBy\", "
            + "UPDATED_AT AS \"updatedAt\" FROM MANUAL_REV WHERE DOC_ID = #{id} ORDER BY VERSION DESC")
    List<Map<String, Object>> manualRevs(@Param("id") long id);

    @Select("SELECT DOC_ID AS \"id\", VERSION AS \"version\", TITLE AS \"title\", BODY AS \"body\" FROM MANUAL_REV "
            + "WHERE DOC_ID = #{id} AND VERSION = #{version}")
    Map<String, Object> manualRev(@Param("id") long id, @Param("version") int version);

    /* ---------------------------------------------------------------- 경조사 · 칭찬 */

    @Insert("INSERT INTO FAMILY_EVENT (EV_ID, KIND, TITLE, BODY, EVENT_DATE, POSTED_BY, POSTED_NAME, CREATED_AT) "
            + "VALUES (#{id}, #{kind}, #{title}, #{body, jdbcType=VARCHAR}, #{day, jdbcType=DATE}, #{by}, #{name}, #{at})")
    int insertFamilyEvent(@Param("id") long id, @Param("kind") String kind, @Param("title") String title, @Param("body") String body,
                          @Param("day") LocalDate day, @Param("by") String by, @Param("name") String name, @Param("at") LocalDateTime at);

    @Select("SELECT * FROM (SELECT EV_ID AS \"id\", KIND AS \"kind\", TITLE AS \"title\", BODY AS \"body\", EVENT_DATE AS \"day\", "
            + "POSTED_BY AS \"by\", POSTED_NAME AS \"byName\" FROM FAMILY_EVENT WHERE EVENT_DATE >= #{from, jdbcType=DATE} "
            + "ORDER BY EVENT_DATE DESC, EV_ID DESC) WHERE ROWNUM <= 100")
    List<Map<String, Object>> familyEvents(@Param("from") LocalDate from);

    @Select("SELECT POSTED_BY FROM FAMILY_EVENT WHERE EV_ID = #{id}")
    String familyEventOwner(@Param("id") long id);

    @Delete("DELETE FROM FAMILY_EVENT WHERE EV_ID = #{id}")
    int deleteFamilyEvent(@Param("id") long id);

    @Insert("INSERT INTO PRAISE (PRAISE_ID, FROM_ID, FROM_NAME, TO_ID, TO_NAME, BODY, CREATED_AT) "
            + "VALUES (#{id}, #{fromId}, #{fromName}, #{toId}, #{toName}, #{body}, #{at})")
    int insertPraise(@Param("id") long id, @Param("fromId") String fromId, @Param("fromName") String fromName, @Param("toId") String toId,
                     @Param("toName") String toName, @Param("body") String body, @Param("at") LocalDateTime at);

    @Select("SELECT * FROM (SELECT PRAISE_ID AS \"id\", FROM_NAME AS \"fromName\", TO_ID AS \"toId\", TO_NAME AS \"toName\", BODY AS \"body\", "
            + "CREATED_AT AS \"at\" FROM PRAISE ORDER BY PRAISE_ID DESC) WHERE ROWNUM <= 50")
    List<Map<String, Object>> praises();

    @Select("SELECT * FROM (SELECT TO_NAME AS \"name\", COUNT(*) AS \"cnt\" FROM PRAISE WHERE CREATED_AT >= #{from} "
            + "GROUP BY TO_ID, TO_NAME ORDER BY COUNT(*) DESC, TO_NAME) WHERE ROWNUM <= 5")
    List<Map<String, Object>> praiseTop(@Param("from") LocalDateTime from);

    @Select("SELECT COUNT(*) FROM PRAISE WHERE FROM_ID = #{fromId} AND TO_ID = #{toId} AND CREATED_AT >= #{since}")
    int praisedRecently(@Param("fromId") String fromId, @Param("toId") String toId, @Param("since") LocalDateTime since);
}
