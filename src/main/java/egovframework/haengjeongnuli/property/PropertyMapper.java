package egovframework.haengjeongnuli.property;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PropertyMapper {

    String COLS = "P.PROP_ID, P.PROP_NO, P.PROP_KIND, P.PROP_CLASS, P.NAME, P.LOCATION, P.AREA, P.APPRAISED, P.ACQUIRED_ON, P.DEPT_NAME, "
            + "P.USE_PURPOSE, P.STATUS, P.DISPOSED_ON, P.DISPOSE_REASON, P.CREATED_BY, P.CREATED_AT, "
            + "(SELECT COUNT(*) FROM PROPERTY_USE U WHERE U.PROP_ID = P.PROP_ID AND U.STATUS = 'ACTIVE') AS ACTIVE_USES, "
            + "(SELECT MIN(U.END_DATE) FROM PROPERTY_USE U WHERE U.PROP_ID = P.PROP_ID AND U.STATUS = 'ACTIVE') AS NEXT_END ";

    @Select("SELECT SEQ_PUBLIC_PROPERTY.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO PUBLIC_PROPERTY (PROP_ID, PROP_NO, PROP_KIND, PROP_CLASS, NAME, LOCATION, AREA, APPRAISED, ACQUIRED_ON, DEPT_NAME, "
            + "USE_PURPOSE, STATUS, CREATED_BY, CREATED_AT) VALUES (#{propId}, #{propNo}, #{propKind}, #{propClass}, #{name}, #{location}, "
            + "#{area, jdbcType=NUMERIC}, #{appraised}, #{acquiredOn}, #{deptName}, #{usePurpose}, 'HELD', #{createdBy}, #{createdAt})")
    void insert(PropertyVO p);

    @Select("<script>SELECT " + COLS + "FROM PUBLIC_PROPERTY P WHERE 1 = 1 "
            + "<if test='cls != null'>AND P.PROP_CLASS = #{cls} </if>"
            + "<if test='!all'>AND P.STATUS = 'HELD' </if>"
            + "<if test='q != null'>AND (P.NAME LIKE #{q} ESCAPE '\\' OR P.LOCATION LIKE #{q} ESCAPE '\\' OR P.PROP_NO LIKE #{q} ESCAPE '\\') </if>"
            + "ORDER BY P.PROP_ID DESC</script>")
    List<PropertyVO> selectList(@Param("cls") String cls, @Param("q") String likeQuery, @Param("all") boolean includeDisposed);

    @Select("SELECT " + COLS + "FROM PUBLIC_PROPERTY P WHERE P.PROP_ID = #{id}")
    PropertyVO selectById(@Param("id") long id);

    @Update("UPDATE PUBLIC_PROPERTY SET APPRAISED = #{appraised}, USE_PURPOSE = #{purpose} WHERE PROP_ID = #{id} AND STATUS = 'HELD'")
    int updateValue(@Param("id") long id, @Param("appraised") long appraised, @Param("purpose") String purpose);

    @Update("UPDATE PUBLIC_PROPERTY SET STATUS = 'DISPOSED', DISPOSED_ON = #{on}, DISPOSE_REASON = #{reason} WHERE PROP_ID = #{id} AND STATUS = 'HELD'")
    int dispose(@Param("id") long id, @Param("on") LocalDate on, @Param("reason") String reason);

    // ===== 사용허가·대부 =====

    String U_COLS = "U.USE_ID, U.PROP_ID, U.USE_TYPE, U.USER_NAME, U.PURPOSE, U.START_DATE, U.END_DATE, U.ANNUAL_FEE, U.FEE_NOTE, U.STATUS, "
            + "U.END_NOTE, U.CREATED_BY, U.CREATED_AT, P.PROP_NO, P.NAME AS PROP_NAME, P.DEPT_NAME ";

    @Select("SELECT SEQ_PROPERTY_USE.NEXTVAL FROM DUAL")
    long nextUseId();

    @Insert("INSERT INTO PROPERTY_USE (USE_ID, PROP_ID, USE_TYPE, USER_NAME, PURPOSE, START_DATE, END_DATE, ANNUAL_FEE, FEE_NOTE, STATUS, "
            + "CREATED_BY, CREATED_AT) VALUES (#{useId}, #{propId}, #{useType}, #{userName}, #{purpose}, #{startDate}, #{endDate}, #{annualFee}, "
            + "#{feeNote}, 'ACTIVE', #{createdBy}, #{createdAt})")
    void insertUse(PropertyUseVO u);

    @Select("SELECT " + U_COLS + "FROM PROPERTY_USE U JOIN PUBLIC_PROPERTY P ON P.PROP_ID = U.PROP_ID WHERE U.PROP_ID = #{id} "
            + "ORDER BY U.START_DATE DESC, U.USE_ID DESC")
    List<PropertyUseVO> selectUses(@Param("id") long propId);

    @Select("SELECT " + U_COLS + "FROM PROPERTY_USE U JOIN PUBLIC_PROPERTY P ON P.PROP_ID = U.PROP_ID WHERE U.USE_ID = #{id}")
    PropertyUseVO selectUse(@Param("id") long useId);

    /** 기간이 겹치는 진행 중인 허가·대부 */
    @Select("SELECT COUNT(*) FROM PROPERTY_USE WHERE PROP_ID = #{id} AND STATUS = 'ACTIVE' AND START_DATE <= #{end} AND END_DATE >= #{start}")
    int countOverlap(@Param("id") long propId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM PROPERTY_USE WHERE PROP_ID = #{id} AND STATUS = 'ACTIVE'")
    int countActive(@Param("id") long propId);

    @Update("UPDATE PROPERTY_USE SET STATUS = 'ENDED', END_NOTE = #{note} WHERE USE_ID = #{id} AND STATUS = 'ACTIVE'")
    int endUse(@Param("id") long useId, @Param("note") String note);

    /** 끝나는 날이 가까운(또는 지난) 진행 중 허가·대부 */
    @Select("SELECT " + U_COLS + "FROM PROPERTY_USE U JOIN PUBLIC_PROPERTY P ON P.PROP_ID = U.PROP_ID "
            + "WHERE U.STATUS = 'ACTIVE' AND U.END_DATE <= #{until} ORDER BY U.END_DATE")
    List<PropertyUseVO> selectEnding(@Param("until") LocalDate until);
}
