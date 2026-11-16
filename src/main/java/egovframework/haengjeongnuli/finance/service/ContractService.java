package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.finance.service.FinanceService.Result;
import egovframework.haengjeongnuli.finance.service.impl.ContractMapper;

/**
 * 계약대장: 체결한 계약을 등록하고 완료·해지를 기록한다. 대금 지급은 전자결재 지출결의로 한다.
 * 수의계약은 「지방자치단체를 당사자로 하는 계약에 관한 법률 시행령」 제25조의 해당 사유를 적어야 등록된다.
 */
@Service
public class ContractService {

    private final ContractMapper mapper;
    private final FinanceService finance;
    private final PurchaseService purchases;

    public ContractService(ContractMapper mapper, FinanceService finance, PurchaseService purchases) {
        this.mapper = mapper;
        this.finance = finance;
        this.purchases = purchases;
    }

    /** 볼 부서: 예산편성과·관리자는 고른 부서(비우면 전체), 그 밖에는 내 부서 */
    public String scope(LoginUser me, String wanted) {
        if (finance.canSeeAllExp(me)) {
            return wanted == null || wanted.isBlank() ? null : wanted.trim();
        }
        return me.dept();
    }

    public List<ContractVO> list(LoginUser me, int year, String dept) {
        return mapper.selectList(year, scope(me, dept));
    }

    public ContractVO get(long id) {
        return mapper.selectById(id);
    }

    public boolean canView(ContractVO c, LoginUser me) {
        return finance.canSeeAllExp(me) || me.dept().equals(c.getDeptName());
    }

    public boolean canEdit(ContractVO c, LoginUser me) {
        return ContractVO.ACTIVE.equals(c.getStatus()) && me.dept().equals(c.getDeptName());
    }

    @Transactional
    public Result create(ContractVO in, LoginUser me) {
        if (me.dept().isEmpty()) {
            return Result.fail("부서가 정해진 직원만 계약을 등록할 수 있습니다.");
        }
        String title = trim(in.getTitle());
        if (title == null || title.length() > 300) {
            return Result.fail("계약명을 300자 이내로 입력하세요.");
        }
        if (in.getContractKind() == null || !ContractVO.KINDS.contains(in.getContractKind())) {
            return Result.fail("계약 종류(공사·물품·용역)를 고르세요.");
        }
        if (in.getMethod() == null || !ContractVO.METHODS.contains(in.getMethod())) {
            return Result.fail("계약 방법을 고르세요.");
        }
        String reason = trim(in.getPrivateReason());
        if ("수의계약".equals(in.getMethod()) && reason == null) {
            return Result.fail("수의계약은 근거(시행령 제25조의 해당 호)와 사유를 적어야 합니다.");
        }
        if (reason != null && reason.length() > 500) {
            return Result.fail("수의계약 사유는 500자 이내로 입력하세요.");
        }
        String party = trim(in.getParty());
        if (party == null || party.length() > 200) {
            return Result.fail("계약 상대자를 입력하세요.");
        }
        String biz = trim(in.getPartyBizNo());
        if (biz != null && !biz.matches("\\d{3}-?\\d{2}-?\\d{5}")) {
            return Result.fail("사업자등록번호는 000-00-00000 형식으로 입력하세요.");
        }
        if (in.getAmount() <= 0) {
            return Result.fail("계약 금액을 입력하세요.");
        }
        if (in.getDeposit() != null && (in.getDeposit() < 0 || in.getDeposit() > in.getAmount())) {
            return Result.fail("계약보증금은 0원 이상, 계약 금액 이하로 입력하세요.");
        }
        if (in.getContractDate() == null || in.getContractDate().isAfter(LocalDate.now())) {
            return Result.fail("계약일은 오늘 이전 날짜로 입력하세요.");
        }
        if (in.getStartDate() != null && in.getEndDate() != null && in.getEndDate().isBefore(in.getStartDate())) {
            return Result.fail("끝나는 날이 시작일보다 빠릅니다.");
        }
        if (in.getBudgetId() != null) {
            BudgetVO b = finance.get(in.getBudgetId());
            if (b == null || !finance.canExecute(b, me)) {
                return Result.fail("관련 예산은 우리 부서의 승인된 세출예산에서 고르세요.");
            }
        }
        if (in.getPurchaseId() != null) {
            PurchaseVO p = purchases.get(in.getPurchaseId());
            if (!purchases.canEdit(p, me) || !PurchaseVO.SELECTED.equals(p.getStatus())) {
                return Result.fail("구매 건은 업체를 고르고 납품을 기다리는 우리 부서 건에서 고르세요.");
            }
            if (!in.getContractKind().equals(p.getPurType())) {
                return Result.fail("계약 종류가 구매 건의 종류(" + p.getPurType() + ")와 다릅니다.");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ContractVO c = new ContractVO();
        c.setContractId(mapper.nextId());
        c.setContractYear(in.getContractDate().getYear());
        c.setContractNo("계약-" + c.getContractYear() + "-" + String.format("%05d", c.getContractId()));
        c.setTitle(title);
        c.setContractKind(in.getContractKind());
        c.setMethod(in.getMethod());
        c.setPrivateReason(reason);
        c.setParty(party);
        c.setPartyBizNo(biz);
        c.setAmount(in.getAmount());
        c.setDeposit(in.getDeposit());
        c.setContractDate(in.getContractDate());
        c.setStartDate(in.getStartDate());
        c.setEndDate(in.getEndDate());
        c.setBudgetId(in.getBudgetId());
        c.setPurchaseId(in.getPurchaseId());
        c.setDeptName(me.dept());
        c.setCreatedBy(me.getLoginId());
        c.setCreatedName(me.getUserName());
        c.setCreatedAt(now);
        mapper.insert(c);
        return Result.ok("계약을 등록했습니다. 계약번호 " + c.getContractNo(), c.getContractId());
    }

    @Transactional
    public Result close(long id, String status, String note, LoginUser me) {
        ContractVO c = get(id);
        if (c == null || !canEdit(c, me)) {
            return Result.fail("이행 중인 우리 부서 계약만 완료·해지할 수 있습니다.");
        }
        if (!ContractVO.DONE.equals(status) && !ContractVO.TERMINATED.equals(status)) {
            return Result.fail("완료 또는 해제·해지를 고르세요.");
        }
        String n = trim(note);
        if (ContractVO.TERMINATED.equals(status) && n == null) {
            return Result.fail("해제·해지 사유를 입력하세요.");
        }
        if (n != null && n.length() > 500) {
            return Result.fail("내용은 500자 이내로 입력하세요.");
        }
        mapper.close(id, status, n, LocalDateTime.now());
        return Result.ok(ContractVO.DONE.equals(status) ? "계약을 완료로 기록했습니다." : "계약 해제·해지를 기록했습니다.", id);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
