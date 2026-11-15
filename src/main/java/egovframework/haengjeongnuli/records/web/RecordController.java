package egovframework.haengjeongnuli.records.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.records.service.RecordFileVO;
import egovframework.haengjeongnuli.records.service.RecordService;
import egovframework.haengjeongnuli.records.service.RecordVO;
import egovframework.haengjeongnuli.user.service.UserService;

/** 문서 등록: 기록물 등록대장과 직접 등록(시스템 밖에서 생산·접수한 문서) */
@Controller
public class RecordController {

    private final RecordService records;
    private final AuditService audit;
    private final FileStorage fileStorage;
    private final UserService userService;

    public RecordController(RecordService records, AuditService audit, FileStorage fileStorage, UserService userService) {
        this.records = records;
        this.audit = audit;
        this.fileStorage = fileStorage;
        this.userService = userService;
    }

    @GetMapping("/records")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) String type, @RequestParam(required = false) String q, Model model) {
        int now = Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now : year;
        model.addAttribute("year", y);
        model.addAttribute("years", List.of(now - 2, now - 1, now));
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("rows", records.list(me, y, type, q));
        model.addAttribute("dept", RecordService.deptKey(me.getDeptName()));
        model.addAttribute("toConfirm", records.fixesToConfirm(me));
        return "records/list";
    }

    /** 기록물 생산현황 (처리과별, 전년도 1.1.~12.31. 생산·접수 기록물을 다음 해 기록관에 통보) */
    @GetMapping("/records/report")
    public String report(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                         @RequestParam(required = false) String dept, Model model) {
        int now = Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now - 1 : year;
        model.addAttribute("rep", records.report(me, dept, y));
        model.addAttribute("years", java.util.stream.IntStream.rangeClosed(now - 5, now).boxed().sorted(java.util.Comparator.reverseOrder()).toList());
        model.addAttribute("depts", me.isAdmin() ? records.reportDepts() : List.of());
        return "records/report";
    }

    /** 이관 목록 CSV */
    @GetMapping("/records/report.csv")
    public ResponseEntity<byte[]> reportCsv(@AuthenticationPrincipal LoginUser me, @RequestParam int year,
                                            @RequestParam(required = false) String dept) {
        RecordService.Report rep = records.report(me, dept, year);
        audit.record(AuditAction.RECORD_TRANSFER_LIST, (rep.dept() == null ? "전체" : rep.dept()) + " " + year, rep.rows().size() + "건");
        String name = year + "_" + (rep.dept() == null ? "전체" : rep.dept()) + "_이관목록.csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(records.transferCsv(rep).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/records/new")
    public String form(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "RECEIVE") String type, Model model) {
        model.addAttribute("type", RecordVO.PRODUCE.equals(type) ? RecordVO.PRODUCE : RecordVO.RECEIVE);
        model.addAttribute("media", RecordVO.MEDIA);
        model.addAttribute("retentions", ApprovalForm.RETENTIONS);
        model.addAttribute("disclosures", ApprovalForm.DISCLOSURES);
        model.addAttribute("staff", userService.list().stream().filter(LoginUser::isEnabled).toList());
        model.addAttribute("dept", RecordService.deptKey(me.getDeptName()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("allowedExt", String.join(", ", fileStorage.getAllowedExtensions().stream().sorted().toList()));
        return "records/form";
    }

    @PostMapping("/records")
    public String register(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String type,
                           @RequestParam(required = false) String title,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regDate,
                           @RequestParam(required = false) String counterpart, @RequestParam(required = false) String counterpartNo,
                           @RequestParam(required = false) String medium, @RequestParam(required = false) Integer pages,
                           @RequestParam(required = false) String retention, @RequestParam(required = false) String disclosure,
                           @RequestParam(required = false) String handlerId, @RequestParam(required = false) String note,
                           @RequestParam(value = "files", required = false) List<MultipartFile> files,
                           RedirectAttributes ra) throws IOException {
        RecordService.Result r = records.register(type, title, regDate, counterpart, counterpartNo, medium, pages, retention,
                disclosure, handlerId, note, files, me);
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/records/new?type=" + (RecordVO.PRODUCE.equals(type) ? "PRODUCE" : "RECEIVE");
        }
        audit.record(AuditAction.RECORD_REGISTER, "기록물 " + r.id(), title);
        return "redirect:/records/" + r.id();
    }

    @GetMapping("/records/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        RecordVO r = records.get(id);
        if (r == null || !records.canView(r, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("r", r);
        model.addAttribute("fixes", records.fixes(id));
        model.addAttribute("canRequestFix", !r.isCanceled() && records.canRequestFix(r, me));
        model.addAttribute("canConfirmFix", records.canConfirmFix(r, me));
        model.addAttribute("fields", egovframework.haengjeongnuli.records.service.RecordFixVO.FIELDS);
        model.addAttribute("media", RecordVO.MEDIA);
        model.addAttribute("retentions", ApprovalForm.RETENTIONS);
        model.addAttribute("disclosures", ApprovalForm.DISCLOSURES);
        return "records/view";
    }

    /** 등록정보 수정·등록 취소 요청 (기록물관리책임자 확인 뒤 반영, 공공기록물법 시행규칙 제6조) */
    @PostMapping("/records/{id:\\d+}/fix")
    public String requestFix(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                             @RequestParam(required = false) String field, @RequestParam(required = false) String newValue,
                             @RequestParam(required = false) String reason, RedirectAttributes ra) {
        RecordVO r = records.get(id);
        if (r == null || !records.canView(r, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        RecordService.Result res = records.requestFix(id, field, newValue, reason, me);
        if (res.ok()) {
            audit.record(AuditAction.RECORD_FIX_REQUEST, "기록물 " + id, field + ": " + reason);
        }
        ra.addFlashAttribute("message", res.message());
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/fixes/{fixId:\\d+}")
    public String confirmFix(@AuthenticationPrincipal LoginUser me, @PathVariable long fixId,
                             @RequestParam boolean approve, RedirectAttributes ra) {
        RecordService.Result res = records.confirmFix(fixId, approve, me);
        if (res.ok()) {
            audit.record(AuditAction.RECORD_FIX_CONFIRM, "수정 요청 " + fixId, approve ? "반영" : "반려");
        }
        ra.addFlashAttribute("message", res.message());
        return res.id() == null ? "redirect:/records" : "redirect:/records/" + res.id();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.cmm.hwp.HwpViewSupport hwpView;

    @GetMapping("/records/files/{fileId:\\d+}/view")
    public String viewFile(@AuthenticationPrincipal LoginUser me, @PathVariable long fileId, Model model) {
        RecordFileVO f = records.fileForDownload(fileId, me);
        return hwpView.view(f == null ? null : f.getStoredName(), f == null ? "" : f.getOriginalName(), "기록물 파일 " + fileId,
                "/records/files/" + fileId, f == null ? "/records" : "/records/" + f.getRegId(), "approval", model);
    }

    @GetMapping("/records/files/{fileId:\\d+}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser me, @PathVariable long fileId) {
        RecordFileVO f = records.fileForDownload(fileId, me);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorage.path(f.getStoredName());
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        audit.record(AuditAction.FILE_DOWNLOAD, "기록물 파일 " + fileId, f.getOriginalName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
