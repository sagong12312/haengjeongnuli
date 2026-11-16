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
    private final egovframework.haengjeongnuli.approval.service.DispatchService dispatch;
    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.approval.service.ApprovalTemplateService templates;
    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.finance.service.PurchaseService purchases;

    /** 구매 건에서 품의·결의 시작: 종류·예산·업체·금액을 가져온다 */
    private void prefillPurchase(ApprovalForm form, long purchaseId, LoginUser me) {
        egovframework.haengjeongnuli.finance.service.PurchaseVO p = purchases.get(purchaseId);
        if (!purchases.canEdit(p, me) || egovframework.haengjeongnuli.finance.service.PurchaseVO.CANCELED.equals(p.getStatus())) {
            return;
        }
        form.setPurchaseId(purchaseId);
        form.setSpendType(switch (p.getPurType()) {
            case "물품" -> "물품구매";
            case "용역" -> "용역";
            case "공사" -> "공사";
            default -> "일반";
        });
        if (p.getVendor() != null) {
            form.setPayee(p.getVendor());
        }
        if (ApprovalForm.SPENDING.equals(form.getKind())) {
            form.setTitle("[지출] " + p.getTitle());
            long left = purchases.payLimit(p) - purchases.paid(purchaseId);
            if (left > 0) {
                form.setPayAmount(String.format("%,d", left));
            }
            // 이 구매 건의 결재 끝난 품의가 있으면 그 안에서 지급
            java.util.Set<Long> open = new java.util.HashSet<>();
            for (egovframework.haengjeongnuli.finance.service.CommitVO c : finance.openCommits(me.getDeptName())) {
                open.add(c.getDocId());
            }
            for (java.util.Map<String, Object> d : purchases.docs(purchaseId)) {
                Long id = ((Number) d.get("docId")).longValue();
                if (ApprovalForm.COMMIT.equals(d.get("kind")) && open.contains(id)) {
                    form.setCommitDocId(id);
                    egovframework.haengjeongnuli.finance.service.CommitVO cv = finance.commitOf(id);
                    form.setBudgetId(cv.getBudgetId());
                    break;
                }
            }
        } else {
            form.setKind(ApprovalForm.COMMIT);
            form.setTitle("[품의] " + p.getTitle());
            long amt = p.getSelectedAmount() != null ? purchases.payLimit(p) : p.getEstimatedAmount();
            form.setPayAmount(String.format("%,d", amt));
        }
        if (form.getBudgetId() == null) {
            form.setBudgetId(p.getBudgetId());
        }
    }

    public ApprovalController(ApprovalService approvalService, AuditService auditService, FileStorage fileStorage,
                              egovframework.haengjeongnuli.finance.service.FinanceService finance,
                              egovframework.haengjeongnuli.approval.service.DispatchService dispatch) {
        this.dispatch = dispatch;
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
                            @RequestParam(required = false) Long template, @RequestParam(required = false) Long commit,
                            @RequestParam(required = false) Long purchase,
                            @RequestParam(required = false) java.util.List<Long> assets, Model model) {
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
        if (commit != null) {
            // 품의에서 지출결의 시작: 종류·예산·거래처·제목을 가져온다
            ApprovalDocVO c = approvalService.get(commit);
            egovframework.haengjeongnuli.finance.service.CommitVO cv = finance.commitOf(commit);
            if (c != null && cv != null && c.canView(me) && !cv.isClosed() && me.dept().equals(cv.getDeptName())) {
                form.setKind(ApprovalForm.SPENDING);
                form.setCommitDocId(commit);
                form.setBudgetId(cv.getBudgetId());
                form.setSpendType(cv.getSpendType() == null ? "일반" : cv.getSpendType());
                form.setPayee(c.getPayee());
                form.setTitle("[지출] " + c.getTitle());
                form.setSpendTarget(c.getSpendTarget());
            }
        }
        if (purchase != null) {
            prefillPurchase(form, purchase, me);
        }
        if (assets != null && !assets.isEmpty()) {
            // 물품대장에서 고른 물품의 불용 결정 기안
            String body = purchases.idleDraftBody(assets, me);
            if (body != null) {
                form.setKind(ApprovalForm.IDLE);
                form.setTitle("물품 불용 결정 (" + egovframework.haengjeongnuli.finance.service.PurchaseService.assetNumbersIn(body).size() + "건)");
                form.setBody(body);
            }
        }
        if (template != null) {
            egovframework.haengjeongnuli.approval.service.ApprovalTemplateVO t = templates.get(template, me);
            if (t != null) {
                form.setKind(t.getKind());
                if (t.getRetention() != null) {
                    form.setRetention(t.getRetention());
                }
                form.setTitle(t.getTitle());
                form.setBody(t.getBody());
                model.addAttribute("message", "'" + t.getName() + "' 서식을 불러왔습니다. 결재선과 수신자는 직접 정하세요.");
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
        if (d.canDispatch(me)) {
            model.addAttribute("dispatches", dispatch.list(docId));
            model.addAttribute("dispatchMethods", egovframework.haengjeongnuli.approval.service.DispatchVO.METHODS);
        }
        if (d.isCommitment() && ApprovalDocVO.DONE.equals(d.getStatus())) {
            egovframework.haengjeongnuli.finance.service.CommitVO c = finance.commitOf(docId);
            model.addAttribute("commit", c);
            model.addAttribute("commitSpends", c == null ? java.util.List.of() : finance.commitSpends(docId));
            model.addAttribute("canUseCommit", c != null && !c.isClosed() && me.dept().equals(c.getDeptName()));
        }
        if (d.isBudgetDoc()) {
            model.addAttribute("budget", finance.get(d.getBudgetId()));
            model.addAttribute("purchase", d.getPurchaseId() == null ? null : purchases.get(d.getPurchaseId()));
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

    /**
     * 시행문: 결재가 끝난 발신 문서를 수신자별로 출력한다 (행정기관명, 수신, 제목, 본문, 발신명의와 관인,
     * 결재선 서명과 전결·대결 표시, 시행 번호·일자, 주소·연락처, 공개 구분).
     */
    @GetMapping("/{docId}/sihaeng")
    public String sihaeng(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                          @RequestParam(required = false) Integer to, Model model) {
        ApprovalDocVO d = visible(docId, me);
        if (!ApprovalDocVO.DONE.equals(d.getStatus()) || !d.isExternal()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        java.util.List<String> receivers = d.getReceiverList();
        String receiver = to != null && to >= 0 && to < receivers.size() ? receivers.get(to)
                : (receivers.size() == 1 ? receivers.get(0) : "수신자 참조");
        model.addAttribute("doc", d);
        model.addAttribute("receiver", receiver);
        model.addAttribute("receivers", receivers);
        model.addAttribute("showList", to == null && receivers.size() > 1);
        auditService.record(AuditAction.APPROVAL_SIHAENG, "결재 " + docId, receiver);
        return "approval/sihaeng";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.approval.service.ApprovalHwpService hwpService;

    /** 결재 문서를 한글(HWP) 파일로 내려받기. to 를 주면 그 수신자의 시행문 */
    @GetMapping("/{docId}/hwp")
    public org.springframework.http.ResponseEntity<byte[]> hwp(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                                                              @RequestParam(required = false) Integer to) throws Exception {
        ApprovalDocVO d = visible(docId, me);
        String receiver = null;
        if (to != null) {
            java.util.List<String> rs = d.getReceiverList();
            if (!ApprovalDocVO.DONE.equals(d.getStatus()) || to < 0 || to >= rs.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            receiver = rs.get(to);
        }
        byte[] bytes = hwpService.make(d, receiver);
        auditService.record(AuditAction.APPROVAL_HWP, "결재 " + docId, receiver == null ? "문서" : "시행문 " + receiver);
        String name = (d.getDocNo() == null ? "기안" : d.getDocNo()) + "_" + d.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_")
                + (receiver == null ? "" : "_" + receiver) + ".hwp";
        if (name.length() > 120) {
            name = name.substring(0, 116) + ".hwp";
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/x-hwp"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    @PostMapping("/{docId}/dispatch")
    public String recordDispatch(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                                 @RequestParam(required = false) String receiver, @RequestParam(required = false) String method,
                                 @RequestParam(required = false) String note, RedirectAttributes ra) {
        ApprovalDocVO d = visible(docId, me);
        String problem = dispatch.record(d, receiver, method, note, me);
        if (problem == null) {
            auditService.record(AuditAction.APPROVAL_DISPATCH, "결재 " + docId, receiver + " · " + method);
        }
        ra.addFlashAttribute("message", problem == null ? receiver + "에 " + method + "(으)로 발송했다고 기록했습니다." : problem);
        return "redirect:/approval/" + docId + "#dispatch";
    }

    /** 지출품의 남은 금액 해제 (낙찰 차액·사업 취소 등) */
    @PostMapping("/{docId}/commit/close")
    public String closeCommit(@AuthenticationPrincipal LoginUser me, @PathVariable long docId,
                              @RequestParam(required = false) String note, RedirectAttributes ra) {
        visible(docId, me);
        String problem = finance.closeCommit(docId, note, me);
        if (problem == null) {
            auditService.record(AuditAction.FIN_COMMIT_CLOSE, "품의 " + docId, note);
        }
        ra.addFlashAttribute("message", problem == null ? "남은 금액을 해제했습니다. 예산의 쓸 수 있는 금액으로 돌아갔습니다." : problem);
        return "redirect:/approval/" + docId;
    }

    /** 기안 서식: 목록, 지금 쓰는 기안 내용을 서식으로 저장, 지우기 */
    @GetMapping("/templates")
    public String templateList(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("templates", templates.visible(me));
        model.addAttribute("scopes", egovframework.haengjeongnuli.approval.service.ApprovalTemplateVO.SCOPES);
        model.addAttribute("kinds", ApprovalForm.KINDS);
        return "approval/templates";
    }

    @PostMapping("/templates")
    public String templateSave(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String templateName,
                               @RequestParam(required = false) String templateScope, @RequestParam(required = false) String kind,
                               @RequestParam(required = false) String retention, @RequestParam(required = false) String title,
                               @RequestParam(required = false) String body, RedirectAttributes ra) {
        String problem = templates.save(templateName, templateScope == null ? "MINE" : templateScope, kind, retention, title, body, me);
        if (problem == null) {
            auditService.record(AuditAction.APPROVAL_TEMPLATE, templateName, "저장");
        }
        ra.addFlashAttribute("message", problem == null ? "'" + templateName.trim() + "' 서식을 저장했습니다." : problem);
        return "redirect:/approval/templates";
    }

    @PostMapping("/templates/{id}/delete")
    public String templateDelete(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        boolean ok = templates.delete(id, me);
        if (ok) {
            auditService.record(AuditAction.APPROVAL_TEMPLATE, "서식 " + id, "삭제");
        }
        ra.addFlashAttribute("message", ok ? "서식을 지웠습니다." : "만든 사람이나 관리자만 지울 수 있습니다.");
        return "redirect:/approval/templates";
    }

    /** 대결자 지정: 휴가·출장 등으로 결재할 수 없는 기간에 대신 결재(대결)할 직원 */
    @GetMapping("/proxy")
    public String proxyPage(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("proxies", approvalService.proxies(me));
        model.addAttribute("candidates", approvalService.approverCandidates(me));
        model.addAttribute("reasons", egovframework.haengjeongnuli.approval.service.ApprovalProxyVO.REASONS);
        model.addAttribute("today", java.time.LocalDate.now());
        return "approval/proxy";
    }

    @PostMapping("/proxy")
    public String addProxy(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String proxyLoginId,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
                           @RequestParam(required = false) String reason, RedirectAttributes ra) {
        String problem = approvalService.addProxy(me, proxyLoginId, startDate, endDate, reason);
        if (problem == null) {
            auditService.record(AuditAction.APPROVAL_PROXY, proxyLoginId, startDate + "~" + endDate + " " + reason);
        }
        ra.addFlashAttribute("message", problem == null ? "대결자를 지정했습니다. 그 기간에 결재할 문서는 대결자도 결재할 수 있습니다." : problem);
        return "redirect:/approval/proxy";
    }

    @PostMapping("/proxy/{proxyId}/cancel")
    public String cancelProxy(@AuthenticationPrincipal LoginUser me, @PathVariable long proxyId, RedirectAttributes ra) {
        boolean ok = approvalService.cancelProxy(me, proxyId);
        if (ok) {
            auditService.record(AuditAction.APPROVAL_PROXY, "지정 " + proxyId, "취소");
        }
        ra.addFlashAttribute("message", ok ? "대결자 지정을 취소했습니다." : "취소할 수 없는 지정입니다.");
        return "redirect:/approval/proxy";
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
            boolean proxied = d.getLines().stream().anyMatch(l -> me.getLoginId().equals(l.getActedById()));
            auditService.record(AuditAction.APPROVAL_APPROVE, "결재 " + docId, (proxied ? "[대결] " : "") + d.getTitle());
            ra.addFlashAttribute("message", ApprovalDocVO.DONE.equals(d.getStatus())
                    ? "결재했습니다. 결재가 끝나 문서번호 " + d.getDocNo() + "이(가) 붙었습니다."
                        + (d.isSpending() ? String.format(" 지출 %,d원을 예산 집행으로 기록했습니다.", d.getPayAmount())
                            : d.isCommitment() ? String.format(" 품의 금액 %,d원만큼 예산을 묶었습니다.", d.getPayAmount()) : "")
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
    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.cmm.hwp.HwpViewSupport hwpView;

    @GetMapping("/files/{fileId}/view")
    public String viewFile(@AuthenticationPrincipal LoginUser me, @PathVariable long fileId, Model model) {
        ApprovalFileVO f = approvalService.fileForDownload(fileId, me);
        return hwpView.view(f == null ? null : f.getStoredName(), f == null ? "" : f.getOriginalName(), "결재 붙임 " + fileId,
                "/approval/files/" + fileId, f == null ? "/approval" : "/approval/" + f.getDocId(), "approval", model);
    }

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
            List<MultipartFile> real = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
            if (real.size() > fileStorage.getMaxCount()) {
                result.reject("files", "파일은 한 번에 " + fileStorage.getMaxCount() + "개까지 올릴 수 있습니다.");
                return;
            }
            for (MultipartFile f : real) {
                String problem = fileStorage.check(f);
                if (problem != null) {
                    result.reject("files", problem);
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
        model.addAttribute("maxCooperators", ApprovalForm.MAX_COOPERATORS);
        model.addAttribute("templates", templates.visible(me));
        model.addAttribute("payMethods", ApprovalForm.PAY_METHODS);
        model.addAttribute("spendTypes", ApprovalForm.SPEND_TYPES);
        model.addAttribute("commits", finance.openCommits(me.getDeptName()));
        model.addAttribute("purchases", purchases.openForDept(me.dept()));
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
