package egovframework.haengjeongnuli.cmm.mark;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 내려받은 파일의 추적 번호 기록 */
@Mapper
public interface DownloadMarkMapper {

    String COLS = "MARK_CODE AS \"code\", LOGIN_ID AS \"loginId\", USER_NAME AS \"userName\", DEPT_NAME AS \"dept\", CLIENT_IP AS \"ip\", "
            + "KIND AS \"kind\", REQ_PATH AS \"path\", FILE_NAME AS \"fileName\", REASON AS \"reason\", CREATED_AT AS \"at\"";

    @Select("SELECT SEQ_DOWNLOAD_MARK.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO DOWNLOAD_MARK (MARK_ID, MARK_CODE, LOGIN_ID, USER_NAME, DEPT_NAME, CLIENT_IP, KIND, REQ_PATH, FILE_NAME, REASON, CREATED_AT) "
            + "VALUES (#{id}, #{code}, #{loginId}, #{userName}, #{dept, jdbcType=VARCHAR}, #{ip, jdbcType=VARCHAR}, #{kind}, #{path, jdbcType=VARCHAR}, "
            + "#{fileName, jdbcType=VARCHAR}, #{reason, jdbcType=VARCHAR}, #{at})")
    int insert(@Param("id") long id, @Param("code") String code, @Param("loginId") String loginId, @Param("userName") String userName,
               @Param("dept") String dept, @Param("ip") String ip, @Param("kind") String kind, @Param("path") String path,
               @Param("fileName") String fileName, @Param("reason") String reason, @Param("at") LocalDateTime at);

    @Select("<script>SELECT " + COLS + " FROM DOWNLOAD_MARK WHERE MARK_CODE IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach> ORDER BY CREATED_AT</script>")
    List<Map<String, Object>> byCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT * FROM (SELECT " + COLS + " FROM DOWNLOAD_MARK "
            + "<if test='q != null'>WHERE LOGIN_ID LIKE #{q} ESCAPE '\\' OR USER_NAME LIKE #{q} ESCAPE '\\' OR FILE_NAME LIKE #{q} ESCAPE '\\' "
            + "OR MARK_CODE LIKE #{q} ESCAPE '\\' </if>ORDER BY MARK_ID DESC) WHERE ROWNUM &lt;= 100</script>")
    List<Map<String, Object>> recent(@Param("q") String q);
}
