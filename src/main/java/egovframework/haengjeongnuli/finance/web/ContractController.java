package egovframework.haengjeongnuli.finance.web;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

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

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.finance.service.ContractService;
import egovframework.haengjeongnuli.finance.service.ContractVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;

/** 재정 › 계약대장 */
@Controller
public class ContractController {

    private final ContractService contracts;
    private final FinanceService finance;
    private final AuditService audit;
    private final egovframework.haengjeongnuli.finance.service.PurchaseService purchases;

    public ContractController(ContractService contracts, FinanceService finance, AuditService audit,
                              egovframework.haengjeongnuli.finance.service.PurchaseService purchases) {
        this.contracts = contracts;
        this.finance = finance;
        this.audit = audit;
        this.purchases = purchases;
    }

    @GetMapping("/finance/contracts")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) String dept, @RequestParam(required = false) Long purchase, Model model) {
        int now = Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now : year;
        List<ContractVO> rows = contracts.list(me, y, dept);
        model.addAttribute("year", y);
        model.addAttribute("years", List.of(now - 2, now - 1, now));
        model.addAttribute("rows", rows);
        model.addAttribute("total", rows.stream().filter(c -> !ContractVO.TERMINATED.equals(c.getStatus())).mapToLong(ContractVO::getAmount).sum());
        model.addAttribute("scope", contracts.scope(me, dept));
        model.addAttribute("seeAll", finance.canSeeAllExp(me));
        model.addAttribute("kinds", ContractVO.KINDS);
        model.addAttribute("methods", ContractVO.METHODS);
        model.addAttribute("budgets", finance.spendable(me, now));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("purchases", purchases.openForDept(me.dept()).stream()
                .filter(p -> egovframework.haengjeongnuli.finance.service.PurchaseVO.SELECTED.equals(p.getStatus())).toList());
        model.addAttribute("purchaseId", purchase);
        return "finance/contracts";
    }

    @GetMapping("/finance/contracts.csv")
    public org.springframework.http.ResponseEntity<byte[]> csv(@AuthenticationPrincipal LoginUser me,
                                                              @RequestParam(required = false) Integer year,
                                                              @RequestParam(required = false) String dept) {
        int now = Year.now().getValue();
        int y = year == null || year < 2000 || year > now ? now : year;
        egovframework.haengjeongnuli.cmm.text.Csv csv = egovframework.haengjeongnuli.cmm.text.Csv.of(
                "계약번호", "계약명", "종류", "계약 방법", "수의계약 근거·사유", "계약 상대자", "사업자등록번호", "계약 금액", "계약보증금",
                "계약일", "착수일", "완료 예정일", "부서", "상태", "비고");
        for (ContractVO c : contracts.list(me, y, dept)) {
            csv.row(c.getContractNo(), c.getTitle(), c.getContractKind(), c.getMethod(), c.getPrivateReason(), c.getParty(),
                    c.getPartyBizNo(), c.getAmount(), c.getDeposit(), c.getContractDate(), c.getStartDate(), c.getEndDate(),
                    c.getDeptName(), c.getStatusLabel(), c.getStatusNote());
        }
        audit.record(AuditAction.FIN_EXPORT, "계약대장 " + y, dept);
        return csv.download(y + "_계약대장.csv");
    }

    @PostMapping("/finance/contracts")
    public String create(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                         @RequestParam(required = false) String contractKind, @RequestParam(required = false) String method,
                         @RequestParam(required = false) String privateReason, @RequestParam(required = false) String party,
                         @RequestParam(required = false) String partyBizNo, @RequestParam(required = false) String amount,
                         @RequestParam(required = false) String deposit,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         @RequestParam(required = false) Long budgetId, @RequestParam(required = false) Long purchaseId,
                         RedirectAttributes ra) {
        ContractVO in = new ContractVO();
        in.setTitle(title);
        in.setContractKind(contractKind);
        in.setMethod(method);
        in.setPrivateReason(privateReason);
        in.setParty(party);
        in.setPartyBizNo(partyBizNo);
        Long a = money(amount);
        in.setAmount(a == null ? 0 : a);
        in.setDeposit(money(deposit));
        in.setContractDate(contractDate);
        in.setStartDate(startDate);
        in.setEndDate(endDate);
        in.setBudgetId(budgetId);
        in.setPurchaseId(purchaseId);
        FinanceService.Result r = contracts.create(in, me);
        if (r.ok()) {
            audit.record(AuditAction.CONTRACT_SAVE, "계약 " + r.id(), "등록: " + title);
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() && purchaseId != null ? "redirect:/finance/purchases/" + purchaseId : "redirect:/finance/contracts";
    }

    @PostMapping("/finance/contracts/{id}/close")
    public String close(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String status,
                        @RequestParam(required = false) String note, RedirectAttributes ra) {
        ContractVO c = contracts.get(id);
        if (c == null || !contracts.canView(c, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        FinanceService.Result r = contracts.close(id, status, note, me);
        if (r.ok()) {
            audit.record(AuditAction.CONTRACT_SAVE, "계약 " + id, status + " " + (note == null ? "" : note));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/contracts?year=" + c.getContractYear();
    }

    private static Long money(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.replace(",", "").replace("원", "").trim();
        return t.matches("\\d{1,15}") ? Long.valueOf(t) : -1L;
    }
}
