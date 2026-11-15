package egovframework.haengjeongnuli.finance.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.finance.service.FinanceService.Result;
import egovframework.haengjeongnuli.finance.service.impl.BudgetChangeMapper;
import egovframework.haengjeongnuli.finance.service.impl.FinanceMapper;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 승인된 세출예산의 추가경정(증감)과 이월.
 * <ul>
 *   <li>추가경정: 소관 부서가 회차·증감액·사유로 요청하고 예산편성과가 승인하면 예산액에 더한다.
 *       줄이는 경우 이미 집행했거나 결재 중인 금액 아래로는 줄일 수 없다.</li>
 *   <li>이월(지방재정법 제50조): 쓰고 남은 금액 안에서 명시이월·사고이월을 요청하고, 승인하면
 *       올해 예산액에서 빼고 다음 해에 같은 사업·과목으로 승인된 예산을 만든다.</li>
 * </ul>
 * 지방의회 의결 등 실제 절차는 시스템 밖에서 거치고, 여기서는 그 결과를 반영한다.
 */
@Service
public class BudgetChangeService {

    private final BudgetChangeMapper mapper;
    private final FinanceMapper financeMapper;
    private final FinanceService finance;
    private final UserService userService;
    private final NotificationService notificationService;

    public BudgetChangeService(BudgetChangeMapper mapper, FinanceMapper financeMapper, FinanceService finance,
                               UserService userService, NotificationService notificationService) {
        this.mapper = mapper;
        this.financeMapper = financeMapper;
        this.finance = finance;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public List<BudgetChangeVO> ofBudget(long budgetId) {
        return mapper.selectByBudget(budgetId);
    }

    public List<BudgetChangeVO> requested() {
        return mapper.selectRequested();
    }

    /** 요청할 수 있는지: 승인된 세출예산의 소관 부서 직원 */
    public boolean canRequest(BudgetVO b, LoginUser u) {
        return BudgetVO.EXP.equals(b.getKind()) && b.isApproved() && u.dept().equals(b.getDeptName());
    }

    @Transactional
    public Result request(long budgetId, String type, Long delta, Integer round, String carryKind, String reason, LoginUser me) {
        BudgetVO b = finance.get(budgetId);
        if (b == null || !canRequest(b, me)) {
            return Result.fail("승인된 우리 부서 세출예산만 추경·이월을 요청할 수 있습니다.");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 1000) {
            return Result.fail("사유를 1,000자 이내로 적으세요.");
        }
        if (delta == null || delta == 0 || Math.abs(delta) > 999_999_999_999L) {
            return Result.fail("금액을 입력하세요.");
        }
        if (mapper.countOpen(budgetId) > 0) {
            return Result.fail("이 예산에 승인을 기다리는 추경·이월 요청이 있습니다.");
        }
        long used = b.getAmount() - finance.available(budgetId, null);
        BudgetChangeVO c = new BudgetChangeVO();
        if (BudgetChangeVO.SUPP.equals(type)) {
            if (round == null || round < 1 || round > 99) {
                return Result.fail("추가경정예산 회차를 입력하세요.");
            }
            if (b.getAmount() + delta < used) {
                return Result.fail(String.format("이미 집행했거나 결재 중인 %,d원보다 적게 줄일 수 없습니다.", used));
            }
            c.setRoundNo(round);
        } else if (BudgetChangeVO.CARRY.equals(type)) {
            if (carryKind == null || !BudgetChangeVO.CARRY_KINDS.contains(carryKind)) {
                return Result.fail("이월 종류(명시이월·사고이월)를 고르세요.");
            }
            if (delta < 0 || delta > b.getAmount() - used) {
                return Result.fail(String.format("이월할 수 있는 금액은 쓰고 남은 %,d원까지입니다.", b.getAmount() - used));
            }
            c.setCarryKind(carryKind);
        } else {
            return Result.fail("추가경정 또는 이월을 고르세요.");
        }
        c.setChangeId(mapper.nextId());
        c.setBudgetId(budgetId);
        c.setChangeType(type);
        c.setDelta(delta);
        c.setReason(why);
        c.setRequesterId(me.getLoginId());
        c.setRequesterName(me.getUserName());
        c.setRequestedAt(LocalDateTime.now());
        mapper.insert(c);
        for (LoginUser u : userService.list()) {
            if (u.isEnabled() && finance.isBudgetStaff(u) && !u.getLoginId().equals(me.getLoginId())) {
                notificationService.notify(u.getLoginId(), me.getUserName() + "님이 " + b.getDeptName() + " 예산의 "
                        + c.getTypeLabel() + " 승인을 요청했습니다.", "/finance/budget/" + budgetId);
            }
        }
        return Result.ok(c.getTypeLabel() + " 승인을 요청했습니다.", budgetId);
    }

    @Transactional
    public Result review(long changeId, boolean approve, String note, LoginUser me) {
        BudgetChangeVO c = mapper.selectById(changeId);
        if (c == null || !BudgetChangeVO.REQUESTED.equals(c.getStatus())) {
            return Result.fail("이미 처리했거나 없는 요청입니다.");
        }
        if (!finance.isBudgetStaff(me)) {
            return Result.fail(finance.getBudgetDept() + " 직원만 승인·반려할 수 있습니다.");
        }
        if (me.getLoginId().equals(c.getRequesterId())) {
            return Result.fail("자기가 요청한 것은 승인·반려할 수 없습니다.");
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (!approve && n == null) {
            return Result.fail("반려 사유를 입력하세요.");
        }
        if (n != null && n.length() > 1000) {
            return Result.fail("의견은 1,000자 이내로 입력하세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        Long newBudgetId = null;
        if (approve) {
            BudgetVO locked = financeMapper.lockBudget(c.getBudgetId());
            long used = financeMapper.sumUsed(c.getBudgetId()) + financeMapper.sumReserved(c.getBudgetId(), null);
            if (BudgetChangeVO.SUPP.equals(c.getChangeType())) {
                if (locked.getAmount() + c.getDelta() < used) {
                    return Result.fail(String.format("그 사이 집행이 늘어 %,d원 아래로 줄일 수 없습니다. 반려하세요.", used));
                }
                mapper.addAmount(c.getBudgetId(), c.getDelta(), now);
            } else {
                if (c.getDelta() > locked.getAmount() - used) {
                    return Result.fail(String.format("그 사이 집행이 늘어 이월할 수 있는 금액이 %,d원입니다. 반려하세요.",
                            locked.getAmount() - used));
                }
                mapper.addAmount(c.getBudgetId(), -c.getDelta(), now);
                newBudgetId = financeMapper.nextBudgetId();
                mapper.insertCarried(newBudgetId, c.getBudgetId(), c.getDelta(),
                        c.getFiscalYear() + "년도 " + c.getCarryKind() + ": " + c.getReason(), c.getRequesterId(),
                        c.getRequesterName(), me.getLoginId(), me.getUserName(), now);
            }
        }
        if (mapper.close(changeId, approve ? BudgetChangeVO.APPROVED : BudgetChangeVO.REJECTED, me.getLoginId(),
                me.getUserName(), now, n, newBudgetId) != 1) {
            throw new IllegalStateException("이미 처리된 요청입니다");
        }
        notificationService.notify(c.getRequesterId(), me.getUserName() + "님이 " + c.getTypeLabel() + " 요청을 "
                + (approve ? "승인했습니다." : "반려했습니다."), "/finance/budget/" + c.getBudgetId());
        return Result.ok(c.getTypeLabel() + (approve ? (newBudgetId != null
                ? String.format(" %,d원을 %d년 예산으로 넘겼습니다.", c.getDelta(), c.getFiscalYear() + 1)
                : " 금액을 예산에 반영했습니다.") : " 요청을 반려했습니다."), c.getBudgetId());
    }
}
