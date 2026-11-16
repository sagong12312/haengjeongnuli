package egovframework.haengjeongnuli.vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.text.Csv;

/** 관용차량: 차량 대장, 운행일지, 주유·정비 */
@Controller
public class VehicleController {

    private final VehicleService service;
    private final AuditService audit;

    public VehicleController(VehicleService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    private static YearMonth month(String m) {
        try {
            YearMonth ym = m == null ? YearMonth.now() : YearMonth.parse(m);
            return ym.isAfter(YearMonth.now()) || ym.getYear() < 2000 ? YearMonth.now() : ym;
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    @GetMapping("/vehicles")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month, Model model) {
        YearMonth ym = month(month);
        model.addAttribute("vehicles", service.list(false));
        model.addAttribute("monthly", service.monthly(ym));
        model.addAttribute("ym", ym);
        model.addAttribute("canRegister", service.canRegister(me));
        model.addAttribute("kinds", VehicleVO.KINDS);
        model.addAttribute("fuels", VehicleVO.FUELS);
        model.addAttribute("depts", String.join("·", service.getDepts()));
        model.addAttribute("today", LocalDate.now());
        return "vehicle/list";
    }

    @PostMapping("/vehicles")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String plateNo,
                           @RequestParam(required = false) String model, @RequestParam(required = false) String vehicleKind,
                           @RequestParam(required = false) String fuel, @RequestParam(required = false) String deptName,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredOn,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionDue,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate insuranceDue,
                           @RequestParam(required = false) String odometer, RedirectAttributes ra) {
        VehicleVO in = new VehicleVO();
        in.setPlateNo(plateNo);
        in.setModel(model);
        in.setVehicleKind(vehicleKind);
        in.setFuel(fuel);
        in.setDeptName(deptName);
        in.setAcquiredOn(acquiredOn);
        in.setInspectionDue(inspectionDue);
        in.setInsuranceDue(insuranceDue);
        Integer km = number(odometer);
        in.setOdometer(km == null ? 0 : km);
        VehicleService.Result r = service.register(in, me);
        if (r.ok()) {
            audit.record(AuditAction.VEHICLE_SAVE, "차량 " + r.id(), "등록: " + plateNo);
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() ? "redirect:/vehicles/" + r.id() : "redirect:/vehicles";
    }

    @GetMapping("/vehicles/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String month, Model model) {
        VehicleVO v = service.get(id);
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        YearMonth ym = month(month);
        java.util.List<VehicleLogVO> logs = service.logs(id, ym);
        java.util.List<VehicleCostVO> costs = service.costs(id, ym);
        model.addAttribute("v", v);
        model.addAttribute("logs", logs);
        model.addAttribute("costs", costs);
        model.addAttribute("km", logs.stream().mapToInt(VehicleLogVO::getDistance).sum());
        model.addAttribute("cost", costs.stream().mapToLong(VehicleCostVO::getAmount).sum());
        model.addAttribute("ym", ym);
        model.addAttribute("canManage", service.canManage(v, me));
        model.addAttribute("canRetire", service.canRegister(me));
        model.addAttribute("costKinds", VehicleCostVO.KINDS);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("now", LocalDateTime.now().withSecond(0).withNano(0));
        return "vehicle/view";
    }

    @GetMapping("/vehicles/{id:\\d+}/logs.csv")
    public ResponseEntity<byte[]> logsCsv(@PathVariable long id, @RequestParam(required = false) String month) {
        VehicleVO v = service.get(id);
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        YearMonth ym = month(month);
        Csv csv = Csv.of("차량번호", "출발", "도착", "운전자", "목적", "행선지", "출발 km", "도착 km", "운행 km");
        for (VehicleLogVO l : service.logs(id, ym)) {
            csv.row(v.getPlateNo(), l.getStartAt(), l.getEndAt(), l.getDriverName(), l.getPurpose(), l.getDestination(), l.getStartKm(),
                    l.getEndKm(), l.getDistance());
        }
        audit.record(AuditAction.FIN_EXPORT, "운행일지 " + v.getPlateNo() + " " + ym, null);
        return csv.download(ym + "_" + v.getPlateNo() + "_운행일지.csv");
    }

    @PostMapping("/vehicles/{id:\\d+}/{action:log|cost|dues|retire}")
    public String action(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                         @RequestParam(required = false) String purpose, @RequestParam(required = false) String destination,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                         @RequestParam(required = false) String startKm, @RequestParam(required = false) String endKm,
                         @RequestParam(required = false) String kind,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doneOn,
                         @RequestParam(required = false) String amount, @RequestParam(required = false) String liters,
                         @RequestParam(required = false) String odometer, @RequestParam(required = false) String vendor,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionDue,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate insuranceDue,
                         RedirectAttributes ra) {
        if (service.get(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        VehicleService.Result r = switch (action) {
            case "log" -> service.addLog(id, purpose, destination, startAt, endAt, number(startKm), number(endKm), me);
            case "cost" -> {
                Long amt = number(amount) == null ? null : number(amount).longValue();
                BigDecimal l = null;
                try {
                    l = liters == null || liters.isBlank() ? null : new BigDecimal(liters.trim());
                } catch (NumberFormatException e) {
                    l = BigDecimal.ONE.negate();
                }
                yield service.addCost(id, kind, doneOn, amt, l, number(odometer), vendor, note, me);
            }
            case "dues" -> service.setDues(id, inspectionDue, insuranceDue, me);
            default -> service.retire(id, me);
        };
        if (r.ok()) {
            audit.record(AuditAction.VEHICLE_SAVE, "차량 " + id, action + " " + (kind == null ? "" : kind));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/vehicles/" + id;
    }

    static Integer number(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.replace(",", "").replace("km", "").replace("원", "").trim();
        return t.matches("\\d{1,9}") ? Integer.valueOf(t) : -1;
    }
}
