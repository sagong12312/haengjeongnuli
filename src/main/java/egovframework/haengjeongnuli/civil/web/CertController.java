package egovframework.haengjeongnuli.civil.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.civil.service.CertIssueVO;
import egovframework.haengjeongnuli.civil.service.CertService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/** 민원 › 증명 발급대장 (창구 직원·관리자) */
@Controller
public class CertController {

    private final CertService certs;
    private final AuditService audit;

    public CertController(CertService certs, AuditService audit) {
        this.certs = certs;
        this.audit = audit;
    }

    @GetMapping("/civil/certs")
    public String list(@AuthenticationPrincipal LoginUser me,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day, Model model) {
        if (!certs.canUse(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        LocalDate d = day == null || day.isAfter(LocalDate.now()) ? LocalDate.now() : day;
        List<CertIssueVO> rows = certs.ofDay(d);
        model.addAttribute("day", d);
        model.addAttribute("rows", rows);
        model.addAttribute("totalCopies", rows.stream().mapToInt(CertIssueVO::getCopies).sum());
        model.addAttribute("totalFee", rows.stream().mapToLong(CertIssueVO::getFee).sum());
        model.addAttribute("fees", CertIssueVO.FEES);
        model.addAttribute("applicantTypes", CertIssueVO.APPLICANT_TYPES);
        return "civil/certs";
    }

    @PostMapping("/civil/certs")
    public String issue(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String certType,
                        @RequestParam(required = false) String applicantName, @RequestParam(required = false) String applicantType,
                        @RequestParam(required = false) String purpose, @RequestParam(required = false) Integer copies,
                        @RequestParam(defaultValue = "false") boolean idChecked, @RequestParam(defaultValue = "false") boolean exempt,
                        @RequestParam(required = false) String exemptReason, RedirectAttributes ra) {
        if (!certs.canUse(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        CertService.Result r = certs.issue(certType, applicantName, applicantType, purpose, copies, idChecked, exempt, exemptReason, me);
        if (r.ok()) {
            audit.record(AuditAction.CERT_ISSUE, "증명 " + r.issueNo(), certType + " " + copies + "통");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/civil/certs";
    }
}
