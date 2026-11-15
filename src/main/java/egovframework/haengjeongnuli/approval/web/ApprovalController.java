package egovframework.haengjeongnuli.approval.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalFileVO;
import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.approval.service.ApprovalService.Result;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import jakarta.servlet.http.HttpServletResponse;

/** 전자결재: 문서함, 기안, 결재·반려, 회수, 접수 */
@Controller
@RequestMapping("/approval")
public class ApprovalController {

    public record Box(String id, String label, String desc, String status, String badge) {}

    static final List<Box> BOXES = List.of(
            new Box("wait", "결재 대기", "내가 결재할 문서", "결재 대기", "badge-orange"),
            new Box("prog", "진행 중", "내가 올렸거나 결재한 문서", "진행 중", "badge-blue"),
            new Box("done", "완료", "결재가 끝난 문서", "완료", "badge-green"),
            new Box("back", "반려", "돌려받은 문서", "반려", "badge-red"),
            new Box("recv", "수신 문서", "우리 부서로 온 문서", "접수 대기", "badge-gray"),
            new Box("temp", "임시저장", "아직 올리지 않은 문서", "임시저장", "badge-gray"));

    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final FileStorage fileStorage;
    private final egovframework.haengjeongnuli.finance.service.FinanceService finance;

    public ApprovalController(ApprovalService approvalService, AuditService auditService, FileStorage fileStorage,
                              egovframework.haengjeongnuli.finance.service.FinanceService finance) {
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.fileStorage = fileStorage;
        this.finance = finance;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me,
                       @RequestParam(defaultValue = "wait") String box,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "all") String period,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        Box current = BOXES.stream().filter(b -> b.id().equals(box)).findFirst().orElse(BOXES.get(0));
        ApprovalSearchVO s = ApprovalSearchVO.of(current.id(), me);
        s.setQ(q);
        s.setPeriod(List.of("1m", "3m", "year", "all").contains(period) ? period : "all");
        int total = approvalService.count(s);
        int totalPages = Math.max(1, (total + ApprovalSearchVO.PAGE_SIZE - 1) / ApprovalSearchVO.PAGE_SIZE);
        s.setPage(Math.min(page, totalPages));

        model.addAttribute("boxes", BOXES);
        model.addAttribute("counts", approvalService.boxCounts(me));
        model.addAttribute("current", current);
        model.addAttribute("docs", approvalService.list(s));
        model.addAttribute("total", total);
        model.addAttribute("q", s.getQ());
        model.addAttribute("period", s.getPeriod());
        model.addAttribute("page", s.getPage());
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageFrom", Math.max(1, s.getPage() - 4));
        model.addAttribute("pageTo", Math.min(totalPages, Math.max(1, s.getPage() - 4) + 9));
        return "approval/list";
    }

    /** 새 기안. from 을 주면 그 문서(반려·완료 등 내가 볼 수 있는 문서)의 내용을 옮겨 온다 */
    @GetMapping("/draft")
    public String draftForm(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long from,
                            @RequestParam(required = false) String kind, @RequestParam(required = false) Long budgetId,
                            Model model) {
        ApprovalForm form = new ApprovalForm();
        if (kind != null && ApprovalForm.KINDS.contains(kind)) {
            form.setKind(kind);
        }
        form.setBudgetId(budgetId);
        if (from != null) {
            ApprovalDocVO src = approvalService.get(from);
            if (src != null && src.isDraftedBy(me)) {
                form = ApprovalForm.from(src);
            }
        }
        model.addAttribute("approvalForm", form);
        return formView(me, null, model);
    }

    @PostMapping("/draft")
    public String draftSubmit(@AuthenticationPrincipal LoginUser me,
                              @ModelAttribute("approvalForm") ApprovalForm form, BindingResult result,
                              @RequestParam(defaultValue = "send") String action,
                              @RequestParam(value = "files", required = false) List<MultipartFile> files,
                              Model model, RedirectAttributes ra, HttpServletResponse response) throws IOException {
        boolean submit = !"save".equals(action);
        check(form, submit, files, me, result, null);
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return formView(me, null, model);
        }
        long id = approvalService.create(form, submit, files, me);
        return afterSave(id, form, submit, ra);
    }

    @GetMapping("/{docId}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long docId, Model model) {
        ApprovalDocVO d = visible(docId, me);
        if (ApprovalDocVO.DRAFT.equals(d.getStatus())) {
            return "redirect:/approval/" + docId + "/edit";
        }
        model.addAttribute("doc", d);
        if (d.isSpending()) {
            model.addAttribute("budget", finance.get(d.getBudgetId()));
            model.addAttribute("payText", egovframework.haengjeongnuli.cmm.KoreanAmount.official(d.getPayAmount() == null ? 0 : d.getPayAmount()));
        }
        return "approval/view";
    }

    @GetMapping("/{docId}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                           Model model, RedirectAttributes ra) {
        ApprovalDocVO d = visible(docId, me);
        if (!ApprovalDocVO.DRAFT.equals(d.getStatus())) {
            ra.addFlashAttribute("message", "임시저장 문서만 고칠 수 있습니다.");
            return "redirect:/approval/" + docId;
        }
        if (ApprovalForm.DUTY.equals(d.getKind())) {
            ra.addFlashAttribute("message", "회수한 복무 신청은 인사 화면에서 새로 신청하세요.");
            return "redirect:/hr";
        }
        model.addAttribute("approvalForm", ApprovalForm.from(d));
        return formView(me, d, model);
    }

    @PostMapping("/{docId}/edit")
    public String editSubmit(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                             @ModelAttribute("approvalForm") ApprovalForm form, BindingResult result,
                             @RequestParam(defaultValue = "send") String action,
                             @RequestParam(value = "files", required = false) List<MultipartFile> files,
                             @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds,
                             Model model, RedirectAttributes ra, HttpServletResponse response) throws IOException {
        ApprovalDocVO d = visible(docId, me);
        boolean submit = !"save".equals(action);
        check(form, submit, files, me, result, docId);
        if (result.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return formView(me, d, model);
        }
        Result r = approvalService.update(docId, form, submit, files, removeFileIds, me);
        if (r != Result.OK) {
            ra.addFlashAttribute("message", "이미 상신한 문서라 고칠 수 없습니다.");
            return "redirect:/approval/" + docId;
        }
        return afterSave(docId, form, submit, ra);
    }

    @PostMapping("/{docId}/approve")
    public String approve(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                          @RequestParam(required = false) String opinion, RedirectAttributes ra) {
        visible(docId, me);
        if (opinion != null && opinion.length() > 1000) {
            ra.addFlashAttribute("message", "의견은 1,000자 이내로 입력하세요.");
            return "redirect:/approval/" + docId;
        }
        Result r = approvalService.approve(docId, opinion, me);
        if (r == Result.OK) {
            ApprovalDocVO d = approvalService.get(docId);
            auditService.record(AuditAction.APPROVAL_APPROVE, "결재 " + docId, d.getTitle());
            ra.addFlashAttribute("message", ApprovalDocVO.DONE.equals(d.getStatus())
                    ? "결재했습니다. 결재가 끝나 문서번호 " + d.getDocNo() + "이(가) 붙었습니다."
                        + (d.isSpending() ? String.format(" 지출 %,d원을 예산 집행으로 기록했습니다.", d.getPayAmount()) : "")
                    : "결재했습니다. 다음 결재자에게 넘어갔습니다.");
            return "redirect:/approval?box=wait";
        }
        ra.addFlashAttribute("message", r == Result.BUDGET_SHORT
                ? "결재를 마칠 수 없습니다: " + approvalService.spendingProblem(docId) + " 반려해서 기안자가 금액을 고치게 하세요."
                : "지금 결재할 차례가 아닙니다.");
        return "redirect:/approval/" + docId;
    }

    @PostMapping("/{docId}/reject")
    public String reject(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                         @RequestParam(required = false) String opinion, RedirectAttributes ra) {
        visible(docId, me);
        if (opinion == null || opinion.isBlank() || opinion.length() > 1000) {
            ra.addFlashAttribute("message", "반려 사유를 1,000자 이내로 입력하세요.");
            return "redirect:/approval/" + docId;
        }
        Result r = approvalService.reject(docId, opinion, me);
        if (r == Result.OK) {
            auditService.record(AuditAction.APPROVAL_REJECT, "결재 " + docId, opinion);
            ra.addFlashAttribute("message", "반려했습니다. 기안자에게 알렸습니다.");
            return "redirect:/approval?box=wait";
        }
        ra.addFlashAttribute("message", "지금 결재할 차례가 아닙니다.");
        return "redirect:/approval/" + docId;
    }

    @PostMapping("/{docId}/withdraw")
    public String withdraw(@AuthenticationPrincipal LoginUser me, @PathVariable long docId, RedirectAttributes ra) {
        visible(docId, me);
        if (approvalService.withdraw(docId, me) == Result.OK) {
            auditService.record(AuditAction.APPROVAL_WITHDRAW, "결재 " + docId, null);
            ra.addFlashAttribute("message", "회수했습니다. 고친 뒤 다시 상신할 수 있습니다.");
            return "redirect:/approval/" + docId + "/edit";
        }
        ra.addFlashAttribute("message", "이미 결재가 진행되어 회수할 수 없습니다.");
        return "redirect:/approval/" + docId;
    }

    @PostMapping("/{docId}/receive")
    public String receive(@AuthenticationPrincipal LoginUser me, @PathVariable long docId, RedirectAttributes ra) {
        visible(docId, me);
        if (approvalService.receive(docId, me) == Result.OK) {
            auditService.record(AuditAction.APPROVAL_RECEIVE, "결재 " + docId, me.dept());
            ra.addFlashAttribute("message", "접수했습니다. 접수번호 " + approvalService.get(docId).getReceiptNo());
        } else {
            ra.addFlashAttribute("message", "접수할 수 없는 문서입니다.");
        }
        return "redirect:/approval/" + docId;
    }

    @PostMapping("/{docId}/delete")
    public String delete(@AuthenticationPrincipal LoginUser me, @PathVariable long docId, RedirectAttributes ra) {
        visible(docId, me);
        if (approvalService.deleteDraft(docId, me) == Result.OK) {
            auditService.record(AuditAction.APPROVAL_DELETE, "결재 " + docId, null);
            ra.addFlashAttribute("message", "임시저장 문서를 지웠습니다.");
            return "redirect:/approval?box=temp";
        }
        ra.addFlashAttribute("message", "임시저장 문서만 지울 수 있습니다.");
        return "redirect:/approval/" + docId;
    }

    /** 붙임 내려받기. 항상 '내려받기'로 보낸다. */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser me, @PathVariable long fileId) {
        ApprovalFileVO f = approvalService.fileForDownload(fileId, me);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorage.path(f.getStoredName());
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        auditService.record(AuditAction.FILE_DOWNLOAD, "결재 붙임 " + fileId, f.getOriginalName());
        ContentDisposition cd = ContentDisposition.attachment().filename(f.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    /** 문서가 없거나 볼 수 없으면 404 (있는지도 알리지 않는다) */
    private ApprovalDocVO visible(long docId, LoginUser me) {
        ApprovalDocVO d = approvalService.get(docId);
        if (d == null || !d.canView(me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return d;
    }

    private void check(ApprovalForm form, boolean submit, List<MultipartFile> files, LoginUser me,
                       BindingResult result, Long docId) {
        for (Map.Entry<String, String> e : approvalService.validate(form, submit, me, docId).entrySet()) {
            result.rejectValue(e.getKey(), e.getKey(), e.getValue());
        }
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty() && !fileStorage.isAllowed(FileStorage.cleanName(f.getOriginalFilename()))) {
                    result.reject("files", "올릴 수 없는 형식입니다: " + FileStorage.cleanName(f.getOriginalFilename()));
                }
            }
        }
    }

    private String afterSave(long docId, ApprovalForm form, boolean submit, RedirectAttributes ra) {
        if (submit) {
            auditService.record(AuditAction.APPROVAL_SUBMIT, "결재 " + docId, form.getTitle());
            ra.addFlashAttribute("message", "결재를 상신했습니다. 진행 상황은 '진행 중' 문서함에서 볼 수 있습니다.");
            return "redirect:/approval/" + docId;
        }
        auditService.record(AuditAction.APPROVAL_SAVE, "결재 " + docId, form.getTitle());
        ra.addFlashAttribute("message", "임시저장했습니다.");
        return "redirect:/approval/" + docId + "/edit";
    }

    private String formView(LoginUser me, ApprovalDocVO doc, Model model) {
        model.addAttribute("doc", doc);
        model.addAttribute("candidates", approvalService.approverCandidates(me));
        model.addAttribute("kinds", ApprovalForm.KINDS);
        model.addAttribute("retentions", ApprovalForm.RETENTIONS);
        model.addAttribute("disclosures", ApprovalForm.DISCLOSURES);
        model.addAttribute("maxReviewers", ApprovalForm.MAX_REVIEWERS);
        model.addAttribute("payMethods", ApprovalForm.PAY_METHODS);
        // 지출결의에 고를 수 있는 예산과 지금 쓸 수 있는 금액
        Long selfId = doc == null ? null : doc.getDocId();
        java.util.List<egovframework.haengjeongnuli.finance.service.BudgetVO> spendable =
                finance.spendable(me, java.time.Year.now().getValue());
        model.addAttribute("spendable", spendable);
        model.addAttribute("available", spendable.stream().collect(java.util.stream.Collectors.toMap(
                egovframework.haengjeongnuli.finance.service.BudgetVO::getBudgetId, b -> finance.available(b.getBudgetId(), selfId))));
        model.addAttribute("allowedExt", String.join(", ", fileStorage.getAllowedExtensions().stream().sorted().toList()));
        return "approval/draft";
    }
}
