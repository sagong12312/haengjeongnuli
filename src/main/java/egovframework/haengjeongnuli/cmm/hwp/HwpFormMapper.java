package egovframework.haengjeongnuli.cmm.hwp;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HwpFormMapper {

    String COLS = "FORM_ID, NAME, CATEGORY, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, FIELDS, FILLABLE_YN, USE_YN, UPLOADED_BY, UPLOADED_AT ";

    @Select("SELECT SEQ_HWP_FORM.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO HWP_FORM (" + COLS + ") VALUES (#{formId}, #{name}, #{category}, #{originalName}, #{storedName}, #{fileSize}, "
            + "#{fields}, #{fillableYn}, 'Y', #{uploadedBy}, #{uploadedAt})")
    void insert(HwpFormVO f);

    @Select("<script>SELECT " + COLS + "FROM HWP_FORM <if test='onlyInUse'>WHERE USE_YN = 'Y' </if>ORDER BY CATEGORY, NAME, FORM_ID</script>")
    List<HwpFormVO> selectAll(@Param("onlyInUse") boolean onlyInUse);

    @Select("SELECT " + COLS + "FROM HWP_FORM WHERE FORM_ID = #{id}")
    HwpFormVO selectById(@Param("id") long id);

    @Update("UPDATE HWP_FORM SET USE_YN = #{useYn} WHERE FORM_ID = #{id}")
    int setUse(@Param("id") long id, @Param("useYn") String useYn);
}
