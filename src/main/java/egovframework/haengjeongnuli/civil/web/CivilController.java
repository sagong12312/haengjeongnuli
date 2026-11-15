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
    private final egovframework.haengjeongnuli.civil.service.CivilProcessService process;
    private final CivilService civilService;
    private final AuditService auditService;
    private final egovframework.haengjeongnuli.civil.service.CivilFileService civilFiles;
    private final egovframework.haengjeongnuli.cmm.file.FileStorage fileStorage;

    public CivilController(DeadlineCalculator deadlineCalculator, CivilService civilService, AuditService auditService,
                           egovframework.haengjeongnuli.civil.service.CivilProcessService process,
                           egovframework.haengjeongnuli.civil.service.CivilFileService civilFiles,
                           egovframework.haengjeongnuli.cmm.file.FileStorage fileStorage) {
        this.civilFiles = civilFiles;
        this.fileStorage = fileStorage;
        this.process = process;
        this.auditService = auditService;
        this.deadlineCalculator = deadlineCalculator;
        this.civilService = civilService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me, @ModelAttribute("search") CivilSearchVO search, Model model) {
        // 요청 값으로 접근 범위를 바꾸지 못하게 서버에서 다시 정한다
        search.applyViewer(me);
        model.addAttribute("seeAll", search.isViewerAll());
        model.addAttribute("kinds", CivilKind.values());
        model.addAttribute("statuses", CivilStatus.values());
        model.addAttribute("kind", search.getKind());
        model.addAttribute("summary", civilService.summary(me));
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
                         @org.springframework.web.bind.annotation.RequestParam(value = "files", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> files,
                         Model model, RedirectAttributes ra, HttpServletResponse response) throws java.io.IOException {
        if (!loginUser.isCounterStaff()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return "civil/denied";
        }
        String fileProblem = civilFiles.check(files);
        if (fileProblem != null) {
            result.reject("files", fileProblem);
        }
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addFormOptions(model);
            return "civil/new";
        }
        LoginUser staff = process.findStaff(form.getStaffId());
        form.setStaff(staff == null ? null : staff.getUserName());
        form.setStaffId(staff == null ? null : staff.getLoginId());
        if (staff != null && (form.getDept() == null || form.getDept().isBlank())) {
            form.setDept(staff.getDeptName());
        }
        CivilComplaintVO saved = civilService.receive(form, loginUser.getLoginId());
        int saved_files = civilFiles.save(saved.getComplaintId(), files, loginUser.getLoginId());
        if (staff != null && !staff.getLoginId().equals(loginUser.getLoginId())) {
            process.assign(saved.getComplaintId(), saved.getDept(), staff.getLoginId(), loginUser);
        }
        auditService.record(AuditAction.CIVIL_RECEIVE, "민원 " + saved.getReceiptNo(), saved.getKind().getLabel());
        ra.addFlashAttribute("message", "민원이 접수되었습니다. 접수번호: " + saved.getReceiptNo()
                + (saved_files > 0 ? " (구비서류 " + saved_files + "개 저장)" : ""));
        return "redirect:/civil/" + saved.getComplaintId();
    }

    private void addFormOptions(Model model) {
        model.addAttribute("kinds", CivilKind.values());
        Map<String, java.util.List<LegalAffair>> groups = Arrays.stream(LegalAffair.values())
                .collect(Collectors.groupingBy(LegalAffair::getGroup, LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("affairGroups", groups);
        model.addAttribute("staffList", process.staffList());
    }

    /** 구비서류 추가 (처리할 수 있는 직원) */
    @PostMapping("/{id:\\d+}/files")
    public String addFiles(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                           @org.springframework.web.bind.annotation.RequestParam(value = "files", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> files,
                           RedirectAttributes ra) throws java.io.IOException {
        CivilComplaintVO c = visible(id, me);
        if (!process.canProcess(c, me)) {
            ra.addFlashAttribute("message", "처리 담당·부서 직원만 구비서류를 추가할 수 있습니다.");
            return "redirect:/civil/" + id;
        }
        String problem = civilFiles.check(files);
        if (problem == null && (files == null || files.stream().allMatch(f -> f == null || f.isEmpty()))) {
            problem = "올릴 파일을 고르세요.";
        }
        if (problem != null) {
            ra.addFlashAttribute("message", problem);
            return "redirect:/civil/" + id;
        }
        int n = civilFiles.save(id, files, me.getLoginId());
        auditService.record(AuditAction.CIVIL_FILE_ADD, "민원 " + c.getReceiptNo(), n + "개");
        ra.addFlashAttribute("message", "구비서류 " + n + "개를 추가했습니다.");
        return "redirect:/civil/" + id;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.cmm.hwp.HwpViewSupport hwpView;

    @GetMapping("/files/{fileId:\\d+}/view")
    public String viewFile(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long fileId,
                           Model model) {
        egovframework.haengjeongnuli.civil.service.CivilFileVO f = civilFiles.get(fileId);
        CivilComplaintVO c = f == null ? null : process.get(f.getComplaintId());
        boolean ok = c != null && process.canView(c, me);
        return hwpView.view(ok ? f.getStoredName() : null, ok ? f.getOriginalName() : "", ok ? "민원 " + c.getReceiptNo() + " 구비서류 " + fileId : "",
                "/civil/files/" + fileId, ok ? "/civil/" + c.getComplaintId() : "/civil", "civil", model);
    }

    /** 구비서류 내려받기 (민원 상세를 볼 수 있는 직원만, 개인정보이므로 기록) */
    @GetMapping("/files/{fileId:\\d+}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long fileId) {
        egovframework.haengjeongnuli.civil.service.CivilFileVO f = civilFiles.get(fileId);
        if (f == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        CivilComplaintVO c = process.get(f.getComplaintId());
        java.nio.file.Path path = fileStorage.path(f.getStoredName());
        if (c == null || !process.canView(c, me) || !java.nio.file.Files.isReadable(path)) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        auditService.record(AuditAction.FILE_DOWNLOAD, "민원 " + c.getReceiptNo() + " 구비서류 " + fileId, f.getOriginalName());
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.getFileSize())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(f.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new org.springframework.core.io.FileSystemResource(path));
    }

    // ===== 민원 처리 =====

    @GetMapping("/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                       Model model) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = visible(id, me);
        // 민원인 개인정보가 있는 화면이므로 열람을 기록한다
        auditService.record(AuditAction.CIVIL_VIEW, "민원 " + c.getReceiptNo(), null);
        model.addAttribute("c", c);
        model.addAttribute("history", process.history(id));
        model.addAttribute("files", civilFiles.list(id));
        model.addAttribute("canProcess", process.canProcess(c, me));
        model.addAttribute("staffList", process.staffList());
        model.addAttribute("reasons", egovframework.haengjeongnuli.civil.service.ExtendReason.values());
        model.addAttribute("resultTypes", egovframework.haengjeongnuli.civil.service.ResultType.values());
        model.addAttribute("notifyMethods", egovframework.haengjeongnuli.civil.service.CivilProcessService.NOTIFY_METHODS);
        return "civil/view";
    }

    @PostMapping("/{id:\\d+}/assign")
    public String assign(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) String dept,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) String assigneeId,
                         RedirectAttributes ra) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = visible(id, me);
        egovframework.haengjeongnuli.civil.service.CivilProcessService.Result r = process.assign(id, dept, assigneeId, me);
        if (r.ok()) {
            auditService.record(AuditAction.CIVIL_ASSIGN, "민원 " + c.getReceiptNo(), assigneeId);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil/" + id;
    }

    @PostMapping("/{id:\\d+}/extend")
    public String extend(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) egovframework.haengjeongnuli.civil.service.ExtendReason reason,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) Integer days,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) String detail,
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean consent,
                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean notified,
                         RedirectAttributes ra) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = visible(id, me);
        egovframework.haengjeongnuli.civil.service.CivilProcessService.Result r = process.extend(id, reason, days, detail, consent, notified, me);
        if (r.ok()) {
            auditService.record(AuditAction.CIVIL_EXTEND, "민원 " + c.getReceiptNo(), days + "일");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil/" + id;
    }

    @PostMapping("/{id:\\d+}/progress")
    public String progress(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String method,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String detail,
                           RedirectAttributes ra) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = visible(id, me);
        egovframework.haengjeongnuli.civil.service.CivilProcessService.Result r = process.progressNotice(id, method, detail, me);
        if (r.ok()) {
            auditService.record(AuditAction.CIVIL_PROGRESS, "민원 " + c.getReceiptNo(), method);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil/" + id;
    }

    @PostMapping("/{id:\\d+}/complete")
    public String complete(@AuthenticationPrincipal LoginUser me, @org.springframework.web.bind.annotation.PathVariable long id,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) egovframework.haengjeongnuli.civil.service.ResultType type,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String result,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String remedy,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String notifyMethod,
                           RedirectAttributes ra) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = visible(id, me);
        egovframework.haengjeongnuli.civil.service.CivilProcessService.Result r = process.complete(id, type, result, remedy, notifyMethod, me);
        if (r.ok()) {
            auditService.record(AuditAction.CIVIL_DONE, "민원 " + c.getReceiptNo(), type == null ? null : type.getLabel());
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil/" + id;
    }

    /** 없거나 볼 수 없는 민원은 404 */
    private egovframework.haengjeongnuli.civil.service.CivilComplaintVO visible(long id, LoginUser me) {
        egovframework.haengjeongnuli.civil.service.CivilComplaintVO c = process.get(id);
        if (c == null || !process.canView(c, me)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return c;
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
