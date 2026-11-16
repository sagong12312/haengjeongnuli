package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CdMapper {

    String COLS = "MEMBER_ID, NAME, BIRTH_DATE, ADDRESS, PHONE, UNIT_NAME, JOINED_ON, STATUS, RELEASED_ON, RELEASE_REASON, PURGE_AFTER, "
            + "CREATED_BY, CREATED_AT ";

    @Select("SELECT SEQ_CD_MEMBER.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO CD_MEMBER (" + COLS + ") VALUES (#{memberId}, #{name}, #{birthDate}, #{address}, #{phone}, #{unitName}, #{joinedOn}, "
            + "'ACTIVE', NULL, NULL, NULL, #{createdBy}, #{createdAt})")
    void insert(CdMemberVO m);

    @Select("<script>SELECT " + COLS + "FROM CD_MEMBER WHERE STATUS = #{status} <if test='unit != null'>AND UNIT_NAME = #{unit} </if>"
            + "<if test='q != null'>AND NAME LIKE #{q} ESCAPE '\\' </if>ORDER BY UNIT_NAME, NAME</script>")
    List<CdMemberVO> selectList(@Param("status") String status, @Param("unit") String unit, @Param("q") String likeQuery);

    @Select("SELECT " + COLS + "FROM CD_MEMBER WHERE MEMBER_ID = #{id}")
    CdMemberVO selectById(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM CD_MEMBER WHERE NAME = #{name} AND BIRTH_DATE = #{birth} AND STATUS = 'ACTIVE'")
    int countSame(@Param("name") String name, @Param("birth") LocalDate birth);

    @Select("SELECT DISTINCT UNIT_NAME FROM CD_MEMBER WHERE STATUS = 'ACTIVE' ORDER BY UNIT_NAME")
    List<String> units();

    /** 올해 편성 나이를 넘는 대원 (해제 대상) */
    @Select("SELECT " + COLS + "FROM CD_MEMBER WHERE STATUS = 'ACTIVE' AND BIRTH_DATE < #{bornBefore} ORDER BY BIRTH_DATE")
    List<CdMemberVO> selectOverAge(@Param("bornBefore") LocalDate bornBefore);

    @Update("UPDATE CD_MEMBER SET STATUS = 'RELEASED', RELEASED_ON = #{on}, RELEASE_REASON = #{reason}, PURGE_AFTER = #{purge} "
            + "WHERE MEMBER_ID = #{id} AND STATUS = 'ACTIVE'")
    int release(@Param("id") long id, @Param("on") LocalDate on, @Param("reason") String reason, @Param("purge") LocalDate purge);

    @Update("UPDATE CD_MEMBER SET ADDRESS = #{address}, PHONE = #{phone}, UNIT_NAME = #{unit} WHERE MEMBER_ID = #{id} AND STATUS = 'ACTIVE'")
    int update(@Param("id") long id, @Param("address") String address, @Param("phone") String phone, @Param("unit") String unit);

    // ===== 교육 =====

    @Select("SELECT SEQ_CD_TRAINING.NEXTVAL FROM DUAL")
    long nextTrainingId();

    @Insert("INSERT INTO CD_TRAINING (TRAINING_ID, TITLE, HELD_ON, PLACE, HOURS, CREATED_BY, CREATED_AT) VALUES (#{trainingId}, #{title}, "
            + "#{heldOn}, #{place}, #{hours}, #{createdBy}, #{createdAt})")
    void insertTraining(CdTrainingVO t);

    @Select("SELECT T.TRAINING_ID, T.TITLE, T.HELD_ON, T.PLACE, T.HOURS, T.CREATED_BY, T.CREATED_AT, "
            + "(SELECT COUNT(*) FROM CD_ATTEND A WHERE A.TRAINING_ID = T.TRAINING_ID) AS INVITED, "
            + "(SELECT COUNT(*) FROM CD_ATTEND A WHERE A.TRAINING_ID = T.TRAINING_ID AND A.RESULT = '참석') AS ATTENDED "
            + "FROM CD_TRAINING T WHERE T.HELD_ON >= #{from} AND T.HELD_ON < #{to} ORDER BY T.HELD_ON DESC")
    List<CdTrainingVO> selectTrainings(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT TRAINING_ID, TITLE, HELD_ON, PLACE, HOURS, CREATED_BY, CREATED_AT FROM CD_TRAINING WHERE TRAINING_ID = #{id}")
    CdTrainingVO selectTraining(@Param("id") long id);

    /** 편성된 대원을 교육 대상자로 넣는다 (부대 지정 시 그 부대만, 이미 있는 사람은 건너뜀) */
    @Insert("<script>INSERT INTO CD_ATTEND (TRAINING_ID, MEMBER_ID, NOTIFIED_ON, RESULT) SELECT #{tid}, M.MEMBER_ID, #{on}, '통지' FROM CD_MEMBER M "
            + "WHERE M.STATUS = 'ACTIVE' <if test='unit != null'>AND M.UNIT_NAME = #{unit} </if>"
            + "AND NOT EXISTS (SELECT 1 FROM CD_ATTEND A WHERE A.TRAINING_ID = #{tid} AND A.MEMBER_ID = M.MEMBER_ID)</script>")
    int invite(@Param("tid") long trainingId, @Param("unit") String unit, @Param("on") LocalDate notifiedOn);

    @Select("SELECT A.TRAINING_ID, A.MEMBER_ID, A.NOTIFIED_ON, A.RESULT, A.NOTE, M.NAME, M.UNIT_NAME, M.PHONE FROM CD_ATTEND A "
            + "JOIN CD_MEMBER M ON M.MEMBER_ID = A.MEMBER_ID WHERE A.TRAINING_ID = #{id} ORDER BY M.UNIT_NAME, M.NAME")
    List<CdAttendVO> selectAttends(@Param("id") long trainingId);

    @Update("UPDATE CD_ATTEND SET RESULT = #{result}, NOTE = #{note} WHERE TRAINING_ID = #{tid} AND MEMBER_ID = #{mid}")
    int setResult(@Param("tid") long trainingId, @Param("mid") long memberId, @Param("result") String result, @Param("note") String note);

    @Select("SELECT T.TITLE AS \"title\", T.HELD_ON AS \"heldOn\", A.RESULT AS \"result\" FROM CD_ATTEND A JOIN CD_TRAINING T "
            + "ON T.TRAINING_ID = A.TRAINING_ID WHERE A.MEMBER_ID = #{id} ORDER BY T.HELD_ON DESC")
    List<Map<String, Object>> selectHistory(@Param("id") long memberId);

    // ===== 파기 =====

    @Delete("DELETE FROM CD_ATTEND WHERE MEMBER_ID IN (SELECT MEMBER_ID FROM CD_MEMBER WHERE STATUS = 'RELEASED' AND PURGE_AFTER < #{today})")
    int purgeAttends(@Param("today") LocalDate today);

    @Delete("DELETE FROM CD_MEMBER WHERE STATUS = 'RELEASED' AND PURGE_AFTER < #{today}")
    int purgeMembers(@Param("today") LocalDate today);
}
