package egovframework.haengjeongnuli.finance.web;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.KoreanAmount;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.FinCodeVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.finance.service.FinanceService.Result;
import egovframework.haengjeongnuli.finance.service.ProgramVO;
import egovframework.haengjeongnuli.finance.service.TxnVO;
import jakarta.servlet.http.HttpServletResponse;

/** 재정·세무: 세출 예산 편성(area=fin, 집행은 전자결재 지출결의로), 세입 예산·수납(area=rev, 세입 담당만) */
@Controller
public class FinanceController {

    private final FinanceService finance;
    private final AuditService auditService;
    private final egovframework.haengjeongnuli.finance.service.BudgetChangeService changes;

    public FinanceController(FinanceService finance, AuditService auditService,
                             egovframework.haengjeongnuli.finance.service.BudgetChangeService changes) {
        this.changes = changes;
        this.finance = finance;
        this.auditService = auditService;
    }

    @GetMapping("/finance")
    public String index(@AuthenticationPrincipal LoginUser me,
                        @RequestParam(defaultValue = "fin") String area,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) String dept,
                        Model model, HttpServletResponse response) {
        int y = year(year);
        boolean rev = "rev".equals(area) || "tax".equals(area);
        common(model, me, y);
        model.addAttribute("area", rev ? "rev" : "fin");
        if (rev) {
            if (!finance.canRevenue(me)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                model.addAttribute("denied", true);
                return "finance/index";
            }
            List<BudgetVO> budgets = finance.budgets(BudgetVO.REV, y, null, null);
            Map<String, Long> receipts = finance.receiptsByCode(y);
            long approved = budgets.stream().filter(BudgetVO::isApproved).mapToLong(BudgetVO::getAmount).sum();
            long received = receipts.values().stream().mapToLong(Long::longValue).sum();
            model.addAttribute("budgets", budgets);
            model.addAttribute("receipts", receipts);
            model.addAttribute("approvedTotal", approved);
            model.addAttribute("usedTotal", received);
            model.addAttribute("rate", approved == 0 ? 0 : Math.round(received * 1000.0 / approved) / 10.0);
            model.addAttribute("recent", finance.recentTxns(BudgetVO.REV, y, 20));
            model.addAttribute("revItems", finance.codes(FinCodeVO.REV).stream()
                    .filter(c -> "목".equals(c.getCodeLevel())).toList());
            model.addAttribute("pending", finance.budgets(BudgetVO.REV, y, null, BudgetVO.REQUESTED).stream()
                    .filter(b -> finance.canReview(b, me)).count());
            return "finance/index";
        }
        boolean all = finance.canSeeAllExp(me);
        String filter = all ? (dept == null || dept.isBlank() ? null : dept) : me.dept();
        List<BudgetVO> budgets = finance.budgets(BudgetVO.EXP, y, filter, null);
        long approved = budgets.stream().filter(BudgetVO::isApproved).mapToLong(BudgetVO::getAmount).sum();
        long used = budgets.stream().mapToLong(BudgetVO::getUsed).sum();
        model.addAttribute("budgets", budgets);
        model.addAttribute("seeAll", all);
        model.addAttribute("deptFilter", filter);
        model.addAttribute("depts", finance.details(y, null).stream().map(ProgramVO::getDeptName).distinct().sorted().toList());
        model.addAttribute("approvedTotal", approved);
        model.addAttribute("usedTotal", used);
        model.addAttribute("rate", approved == 0 ? 0 : Math.round(used * 1000.0 / approved) / 10.0);
        model.addAttribute("myDetails", finance.details(y, me.dept()).size());
        model.addAttribute("pending", finance.budgets(BudgetVO.EXP, y, null, BudgetVO.REQUESTED).stream()
                .filter(b -> finance.canReview(b, me)).count());
        return "finance/index";
    }

    // ===== 사업 구조 =====

    /** 세출예산 집행 현황 CSV (보는 범위는 화면과 같다) */
    @GetMapping("/finance/budgets.csv")
    public org.springframework.http.ResponseEntity<byte[]> budgetsCsv(@AuthenticationPrincipal LoginUser me,
                                                                     @RequestParam(required = false) Integer year,
                                                                     @RequestParam(required = false) String dept) {
        int y = year(year);
        String filter = finance.canSeeAllExp(me) ? (dept == null || dept.isBlank() ? null : dept) : me.dept();
        egovframework.haengjeongnuli.cmm.text.Csv csv = egovframework.haengjeongnuli.cmm.text.Csv.of(
                "회계연도", "부서", "정책사업", "단위사업", "세부사업", "통계목", "과목명", "상태", "예산액", "집행액", "남은 금액", "집행률(%)");
        long amt = 0, used = 0;
        for (BudgetVO b : finance.budgets(BudgetVO.EXP, y, filter, null)) {
            csv.row(b.getFiscalYear(), b.getDeptName(), b.getPolicyName(), b.getUnitName(), b.getDetailName(), b.getCode(),
                    b.getCodeName(), b.getStatusLabel(), b.getAmount(), b.getUsed(), b.getAmount() - b.getUsed(),
                    b.getAmount() == 0 ? 0 : Math.round(b.getUsed() * 1000.0 / b.getAmount()) / 10.0);
            if (b.isApproved()) {
                amt += b.getAmount();
                used += b.getUsed();
            }
        }
        csv.row("합계(승인)", "", "", "", "", "", "", "", amt, used, amt - used, amt == 0 ? 0 : Math.round(used * 1000.0 / amt) / 10.0);
        auditService.record(AuditAction.FIN_EXPORT, "세출예산 " + y, filter == null ? "전체" : filter);
        return csv.download(y + "_세출예산_집행현황" + (filter == null ? "" : "_" + filter) + ".csv");
    }

    @GetMapping("/finance/programs")
    public String programs(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year, Model model) {
        int y = year(year);
        common(model, me, y);
        model.addAttribute("tree", finance.programTree(y));
        model.addAttribute("funcs", finance.codes(FinCodeVO.FUNC));
        model.addAttribute("types", ProgramVO.TYPES);
        return "finance/programs";
    }

    @PostMapping("/finance/programs")
    public String addProgram(@AuthenticationPrincipal LoginUser me, @RequestParam int year, @RequestParam String level,
                             @RequestParam(required = false) Long parentId, @RequestParam(required = false) String type,
                             @RequestParam(required = false) String funcCode, @RequestParam String name,
                             @RequestParam(required = false) String dept, RedirectAttributes ra) {
        Result r = finance.addProgram(year, level, parentId, type, funcCode, name, dept, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_PROGRAM, "사업 " + r.id(), "등록: " + name);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/programs?year=" + year;
    }

    @PostMapping("/finance/programs/{id}/delete")
    public String deleteProgram(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam int year,
                                RedirectAttributes ra) {
        Result r = finance.deleteProgram(id, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_PROGRAM, "사업 " + id, "삭제");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/programs?year=" + year;
    }

    // ===== 예산 작성 =====

    @GetMapping("/finance/budget/new")
    public String newForm(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "EXP") String kind,
                          @RequestParam(required = false) Integer year, Model model) {
        String k = kind(kind);
        if (BudgetVO.REV.equals(k) && !finance.canRevenue(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        int y = year(year);
        common(model, me, y);
        model.addAttribute("kind", k);
        model.addAttribute("budget", null);
        formModel(model, me, y, k);
        return "finance/budget-form";
    }

    @PostMapping("/finance/budget")
    public String create(@AuthenticationPrincipal LoginUser me, @RequestParam String kind, @RequestParam int year,
                         @RequestParam(required = false) Long detailId, @RequestParam(required = false) String code,
                         @RequestParam(required = false) String amount, @RequestParam(required = false) String basis,
                         @RequestParam(defaultValue = "save") String action, RedirectAttributes ra) {
        boolean request = "request".equals(action);
        Result r = finance.create(kind(kind), year, detailId, code, parseAmount(amount), basis, request, me);
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            ra.addFlashAttribute("prev", Map.of("detailId", detailId == null ? "" : detailId.toString(),
                    "code", code == null ? "" : code, "amount", amount == null ? "" : amount, "basis", basis == null ? "" : basis));
            return "redirect:/finance/budget/new?kind=" + kind(kind) + "&year=" + year;
        }
        auditService.record(request ? AuditAction.FIN_BUDGET_REQUEST : AuditAction.FIN_BUDGET_SAVE, "예산 " + r.id(), code);
        return "redirect:/finance/budget/" + r.id();
    }

    @GetMapping("/finance/budget/{id}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        BudgetVO b = visible(id, me);
        common(model, me, b.getFiscalYear());
        model.addAttribute("b", b);
        model.addAttribute("amountText", KoreanAmount.official(b.getAmount()));
        model.addAttribute("txns", BudgetVO.EXP.equals(b.getKind()) ? finance.txnsOf(id) : List.<TxnVO>of());
        model.addAttribute("canEdit", finance.canEdit(b, me));
        model.addAttribute("canReview", finance.canReview(b, me));
        model.addAttribute("canExecute", finance.canExecute(b, me));
        model.addAttribute("pendingSpends", BudgetVO.EXP.equals(b.getKind()) ? finance.pendingSpends(id) : List.of());
        model.addAttribute("available", b.isApproved() ? finance.available(id, null) : 0L);
        model.addAttribute("isBudgetStaff", finance.isBudgetStaff(me));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("changes", changes.ofBudget(id));
        model.addAttribute("canRequestChange", changes.canRequest(b, me));
        model.addAttribute("carryKinds", egovframework.haengjeongnuli.finance.service.BudgetChangeVO.CARRY_KINDS);
        return "finance/budget-view";
    }

    @GetMapping("/finance/budget/{id}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model, RedirectAttributes ra) {
        BudgetVO b = visible(id, me);
        if (!finance.canEdit(b, me)) {
            ra.addFlashAttribute("message", "작성 중이거나 반려된 우리 부서 예산만 고칠 수 있습니다.");
            return "redirect:/finance/budget/" + id;
        }
        common(model, me, b.getFiscalYear());
        model.addAttribute("kind", b.getKind());
        model.addAttribute("budget", b);
        formModel(model, me, b.getFiscalYear(), b.getKind());
        return "finance/budget-form";
    }

    @PostMapping("/finance/budget/{id}/edit")
    public String edit(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                       @RequestParam(required = false) Long detailId, @RequestParam(required = false) String code,
                       @RequestParam(required = false) String amount, @RequestParam(required = false) String basis,
                       @RequestParam(defaultValue = "save") String action, RedirectAttributes ra) {
        visible(id, me);
        boolean request = "request".equals(action);
        Result r = finance.update(id, detailId, code, parseAmount(amount), basis, request, me);
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/finance/budget/" + id + "/edit";
        }
        auditService.record(request ? AuditAction.FIN_BUDGET_REQUEST : AuditAction.FIN_BUDGET_SAVE, "예산 " + id, code);
        return "redirect:/finance/budget/" + id;
    }

    @PostMapping("/finance/budget/{id}/request")
    public String request(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        visible(id, me);
        Result r = finance.request(id, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_BUDGET_REQUEST, "예산 " + id, null);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/budget/" + id;
    }

    @PostMapping("/finance/budget/{id}/approve")
    public String approve(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                          @RequestParam(required = false) String note, RedirectAttributes ra) {
        return review(me, id, true, note, ra);
    }

    @PostMapping("/finance/budget/{id}/reject")
    public String reject(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        return review(me, id, false, note, ra);
    }

    private String review(LoginUser me, long id, boolean approve, String note, RedirectAttributes ra) {
        visible(id, me);
        Result r = finance.review(id, approve, note, me);
        if (r.ok()) {
            auditService.record(approve ? AuditAction.FIN_BUDGET_APPROVE : AuditAction.FIN_BUDGET_REJECT, "예산 " + id, note);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/budget/" + id;
    }

    @PostMapping("/finance/budget/{id}/delete")
    public String delete(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        BudgetVO b = visible(id, me);
        Result r = finance.delete(id, me);
        ra.addFlashAttribute("message", r.message());
        if (!r.ok()) {
            return "redirect:/finance/budget/" + id;
        }
        auditService.record(AuditAction.FIN_BUDGET_SAVE, "예산 " + id, "삭제");
        return "redirect:/finance?area=" + (BudgetVO.REV.equals(b.getKind()) ? "rev" : "fin") + "&year=" + b.getFiscalYear();
    }

    /** 추가경정(증감)·이월 요청 (지방재정법 제45조·제50조) */
    @PostMapping("/finance/budget/{id}/change")
    public String requestChange(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                                @RequestParam(required = false) String type, @RequestParam(required = false) String amount,
                                @RequestParam(required = false) Integer round, @RequestParam(required = false) String carryKind,
                                @RequestParam(required = false) String reason, RedirectAttributes ra) {
        visible(id, me);
        Long delta = null;
        if (amount != null) {
            String t = amount.replace(",", "").replace("원", "").trim();
            if (t.matches("-?\\d{1,13}")) {
                delta = Long.valueOf(t);
            }
        }
        Result r = changes.request(id, type, delta, round, carryKind, reason, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_BUDGET_CHANGE, "예산 " + id, type + " " + amount + " " + reason);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/budget/" + id;
    }

    @PostMapping("/finance/changes/{cid}")
    public String reviewChange(@AuthenticationPrincipal LoginUser me, @PathVariable long cid, @RequestParam boolean approve,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        Result r = changes.review(cid, approve, note, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_BUDGET_CHANGE, "추경·이월 " + cid, approve ? "승인" : "반려: " + note);
        }
        ra.addFlashAttribute("message", r.message());
        return r.id() == null ? "redirect:/finance/review" : "redirect:/finance/budget/" + r.id();
    }

    @GetMapping("/finance/review")
    public String reviewList(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "EXP") String kind,
                             @RequestParam(required = false) Integer year, Model model) {
        String k = kind(kind);
        if (BudgetVO.REV.equals(k) && !finance.canRevenue(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        int y = year(year);
        common(model, me, y);
        model.addAttribute("kind", k);
        // 예산편성과·관리자(세입은 세입 담당)는 모든 부서, 그 밖에는 우리 부서 요청만
        boolean all = BudgetVO.REV.equals(k) || finance.canSeeAllExp(me);
        model.addAttribute("budgets", finance.budgets(k, y, all ? null : me.dept(), BudgetVO.REQUESTED));
        model.addAttribute("changeRequests", BudgetVO.EXP.equals(k) && finance.isBudgetStaff(me) ? changes.requested() : List.of());
        return "finance/review";
    }

    // ===== 집행·수납 =====

    @PostMapping("/finance/receipts")
    public String receipt(@AuthenticationPrincipal LoginUser me, @RequestParam int year,
                          @RequestParam(required = false) String code,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) String amount, @RequestParam(required = false) String party,
                          @RequestParam(required = false) String description, RedirectAttributes ra) {
        if (!finance.canRevenue(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Result r = finance.receive(year, code, date, parseAmount(amount), party, description, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_RECEIPT, "세입 " + code, amount + "원");
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance?area=rev&year=" + year + "#receipts";
    }

    @PostMapping("/finance/txn/{id}/cancel")
    public String cancel(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        TxnVO t = finance.txn(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (BudgetVO.REV.equals(t.getKind()) && !finance.canRevenue(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Result r = finance.cancel(id, me);
        if (r.ok()) {
            auditService.record(AuditAction.FIN_TXN_CANCEL, "기록 " + id, t.getAmount() + "원");
        }
        ra.addFlashAttribute("message", r.message());
        return BudgetVO.REV.equals(t.getKind()) ? "redirect:/finance?area=rev&year=" + t.getFiscalYear() + "#receipts"
                : "redirect:/finance/budget/" + t.getBudgetId() + "#txns";
    }

    // ===== 공통 =====

    /** 볼 수 없는 예산은 404 (세입은 세입 담당만) */
    private BudgetVO visible(long id, LoginUser me) {
        BudgetVO b = finance.get(id);
        if (b == null || !finance.canView(b, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return b;
    }

    private void common(Model model, LoginUser me, int year) {
        int now = Year.now().getValue();
        model.addAttribute("year", year);
        model.addAttribute("years", List.of(now - 2, now - 1, now, now + 1));
        model.addAttribute("budgetDept", finance.getBudgetDept());
        model.addAttribute("isBudgetStaff", finance.isBudgetStaff(me));
        model.addAttribute("canManage", finance.canManagePrograms(me));
        model.addAttribute("canRevenue", finance.canRevenue(me));
    }

    private void formModel(Model model, LoginUser me, int year, String kind) {
        if (BudgetVO.EXP.equals(kind)) {
            model.addAttribute("details", finance.details(year, me.dept()));
            List<FinCodeVO> items = finance.codes(FinCodeVO.ITEM);
            Map<String, String> names = items.stream().collect(Collectors.toMap(FinCodeVO::getCode, FinCodeVO::getCodeName));
            // 편성목별로 통계목을 묶는다
            model.addAttribute("codeGroups", items.stream().filter(c -> "통계목".equals(c.getCodeLevel()))
                    .collect(Collectors.groupingBy(c -> c.getParentCode() + " " + names.get(c.getParentCode()),
                            java.util.LinkedHashMap::new, Collectors.toList())));
        } else {
            List<FinCodeVO> revs = finance.codes(FinCodeVO.REV);
            Map<String, String> names = revs.stream().collect(Collectors.toMap(FinCodeVO::getCode, FinCodeVO::getCodeName));
            model.addAttribute("codeGroups", revs.stream().filter(c -> "목".equals(c.getCodeLevel()))
                    .collect(Collectors.groupingBy(c -> c.getParentCode() + " " + names.get(c.getParentCode()),
                            java.util.LinkedHashMap::new, Collectors.toList())));
        }
    }

    private static int year(Integer year) {
        int now = Year.now().getValue();
        return year == null || year < 2000 || year > now + 1 ? now : year;
    }

    private static String kind(String kind) {
        return BudgetVO.REV.equals(kind) ? BudgetVO.REV : BudgetVO.EXP;
    }

    /** "1,234,000" 같은 입력을 숫자로. 잘못된 값이면 null */
    static Long parseAmount(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace(",", "").replace("원", "").trim();
        if (!t.matches("\\d{1,15}")) {
            return null;
        }
        return Long.parseLong(t);
    }
}
