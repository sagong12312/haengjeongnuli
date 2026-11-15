package egovframework.haengjeongnuli.finance.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.finance.service.impl.FinanceMapper;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 재정: 예산 편성(작성 → 승인 요청 → 예산편성과 승인/반려) → 지출결의(전자결재) → 집행, 세입 예산·수납.
 * <ul>
 *   <li>세출 예산은 세부사업의 소관 부서 직원이 작성하고, 예산편성과(app.finance.budget-dept) 직원이 승인한다.</li>
 *   <li>자기가 요청한 예산은 자기가 승인할 수 없다.</li>
 *   <li>세입은 세입 담당으로 지정된 직원만 본다. 세입 예산 승인은 세입 담당이면서 예산편성과 직원이 한다.</li>
 *   <li>사업 구조(정책·단위·세부사업)는 예산편성과 직원과 시스템 관리자가 만든다.</li>
 * </ul>
 */
@Service
public class FinanceService implements ApplicationRunner {

    /** 처리 결과. 실패하면 화면에 보여 줄 안내 문구를 담는다 */
    public record Result(boolean ok, String message, Long id) {
        static Result ok(String message, Long id) { return new Result(true, message, id); }
        static Result fail(String message) { return new Result(false, message, null); }
    }

    public static final long MAX_AMOUNT = 999_999_999_999_999L;

    private final FinanceMapper mapper;
    private final NotificationService notificationService;
    private final UserService userService;
    private final String budgetDept;

    public FinanceService(FinanceMapper mapper, NotificationService notificationService, UserService userService,
                          @Value("${app.finance.budget-dept:예산편성과}") String budgetDept) {
        this.mapper = mapper;
        this.notificationService = notificationService;
        this.userService = userService;
        this.budgetDept = budgetDept;
    }

    // ===== 과목 코드 =====

    /** 처음 실행할 때 예산 과목 코드(운영기준 별표 8·9·11)를 넣는다 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        load(FinCodeVO.FUNC, "egovframework/finance/func.csv");
        load(FinCodeVO.ITEM, "egovframework/finance/item.csv");
        load(FinCodeVO.REV, "egovframework/finance/rev.csv");
    }

    private void load(String type, String path) throws IOException {
        if (mapper.countCodes(type) > 0) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int sort = 0;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                // 코드,이름,상위코드,단계 — 이름에 쉼표가 있을 수 있어 앞뒤에서 나눈다
                int first = line.indexOf(',');
                int last = line.lastIndexOf(',');
                int beforeLast = line.lastIndexOf(',', last - 1);
                FinCodeVO c = new FinCodeVO();
                c.setCodeType(type);
                c.setCode(line.substring(0, first));
                c.setCodeName(line.substring(first + 1, beforeLast));
                String parent = line.substring(beforeLast + 1, last);
                c.setParentCode(parent.isEmpty() ? null : parent);
                c.setCodeLevel(line.substring(last + 1));
                c.setSortNo(++sort);
                mapper.insertCode(c);
            }
        }
    }

    public List<FinCodeVO> codes(String type) {
        return mapper.selectCodes(type);
    }

    /** 코드 → 이름 */
    public Map<String, String> codeNames(String type) {
        return codes(type).stream().collect(Collectors.toMap(FinCodeVO::getCode, FinCodeVO::getCodeName,
                (a, b) -> a, LinkedHashMap::new));
    }

    // ===== 권한 =====

    public String getBudgetDept() { return budgetDept; }

    public boolean isBudgetStaff(LoginUser u) {
        return u != null && budgetDept.equals(u.dept());
    }

    /** 사업 구조를 만들고 지울 수 있는지 */
    public boolean canManagePrograms(LoginUser u) {
        return isBudgetStaff(u) || (u != null && u.isAdmin());
    }

    /** 모든 부서의 세출 예산을 볼 수 있는지 */
    public boolean canSeeAllExp(LoginUser u) {
        return canManagePrograms(u);
    }

    public boolean canRevenue(LoginUser u) {
        return u != null && u.isRevenueStaff();
    }

    public boolean canView(BudgetVO b, LoginUser u) {
        if (BudgetVO.REV.equals(b.getKind())) {
            return canRevenue(u);
        }
        return canSeeAllExp(u) || u.dept().equals(b.getDeptName());
    }

    /** 작성 중·반려 예산을 고치거나 승인 요청할 수 있는지 */
    public boolean canEdit(BudgetVO b, LoginUser u) {
        if (!b.isEditable()) {
            return false;
        }
        return BudgetVO.REV.equals(b.getKind()) ? canRevenue(u) : u.dept().equals(b.getDeptName());
    }

    /** 승인·반려할 수 있는지 (자기 요청 제외) */
    public boolean canReview(BudgetVO b, LoginUser u) {
        if (!BudgetVO.REQUESTED.equals(b.getStatus()) || u.getLoginId().equals(b.getRequesterId())) {
            return false;
        }
        return BudgetVO.REV.equals(b.getKind()) ? canRevenue(u) && isBudgetStaff(u) : isBudgetStaff(u);
    }

    /** 승인된 세출 예산으로 지출결의를 올릴 수 있는지 (소관 부서 직원) */
    public boolean canExecute(BudgetVO b, LoginUser u) {
        return BudgetVO.EXP.equals(b.getKind()) && b.isApproved() && u.dept().equals(b.getDeptName());
    }

    // ===== 사업 =====

    /** 그 해 사업 구조 (정책사업 목록, 각 children 에 단위사업, 그 children 에 세부사업) */
    public List<ProgramVO> programTree(int year) {
        List<ProgramVO> all = mapper.selectPrograms(year);
        Map<Long, ProgramVO> byId = all.stream().collect(Collectors.toMap(ProgramVO::getProgId, Function.identity()));
        List<ProgramVO> roots = new ArrayList<>();
        for (ProgramVO p : all) {
            if (p.getParentId() == null) {
                roots.add(p);
            } else if (byId.containsKey(p.getParentId())) {
                byId.get(p.getParentId()).getChildren().add(p);
            }
        }
        roots.sort((a, b) -> {
            String fa = a.getFuncCode() == null ? "" : a.getFuncCode();
            String fb = b.getFuncCode() == null ? "" : b.getFuncCode();
            int c = fa.compareTo(fb);
            return c != 0 ? c : a.getProgId().compareTo(b.getProgId());
        });
        return roots;
    }

    /** 세부사업 목록. dept 가 있으면 그 부서 소관만 */
    public List<ProgramVO> details(int year, String dept) {
        return mapper.selectPrograms(year).stream()
                .filter(p -> ProgramVO.DETAIL.equals(p.getProgLevel()))
                .filter(p -> dept == null || dept.equals(p.getDeptName()))
                .toList();
    }

    @Transactional
    public Result addProgram(int year, String level, Long parentId, String type, String funcCode, String name,
                             String dept, LoginUser by) {
        if (!canManagePrograms(by)) {
            return Result.fail("사업은 " + budgetDept + " 직원과 시스템 관리자만 만들 수 있습니다.");
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 200) {
            return Result.fail("사업 이름을 1~200자로 입력하세요.");
        }
        ProgramVO p = new ProgramVO();
        p.setFiscalYear(year);
        p.setProgName(n);
        switch (level == null ? "" : level) {
            case ProgramVO.POLICY -> {
                if (type == null || !ProgramVO.TYPES.contains(type)) {
                    return Result.fail("정책사업 구분을 고르세요.");
                }
                FinCodeVO f = codes(FinCodeVO.FUNC).stream().filter(c -> c.getCode().equals(funcCode)).findFirst().orElse(null);
                if (f == null || !("부문".equals(f.getCodeLevel()) || "900".equals(f.getCode()))) {
                    return Result.fail("기능별 분류(부문)를 고르세요.");
                }
                if (!"정책사업".equals(type) && !"900".equals(funcCode)) {
                    return Result.fail("행정운영경비·재무활동은 기능별 분류를 '900 기타'로 합니다 (운영기준 별표 9).");
                }
                p.setProgLevel(ProgramVO.POLICY);
                p.setProgType(type);
                p.setFuncCode(funcCode);
            }
            case ProgramVO.UNIT, ProgramVO.DETAIL -> {
                ProgramVO parent = parentId == null ? null : mapper.selectProgram(parentId);
                String need = ProgramVO.UNIT.equals(level) ? ProgramVO.POLICY : ProgramVO.UNIT;
                if (parent == null || !need.equals(parent.getProgLevel()) || parent.getFiscalYear() != year) {
                    return Result.fail(ProgramVO.UNIT.equals(level) ? "정책사업을 고르세요." : "단위사업을 고르세요.");
                }
                if (ProgramVO.DETAIL.equals(level)) {
                    String d = dept == null ? "" : dept.trim();
                    if (d.isEmpty() || d.length() > 100) {
                        return Result.fail("세부사업의 소관 부서를 입력하세요.");
                    }
                    p.setDeptName(d);
                }
                p.setProgLevel(level);
                p.setParentId(parent.getProgId());
            }
            default -> {
                return Result.fail("사업 단계를 고르세요.");
            }
        }
        p.setProgId(mapper.nextProgramId());
        p.setCreatedBy(by.getLoginId());
        p.setCreatedAt(LocalDateTime.now());
        mapper.insertProgram(p);
        return Result.ok(p.getLevelLabel() + " '" + n + "'을(를) 만들었습니다.", p.getProgId());
    }

    @Transactional
    public Result deleteProgram(long progId, LoginUser by) {
        if (!canManagePrograms(by)) {
            return Result.fail("사업은 " + budgetDept + " 직원과 시스템 관리자만 지울 수 있습니다.");
        }
        ProgramVO p = mapper.selectProgram(progId);
        if (p == null) {
            return Result.fail("없는 사업입니다.");
        }
        if (mapper.countChildren(progId) > 0) {
            return Result.fail("아래 사업이 있어 지울 수 없습니다. 아래 사업부터 지우세요.");
        }
        if (mapper.countBudgetsOfDetail(progId) > 0) {
            return Result.fail("이 세부사업에 예산이 있어 지울 수 없습니다.");
        }
        mapper.deleteProgram(progId);
        return Result.ok("'" + p.getProgName() + "'을(를) 지웠습니다.", progId);
    }

    // ===== 예산 편성 =====

    public BudgetVO get(long budgetId) {
        return mapper.selectBudget(budgetId);
    }

    public List<BudgetVO> budgets(String kind, int year, String dept, String status) {
        return mapper.selectBudgets(kind, year, dept, status);
    }

    /**
     * 예산을 새로 작성한다.
     *
     * @param request true 면 바로 승인 요청
     */
    @Transactional
    public Result create(String kind, int year, Long detailId, String code, Long amount, String basis,
                         boolean request, LoginUser by) {
        BudgetVO b = new BudgetVO();
        b.setKind(kind);
        b.setFiscalYear(year);
        String err = fill(b, detailId, code, amount, basis, by, null);
        if (err != null) {
            return Result.fail(err);
        }
        LocalDateTime now = LocalDateTime.now();
        b.setBudgetId(mapper.nextBudgetId());
        b.setStatus(request ? BudgetVO.REQUESTED : BudgetVO.DRAFT);
        b.setRequestedAt(request ? now : null);
        b.setCreatedAt(now);
        mapper.insertBudget(b);
        if (request) {
            notifyReviewers(b);
        }
        return Result.ok(request ? "승인을 요청했습니다." : "저장했습니다. 다 작성하면 승인을 요청하세요.", b.getBudgetId());
    }

    /** 작성 중·반려 예산을 고친다 (request 면 승인 요청까지) */
    @Transactional
    public Result update(long budgetId, Long detailId, String code, Long amount, String basis, boolean request,
                         LoginUser by) {
        BudgetVO b = get(budgetId);
        if (b == null) {
            return Result.fail("없는 예산입니다.");
        }
        if (!canEdit(b, by)) {
            return Result.fail("작성 중이거나 반려된 우리 부서 예산만 고칠 수 있습니다.");
        }
        String err = fill(b, detailId, code, amount, basis, by, budgetId);
        if (err != null) {
            return Result.fail(err);
        }
        LocalDateTime now = LocalDateTime.now();
        b.setStatus(request ? BudgetVO.REQUESTED : BudgetVO.DRAFT);
        b.setRequestedAt(request ? now : null);
        b.setUpdatedAt(now);
        if (mapper.updateBudgetContent(b) == 0) {
            return Result.fail("이미 승인 요청된 예산입니다.");
        }
        if (request) {
            notifyReviewers(b);
        }
        return Result.ok(request ? "승인을 요청했습니다." : "저장했습니다.", budgetId);
    }

    /** 입력값을 확인하고 채운다. 문제가 있으면 안내 문구 */
    private String fill(BudgetVO b, Long detailId, String code, Long amount, String basis, LoginUser by, Long exceptId) {
        if (amount == null || amount <= 0 || amount > MAX_AMOUNT) {
            return "금액을 원 단위로 1원 이상 입력하세요.";
        }
        if (basis != null && basis.length() > 2000) {
            return "산출근거는 2,000자 이내로 입력하세요.";
        }
        if (BudgetVO.EXP.equals(b.getKind())) {
            ProgramVO d = detailId == null ? null : mapper.selectProgram(detailId);
            if (d == null || !ProgramVO.DETAIL.equals(d.getProgLevel()) || d.getFiscalYear() != b.getFiscalYear()) {
                return "세부사업을 고르세요.";
            }
            if (!by.dept().equals(d.getDeptName())) {
                return "우리 부서 소관 세부사업의 예산만 작성할 수 있습니다.";
            }
            if (!isLevel(FinCodeVO.ITEM, code, "통계목")) {
                return "통계목을 고르세요.";
            }
            b.setDetailId(detailId);
            b.setDeptName(d.getDeptName());
        } else if (BudgetVO.REV.equals(b.getKind())) {
            if (!canRevenue(by)) {
                return "세입은 세입 담당 직원만 작성할 수 있습니다.";
            }
            if (!isLevel(FinCodeVO.REV, code, "목")) {
                return "세입 목을 고르세요.";
            }
            b.setDetailId(null);
            b.setDeptName(by.getDeptName());
        } else {
            return "예산 종류가 잘못되었습니다.";
        }
        if (mapper.countSame(b.getKind(), b.getFiscalYear(), b.getDetailId(), code, exceptId) > 0) {
            return BudgetVO.EXP.equals(b.getKind())
                    ? "같은 세부사업에 같은 통계목 예산이 이미 있습니다. 그 예산을 고치세요."
                    : "같은 세입 목 예산이 이미 있습니다. 그 예산을 고치세요.";
        }
        b.setCode(code);
        b.setAmount(amount);
        b.setBasis(basis == null || basis.isBlank() ? null : basis.trim());
        b.setRequesterId(by.getLoginId());
        b.setRequesterName(by.getUserName());
        return null;
    }

    private boolean isLevel(String type, String code, String level) {
        return code != null && codes(type).stream()
                .anyMatch(c -> c.getCode().equals(code) && level.equals(c.getCodeLevel()));
    }

    @Transactional
    public Result request(long budgetId, LoginUser by) {
        BudgetVO b = get(budgetId);
        if (b == null || !canEdit(b, by)) {
            return Result.fail("작성 중이거나 반려된 우리 부서 예산만 승인을 요청할 수 있습니다.");
        }
        b.setRequesterId(by.getLoginId());
        b.setRequesterName(by.getUserName());
        b.setStatus(BudgetVO.REQUESTED);
        b.setRequestedAt(LocalDateTime.now());
        b.setUpdatedAt(b.getRequestedAt());
        if (mapper.updateBudgetContent(b) == 0) {
            return Result.fail("이미 승인 요청된 예산입니다.");
        }
        notifyReviewers(b);
        return Result.ok("승인을 요청했습니다.", budgetId);
    }

    @Transactional
    public Result review(long budgetId, boolean approve, String note, LoginUser by) {
        BudgetVO b = get(budgetId);
        if (b == null) {
            return Result.fail("없는 예산입니다.");
        }
        if (!canReview(b, by)) {
            return Result.fail(by.getLoginId().equals(b.getRequesterId())
                    ? "자기가 요청한 예산은 승인·반려할 수 없습니다."
                    : "승인 요청된 예산을 " + budgetDept + (BudgetVO.REV.equals(b.getKind()) ? " 세입 담당" : "")
                        + " 직원만 승인·반려할 수 있습니다.");
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (!approve && n == null) {
            return Result.fail("반려 사유를 입력하세요.");
        }
        if (n != null && n.length() > 1000) {
            return Result.fail("의견은 1,000자 이내로 입력하세요.");
        }
        int changed = mapper.updateStatus(budgetId, BudgetVO.REQUESTED, approve ? BudgetVO.APPROVED : BudgetVO.REJECTED,
                LocalDateTime.now(), by.getLoginId(), by.getUserName(), n);
        if (changed == 0) {
            return Result.fail("이미 처리된 예산입니다.");
        }
        notificationService.notify(b.getRequesterId(),
                by.getUserName() + "님이 '" + title(b) + "' 예산을 " + (approve ? "승인했습니다." : "반려했습니다."),
                "/finance/budget/" + budgetId);
        return Result.ok(approve ? "승인했습니다." : "반려했습니다. 요청한 직원에게 알렸습니다.", budgetId);
    }

    @Transactional
    public Result delete(long budgetId, LoginUser by) {
        BudgetVO b = get(budgetId);
        if (b == null || !canEdit(b, by)) {
            return Result.fail("작성 중이거나 반려된 우리 부서 예산만 지울 수 있습니다.");
        }
        mapper.markBudgetDeleted(budgetId);
        return Result.ok("예산을 지웠습니다.", budgetId);
    }

    /** 승인 요청을 받을 직원에게 알린다 */
    private void notifyReviewers(BudgetVO b) {
        boolean rev = BudgetVO.REV.equals(b.getKind());
        for (LoginUser u : userService.list()) {
            if (u.isEnabled() && budgetDept.equals(u.getDeptName()) && (!rev || u.isRevenueStaff())
                    && !u.getLoginId().equals(b.getRequesterId())) {
                notificationService.notify(u.getLoginId(),
                        b.getRequesterName() + "님이 '" + title(b) + "' 예산 승인을 요청했습니다.",
                        "/finance/budget/" + b.getBudgetId());
            }
        }
    }

    private String title(BudgetVO b) {
        BudgetVO full = b.getCodeName() != null ? b : get(b.getBudgetId());
        String code = full == null ? b.getCode() : b.getCode() + " " + full.getCodeName();
        if (BudgetVO.EXP.equals(b.getKind()) && full != null && full.getDetailName() != null) {
            return full.getDetailName() + " / " + code;
        }
        return code;
    }

    // ===== 집행·수납 =====

    public List<TxnVO> txnsOf(long budgetId) {
        return mapper.selectTxnsOfBudget(budgetId);
    }

    public List<TxnVO> recentTxns(String kind, int year, int limit) {
        return mapper.selectRecentTxns(kind, year, limit);
    }

    /** 세입 목별 수납액 */
    public Map<String, Long> receiptsByCode(int year) {
        return mapper.sumReceiptsByCode(year).stream()
                .collect(Collectors.toMap(TxnVO::getCode, TxnVO::getAmount));
    }

    /** 지출결의에 고를 수 있는 예산: 그 해 우리 부서의 승인된 세출 예산 */
    public List<BudgetVO> spendable(LoginUser me, int year) {
        return budgets(BudgetVO.EXP, year, me.dept(), BudgetVO.APPROVED);
    }

    /** 결재 진행 중인 지출결의 (잔액에서 미리 뺀 금액) */
    public List<Map<String, Object>> pendingSpends(long budgetId) {
        return mapper.selectPendingSpends(budgetId);
    }

    /** 지금 지출결의로 쓸 수 있는 금액 = 예산액 - 집행액 - 결재 중인 지출결의 금액 */
    public long available(long budgetId, Long exceptDocId) {
        BudgetVO b = get(budgetId);
        if (b == null || !b.isApproved()) {
            return 0;
        }
        return b.getAmount() - mapper.sumUsed(budgetId) - mapper.sumReserved(budgetId, exceptDocId);
    }

    /**
     * 지출결의를 올리거나 마지막 결재를 하기 전에 확인한다 (지방회계법 제29조 제2항: 배정된 예산의 범위,
     * 같은 법 시행령 제33조: 예산액 이내, 소속 연도의 세출과목 일치).
     *
     * @param dept        기안 부서 (예산의 소관 부서와 같아야 한다)
     * @param exceptDocId 확인하는 문서 자신 (결재 중 금액에서 뺀다)
     * @return 문제가 있으면 안내 문구, 없으면 null
     */
    public String checkSpend(Long budgetId, Long amount, String dept, Long exceptDocId) {
        BudgetVO b = budgetId == null ? null : get(budgetId);
        if (b == null || !BudgetVO.EXP.equals(b.getKind())) {
            return "지출할 예산(세부사업·통계목)을 고르세요.";
        }
        if (!b.isApproved()) {
            return "승인된 예산에서만 지출할 수 있습니다.";
        }
        if (dept == null || !dept.equals(b.getDeptName())) {
            return "우리 부서 소관 예산에서만 지출할 수 있습니다.";
        }
        if (b.getFiscalYear() != LocalDate.now().getYear()) {
            return b.getFiscalYear() + "년 예산은 올해 지출할 수 없습니다 (소속 연도의 세출과목과 일치해야 합니다).";
        }
        if (amount == null || amount <= 0 || amount > MAX_AMOUNT) {
            return "지출 금액을 원 단위로 1원 이상 입력하세요.";
        }
        long avail = available(budgetId, exceptDocId);
        if (amount > avail) {
            return String.format("지출 가능 잔액(%,d원)을 넘습니다. 결재 중인 다른 지출결의 금액도 잔액에서 미리 뺍니다.", avail);
        }
        return null;
    }

    /**
     * 결재가 끝난 지출결의를 집행으로 기록한다. 예산 줄을 잠그고 잔액을 다시 확인한다.
     * 확인에 실패하면 예외를 던져 결재까지 되돌린다.
     */
    @Transactional
    public TxnVO executeFromDoc(long docId, String title, long budgetId, long amount, String payee, String drafterId,
                                String drafterName, String drafterDept) {
        mapper.lockBudget(budgetId);
        String err = checkSpend(budgetId, amount, drafterDept, docId);
        if (err != null) {
            throw new IllegalStateException(err);
        }
        BudgetVO b = get(budgetId);
        TxnVO t = txn(BudgetVO.EXP, b.getFiscalYear(), budgetId, b.getCode(), LocalDate.now(), amount, payee,
                "지출결의: " + (title.length() > 480 ? title.substring(0, 480) : title), null);
        t.setCreatedBy(drafterId);
        t.setCreatedName(drafterName);
        t.setDeptName(b.getDeptName());
        t.setDocId(docId);
        mapper.insertTxn(t);
        return t;
    }

    /** 세입 수납을 기록한다 (세입 담당) */
    @Transactional
    public Result receive(int year, String code, LocalDate date, Long amount, String party, String description,
                          LoginUser by) {
        if (!canRevenue(by)) {
            return Result.fail("세입은 세입 담당 직원만 기록할 수 있습니다.");
        }
        if (!isLevel(FinCodeVO.REV, code, "목")) {
            return Result.fail("세입 목을 고르세요.");
        }
        String err = checkTxn(date, amount, party, description, year);
        if (err != null) {
            return Result.fail(err);
        }
        TxnVO t = txn(BudgetVO.REV, year, null, code, date, amount, party, description, by);
        t.setDeptName(by.getDeptName());
        mapper.insertTxn(t);
        return Result.ok(String.format("%,d원 수납을 기록했습니다.", amount), t.getTxnId());
    }

    /** 집행·수납 취소 (기록한 직원, 세출은 예산편성과도) */
    @Transactional
    public Result cancel(long txnId, LoginUser by) {
        TxnVO t = mapper.selectTxn(txnId);
        if (t == null || t.isCancelled()) {
            return Result.fail("취소할 수 없는 기록입니다.");
        }
        boolean rev = BudgetVO.REV.equals(t.getKind());
        // 세출 집행은 결재가 끝난 지출결의로 생기므로 예산편성과만 취소(정정)한다
        boolean allowed = rev ? canRevenue(by) && (by.getLoginId().equals(t.getCreatedBy()) || isBudgetStaff(by))
                : isBudgetStaff(by);
        if (!allowed) {
            return Result.fail(rev ? "기록한 직원이나 " + budgetDept + " 직원만 취소할 수 있습니다."
                    : "집행 취소는 " + budgetDept + " 직원만 할 수 있습니다.");
        }
        mapper.cancelTxn(txnId, by.getLoginId(), LocalDateTime.now());
        return Result.ok((rev ? "수납" : "집행") + " 기록을 취소했습니다.", t.getBudgetId());
    }

    public TxnVO txn(long txnId) {
        return mapper.selectTxn(txnId);
    }

    private static String checkTxn(LocalDate date, Long amount, String party, String description, int year) {
        if (date == null || date.getYear() != year) {
            return year + "년 안의 날짜를 입력하세요.";
        }
        if (amount == null || amount <= 0 || amount > MAX_AMOUNT) {
            return "금액을 원 단위로 1원 이상 입력하세요.";
        }
        if ((party != null && party.length() > 200) || (description != null && description.length() > 500)) {
            return "거래처·납부자는 200자, 내용은 500자 이내로 입력하세요.";
        }
        if (description == null || description.isBlank()) {
            return "내용을 입력하세요.";
        }
        return null;
    }

    private TxnVO txn(String kind, int year, Long budgetId, String code, LocalDate date, long amount, String party,
                      String description, LoginUser by) {
        TxnVO t = new TxnVO();
        t.setTxnId(mapper.nextTxnId());
        t.setKind(kind);
        t.setFiscalYear(year);
        t.setBudgetId(budgetId);
        t.setCode(code);
        t.setTxnDate(date);
        t.setAmount(amount);
        t.setParty(party == null || party.isBlank() ? null : party.trim());
        t.setDescription(description.trim());
        if (by != null) {
            t.setCreatedBy(by.getLoginId());
            t.setCreatedName(by.getUserName());
        }
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }
}
