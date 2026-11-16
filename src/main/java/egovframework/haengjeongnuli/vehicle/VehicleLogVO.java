package egovframework.haengjeongnuli.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 차량 운행일지 한 줄 */
public class VehicleLogVO {

    private Long logId;
    private Long vehicleId;
    private String driverId;
    private String driverName;
    private String purpose;
    private String destination;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private int startKm;
    private int endKm;
    private LocalDateTime createdAt;
    private String plateNo;

    public int getDistance() { return endKm - startKm; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public int getStartKm() { return startKm; }
    public void setStartKm(int startKm) { this.startKm = startKm; }
    public int getEndKm() { return endKm; }
    public void setEndKm(int endKm) { this.endKm = endKm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
}
