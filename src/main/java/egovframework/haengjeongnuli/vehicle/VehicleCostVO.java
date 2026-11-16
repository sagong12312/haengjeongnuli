package egovframework.haengjeongnuli.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 차량 주유·정비·검사·보험 등 비용 한 줄 */
public class VehicleCostVO {

    public static final List<String> KINDS = List.of("주유", "충전", "정비", "검사", "보험", "기타");

    private Long costId;
    private Long vehicleId;
    private String costKind;
    private LocalDate doneOn;
    private long amount;
    private BigDecimal liters;
    private Integer odometer;
    private String vendor;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getCostId() { return costId; }
    public void setCostId(Long costId) { this.costId = costId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getCostKind() { return costKind; }
    public void setCostKind(String costKind) { this.costKind = costKind; }
    public LocalDate getDoneOn() { return doneOn; }
    public void setDoneOn(LocalDate doneOn) { this.doneOn = doneOn; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public BigDecimal getLiters() { return liters; }
    public void setLiters(BigDecimal liters) { this.liters = liters; }
    public Integer getOdometer() { return odometer; }
    public void setOdometer(Integer odometer) { this.odometer = odometer; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
