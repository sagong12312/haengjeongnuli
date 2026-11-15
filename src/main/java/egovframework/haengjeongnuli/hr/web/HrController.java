package egovframework.haengjeongnuli.hr.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.hr.service.AttendanceService;
import egovframework.haengjeongnuli.hr.service.AttendanceVO;

/** 인사: 내 인사정보, 오늘 근태, 월별 출퇴근 기록, 복무 신청 */
@Controller
public class HrController {

    private final AttendanceService attendanceService;

    public HrController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/hr")
    public String index(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(required = false) String month, Model model) {
        AttendanceVO current = attendanceService.current(me.getLoginId());
        AttendanceVO today = current != null ? current : attendanceService.today(me.getLoginId());
        model.addAttribute("working", current != null);
        model.addAttribute("today", today);

        YearMonth ym = YearMonth.now();
        if (month != null) {
            try {
                ym = YearMonth.parse(month);
            } catch (DateTimeParseException ignored) {
                // 잘못된 값이면 이번 달
            }
        }
        if (ym.isAfter(YearMonth.now())) {
            ym = YearMonth.now();
        }
        List<AttendanceVO> records = attendanceService.month(me.getLoginId(), ym);
        model.addAttribute("ym", ym);
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.isBefore(YearMonth.now()) ? ym.plusMonths(1).toString() : null);
        model.addAttribute("records", records);
        model.addAttribute("daysWorked", records.size());
        return "hr/index";
    }
}
