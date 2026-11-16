package egovframework.haengjeongnuli.foreign;

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
public interface ForeignMapper {

    String COLS = "R.RES_ID, R.NAME, R.NATIONALITY, R.BIRTH_DATE, R.ADDRESS, R.PHONE, R.LANGUAGE, R.STAY_STATUS, R.CONSENT_ON, R.STATUS, "
            + "R.ENDED_ON, R.END_REASON, R.PURGE_AFTER, R.CREATED_BY, R.CREATED_AT, "
            + "(SELECT COUNT(*) FROM FOREIGN_SUPPORT S WHERE S.RES_ID = R.RES_ID) AS SUPPORT_COUNT ";

    @Select("SELECT SEQ_FOREIGN_RESIDENT.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO FOREIGN_RESIDENT (RES_ID, NAME, NATIONALITY, BIRTH_DATE, ADDRESS, PHONE, LANGUAGE, STAY_STATUS, CONSENT_ON, STATUS, "
            + "CREATED_BY, CREATED_AT) VALUES (#{resId}, #{name}, #{nationality}, #{birthDate}, #{address}, #{phone}, #{language}, #{stayStatus}, "
            + "#{consentOn}, 'ACTIVE', #{createdBy}, #{createdAt})")
    void insert(ForeignResidentVO r);

    @Select("<script>SELECT " + COLS + "FROM FOREIGN_RESIDENT R WHERE R.STATUS = #{status} "
            + "<if test='nat != null'>AND R.NATIONALITY = #{nat} </if><if test='q != null'>AND R.NAME LIKE #{q} ESCAPE '\\' </if>"
            + "ORDER BY R.RES_ID DESC</script>")
    List<ForeignResidentVO> selectList(@Param("status") String status, @Param("nat") String nationality, @Param("q") String likeQuery);

    @Select("SELECT " + COLS + "FROM FOREIGN_RESIDENT R WHERE R.RES_ID = #{id}")
    ForeignResidentVO selectById(@Param("id") long id);

    @Update("UPDATE FOREIGN_RESIDENT SET STATUS = 'ENDED', ENDED_ON = #{on}, END_REASON = #{reason}, PURGE_AFTER = #{purge} "
            + "WHERE RES_ID = #{id} AND STATUS = 'ACTIVE'")
    int end(@Param("id") long id, @Param("on") LocalDate on, @Param("reason") String reason, @Param("purge") LocalDate purge);

    @Select("SELECT SEQ_FOREIGN_SUPPORT.NEXTVAL FROM DUAL")
    long nextSupportId();

    @Insert("INSERT INTO FOREIGN_SUPPORT (SUPPORT_ID, RES_ID, SUPPORT_ON, KIND, CONTENT, STAFF_NAME, CREATED_AT) VALUES (#{supportId}, #{resId}, "
            + "#{supportOn}, #{kind}, #{content}, #{staffName}, #{createdAt})")
    void insertSupport(ForeignSupportVO s);

    @Select("SELECT SUPPORT_ID, RES_ID, SUPPORT_ON, KIND, CONTENT, STAFF_NAME, CREATED_AT FROM FOREIGN_SUPPORT WHERE RES_ID = #{id} "
            + "ORDER BY SUPPORT_ON DESC, SUPPORT_ID DESC")
    List<ForeignSupportVO> selectSupports(@Param("id") long resId);

    /** 통계 (개인정보 없이 숫자만): 국적별 지원 중인 주민 수, 올해 지원 건수 */
    @Select("SELECT R.NATIONALITY AS \"nationality\", COUNT(DISTINCT R.RES_ID) AS \"people\", "
            + "(SELECT COUNT(*) FROM FOREIGN_SUPPORT S JOIN FOREIGN_RESIDENT R2 ON R2.RES_ID = S.RES_ID WHERE R2.NATIONALITY = R.NATIONALITY "
            + "AND S.SUPPORT_ON >= #{yearStart}) AS \"supports\" FROM FOREIGN_RESIDENT R WHERE R.STATUS = 'ACTIVE' "
            + "GROUP BY R.NATIONALITY ORDER BY COUNT(DISTINCT R.RES_ID) DESC")
    List<Map<String, Object>> stats(@Param("yearStart") LocalDate yearStart);

    @Select("SELECT DISTINCT NATIONALITY FROM FOREIGN_RESIDENT ORDER BY NATIONALITY")
    List<String> nationalities();

    @Delete("DELETE FROM FOREIGN_SUPPORT WHERE RES_ID IN (SELECT RES_ID FROM FOREIGN_RESIDENT WHERE STATUS = 'ENDED' AND PURGE_AFTER < #{today})")
    int purgeSupports(@Param("today") LocalDate today);

    @Delete("DELETE FROM FOREIGN_RESIDENT WHERE STATUS = 'ENDED' AND PURGE_AFTER < #{today}")
    int purgeResidents(@Param("today") LocalDate today);
}
