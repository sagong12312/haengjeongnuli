package egovframework.haengjeongnuli.cmm.org;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrgMapper {

    @Select("SELECT SETTING_KEY AS \"k\", SETTING_VALUE AS \"v\" FROM ORG_SETTING")
    List<Map<String, String>> selectAll();

    @Update("UPDATE ORG_SETTING SET SETTING_VALUE = #{value}, UPDATED_BY = #{by}, UPDATED_AT = #{at} WHERE SETTING_KEY = #{key}")
    int update(@Param("key") String key, @Param("value") String value, @Param("by") String by, @Param("at") LocalDateTime at);

    @Insert("INSERT INTO ORG_SETTING (SETTING_KEY, SETTING_VALUE, UPDATED_BY, UPDATED_AT) VALUES (#{key}, #{value}, #{by}, #{at})")
    void insert(@Param("key") String key, @Param("value") String value, @Param("by") String by, @Param("at") LocalDateTime at);
}
