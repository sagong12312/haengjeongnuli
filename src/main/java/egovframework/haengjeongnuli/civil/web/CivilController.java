package egovframework.haengjeongnuli.civil.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilForm;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.civil.service.DeadlineCalculator;
import egovframework.haengjeongnuli.civil.service.LegalAffair;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import jakarta.servlet.http.HttpServletResponse;

/** 민원: 목록, 접수(창구 직원 전용), 처리기한 계산 */
@Controller
@RequestMapping("/civil")
public class CivilController {

    private final DeadlineCalculator deadlineCalculator;
    private final CivilService civilService;
    private final AuditService auditService;

    public CivilController(DeadlineCalculator deadlineCalculator, CivilService civilService, AuditService auditService) {
        this.auditService = auditService;
        this.deadlineCalculator = deadlineCalculator;
        this.civilService = civilService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") CivilSearchVO search, Model model) {
        model.addAttribute("kinds", CivilKind.values());
        model.addAttribute("statuses", CivilStatus.values());
        model.addAttribute("kind", search.getKind());
        model.addAttribute("summary", civilService.summary());
        model.addAttribute("complaints", civilService.search(search));
        model.addAttribute("total", civilService.count(search));
        return "civil/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal LoginUser loginUser, Model model, HttpServletResponse response) {
        if (!loginUser.isCounterStaff()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "civil/denied";
        }
        if (!model.containsAttribute("civilForm")) {
            model.addAttribute("civilForm", new CivilForm());
        }
        addFormOptions(model);
        return "civil/new";
    }

    /** 접수. 화면과 별개로 서버에서도 권한과 입력값을 확인한다. */
    @PostMapping("/new")
    public String submit(@AuthenticationPrincipal LoginUser loginUser, @Validated @ModelAttribute("civilForm") CivilForm form, BindingResult result,
                         Model model, RedirectAttributes ra, HttpServletResponse response) {
        if (!loginUser.isCounterStaff()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "civil/denied";
        }
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addFormOptions(model);
            return "civil/new";
        }
        CivilComplaintVO saved = civilService.receive(form, loginUser.getLoginId());
        auditService.record(AuditAction.CIVIL_RECEIVE, "민원 " + saved.getReceiptNo(), saved.getKind().getLabel());
        ra.addFlashAttribute("message", "민원이 접수되었습니다. 접수번호: " + saved.getReceiptNo());
        return "redirect:/civil";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("kinds", CivilKind.values());
        Map<String, java.util.List<LegalAffair>> groups = Arrays.stream(LegalAffair.values())
                .collect(Collectors.groupingBy(LegalAffair::getGroup, LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("affairGroups", groups);
    }

    /** 접수 화면에서 종류·사무를 고를 때 처리기간과 예상 처리기한을 돌려준다 */
    @GetMapping("/api/deadline")
    @ResponseBody
    public Map<String, String> deadline(@RequestParam CivilKind kind,
                                        @RequestParam(defaultValue = "true") boolean law,
                                        @RequestParam(required = false) LegalAffair affair) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (kind == CivilKind.LEGAL && affair == null) {
            return Map.of("period", "사무를 선택하세요", "deadline", "[민원 사무 선택 시 표시]");
        }
        int days = kind == CivilKind.LEGAL ? affair.periodDays() : kind.periodDays(law);
        String period = kind == CivilKind.LEGAL ? affair.getPeriodText()
                : days == 0 ? "즉시 (3근무시간 이내)"
                : (kind == CivilKind.GRIEVANCE ? kind.getPeriodText() : days + "일");
        java.time.LocalDateTime at = deadlineCalculator.deadlineAt(now, days);
        String text = at.format(DateTimeFormatter.ofPattern("yyyy. M. d."))
                + " (" + at.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")"
                + (days <= 5 ? " " + at.format(DateTimeFormatter.ofPattern("HH:mm")) + "까지" : "");
        return Map.of("period", period, "deadline", text);
    }
}
