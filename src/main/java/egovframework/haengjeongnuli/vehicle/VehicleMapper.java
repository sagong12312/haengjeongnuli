package egovframework.haengjeongnuli.vehicle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VehicleMapper {

    String COLS = "VEHICLE_ID, PLATE_NO, MODEL, VEHICLE_KIND, FUEL, DEPT_NAME, ACQUIRED_ON, INSPECTION_DUE, INSURANCE_DUE, ODOMETER, STATUS, "
            + "CREATED_BY, CREATED_AT ";

    @Select("SELECT SEQ_VEHICLE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO VEHICLE (" + COLS + ") VALUES (#{vehicleId}, #{plateNo}, #{model}, #{vehicleKind}, #{fuel}, #{deptName}, #{acquiredOn}, "
            + "#{inspectionDue, jdbcType=DATE}, #{insuranceDue, jdbcType=DATE}, #{odometer}, 'USE', #{createdBy}, #{createdAt})")
    void insert(VehicleVO v);

    @Select("<script>SELECT " + COLS + "FROM VEHICLE <if test='!all'>WHERE STATUS = 'USE' </if>ORDER BY DEPT_NAME, PLATE_NO</script>")
    List<VehicleVO> selectAll(@Param("all") boolean includeRetired);

    @Select("SELECT " + COLS + "FROM VEHICLE WHERE VEHICLE_ID = #{id}")
    VehicleVO selectById(@Param("id") long id);

    @Select("SELECT " + COLS + "FROM VEHICLE WHERE VEHICLE_ID = #{id} FOR UPDATE")
    VehicleVO lock(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM VEHICLE WHERE PLATE_NO = #{plate}")
    int countPlate(@Param("plate") String plate);

    @Update("UPDATE VEHICLE SET ODOMETER = #{km} WHERE VEHICLE_ID = #{id} AND ODOMETER <= #{km}")
    int setOdometer(@Param("id") long id, @Param("km") int km);

    @Update("UPDATE VEHICLE SET INSPECTION_DUE = #{inspection, jdbcType=DATE}, INSURANCE_DUE = #{insurance, jdbcType=DATE} WHERE VEHICLE_ID = #{id}")
    int setDues(@Param("id") long id, @Param("inspection") LocalDate inspection, @Param("insurance") LocalDate insurance);

    @Update("UPDATE VEHICLE SET STATUS = 'RETIRED' WHERE VEHICLE_ID = #{id} AND STATUS = 'USE'")
    int retire(@Param("id") long id);

    // ===== 운행일지 =====

    @Select("SELECT SEQ_VEHICLE_LOG.NEXTVAL FROM DUAL")
    long nextLogId();

    @Insert("INSERT INTO VEHICLE_LOG (LOG_ID, VEHICLE_ID, DRIVER_ID, DRIVER_NAME, PURPOSE, DESTINATION, START_AT, END_AT, START_KM, END_KM, "
            + "CREATED_AT) VALUES (#{logId}, #{vehicleId}, #{driverId}, #{driverName}, #{purpose}, #{destination}, #{startAt}, #{endAt}, "
            + "#{startKm}, #{endKm}, #{createdAt})")
    void insertLog(VehicleLogVO l);

    @Select("SELECT L.LOG_ID, L.VEHICLE_ID, L.DRIVER_ID, L.DRIVER_NAME, L.PURPOSE, L.DESTINATION, L.START_AT, L.END_AT, L.START_KM, L.END_KM, "
            + "L.CREATED_AT, V.PLATE_NO FROM VEHICLE_LOG L JOIN VEHICLE V ON V.VEHICLE_ID = L.VEHICLE_ID "
            + "WHERE L.VEHICLE_ID = #{id} AND L.START_AT >= #{from} AND L.START_AT < #{to} ORDER BY L.START_AT DESC, L.LOG_ID DESC")
    List<VehicleLogVO> selectLogs(@Param("id") long vehicleId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 같은 차량의 운행 시간이 겹치는지 */
    @Select("SELECT COUNT(*) FROM VEHICLE_LOG WHERE VEHICLE_ID = #{id} AND START_AT < #{end} AND END_AT > #{start}")
    int countOverlap(@Param("id") long vehicleId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ===== 비용 =====

    @Select("SELECT SEQ_VEHICLE_COST.NEXTVAL FROM DUAL")
    long nextCostId();

    @Insert("INSERT INTO VEHICLE_COST (COST_ID, VEHICLE_ID, COST_KIND, DONE_ON, AMOUNT, LITERS, ODOMETER, VENDOR, NOTE, CREATED_BY, CREATED_AT) "
            + "VALUES (#{costId}, #{vehicleId}, #{costKind}, #{doneOn}, #{amount}, #{liters, jdbcType=NUMERIC}, #{odometer, jdbcType=NUMERIC}, "
            + "#{vendor}, #{note}, #{createdBy}, #{createdAt})")
    void insertCost(VehicleCostVO c);

    @Select("SELECT COST_ID, VEHICLE_ID, COST_KIND, DONE_ON, AMOUNT, LITERS, ODOMETER, VENDOR, NOTE, CREATED_BY, CREATED_AT FROM VEHICLE_COST "
            + "WHERE VEHICLE_ID = #{id} AND DONE_ON >= #{from} AND DONE_ON < #{to} ORDER BY DONE_ON DESC, COST_ID DESC")
    List<VehicleCostVO> selectCosts(@Param("id") long vehicleId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 차량별 한 달 합계: 운행 거리, 운행 횟수, 비용, 주유량 */
    @Select("SELECT V.VEHICLE_ID AS \"vehicleId\", V.PLATE_NO AS \"plateNo\", V.MODEL AS \"model\", V.DEPT_NAME AS \"deptName\", "
            + "(SELECT COUNT(*) FROM VEHICLE_LOG L WHERE L.VEHICLE_ID = V.VEHICLE_ID AND L.START_AT >= #{from} AND L.START_AT < #{to}) AS \"trips\", "
            + "(SELECT COALESCE(SUM(L.END_KM - L.START_KM), 0) FROM VEHICLE_LOG L WHERE L.VEHICLE_ID = V.VEHICLE_ID AND L.START_AT >= #{from} "
            + "AND L.START_AT < #{to}) AS \"km\", "
            + "(SELECT COALESCE(SUM(C.AMOUNT), 0) FROM VEHICLE_COST C WHERE C.VEHICLE_ID = V.VEHICLE_ID AND C.DONE_ON >= #{fromDate} "
            + "AND C.DONE_ON < #{toDate}) AS \"cost\", "
            + "(SELECT COALESCE(SUM(C.LITERS), 0) FROM VEHICLE_COST C WHERE C.VEHICLE_ID = V.VEHICLE_ID AND C.DONE_ON >= #{fromDate} "
            + "AND C.DONE_ON < #{toDate} AND C.COST_KIND = '주유') AS \"liters\" "
            + "FROM VEHICLE V WHERE V.STATUS = 'USE' ORDER BY V.DEPT_NAME, V.PLATE_NO")
    List<Map<String, Object>> monthly(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);
}
