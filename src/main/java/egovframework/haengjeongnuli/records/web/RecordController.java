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
        return "records/list";
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
        return "records/view";
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
