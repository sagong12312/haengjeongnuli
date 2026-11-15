package egovframework.haengjeongnuli.cmm.hwp;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 첨부 한글 문서를 화면에서 보기 (권한 확인은 각 기능의 내려받기와 같다) */
@Component
public class HwpViewSupport {

    private final HwpViewer viewer;
    private final FileStorage storage;
    private final AuditService audit;

    public HwpViewSupport(HwpViewer viewer, FileStorage storage, AuditService audit) {
        this.viewer = viewer;
        this.storage = storage;
        this.audit = audit;
    }

    /**
     * @param storedName 저장 이름 (볼 수 없으면 null 을 넘겨 404)
     */
    public String view(String storedName, String originalName, String target, String downloadUrl, String backUrl,
                       String active, Model model) {
        if (storedName == null || !HwpViewer.supports(originalName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path p = storage.path(storedName);
        if (!Files.isReadable(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        HwpViewer.Result r = viewer.render(p, originalName);
        audit.record(AuditAction.FILE_VIEW, target, originalName);
        model.addAttribute("fileName", originalName);
        model.addAttribute("html", r.html());
        model.addAttribute("message", r.message());
        model.addAttribute("downloadUrl", downloadUrl);
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("active", active);
        return "cmm/file-view";
    }
}
