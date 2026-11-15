package egovframework.haengjeongnuli.cmm.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HolidayMapper {
    List<Map<String, Object>> selectExtra();
    int insertExtra(@Param("day") LocalDate day, @Param("name") String name, @Param("by") String by);
    int deleteExtra(@Param("day") LocalDate day);
    int countExtra(@Param("day") LocalDate day);
}
