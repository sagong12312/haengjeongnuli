package egovframework.haengjeongnuli.office;

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

/** 공통 업무: 회의실, 당직, 주간 업무 보고, 인계인수, 법정의무교육, 비상연락망 */
@Mapper
public interface OfficeMapper {

    /* ================================================================ 회의실 */

    @Select("SELECT SEQ_ROOM.NEXTVAL FROM DUAL")
    long nextRoomId();

    @Insert("INSERT INTO ROOM (ROOM_ID, NAME, PLACE, CAPACITY, USE_YN, CREATED_AT) VALUES (#{id}, #{name}, #{place}, "
            + "#{capacity, jdbcType=NUMERIC}, 'Y', #{at})")
    int insertRoom(@Param("id") long id, @Param("name") String name, @Param("place") String place,
                   @Param("capacity") Integer capacity, @Param("at") LocalDateTime at);

    @Update("UPDATE ROOM SET USE_YN = #{yn} WHERE ROOM_ID = #{id}")
    int setRoomUse(@Param("id") long id, @Param("yn") String yn);

    @Select("<script>SELECT ROOM_ID AS \"id\", NAME AS \"name\", PLACE AS \"place\", CAPACITY AS \"capacity\", USE_YN AS \"useYn\" "
            + "FROM ROOM <if test='onlyUsed'>WHERE USE_YN = 'Y' </if>ORDER BY NAME</script>")
    List<Map<String, Object>> rooms(@Param("onlyUsed") boolean onlyUsed);

    @Select("SELECT ROOM_ID AS \"id\", NAME AS \"name\", USE_YN AS \"useYn\" FROM ROOM WHERE ROOM_ID = #{id}")
    Map<String, Object> room(@Param("id") long id);

    /** 같은 방을 두 사람이 동시에 예약하지 못하게 방 행을 잠근다 */
    @Select("SELECT ROOM_ID FROM ROOM WHERE ROOM_ID = #{id} FOR UPDATE")
    Long lockRoom(@Param("id") long id);

    /** 앞으로 남은 예약 (회의실을 멈출 때 알리려고) */
    @Select("SELECT RESV_ID AS \"id\", LOGIN_ID AS \"loginId\", RESV_DATE AS \"day\", START_TIME AS \"start\" FROM ROOM_RESV "
            + "WHERE ROOM_ID = #{roomId} AND RESV_DATE >= #{from, jdbcType=DATE}")
    List<Map<String, Object>> futureResvs(@Param("roomId") long roomId, @Param("from") LocalDate from);

    @Select("SELECT COUNT(*) FROM ROOM WHERE NAME = #{name}")
    int roomNameUsed(@Param("name") String name);

    @Select("SELECT SEQ_ROOM_RESV.NEXTVAL FROM DUAL")
    long nextResvId();

    /** 같은 방·같은 날 시간이 겹치는 예약 수 (HH:mm 글자 비교로 충분) */
    @Select("SELECT COUNT(*) FROM ROOM_RESV WHERE ROOM_ID = #{roomId} AND RESV_DATE = #{day, jdbcType=DATE} "
            + "AND START_TIME < #{end} AND END_TIME > #{start}")
    int overlap(@Param("roomId") long roomId, @Param("day") LocalDate day, @Param("start") String start, @Param("end") String end);

    @Insert("INSERT INTO ROOM_RESV (RESV_ID, ROOM_ID, RESV_DATE, START_TIME, END_TIME, TITLE, LOGIN_ID, USER_NAME, DEPT_NAME, CREATED_AT) "
            + "VALUES (#{id}, #{roomId}, #{day, jdbcType=DATE}, #{start}, #{end}, #{title}, #{loginId}, #{userName}, #{dept}, #{at})")
    int insertResv(@Param("id") long id, @Param("roomId") long roomId, @Param("day") LocalDate day, @Param("start") String start,
                   @Param("end") String end, @Param("title") String title, @Param("loginId") String loginId,
                   @Param("userName") String userName, @Param("dept") String dept, @Param("at") LocalDateTime at);

    @Select("SELECT V.RESV_ID AS \"id\", V.ROOM_ID AS \"roomId\", R.NAME AS \"roomName\", V.RESV_DATE AS \"day\", V.START_TIME AS \"start\", "
            + "V.END_TIME AS \"end\", V.TITLE AS \"title\", V.LOGIN_ID AS \"loginId\", V.USER_NAME AS \"userName\", V.DEPT_NAME AS \"dept\" "
            + "FROM ROOM_RESV V JOIN ROOM R ON R.ROOM_ID = V.ROOM_ID "
            + "WHERE V.RESV_DATE >= #{from, jdbcType=DATE} AND V.RESV_DATE <= #{to, jdbcType=DATE} ORDER BY V.RESV_DATE, V.START_TIME, R.NAME")
    List<Map<String, Object>> resvs(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT RESV_ID AS \"id\", LOGIN_ID AS \"loginId\", RESV_DATE AS \"day\", START_TIME AS \"start\" FROM ROOM_RESV WHERE RESV_ID = #{id}")
    Map<String, Object> resv(@Param("id") long id);

    @Delete("DELETE FROM ROOM_RESV WHERE RESV_ID = #{id}")
    int deleteResv(@Param("id") long id);

    /* ================================================================ 당직 */

    @Select("SELECT SEQ_DUTY_ROSTER.NEXTVAL FROM DUAL")
    long nextRosterId();

    @Insert("INSERT INTO DUTY_ROSTER (ROSTER_ID, DUTY_DATE, DUTY_TYPE, LOGIN_ID, USER_NAME, DEPT_NAME, CREATED_BY, CREATED_AT) "
            + "VALUES (#{id}, #{day, jdbcType=DATE}, #{type}, #{loginId}, #{userName}, #{dept}, #{by}, #{at})")
    int insertRoster(@Param("id") long id, @Param("day") LocalDate day, @Param("type") String type, @Param("loginId") String loginId,
                     @Param("userName") String userName, @Param("dept") String dept, @Param("by") String by, @Param("at") LocalDateTime at);

    String ROSTER_COLS = "ROSTER_ID AS \"id\", DUTY_DATE AS \"day\", DUTY_TYPE AS \"type\", LOGIN_ID AS \"loginId\", "
            + "USER_NAME AS \"userName\", DEPT_NAME AS \"dept\", LOG_TEXT AS \"log\", LOG_AT AS \"logAt\" ";

    @Select("SELECT " + ROSTER_COLS + "FROM DUTY_ROSTER WHERE DUTY_DATE >= #{from, jdbcType=DATE} AND DUTY_DATE <= #{to, jdbcType=DATE} "
            + "ORDER BY DUTY_DATE, DUTY_TYPE")
    List<Map<String, Object>> roster(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT " + ROSTER_COLS + "FROM DUTY_ROSTER WHERE ROSTER_ID = #{id}")
    Map<String, Object> rosterOne(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM DUTY_ROSTER WHERE DUTY_DATE = #{day, jdbcType=DATE} AND DUTY_TYPE = #{type}")
    int rosterExists(@Param("day") LocalDate day, @Param("type") String type);

    /** 직원별 당직 횟수와 마지막 당직일 (공평하게 돌리려고) */
    @Select("SELECT LOGIN_ID AS \"loginId\", COUNT(*) AS \"cnt\", MAX(DUTY_DATE) AS \"last\" FROM DUTY_ROSTER GROUP BY LOGIN_ID")
    List<Map<String, Object>> rosterStats();

    /** 그 직원의 그 날짜 앞뒤 하루 당직 수 (교대 받을 사람이 잇달아 서지 않게) */
    @Select("SELECT COUNT(*) FROM DUTY_ROSTER WHERE LOGIN_ID = #{loginId} AND DUTY_DATE >= #{from, jdbcType=DATE} "
            + "AND DUTY_DATE <= #{to, jdbcType=DATE} AND ROSTER_ID <> #{except}")
    int dutyNear(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("except") long exceptRosterId);

    /** 인수 전 인계서 회수 */
    @Delete("DELETE FROM HANDOVER WHERE HO_ID = #{id} AND FROM_ID = #{fromId} AND STATUS = 'SENT'")
    int withdrawHandover(@Param("id") long id, @Param("fromId") String fromId);

    /** 같은 날 같은 과목 이수 기록 */
    @Select("SELECT COUNT(*) FROM EDU_RECORD WHERE LOGIN_ID = #{loginId} AND COURSE_ID = #{courseId} AND DONE_ON = #{on, jdbcType=DATE}")
    int eduSameDay(@Param("loginId") String loginId, @Param("courseId") long courseId, @Param("on") LocalDate on);

    @Update("UPDATE DUTY_ROSTER SET LOGIN_ID = #{loginId}, USER_NAME = #{userName}, DEPT_NAME = #{dept} WHERE ROSTER_ID = #{id}")
    int reassign(@Param("id") long id, @Param("loginId") String loginId, @Param("userName") String userName, @Param("dept") String dept);

    @Update("UPDATE DUTY_ROSTER SET LOG_TEXT = #{log}, LOG_AT = #{at} WHERE ROSTER_ID = #{id}")
    int writeLog(@Param("id") long id, @Param("log") String log, @Param("at") LocalDateTime at);

    @Delete("DELETE FROM DUTY_ROSTER WHERE DUTY_DATE >= #{from, jdbcType=DATE} AND DUTY_DATE <= #{to, jdbcType=DATE} AND LOG_TEXT IS NULL")
    int clearRoster(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 그날 승인된 휴가·출장이 있는 직원 */
    @Select("SELECT DISTINCT R.LOGIN_ID FROM DUTY_REQUEST R JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID "
            + "WHERE D.STATUS IN ('PROG', 'DONE') AND D.DELETED_YN = 'N' AND R.KIND <> 'OVERTIME' "
            + "AND R.START_DATE <= #{day, jdbcType=DATE} AND R.END_DATE >= #{day, jdbcType=DATE}")
    List<String> awayOn(@Param("day") LocalDate day);

    @Select("SELECT SEQ_DUTY_SWAP.NEXTVAL FROM DUAL")
    long nextSwapId();

    @Insert("INSERT INTO DUTY_SWAP (SWAP_ID, ROSTER_ID, FROM_ID, TO_ID, BACK_ROSTER_ID, REASON, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{rosterId}, #{fromId}, #{toId}, #{back, jdbcType=NUMERIC}, #{reason}, 'REQ', #{at})")
    int insertSwap(@Param("id") long id, @Param("rosterId") long rosterId, @Param("fromId") String fromId, @Param("toId") String toId,
                   @Param("back") Long backRosterId, @Param("reason") String reason, @Param("at") LocalDateTime at);

    @Select("SELECT S.SWAP_ID AS \"id\", S.ROSTER_ID AS \"rosterId\", S.FROM_ID AS \"fromId\", S.TO_ID AS \"toId\", "
            + "S.BACK_ROSTER_ID AS \"backId\", S.REASON AS \"reason\", S.STATUS AS \"status\", R.DUTY_DATE AS \"day\", R.DUTY_TYPE AS \"type\", "
            + "B.DUTY_DATE AS \"backDay\", B.DUTY_TYPE AS \"backType\" "
            + "FROM DUTY_SWAP S JOIN DUTY_ROSTER R ON R.ROSTER_ID = S.ROSTER_ID LEFT JOIN DUTY_ROSTER B ON B.ROSTER_ID = S.BACK_ROSTER_ID "
            + "WHERE (S.FROM_ID = #{loginId} OR S.TO_ID = #{loginId}) AND S.STATUS = 'REQ' ORDER BY S.SWAP_ID DESC")
    List<Map<String, Object>> openSwaps(@Param("loginId") String loginId);

    @Select("SELECT SWAP_ID AS \"id\", ROSTER_ID AS \"rosterId\", FROM_ID AS \"fromId\", TO_ID AS \"toId\", BACK_ROSTER_ID AS \"backId\", "
            + "STATUS AS \"status\" FROM DUTY_SWAP WHERE SWAP_ID = #{id}")
    Map<String, Object> swap(@Param("id") long id);

    @Update("UPDATE DUTY_SWAP SET STATUS = #{status}, DECIDED_AT = #{at} WHERE SWAP_ID = #{id} AND STATUS = 'REQ'")
    int decideSwap(@Param("id") long id, @Param("status") String status, @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM DUTY_SWAP WHERE ROSTER_ID = #{rosterId} AND STATUS = 'REQ'")
    int openSwapFor(@Param("rosterId") long rosterId);

    /* ================================================================ 주간 업무 보고 */

    @Select("SELECT SEQ_WEEKLY_REPORT.NEXTVAL FROM DUAL")
    long nextReportId();

    String REPORT_COLS = "REP_ID AS \"id\", LOGIN_ID AS \"loginId\", USER_NAME AS \"userName\", DEPT_NAME AS \"dept\", "
            + "WEEK_START AS \"week\", DONE_TEXT AS \"done\", PLAN_TEXT AS \"plan\", ISSUE_TEXT AS \"issue\", UPDATED_AT AS \"updatedAt\" ";

    @Select("SELECT " + REPORT_COLS + "FROM WEEKLY_REPORT WHERE LOGIN_ID = #{loginId} AND WEEK_START = #{week, jdbcType=DATE}")
    Map<String, Object> report(@Param("loginId") String loginId, @Param("week") LocalDate week);

    @Insert("INSERT INTO WEEKLY_REPORT (REP_ID, LOGIN_ID, USER_NAME, DEPT_NAME, WEEK_START, DONE_TEXT, PLAN_TEXT, ISSUE_TEXT, UPDATED_AT) "
            + "VALUES (#{id}, #{loginId}, #{userName}, #{dept}, #{week, jdbcType=DATE}, #{done}, #{plan}, #{issue}, #{at})")
    int insertReport(@Param("id") long id, @Param("loginId") String loginId, @Param("userName") String userName, @Param("dept") String dept,
                     @Param("week") LocalDate week, @Param("done") String done, @Param("plan") String plan, @Param("issue") String issue,
                     @Param("at") LocalDateTime at);

    @Update("UPDATE WEEKLY_REPORT SET DONE_TEXT = #{done}, PLAN_TEXT = #{plan}, ISSUE_TEXT = #{issue}, DEPT_NAME = #{dept}, UPDATED_AT = #{at} "
            + "WHERE REP_ID = #{id}")
    int updateReport(@Param("id") long id, @Param("dept") String dept, @Param("done") String done, @Param("plan") String plan,
                     @Param("issue") String issue, @Param("at") LocalDateTime at);

    @Select("SELECT " + REPORT_COLS + "FROM WEEKLY_REPORT WHERE DEPT_NAME = #{dept} AND WEEK_START = #{week, jdbcType=DATE} ORDER BY USER_NAME")
    List<Map<String, Object>> deptReports(@Param("dept") String dept, @Param("week") LocalDate week);

    /* ================================================================ 인계인수 */

    @Select("SELECT SEQ_HANDOVER.NEXTVAL FROM DUAL")
    long nextHandoverId();

    @Insert("INSERT INTO HANDOVER (HO_ID, FROM_ID, FROM_NAME, TO_ID, TO_NAME, DEPT_NAME, TITLE, TASKS, DOCS, NOTES, STATUS, CREATED_AT) "
            + "VALUES (#{id}, #{fromId}, #{fromName}, #{toId}, #{toName}, #{dept}, #{title}, #{tasks}, #{docs}, #{notes}, 'SENT', #{at})")
    int insertHandover(@Param("id") long id, @Param("fromId") String fromId, @Param("fromName") String fromName, @Param("toId") String toId,
                       @Param("toName") String toName, @Param("dept") String dept, @Param("title") String title, @Param("tasks") String tasks,
                       @Param("docs") String docs, @Param("notes") String notes, @Param("at") LocalDateTime at);

    String HO_COLS = "HO_ID AS \"id\", FROM_ID AS \"fromId\", FROM_NAME AS \"fromName\", TO_ID AS \"toId\", TO_NAME AS \"toName\", "
            + "DEPT_NAME AS \"dept\", TITLE AS \"title\", TASKS AS \"tasks\", DOCS AS \"docs\", NOTES AS \"notes\", STATUS AS \"status\", "
            + "ACCEPT_NOTE AS \"acceptNote\", CREATED_AT AS \"createdAt\", ACCEPTED_AT AS \"acceptedAt\" ";

    @Select("SELECT " + HO_COLS + "FROM HANDOVER WHERE FROM_ID = #{loginId} OR TO_ID = #{loginId} ORDER BY HO_ID DESC")
    List<Map<String, Object>> handovers(@Param("loginId") String loginId);

    @Select("SELECT " + HO_COLS + "FROM HANDOVER WHERE HO_ID = #{id}")
    Map<String, Object> handover(@Param("id") long id);

    @Update("UPDATE HANDOVER SET STATUS = 'ACCEPTED', ACCEPT_NOTE = #{note}, ACCEPTED_AT = #{at} WHERE HO_ID = #{id} AND STATUS = 'SENT'")
    int acceptHandover(@Param("id") long id, @Param("note") String note, @Param("at") LocalDateTime at);

    /* ================================================================ 법정의무교육 */

    @Select("SELECT SEQ_EDU_COURSE.NEXTVAL FROM DUAL")
    long nextCourseId();

    @Insert("INSERT INTO EDU_COURSE (COURSE_ID, NAME, HOURS, BASIS, USE_YN, SORT_ORDER) VALUES (#{id}, #{name}, #{hours}, #{basis}, 'Y', #{sort})")
    int insertCourse(@Param("id") long id, @Param("name") String name, @Param("hours") double hours, @Param("basis") String basis,
                     @Param("sort") int sort);

    @Select("<script>SELECT COURSE_ID AS \"id\", NAME AS \"name\", HOURS AS \"hours\", BASIS AS \"basis\", USE_YN AS \"useYn\" "
            + "FROM EDU_COURSE <if test='onlyUsed'>WHERE USE_YN = 'Y' </if>ORDER BY SORT_ORDER, COURSE_ID</script>")
    List<Map<String, Object>> courses(@Param("onlyUsed") boolean onlyUsed);

    @Select("SELECT COUNT(*) FROM EDU_COURSE")
    int courseCount();

    @Select("SELECT COUNT(*) FROM EDU_COURSE WHERE NAME = #{name}")
    int courseNameUsed(@Param("name") String name);

    @Update("UPDATE EDU_COURSE SET USE_YN = #{yn} WHERE COURSE_ID = #{id}")
    int setCourseUse(@Param("id") long id, @Param("yn") String yn);

    @Select("SELECT SEQ_EDU_RECORD.NEXTVAL FROM DUAL")
    long nextEduId();

    @Insert("INSERT INTO EDU_RECORD (REC_ID, COURSE_ID, LOGIN_ID, EDU_YEAR, DONE_ON, HOURS, PROVIDER, CREATED_AT) "
            + "VALUES (#{id}, #{courseId}, #{loginId}, #{year}, #{on, jdbcType=DATE}, #{hours}, #{provider}, #{at})")
    int insertEdu(@Param("id") long id, @Param("courseId") long courseId, @Param("loginId") String loginId, @Param("year") int year,
                  @Param("on") LocalDate on, @Param("hours") double hours, @Param("provider") String provider, @Param("at") LocalDateTime at);

    @Select("SELECT E.REC_ID AS \"id\", E.COURSE_ID AS \"courseId\", C.NAME AS \"course\", E.LOGIN_ID AS \"loginId\", E.DONE_ON AS \"on\", "
            + "E.HOURS AS \"hours\", E.PROVIDER AS \"provider\" FROM EDU_RECORD E JOIN EDU_COURSE C ON C.COURSE_ID = E.COURSE_ID "
            + "WHERE E.EDU_YEAR = #{year} ORDER BY E.DONE_ON DESC, E.REC_ID DESC")
    List<Map<String, Object>> eduRecords(@Param("year") int year);

    @Select("SELECT REC_ID AS \"id\", LOGIN_ID AS \"loginId\" FROM EDU_RECORD WHERE REC_ID = #{id}")
    Map<String, Object> eduRecord(@Param("id") long id);

    @Delete("DELETE FROM EDU_RECORD WHERE REC_ID = #{id}")
    int deleteEdu(@Param("id") long id);

    /* ================================================================ 비상연락망 */

    @Select("SELECT LOGIN_ID AS \"loginId\", MOBILE AS \"mobile\", SHARE_YN AS \"shareYn\" FROM EMERGENCY_CONTACT")
    List<Map<String, Object>> contacts();

    @Select("SELECT LOGIN_ID AS \"loginId\", MOBILE AS \"mobile\", SHARE_YN AS \"shareYn\" FROM EMERGENCY_CONTACT WHERE LOGIN_ID = #{loginId}")
    Map<String, Object> contact(@Param("loginId") String loginId);

    @Delete("DELETE FROM EMERGENCY_CONTACT WHERE LOGIN_ID = #{loginId}")
    int deleteContact(@Param("loginId") String loginId);

    @Insert("INSERT INTO EMERGENCY_CONTACT (LOGIN_ID, MOBILE, SHARE_YN, UPDATED_AT) VALUES (#{loginId}, #{mobile}, #{share}, #{at})")
    int insertContact(@Param("loginId") String loginId, @Param("mobile") String mobile, @Param("share") String share,
                      @Param("at") LocalDateTime at);
}
