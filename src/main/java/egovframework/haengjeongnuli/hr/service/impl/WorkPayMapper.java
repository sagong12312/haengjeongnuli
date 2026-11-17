package egovframework.haengjeongnuli.hr.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import egovframework.haengjeongnuli.hr.service.DutyRequestVO;

/** 복무 보강: 결재 끝난 복무 신청, 근태 정정, 유연근무 시간, 출장 복명·정산, 수당·여비 설정 */
@Mapper
public interface WorkPayMapper {

    String DUTY_COLS = "R.REQ_ID, R.LOGIN_ID, R.USER_NAME, R.DEPT_NAME, R.KIND, R.START_DATE, R.END_DATE, R.HALF_DAY, "
            + "R.START_TIME, R.END_TIME, R.DAYS, R.HOURS, R.DESTINATION, R.REASON, R.DOC_ID, R.CREATED_AT, "
            + "R.TRIP_AREA, R.TRIP_HOURS, R.OFFICIAL_CAR, R.LODGING_NIGHTS, R.LODGING_REGION, R.EST_AMOUNT, D.STATUS AS DOC_STATUS ";

    /** 기간과 겹치는 결재 끝난(승인) 복무 신청 */
    @Select("SELECT " + DUTY_COLS + "FROM DUTY_REQUEST R JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID "
            + "WHERE R.LOGIN_ID = #{loginId} AND D.STATUS = 'DONE' AND D.DELETED_YN = 'N' "
            + "AND R.START_DATE <= #{to, jdbcType=DATE} AND R.END_DATE >= #{from, jdbcType=DATE} ORDER BY R.START_DATE, R.REQ_ID")
    List<DutyRequestVO> approvedDuty(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT " + DUTY_COLS + "FROM DUTY_REQUEST R JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID WHERE R.REQ_ID = #{id}")
    DutyRequestVO duty(@Param("id") long id);

    /** 출장 목록 (복명·정산 화면) */
    @Select("SELECT * FROM (SELECT " + DUTY_COLS + ", T.AMOUNT AS SETTLED_AMOUNT FROM DUTY_REQUEST R "
            + "JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID LEFT JOIN TRIP_REPORT T ON T.REQ_ID = R.REQ_ID "
            + "WHERE R.LOGIN_ID = #{loginId} AND R.KIND = 'TRIP' AND D.DELETED_YN = 'N' ORDER BY R.START_DATE DESC, R.REQ_ID DESC) "
            + "WHERE ROWNUM <= 100")
    List<DutyRequestVO> trips(@Param("loginId") String loginId);

    /** 그달 시간외근무 명령(신청) 시간 합계 (결재 중·승인) */
    @Select("SELECT NVL(SUM(R.HOURS), 0) FROM DUTY_REQUEST R JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID "
            + "WHERE R.LOGIN_ID = #{loginId} AND R.KIND = 'OVERTIME' AND D.STATUS IN ('PROG', 'DONE') AND D.DELETED_YN = 'N' "
            + "AND R.START_DATE >= #{from, jdbcType=DATE} AND R.START_DATE <= #{to, jdbcType=DATE}")
    double orderedHours(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /* ---------------------------------------------------------------- 근태 정정 */

    @Select("SELECT SEQ_ATTEND_FIX.NEXTVAL FROM DUAL")
    long nextFixId();

    @Insert("INSERT INTO ATTEND_FIX (FIX_ID, LOGIN_ID, WORK_DATE, IN_TIME, OUT_TIME, OUT_NEXT_DAY, REASON, DOC_ID, CREATED_AT) "
            + "VALUES (#{id}, #{loginId}, #{day, jdbcType=DATE}, #{inTime}, #{outTime}, #{nextDay}, #{reason}, #{docId}, #{at})")
    int insertFix(@Param("id") long id, @Param("loginId") String loginId, @Param("day") LocalDate day, @Param("inTime") String inTime,
                  @Param("outTime") String outTime, @Param("nextDay") String nextDay, @Param("reason") String reason,
                  @Param("docId") long docId, @Param("at") LocalDateTime at);

    /** 기간 안의 정정 신청과 결재 상태 (같은 날이 여러 건이면 나중 것이 이긴다) */
    @Select("SELECT F.FIX_ID AS \"id\", F.WORK_DATE AS \"day\", F.IN_TIME AS \"inTime\", F.OUT_TIME AS \"outTime\", "
            + "F.OUT_NEXT_DAY AS \"nextDay\", F.REASON AS \"reason\", F.DOC_ID AS \"docId\", D.STATUS AS \"status\" "
            + "FROM ATTEND_FIX F JOIN APPROVAL_DOC D ON D.DOC_ID = F.DOC_ID "
            + "WHERE F.LOGIN_ID = #{loginId} AND D.DELETED_YN = 'N' AND F.WORK_DATE >= #{from, jdbcType=DATE} "
            + "AND F.WORK_DATE <= #{to, jdbcType=DATE} ORDER BY F.WORK_DATE, F.FIX_ID")
    List<Map<String, Object>> fixes(@Param("loginId") String loginId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM ATTEND_FIX F JOIN APPROVAL_DOC D ON D.DOC_ID = F.DOC_ID "
            + "WHERE F.LOGIN_ID = #{loginId} AND F.WORK_DATE = #{day, jdbcType=DATE} AND D.STATUS = 'PROG' AND D.DELETED_YN = 'N'")
    int pendingFix(@Param("loginId") String loginId, @Param("day") LocalDate day);

    /* ---------------------------------------------------------------- 유연근무 시간 */

    @Select("SELECT LOGIN_ID AS \"loginId\", START_TIME AS \"start\", END_TIME AS \"end\" FROM WORK_SCHEDULE WHERE LOGIN_ID = #{loginId}")
    Map<String, Object> schedule(@Param("loginId") String loginId);

    @Select("SELECT LOGIN_ID AS \"loginId\", START_TIME AS \"start\", END_TIME AS \"end\", UPDATED_BY AS \"by\", UPDATED_AT AS \"at\" "
            + "FROM WORK_SCHEDULE ORDER BY LOGIN_ID")
    List<Map<String, Object>> schedules();

    @Delete("DELETE FROM WORK_SCHEDULE WHERE LOGIN_ID = #{loginId}")
    int deleteSchedule(@Param("loginId") String loginId);

    @Insert("INSERT INTO WORK_SCHEDULE (LOGIN_ID, START_TIME, END_TIME, UPDATED_BY, UPDATED_AT) "
            + "VALUES (#{loginId}, #{start}, #{end}, #{by}, #{at})")
    int insertSchedule(@Param("loginId") String loginId, @Param("start") String start, @Param("end") String end,
                       @Param("by") String by, @Param("at") LocalDateTime at);

    /* ---------------------------------------------------------------- 출장 복명·정산 */

    @Select("SELECT REQ_ID AS \"reqId\", REPORT AS \"report\", FARE AS \"fare\", LODGING_PAID AS \"lodgingPaid\", AMOUNT AS \"amount\", "
            + "DETAIL AS \"detail\", CREATED_BY AS \"by\", CREATED_AT AS \"at\" FROM TRIP_REPORT WHERE REQ_ID = #{id}")
    Map<String, Object> tripReport(@Param("id") long id);

    @Insert("INSERT INTO TRIP_REPORT (REQ_ID, REPORT, FARE, LODGING_PAID, AMOUNT, DETAIL, CREATED_BY, CREATED_AT) "
            + "VALUES (#{id}, #{report}, #{fare}, #{lodging}, #{amount}, #{detail}, #{by}, #{at})")
    int insertTripReport(@Param("id") long id, @Param("report") String report, @Param("fare") long fare, @Param("lodging") long lodging,
                         @Param("amount") long amount, @Param("detail") String detail, @Param("by") String by, @Param("at") LocalDateTime at);

    /* ---------------------------------------------------------------- 설정 */

    @Select("SELECT SKEY AS \"key\", SVALUE AS \"value\" FROM HR_PAY_SETTING")
    List<Map<String, Object>> settings();

    @Delete("DELETE FROM HR_PAY_SETTING WHERE SKEY = #{key}")
    int deleteSetting(@Param("key") String key);

    @Insert("INSERT INTO HR_PAY_SETTING (SKEY, SVALUE, UPDATED_BY, UPDATED_AT) VALUES (#{key}, #{value}, #{by}, #{at})")
    int insertSetting(@Param("key") String key, @Param("value") String value, @Param("by") String by, @Param("at") LocalDateTime at);

    /* ---------------------------------------------------------------- 부서 근태 */

    @Select("SELECT LOGIN_ID AS \"loginId\", IN_AT AS \"inAt\", OUT_AT AS \"outAt\" FROM ATTENDANCE WHERE WORK_DATE = #{day, jdbcType=DATE}")
    List<Map<String, Object>> attendanceOn(@Param("day") LocalDate day);

    @Select("SELECT R.LOGIN_ID AS \"loginId\", R.KIND AS \"kind\", R.HALF_DAY AS \"halfDay\", R.DESTINATION AS \"destination\" "
            + "FROM DUTY_REQUEST R JOIN APPROVAL_DOC D ON D.DOC_ID = R.DOC_ID "
            + "WHERE D.STATUS = 'DONE' AND D.DELETED_YN = 'N' AND R.KIND <> 'OVERTIME' "
            + "AND R.START_DATE <= #{day, jdbcType=DATE} AND R.END_DATE >= #{day, jdbcType=DATE}")
    List<Map<String, Object>> dutyOn(@Param("day") LocalDate day);
}
