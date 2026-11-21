package egovframework.haengjeongnuli.cmm.mark;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/**
 * 유출 추적 (관리자): 밖에서 발견된 파일이나 글을 넣으면 숨은 추적 번호를 찾아 누가 내려받은 것인지 보여 준다.
 * 넣은 파일·글은 저장하지 않는다.
 */
@Controller
public class LeakTraceController {

    private static final long MAX_FILE = 20L * 1024 * 1024;
    private static final int MAX_TEXT = 200_000;

    private final DownloadMarkService marks;
    private final AuditService audit;

    public LeakTraceController(DownloadMarkService marks, AuditService audit) {
        this.marks = marks;
        this.audit = audit;
    }

    @GetMapping("/admin/download-marks")
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("recent", marks.recent(q));
        return "user/admin-download-marks";
    }

    @PostMapping("/admin/download-marks/trace")
    public String trace(@RequestParam(required = false) String text, @RequestParam(required = false) MultipartFile file, Model model) {
        StringBuilder all = new StringBuilder();
        String error = null;
        if (text != null) {
            all.append(text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
        }
        String fileName = null;
        if (file != null && !file.isEmpty()) {
            fileName = file.getOriginalFilename();
            if (file.getSize() > MAX_FILE) {
                error = "파일이 너무 큽니다 (20MB까지).";
            } else {
                try {
                    byte[] b = file.getBytes();
                    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".hwp")) {
                        all.append('\n').append(kr.dogfoot.hwplib.tool.textextractor.TextExtractor.extract(
                                kr.dogfoot.hwplib.reader.HWPReader.fromInputStream(new ByteArrayInputStream(b)),
                                kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText));
                    } else {
                        all.append('\n').append(new String(b, StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    error = "파일을 읽지 못했습니다. 한글(.hwp), 엑셀(.csv), 글(.txt) 파일을 넣거나 내용을 복사해 붙여 넣으세요.";
                }
            }
        }
        Set<String> codes = DownloadMark.find(all.toString());
        model.addAttribute("q", "");
        model.addAttribute("recent", marks.recent(null));
        model.addAttribute("traced", true);
        model.addAttribute("traceError", error);
        model.addAttribute("codes", codes);
        model.addAttribute("hits", marks.lookup(codes));
        audit.record(AuditAction.LEAK_TRACE, fileName == null ? "붙여 넣은 글" : fileName, codes.isEmpty() ? "찾지 못함" : "추적 번호 " + String.join(",", codes));
        return "user/admin-download-marks";
    }
}
