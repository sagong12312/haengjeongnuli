package egovframework.haengjeongnuli.user.web;

import java.time.LocalDate;
import java.time.Year;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;

/** 시스템 관리 > 공휴일 (선거일·임시공휴일 등록) */
@Controller
@RequestMapping("/admin/holidays")
public class AdminHolidayController {

    private final HolidayService holidayService;
    private final AuditService auditService;

    public AdminHolidayController(HolidayService holidayService, AuditService auditService) {
        this.holidayService = holidayService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Integer year, Model model) {
        int y = year == null || year < 2000 || year > 2100 ? Year.now().getValue() : year;
        model.addAttribute("year", y);
        model.addAttribute("holidays", holidayService.holidays(y));
        model.addAttribute("extra", holidayService.extra());
        return "user/admin-holidays";
    }

    @PostMapping
    public String add(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                      @RequestParam String name, @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        String n = name.trim();
        if (n.isEmpty() || n.length() > 100) {
            ra.addFlashAttribute("message", "공휴일 이름을 1~100자로 입력하세요.");
        } else if (holidayService.addExtra(day, n, me.getLoginId())) {
            auditService.record(AuditAction.HOLIDAY_ADD, day.toString(), n);
            ra.addFlashAttribute("message", day + " '" + n + "'을(를) 공휴일로 추가했습니다. 이후 접수하는 민원의 처리기한부터 반영됩니다.");
        } else {
            ra.addFlashAttribute("message", "이미 등록한 날짜입니다.");
        }
        return "redirect:/admin/holidays?year=" + day.getYear();
    }

    @PostMapping("/delete")
    public String delete(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                         RedirectAttributes ra) {
        if (holidayService.removeExtra(day)) {
            auditService.record(AuditAction.HOLIDAY_DELETE, day.toString(), null);
            ra.addFlashAttribute("message", day + " 공휴일을 지웠습니다.");
        }
        return "redirect:/admin/holidays?year=" + day.getYear();
    }
}
