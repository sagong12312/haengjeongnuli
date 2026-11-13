package egovframework.haengjeongnuli.civil.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.DeadlineCalculator;
import egovframework.haengjeongnuli.civil.service.LegalAffair;
import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletResponse;

/** 민원: 목록, 접수(창구 직원 전용), 처리기한 계산 */
@Controller
@RequestMapping("/civil")
public class CivilController {

    private final LoginUser loginUser;
    private final DeadlineCalculator deadlineCalculator;

    public CivilController(LoginUser loginUser, DeadlineCalculator deadlineCalculator) {
        this.loginUser = loginUser;
        this.deadlineCalculator = deadlineCalculator;
    }

    @GetMapping
    public String list(@RequestParam(required = false) CivilKind kind, Model model) {
        model.addAttribute("kinds", CivilKind.values());
        model.addAttribute("kind", kind);
        return "civil/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, HttpServletResponse response) {
        if (!loginUser.isCounterStaff()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "civil/denied";
        }
        model.addAttribute("kinds", CivilKind.values());
        model.addAttribute("affairGroups", java.util.Arrays.stream(LegalAffair.values())
                .collect(java.util.stream.Collectors.groupingBy(
                        LegalAffair::getGroup, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList())));
        return "civil/new";
    }

    /** 접수 (저장소 연동 전에는 안내만 한다). 화면과 별개로 서버에서도 권한을 확인한다. */
    @PostMapping("/new")
    public String submit(RedirectAttributes ra, HttpServletResponse response) {
        if (!loginUser.isCounterStaff()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "civil/denied";
        }
        ra.addFlashAttribute("message", "민원이 접수되었습니다. 접수번호: [접수번호]");
        return "redirect:/civil";
    }

    /** 접수 화면에서 종류·사무를 고를 때 처리기간과 예상 처리기한을 돌려준다 */
    @GetMapping("/api/deadline")
    @ResponseBody
    public Map<String, String> deadline(@RequestParam CivilKind kind,
                                        @RequestParam(defaultValue = "true") boolean law,
                                        @RequestParam(required = false) LegalAffair affair) {
        if (kind == CivilKind.LEGAL) {
            if (affair == null) {
                return Map.of("period", "사무를 선택하세요", "deadline", "[민원 사무 선택 시 표시]");
            }
            return Map.of("period", affair.getPeriodText(), "deadline", "오늘 접수 시각부터 근무시간 내 3시간 이내");
        }
        int days = kind.periodDays(law);
        String period = days == 0 ? "즉시" : (kind == CivilKind.GRIEVANCE ? kind.getPeriodText() : days + "일");
        if (days == 0) {
            return Map.of("period", period, "deadline", "오늘 즉시 처리");
        }
        LocalDate d = deadlineCalculator.deadline(LocalDate.now(), days);
        String text = d.format(DateTimeFormatter.ofPattern("yyyy. M. d.")) + " ("
                + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
        return Map.of("period", period, "deadline", text);
    }
}
