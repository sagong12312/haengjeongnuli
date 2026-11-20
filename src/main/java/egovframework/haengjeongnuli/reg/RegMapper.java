package egovframework.haengjeongnuli.reg;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 대장 */
@Mapper
public interface RegMapper {

    /** 볼 수 있는 행: 관리자는 모두, 아니면 기관 전체·같은 부서·본인 것 */
    String VISIBLE = "<if test='!v.admin'>AND (SCOPE = 'ALL' OR (SCOPE = 'DEPT' AND DEPT_NAME = #{v.dept}) OR (SCOPE = 'SELF' AND OWNER_ID = #{v.me})) </if>";
    String COLS = "ENTRY_ID AS \"id\", REG_CODE AS \"code\", SCOPE AS \"scope\", DEPT_NAME AS \"dept\", OWNER_ID AS \"ownerId\", OWNER_NAME AS \"ownerName\", "
            + "TITLE AS \"title\", STATUS AS \"status\", CLOSED_YN AS \"closed\", DUE_ON AS \"due\", AMOUNT AS \"amount\", DATA AS \"data\", "
            + "CREATED_AT AS \"createdAt\", UPDATED_AT AS \"updatedAt\", UPDATED_BY AS \"updatedBy\"";
    String FILTER = "WHERE DELETED_YN = 'N' AND REG_CODE = #{code} " + VISIBLE
            + "<if test='q != null'>AND SEARCH_TEXT LIKE #{q} ESCAPE '\\' </if>"
            + "<if test='status != null'>AND STATUS = #{status} </if>"
            + "<if test='open'>AND CLOSED_YN = 'N' </if>"
            + "<if test='late'>AND CLOSED_YN = 'N' AND DUE_ON &lt; #{today, jdbcType=DATE} </if>"
            + "<if test='year != null'>AND CREATED_AT &gt;= #{yearFrom} AND CREATED_AT &lt; #{yearTo} </if>";

    @Select("SELECT SEQ_REG.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO REG_ENTRY (ENTRY_ID, REG_CODE, SCOPE, DEPT_NAME, OWNER_ID, OWNER_NAME, TITLE, STATUS, CLOSED_YN, DUE_ON, AMOUNT, SEARCH_TEXT, DATA, "
            + "CREATED_AT, UPDATED_AT, UPDATED_BY, DELETED_YN) VALUES (#{id}, #{code}, #{scope}, #{dept, jdbcType=VARCHAR}, #{ownerId}, #{ownerName}, #{title}, "
            + "#{status}, #{closed}, #{due, jdbcType=DATE}, #{amount, jdbcType=NUMERIC}, #{search, jdbcType=VARCHAR}, #{data, jdbcType=CLOB}, #{at}, #{at}, #{ownerName}, 'N')")
    int insert(@Param("id") long id, @Param("code") String code, @Param("scope") String scope, @Param("dept") String dept,
               @Param("ownerId") String ownerId, @Param("ownerName") String ownerName, @Param("title") String title, @Param("status") String status,
               @Param("closed") String closed, @Param("due") LocalDate due, @Param("amount") Long amount, @Param("search") String search,
               @Param("data") String data, @Param("at") LocalDateTime at);

    @Update("UPDATE REG_ENTRY SET TITLE = #{title}, STATUS = #{status}, CLOSED_YN = #{closed}, DUE_ON = #{due, jdbcType=DATE}, "
            + "AMOUNT = #{amount, jdbcType=NUMERIC}, SEARCH_TEXT = #{search, jdbcType=VARCHAR}, DATA = #{data, jdbcType=CLOB}, UPDATED_AT = #{at}, "
            + "UPDATED_BY = #{by} WHERE ENTRY_ID = #{id} AND DELETED_YN = 'N'")
    int update(@Param("id") long id, @Param("title") String title, @Param("status") String status, @Param("closed") String closed,
               @Param("due") LocalDate due, @Param("amount") Long amount, @Param("search") String search, @Param("data") String data,
               @Param("at") LocalDateTime at, @Param("by") String by);

    @Update("UPDATE REG_ENTRY SET STATUS = #{status}, CLOSED_YN = #{closed}, UPDATED_AT = #{at}, UPDATED_BY = #{by} WHERE ENTRY_ID = #{id} AND DELETED_YN = 'N'")
    int setStatus(@Param("id") long id, @Param("status") String status, @Param("closed") String closed, @Param("at") LocalDateTime at, @Param("by") String by);

    @Update("UPDATE REG_ENTRY SET DELETED_YN = 'Y', UPDATED_AT = #{at}, UPDATED_BY = #{by} WHERE ENTRY_ID = #{id}")
    int delete(@Param("id") long id, @Param("at") LocalDateTime at, @Param("by") String by);

    @Update("UPDATE REG_ENTRY SET SCOPE = #{scope} WHERE REG_CODE = #{code} AND SCOPE <> #{scope}")
    int syncScope(@Param("code") String code, @Param("scope") String scope);

    @Select("SELECT " + COLS + " FROM REG_ENTRY WHERE ENTRY_ID = #{id} AND DELETED_YN = 'N'")
    Map<String, Object> get(@Param("id") long id);

    @Select("<script>SELECT COUNT(*) FROM REG_ENTRY " + FILTER + "</script>")
    int count(@Param("code") String code, @Param("v") RegService.Viewer v, @Param("q") String q, @Param("status") String status,
              @Param("open") boolean open, @Param("late") boolean late, @Param("today") LocalDate today, @Param("year") Integer year,
              @Param("yearFrom") LocalDateTime yearFrom, @Param("yearTo") LocalDateTime yearTo);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM RN FROM (SELECT " + COLS + " FROM REG_ENTRY " + FILTER
            + "ORDER BY CLOSED_YN, CASE WHEN DUE_ON IS NULL THEN 1 ELSE 0 END, DUE_ON, ENTRY_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<Map<String, Object>> list(@Param("code") String code, @Param("v") RegService.Viewer v, @Param("q") String q, @Param("status") String status,
                                   @Param("open") boolean open, @Param("late") boolean late, @Param("today") LocalDate today, @Param("year") Integer year,
                                   @Param("yearFrom") LocalDateTime yearFrom, @Param("yearTo") LocalDateTime yearTo,
                                   @Param("startRow") int startRow, @Param("endRow") int endRow);

    @Select("<script>SELECT STATUS AS \"status\", COUNT(*) AS \"n\", SUM(AMOUNT) AS \"amount\" FROM REG_ENTRY WHERE DELETED_YN = 'N' AND REG_CODE = #{code} "
            + VISIBLE + "<if test='year != null'>AND CREATED_AT &gt;= #{yearFrom} AND CREATED_AT &lt; #{yearTo} </if>GROUP BY STATUS</script>")
    List<Map<String, Object>> statusCounts(@Param("code") String code, @Param("v") RegService.Viewer v, @Param("year") Integer year,
                                           @Param("yearFrom") LocalDateTime yearFrom, @Param("yearTo") LocalDateTime yearTo);

    @Select("<script>SELECT REG_CODE AS \"code\", COUNT(*) AS \"n\", SUM(CASE WHEN CLOSED_YN = 'N' THEN 1 ELSE 0 END) AS \"open\", "
            + "SUM(CASE WHEN CLOSED_YN = 'N' AND DUE_ON &lt; #{today, jdbcType=DATE} THEN 1 ELSE 0 END) AS \"late\" "
            + "FROM REG_ENTRY WHERE DELETED_YN = 'N' " + VISIBLE + "GROUP BY REG_CODE</script>")
    List<Map<String, Object>> countsByCode(@Param("v") RegService.Viewer v, @Param("today") LocalDate today);

    @Select("<script>SELECT * FROM (SELECT " + COLS + " FROM REG_ENTRY WHERE DELETED_YN = 'N' AND CLOSED_YN = 'N' AND DUE_ON IS NOT NULL "
            + "AND DUE_ON &lt;= #{until, jdbcType=DATE} " + VISIBLE + "ORDER BY DUE_ON, ENTRY_ID) WHERE ROWNUM &lt;= 50</script>")
    List<Map<String, Object>> dueSoon(@Param("v") RegService.Viewer v, @Param("until") LocalDate until);

    @Select("<script>SELECT * FROM (SELECT " + COLS + " FROM REG_ENTRY WHERE DELETED_YN = 'N' AND SEARCH_TEXT LIKE #{q} ESCAPE '\\' " + VISIBLE
            + "ORDER BY ENTRY_ID DESC) WHERE ROWNUM &lt;= 50</script>")
    List<Map<String, Object>> searchAll(@Param("v") RegService.Viewer v, @Param("q") String q);

    @Insert("INSERT INTO REG_HISTORY (HIST_ID, ENTRY_ID, ACTOR_ID, ACTOR_NAME, ACTION, NOTE, AT_TIME) "
            + "VALUES (#{id}, #{entryId}, #{actorId}, #{actorName}, #{action}, #{note, jdbcType=VARCHAR}, #{at})")
    int insertHistory(@Param("id") long id, @Param("entryId") long entryId, @Param("actorId") String actorId, @Param("actorName") String actorName,
                      @Param("action") String action, @Param("note") String note, @Param("at") LocalDateTime at);

    @Select("SELECT ACTOR_NAME AS \"actor\", ACTION AS \"action\", NOTE AS \"note\", AT_TIME AS \"at\" FROM REG_HISTORY WHERE ENTRY_ID = #{id} "
            + "ORDER BY HIST_ID DESC")
    List<Map<String, Object>> history(@Param("id") long id);
}
