package egovframework.haengjeongnuli.cmm.file;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 시스템 관리 › 첨부파일 점검 (관리자 전용, /admin/** ) */
@Controller
public class AdminFileController {

    private final FileCheckService check;
    private final FileStorage storage;
    private final AuditService audit;

    public AdminFileController(FileCheckService check, FileStorage storage, AuditService audit) {
        this.check = check;
        this.storage = storage;
        this.audit = audit;
    }

    @GetMapping("/admin/files")
    public String page(Model model) throws IOException {
        model.addAttribute("r", check.check());
        model.addAttribute("root", storage.getRoot().toString());
        return "user/admin-files";
    }

    @PostMapping("/admin/files/orphans/delete")
    public String deleteOrphans(RedirectAttributes ra) throws IOException {
        int n = check.removeOrphans();
        audit.record(AuditAction.FILE_ORPHAN_DELETE, "첨부 폴더", n + "개");
        ra.addFlashAttribute("message", "DB에 없는 파일 " + n + "개를 지웠습니다.");
        return "redirect:/admin/files";
    }
}
