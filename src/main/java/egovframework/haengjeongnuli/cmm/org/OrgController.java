package egovframework.haengjeongnuli.cmm.org;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 시스템 관리 › 기관 정보와 관인 (관리자 전용, /admin/** ) */
@Controller
public class OrgController {

    /** 관인 이미지 최대 크기 */
    static final long MAX_SEAL_BYTES = 1024 * 1024;

    private final OrgService org;
    private final FileStorage files;
    private final AuditService audit;

    public OrgController(OrgService org, FileStorage files, AuditService audit) {
        this.org = org;
        this.files = files;
        this.audit = audit;
    }

    @GetMapping("/admin/org")
    public String form(Model model) {
        model.addAttribute("fields", OrgService.FIELDS);
        model.addAttribute("values", org.all());
        model.addAttribute("hasSeal", org.hasSeal());
        model.addAttribute("timeout", org.sessionTimeoutMinutes());
        return "user/admin-org";
    }

    @PostMapping("/admin/org")
    public String save(@RequestParam Map<String, String> params, @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        Map<String, String> values = new HashMap<>();
        OrgService.FIELDS.keySet().forEach(k -> values.put(k, params.get(k)));
        String problem = org.save(values, me.getLoginId());
        if (problem == null) {
            audit.record(AuditAction.ORG_UPDATE, "기관 정보", null);
        }
        ra.addFlashAttribute("message", problem == null ? "기관 정보를 저장했습니다." : problem);
        return "redirect:/admin/org";
    }

    /** 자동 로그아웃 시간 (다음 요청부터 모든 직원에게 적용) */
    @PostMapping("/admin/org/session")
    public String sessionTimeout(@RequestParam(required = false) String minutes, @AuthenticationPrincipal LoginUser me,
                                 RedirectAttributes ra) {
        String problem = org.saveSessionTimeout(minutes, me.getLoginId());
        if (problem == null) {
            audit.record(AuditAction.ORG_UPDATE, "자동 로그아웃", minutes + "분");
        }
        ra.addFlashAttribute("message", problem == null ? "자동 로그아웃 시간을 " + minutes.trim() + "분으로 정했습니다." : problem);
        return "redirect:/admin/org";
    }

    /** 관인 이미지 등록·교체 (PNG·JPG, 1MB 이하, 내용도 형식과 맞아야 한다) */
    @PostMapping("/admin/org/seal")
    public String seal(@RequestParam(value = "file", required = false) MultipartFile file,
                       @AuthenticationPrincipal LoginUser me, RedirectAttributes ra) throws IOException {
        String name = file == null ? "" : String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("message", "관인 이미지 파일을 고르세요.");
        } else if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            ra.addFlashAttribute("message", "관인 이미지는 PNG나 JPG 파일이어야 합니다.");
        } else if (file.getSize() > MAX_SEAL_BYTES) {
            ra.addFlashAttribute("message", "관인 이미지는 1MB 이하여야 합니다.");
        } else if (files.check(file) != null) {
            ra.addFlashAttribute("message", files.check(file));
        } else {
            String old = org.get(OrgService.SEAL_FILE);
            String stored = files.save(file);
            org.put(OrgService.SEAL_FILE, stored, me.getLoginId());
            org.put(OrgService.SEAL_TYPE, name.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE, me.getLoginId());
            if (old != null) {
                files.deleteQuietly(old);
            }
            audit.record(AuditAction.ORG_SEAL, "관인", "등록: " + FileStorage.cleanName(file.getOriginalFilename()));
            ra.addFlashAttribute("message", "관인 이미지를 등록했습니다. 시행문에 찍힙니다.");
        }
        return "redirect:/admin/org";
    }

    @PostMapping("/admin/org/seal/delete")
    public String deleteSeal(@AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        String old = org.get(OrgService.SEAL_FILE);
        if (old != null) {
            org.put(OrgService.SEAL_FILE, null, me.getLoginId());
            files.deleteQuietly(old);
            audit.record(AuditAction.ORG_SEAL, "관인", "삭제");
        }
        ra.addFlashAttribute("message", "관인 이미지를 지웠습니다.");
        return "redirect:/admin/org";
    }

    /** 시행문에 찍는 관인 이미지 (로그인한 직원만) */
    @GetMapping("/org/seal")
    public ResponseEntity<Resource> sealImage() {
        String stored = org.get(OrgService.SEAL_FILE);
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }
        Path p = files.path(stored);
        if (!Files.isReadable(p)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(org.get(OrgService.SEAL_TYPE, MediaType.IMAGE_PNG_VALUE)))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(p));
    }
}
