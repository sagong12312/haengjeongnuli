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

    public ContractController(ContractService contracts, FinanceService finance, AuditService audit) {
        this.contracts = contracts;
        this.finance = finance;
        this.audit = audit;
    }

    @GetMapping("/finance/contracts")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) String dept, Model model) {
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
        return "finance/contracts";
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
                         @RequestParam(required = false) Long budgetId, RedirectAttributes ra) {
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
        FinanceService.Result r = contracts.create(in, me);
        if (r.ok()) {
            audit.record(AuditAction.CONTRACT_SAVE, "계약 " + r.id(), "등록: " + title);
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/contracts";
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
