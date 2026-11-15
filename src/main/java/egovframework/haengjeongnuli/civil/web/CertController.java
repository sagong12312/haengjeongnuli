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

    /** 증명 발급 일계표(day) 또는 월계표(month=yyyy-MM): 발급 목록과 종류별 합계 */
    @GetMapping("/civil/certs.csv")
    public org.springframework.http.ResponseEntity<byte[]> csv(@AuthenticationPrincipal LoginUser me,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                                              @RequestParam(required = false) String month) {
        if (!certs.canUse(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        LocalDate from;
        LocalDate to;
        String label;
        if (month != null && month.matches("\\d{4}-\\d{2}")) {
            from = LocalDate.parse(month + "-01");
            to = from.plusMonths(1);
            label = month + "_월계표";
        } else {
            from = day == null ? LocalDate.now() : day;
            to = from.plusDays(1);
            label = from + "_일계표";
        }
        List<CertIssueVO> rows = certs.between(from, to);
        egovframework.haengjeongnuli.cmm.text.Csv csv = egovframework.haengjeongnuli.cmm.text.Csv.of(
                "발급번호", "발급일시", "증명 종류", "신청인", "신청인 구분", "용도", "통수", "수수료", "면제 사유", "발급자");
        java.util.Map<String, long[]> byType = new java.util.TreeMap<>();
        for (CertIssueVO r : rows) {
            csv.row(r.getIssueNo(), r.getIssuedAt().toString().replace('T', ' '), r.getCertType(), r.getApplicantName(),
                    r.getApplicantType(), r.getPurpose(), r.getCopies(), r.getFee(), r.getExemptReason(), r.getIssuedName());
            long[] t = byType.computeIfAbsent(r.getCertType(), k -> new long[3]);
            t[0]++;
            t[1] += r.getCopies();
            t[2] += r.getFee();
        }
        csv.row();
        csv.row("종류별 합계", "", "증명 종류", "", "", "건수", "통수", "수수료");
        long[] all = new long[3];
        for (java.util.Map.Entry<String, long[]> e : byType.entrySet()) {
            csv.row("", "", e.getKey(), "", "", e.getValue()[0], e.getValue()[1], e.getValue()[2]);
            for (int i = 0; i < 3; i++) {
                all[i] += e.getValue()[i];
            }
        }
        csv.row("합계", "", "", "", "", all[0], all[1], all[2]);
        audit.record(AuditAction.CERT_EXPORT, label, rows.size() + "건");
        return csv.download("증명발급_" + label + ".csv");
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
