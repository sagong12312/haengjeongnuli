package egovframework.haengjeongnuli.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 관용차량: 차량 대장(정기검사·보험 만료), 운행일지(모든 직원), 주유·정비 등 비용(차량 담당 부서).
 * 운행일지는 계기판 거리가 이어지게 한다: 출발 거리는 차량의 마지막 주행거리보다 작을 수 없다.
 */
@Service
public class VehicleService {

    public record Result(boolean ok, String message, Long id) {}

    private final VehicleMapper mapper;
    private final Set<String> depts;

    public VehicleService(VehicleMapper mapper, @Value("${app.work.vehicle-depts:총무과}") String depts) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public Set<String> getDepts() { return depts; }

    /** 차량 등록·비용·검사일은 차량 총괄 부서·관리자, 그리고 차량을 배정받은 부서 */
    public boolean canManage(VehicleVO v, LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept()) || (v != null && !u.dept().isEmpty() && u.dept().equals(v.getDeptName()));
    }

    public boolean canRegister(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<VehicleVO> list(boolean all) { return mapper.selectAll(all); }
    public VehicleVO get(long id) { return mapper.selectById(id); }

    public List<VehicleLogVO> logs(long id, YearMonth ym) {
        return mapper.selectLogs(id, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
    }

    public List<VehicleCostVO> costs(long id, YearMonth ym) {
        return mapper.selectCosts(id, ym.atDay(1), ym.plusMonths(1).atDay(1));
    }

    public List<Map<String, Object>> monthly(YearMonth ym) {
        return mapper.monthly(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay(), ym.atDay(1), ym.plusMonths(1).atDay(1));
    }

    @Transactional
    public Result register(VehicleVO in, LoginUser me) {
        if (!canRegister(me)) {
            return new Result(false, "차량 등록은 " + String.join("·", depts) + " 직원과 관리자만 합니다.", null);
        }
        String plate = trim(in.getPlateNo()), model = trim(in.getModel()), dept = trim(in.getDeptName());
        if (plate == null || !plate.replace(" ", "").matches("(\\d{2,3}|[가-힣]{2}\\d{1,2})[가-힣]\\d{4}")) {
            return new Result(false, "차량번호를 '12가3456' 꼴로 입력하세요.", null);
        }
        plate = plate.replace(" ", "");
        if (mapper.countPlate(plate) > 0) {
            return new Result(false, "이미 등록된 차량번호입니다.", null);
        }
        if (model == null || model.length() > 100 || dept == null || dept.length() > 100) {
            return new Result(false, "차종(모델)과 배정 부서를 입력하세요.", null);
        }
        if (in.getVehicleKind() == null || !VehicleVO.KINDS.contains(in.getVehicleKind()) || in.getFuel() == null
                || !VehicleVO.FUELS.contains(in.getFuel())) {
            return new Result(false, "차종 구분과 연료를 고르세요.", null);
        }
        if (in.getAcquiredOn() == null || in.getAcquiredOn().isAfter(LocalDate.now()) || in.getOdometer() < 0 || in.getOdometer() > 9_999_999) {
            return new Result(false, "취득일과 현재 주행거리를 바르게 입력하세요.", null);
        }
        VehicleVO v = new VehicleVO();
        v.setVehicleId(mapper.nextId());
        v.setPlateNo(plate);
        v.setModel(model);
        v.setVehicleKind(in.getVehicleKind());
        v.setFuel(in.getFuel());
        v.setDeptName(dept);
        v.setAcquiredOn(in.getAcquiredOn());
        v.setInspectionDue(in.getInspectionDue());
        v.setInsuranceDue(in.getInsuranceDue());
        v.setOdometer(in.getOdometer());
        v.setCreatedBy(me.getLoginId());
        v.setCreatedAt(LocalDateTime.now());
        mapper.insert(v);
        return new Result(true, "차량을 등록했습니다: " + plate, v.getVehicleId());
    }

    /** 운행일지 (차를 쓴 직원 누구나) */
    @Transactional
    public Result addLog(long id, String purpose, String destination, LocalDateTime start, LocalDateTime end, Integer startKm, Integer endKm,
                         LoginUser me) {
        VehicleVO v = mapper.lock(id);
        if (v == null || !v.isInUse()) {
            return new Result(false, "운행 중인 차량이 아닙니다.", null);
        }
        String p = trim(purpose), d = trim(destination);
        if (p == null || p.length() > 300 || d == null || d.length() > 200) {
            return new Result(false, "운행 목적과 행선지를 입력하세요.", null);
        }
        if (start == null || end == null || !end.isAfter(start) || end.isAfter(LocalDateTime.now().plusMinutes(5))) {
            return new Result(false, "출발·도착 시각을 바르게 입력하세요 (도착은 지금 이전).", null);
        }
        if (startKm == null || endKm == null || endKm < startKm || endKm - startKm > 3000) {
            return new Result(false, "출발·도착 주행거리를 바르게 입력하세요.", null);
        }
        if (startKm < v.getOdometer()) {
            return new Result(false, String.format("출발 주행거리가 이 차의 마지막 기록(%,dkm)보다 작습니다.", v.getOdometer()), null);
        }
        if (mapper.countOverlap(id, start, end) > 0) {
            return new Result(false, "같은 시간에 이 차의 다른 운행 기록이 있습니다.", null);
        }
        VehicleLogVO l = new VehicleLogVO();
        l.setLogId(mapper.nextLogId());
        l.setVehicleId(id);
        l.setDriverId(me.getLoginId());
        l.setDriverName(me.getUserName());
        l.setPurpose(p);
        l.setDestination(d);
        l.setStartAt(start);
        l.setEndAt(end);
        l.setStartKm(startKm);
        l.setEndKm(endKm);
        l.setCreatedAt(LocalDateTime.now());
        mapper.insertLog(l);
        mapper.setOdometer(id, endKm);
        return new Result(true, String.format("운행일지를 적었습니다 (%,dkm).", endKm - startKm), id);
    }

    /** 주유·정비·검사·보험 등 비용 */
    @Transactional
    public Result addCost(long id, String kind, LocalDate on, Long amount, BigDecimal liters, Integer odometer, String vendor, String note,
                          LoginUser me) {
        VehicleVO v = mapper.lock(id);
        if (v == null || !canManage(v, me)) {
            return new Result(false, "주유·정비 기록은 차량 담당 부서 직원과 관리자만 합니다.", null);
        }
        if (kind == null || !VehicleCostVO.KINDS.contains(kind)) {
            return new Result(false, "종류를 고르세요.", null);
        }
        if (on == null || on.isAfter(LocalDate.now()) || amount == null || amount < 0 || amount > 999_999_999L) {
            return new Result(false, "날짜와 금액을 바르게 입력하세요.", null);
        }
        if ("주유".equals(kind) && (liters == null || liters.signum() <= 0 || liters.compareTo(new BigDecimal("500")) > 0)) {
            return new Result(false, "주유량(리터)을 입력하세요.", null);
        }
        if (odometer != null && (odometer < 0 || odometer > 9_999_999)) {
            return new Result(false, "주행거리를 바르게 입력하세요.", null);
        }
        String ven = trim(vendor), n = trim(note);
        if ((ven != null && ven.length() > 200) || (n != null && n.length() > 500)) {
            return new Result(false, "업체는 200자, 내용은 500자 이내로 적으세요.", null);
        }
        if (("정비".equals(kind) || "검사".equals(kind)) && n == null) {
            return new Result(false, "정비·검사 내용을 적으세요.", null);
        }
        VehicleCostVO c = new VehicleCostVO();
        c.setCostId(mapper.nextCostId());
        c.setVehicleId(id);
        c.setCostKind(kind);
        c.setDoneOn(on);
        c.setAmount(amount);
        c.setLiters("주유".equals(kind) ? liters : null);
        c.setOdometer(odometer);
        c.setVendor(ven);
        c.setNote(n);
        c.setCreatedBy(me.getLoginId());
        c.setCreatedAt(LocalDateTime.now());
        mapper.insertCost(c);
        if (odometer != null) {
            mapper.setOdometer(id, odometer);
        }
        return new Result(true, kind + " 기록을 남겼습니다.", id);
    }

    @Transactional
    public Result setDues(long id, LocalDate inspection, LocalDate insurance, LoginUser me) {
        VehicleVO v = get(id);
        if (v == null || !canManage(v, me)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        mapper.setDues(id, inspection, insurance);
        return new Result(true, "정기검사일·보험 만료일을 고쳤습니다.", id);
    }

    @Transactional
    public Result retire(long id, LoginUser me) {
        VehicleVO v = get(id);
        if (v == null || !canRegister(me)) {
            return new Result(false, "차량 불용은 " + String.join("·", depts) + " 직원과 관리자만 합니다.", null);
        }
        return mapper.retire(id) == 1 ? new Result(true, v.getPlateNo() + "을(를) 불용(운행 중지)으로 바꿨습니다. 처분은 물품 불용결정 절차를 따르세요.", id)
                : new Result(false, "이미 불용인 차량입니다.", null);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
