package egovframework.haengjeongnuli.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 관용차량 */
public class VehicleVO {

    public static final List<String> KINDS = List.of("승용", "승합", "화물", "특수");
    public static final List<String> FUELS = List.of("휘발유", "경유", "LPG", "전기", "수소", "하이브리드");
    public static final String USE = "USE";
    public static final String RETIRED = "RETIRED";

    private Long vehicleId;
    private String plateNo;
    private String model;
    private String vehicleKind;
    private String fuel;
    private String deptName;
    private LocalDate acquiredOn;
    private LocalDate inspectionDue;
    private LocalDate insuranceDue;
    private int odometer;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    public boolean isInUse() { return USE.equals(status); }
    /** 정기검사·보험 만료가 30일 안에 오거나 지났는지 */
    public boolean isInspectionSoon() { return inspectionDue != null && !inspectionDue.isAfter(LocalDate.now().plusDays(30)); }
    public boolean isInsuranceSoon() { return insuranceDue != null && !insuranceDue.isAfter(LocalDate.now().plusDays(30)); }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVehicleKind() { return vehicleKind; }
    public void setVehicleKind(String vehicleKind) { this.vehicleKind = vehicleKind; }
    public String getFuel() { return fuel; }
    public void setFuel(String fuel) { this.fuel = fuel; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public LocalDate getAcquiredOn() { return acquiredOn; }
    public void setAcquiredOn(LocalDate acquiredOn) { this.acquiredOn = acquiredOn; }
    public LocalDate getInspectionDue() { return inspectionDue; }
    public void setInspectionDue(LocalDate inspectionDue) { this.inspectionDue = inspectionDue; }
    public LocalDate getInsuranceDue() { return insuranceDue; }
    public void setInsuranceDue(LocalDate insuranceDue) { this.insuranceDue = insuranceDue; }
    public int getOdometer() { return odometer; }
    public void setOdometer(int odometer) { this.odometer = odometer; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
