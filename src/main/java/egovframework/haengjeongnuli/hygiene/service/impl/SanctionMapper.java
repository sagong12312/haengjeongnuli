package egovframework.haengjeongnuli.hygiene.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.hygiene.service.SanctionVO;

@Mapper
public interface SanctionMapper {

    String COLS = "S.SANCTION_ID, S.SANCTION_NO, S.BIZ_ID, S.LAW_NAME, S.KIND, S.VIOLATION, S.BASIS, S.DECIDED_ON, S.START_DATE, S.END_DATE, "
            + "S.AMOUNT, S.CHECK_ID, S.PUBLIC_YN, S.STATUS, S.CANCEL_REASON, S.CREATED_BY, S.CREATED_NAME, S.CREATED_AT, "
            + "B.BIZ_NAME, B.BIZ_TYPE, B.ADDRESS, B.OWNER_NAME ";
    String FROM = "FROM HYGIENE_SANCTION S JOIN FOOD_BUSINESS B ON B.BIZ_ID = S.BIZ_ID ";

    @Select("SELECT SEQ_HYGIENE_SANCTION.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO HYGIENE_SANCTION (SANCTION_ID, SANCTION_NO, BIZ_ID, LAW_NAME, KIND, VIOLATION, BASIS, DECIDED_ON, START_DATE, END_DATE, "
            + "AMOUNT, CHECK_ID, PUBLIC_YN, STATUS, CREATED_BY, CREATED_NAME, CREATED_AT) VALUES (#{sanctionId}, #{sanctionNo}, #{bizId}, "
            + "#{lawName}, #{kind}, #{violation}, #{basis}, #{decidedOn}, #{startDate, jdbcType=DATE}, #{endDate, jdbcType=DATE}, "
            + "#{amount, jdbcType=NUMERIC}, #{checkId, jdbcType=NUMERIC}, #{publicYn}, 'ACTIVE', #{createdBy}, #{createdName}, #{createdAt})")
    void insert(SanctionVO s);

    @Select("SELECT " + COLS + FROM + "WHERE S.BIZ_ID = #{bizId} ORDER BY S.DECIDED_ON DESC, S.SANCTION_ID DESC")
    List<SanctionVO> selectByBiz(@Param("bizId") long bizId);

    @Select("SELECT " + COLS + FROM + "WHERE S.SANCTION_ID = #{id}")
    SanctionVO selectById(@Param("id") long id);

    @Select("<script>SELECT " + COLS + FROM + "WHERE S.DECIDED_ON &gt;= #{from} AND S.DECIDED_ON &lt; #{to} "
            + "<if test='law != null'>AND S.LAW_NAME = #{law} </if>ORDER BY S.DECIDED_ON DESC, S.SANCTION_ID DESC</script>")
    List<SanctionVO> selectRange(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("law") String law);

    /** 이 영업소에 오늘 진행 중인 영업정지가 있는지 */
    @Select("SELECT COUNT(*) FROM HYGIENE_SANCTION WHERE BIZ_ID = #{bizId} AND STATUS = 'ACTIVE' "
            + "AND KIND IN ('영업정지', '일부 시설 사용중지') AND START_DATE <= #{on} AND END_DATE >= #{on}")
    int countInForce(@Param("bizId") long bizId, @Param("on") LocalDate on);

    @Update("UPDATE HYGIENE_SANCTION SET STATUS = 'CANCELED', CANCEL_REASON = #{reason}, PUBLIC_YN = 'N' "
            + "WHERE SANCTION_ID = #{id} AND STATUS = 'ACTIVE'")
    int cancel(@Param("id") long id, @Param("reason") String reason);

    @Update("UPDATE HYGIENE_SANCTION SET PUBLIC_YN = #{yn} WHERE SANCTION_ID = #{id} AND STATUS = 'ACTIVE'")
    int setPublic(@Param("id") long id, @Param("yn") String yn);

    // ===== 공개 (전자민원) =====

    String PUBLIC_WHERE = "WHERE S.STATUS = 'ACTIVE' AND S.PUBLIC_YN = 'Y' AND S.DECIDED_ON &gt;= #{since} "
            + "<if test='q != null'>AND (B.BIZ_NAME LIKE #{q} ESCAPE '\\' OR B.ADDRESS LIKE #{q} ESCAPE '\\') </if>";

    @Select("<script>SELECT COUNT(*) " + FROM + PUBLIC_WHERE + "</script>")
    int countPublic(@Param("since") LocalDate since, @Param("q") String likeQuery);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT " + COLS + FROM + PUBLIC_WHERE
            + "ORDER BY S.DECIDED_ON DESC, S.SANCTION_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<SanctionVO> selectPublic(@Param("since") LocalDate since, @Param("q") String likeQuery, @Param("startRow") int startRow,
                                  @Param("endRow") int endRow);
}
