package egovframework.haengjeongnuli.cmm.hwp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 한글 서식함 (직원), 서식 관리 (관리자, /admin/** ) */
@Controller
public class HwpFormController {

    static final List<String> SOURCES = List.of("civil", "hygiene", "building", "staff");

    private final HwpFormService forms;
    private final FileStorage storage;
    private final AuditService audit;
    private final HwpViewSupport hwpView;
    private final egovframework.haengjeongnuli.user.service.UserService users;

    public HwpFormController(HwpFormService forms, FileStorage storage, AuditService audit, HwpViewSupport hwpView,
                             egovframework.haengjeongnuli.user.service.UserService users) {
        this.forms = forms;
        this.storage = storage;
        this.audit = audit;
        this.hwpView = hwpView;
        this.users = users;
    }

    @GetMapping("/forms")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String source,
                       @RequestParam(required = false) Long sourceId, Model model) {
        model.addAttribute("forms", forms.list(true));
        model.addAttribute("source", validSource(source) ? source : null);
        model.addAttribute("sourceId", sourceId);
        model.addAttribute("categories", HwpFormVO.CATEGORIES);
        return "cmm/forms";
    }

    @GetMapping("/forms/{id:\\d+}/file")
    public ResponseEntity<Resource> file(@PathVariable long id) {
        HwpFormVO f = usable(id);
        Path p = storage.path(f.getStoredName());
        if (!Files.isReadable(p)) {
            return ResponseEntity.notFound().build();
        }
        audit.record(AuditAction.FILE_DOWNLOAD, "한글 서식 " + id, f.getOriginalName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.getOriginalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(p));
    }

    @GetMapping("/forms/{id:\\d+}/view")
    public String view(@PathVariable long id, Model model) {
        HwpFormVO f = usable(id);
        return hwpView.view(f.getStoredName(), f.getOriginalName(), "한글 서식 " + id, "/forms/" + id + "/file", "/forms", "home", model);
    }

    /** 채워 만들기 화면: 누름틀마다 입력칸, 고른 자료의 값을 미리 넣는다 */
    @GetMapping("/forms/{id:\\d+}/fill")
    public String fillForm(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String source,
                           @RequestParam(required = false) Long sourceId, Model model) {
        HwpFormVO f = usable(id);
        if (!f.isFillable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String src = validSource(source) ? source : null;
        Map<String, String> values = forms.values(src, sourceId, me);
        if (values == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, String> rows = new LinkedHashMap<>();
        for (String name : f.getFieldList()) {
            rows.put(name, values.getOrDefault(name, ""));
        }
        model.addAttribute("f", f);
        model.addAttribute("rows", rows);
        model.addAttribute("known", HwpFormService.KNOWN);
        model.addAttribute("source", src);
        model.addAttribute("sourceId", sourceId);
        model.addAttribute("staff", me.isAdmin() ? users.list() : List.of());
        return "cmm/form-fill";
    }

    @PostMapping("/forms/{id:\\d+}/fill")
    public ResponseEntity<byte[]> fill(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                                       @RequestParam(required = false) List<String> names, @RequestParam(required = false) List<String> values,
                                       @RequestParam(required = false) String source, @RequestParam(required = false) Long sourceId) {
        HwpFormVO f = usable(id);
        if (!f.isFillable() || names == null || values == null || names.size() != values.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<String> allowed = f.getFieldList();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            if (allowed.contains(names.get(i))) {
                map.put(names.get(i), values.get(i));
            }
        }
        byte[] out;
        try {
            out = forms.fill(f, map);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "서식을 채우지 못했습니다.");
        }
        String target = validSource(source) && sourceId != null ? " (" + source + " " + sourceId + ")" : "";
        audit.record(AuditAction.HWP_FORM_FILL, "한글 서식 " + id + target, f.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.getName() + ".hwp", StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(out);
    }

    // ===== 관리자 =====

    @GetMapping("/admin/forms")
    public String admin(Model model) {
        model.addAttribute("forms", forms.list(false));
        model.addAttribute("categories", HwpFormVO.CATEGORIES);
        model.addAttribute("known", HwpFormService.KNOWN);
        return "cmm/admin-forms";
    }

    @PostMapping("/admin/forms")
    public String upload(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                         @RequestParam(required = false) String category, @RequestParam(required = false) List<MultipartFile> files,
                         RedirectAttributes ra) throws java.io.IOException {
        StringBuilder msg = new StringBuilder();
        int n = 0;
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            // 여러 개를 한 번에 올리면 파일 이름을 서식 이름으로 쓴다
            HwpFormService.Result r = forms.upload(files.size() == 1 ? name : null, category, file, me);
            if (r.ok()) {
                audit.record(AuditAction.HWP_FORM_SAVE, "한글 서식 " + r.id(), "올림: " + file.getOriginalFilename());
            }
            msg.append(r.message()).append(' ');
            if (++n >= 20) {
                break;
            }
        }
        ra.addFlashAttribute("message", n == 0 ? "서식 파일을 고르세요." : msg.toString().trim());
        return "redirect:/admin/forms";
    }

    @PostMapping("/admin/forms/{id:\\d+}/{action:use|stop}")
    public String setUse(@PathVariable long id, @PathVariable String action, RedirectAttributes ra) {
        boolean ok = forms.setUse(id, "use".equals(action));
        if (ok) {
            audit.record(AuditAction.HWP_FORM_SAVE, "한글 서식 " + id, action);
        }
        ra.addFlashAttribute("message", !ok ? "없는 서식입니다." : "use".equals(action) ? "다시 씁니다." : "서식함에서 숨겼습니다.");
        return "redirect:/admin/forms";
    }

    private static boolean validSource(String s) {
        return s != null && SOURCES.contains(s);
    }

    private HwpFormVO usable(long id) {
        HwpFormVO f = forms.get(id);
        if (f == null || !f.isInUse()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return f;
    }
}
