package egovframework.haengjeongnuli.finance.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.finance.service.FinanceService.Result;
import egovframework.haengjeongnuli.finance.service.impl.PurchaseMapper;

/**
 * 구매: 견적 → 업체 선정 → (계약) → 납품·완료 신고 → 검수(검사) → 지출결의 → 물품대장.
 * <ul>
 * <li>견적이 한 곳뿐이거나 가장 싼 곳이 아닌 업체를 고르면 그 사유를 적어야 한다
 *     (「지방자치단체를 당사자로 하는 계약에 관한 법률 시행령」 제30조: 1인 견적과 2인 이상 견적의 구분).</li>
 * <li>검수는 구매를 맡은 직원이 아닌 다른 직원이 하고, 완료 신고를 받은 날부터 14일이 넘으면 늦어진 사유를 적는다 (시행령 제64조).</li>
 * <li>구매 건에 연결한 지출결의는 검수 합격 뒤에만 올릴 수 있고, 선정 금액(계약이 있으면 계약 금액)을 넘겨 지급할 수 없다.</li>
 * </ul>
 */
@Service
public class PurchaseService {

    /** 완료 신고부터 검사까지 (시행령 제64조) */
    public static final int INSPECT_DAYS = 14;
    public static final int MAX_QUOTES = 20;

    private final PurchaseMapper mapper;
    private final FinanceService finance;
    private final FileStorage storage;

    public PurchaseService(PurchaseMapper mapper, FinanceService finance, FileStorage storage) {
        this.mapper = mapper;
        this.finance = finance;
        this.storage = storage;
    }

    /** 볼 부서: 예산편성과·관리자는 고른 부서(비우면 전체), 그 밖에는 내 부서 */
    public String scope(LoginUser me, String wanted) {
        if (finance.canSeeAllExp(me)) {
            return wanted == null || wanted.isBlank() ? null : wanted.trim();
        }
        return me.dept();
    }

    public List<PurchaseVO> list(LoginUser me, int year, String dept) {
        return mapper.selectList(year, scope(me, dept));
    }

    /** 목록 한 쪽에 보이는 건수 */
    public static final int PAGE_SIZE = 20;

    public record Page<T>(List<T> rows, int total, int page, int totalPages) {
        public int from() { return Math.max(1, page - 4); }
        public int to() { return Math.min(totalPages, from() + 9); }
    }

    private static int[] window(int total, int page) {
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(page, 1), pages);
        return new int[] {p, pages, (p - 1) * PAGE_SIZE, p * PAGE_SIZE};
    }

    public Page<PurchaseVO> page(LoginUser me, int year, String dept, int page) {
        String d = scope(me, dept);
        int total = mapper.countList(year, d);
        int[] w = window(total, page);
        return new Page<>(mapper.selectPage(year, d, w[2], w[3]), total, w[0], w[1]);
    }

    /** 물품대장 한 쪽과 조건 전체의 건수·금액·올해 재물조사 건수 */
    public record AssetPage(Page<AssetVO> page, long totalAmount, long checkedThisYear) {}

    public AssetPage assetPage(LoginUser me, String dept, String status, String q, int page) {
        String d = scope(me, dept);
        String st = assetStatus(status);
        String like = like(q);
        Map<String, Object> sum = mapper.summarizeAssets(d, st, like, LocalDate.now().withDayOfYear(1));
        int total = ((Number) sum.get("count")).intValue();
        int[] w = window(total, page);
        return new AssetPage(new Page<>(mapper.selectAssetPage(d, st, like, w[2], w[3]), total, w[0], w[1]),
                ((Number) sum.get("total")).longValue(), ((Number) sum.get("checked")).longValue());
    }

    private static String assetStatus(String status) {
        return AssetVO.USE.equals(status) || AssetVO.IDLE.equals(status) || AssetVO.DISPOSED.equals(status) ? status : null;
    }

    private static String like(String q) {
        return trim(q) == null ? null : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public PurchaseVO get(long id) {
        return mapper.selectById(id);
    }

    public boolean canView(PurchaseVO p, LoginUser me) {
        return p != null && (finance.canSeeAllExp(me) || me.dept().equals(p.getDeptName()));
    }

    /** 우리 부서 직원만 고친다 */
    public boolean canEdit(PurchaseVO p, LoginUser me) {
        return p != null && !me.dept().isEmpty() && me.dept().equals(p.getDeptName());
    }

    /** 결재·계약에서 고를 수 있는 구매 건 */
    public List<PurchaseVO> openForDept(String dept) {
        return dept == null || dept.isEmpty() ? List.of() : mapper.selectOpenByDept(dept);
    }

    public List<QuoteVO> quotes(long id) { return mapper.selectQuotes(id); }
    public QuoteVO quote(long quoteId) { return mapper.selectQuote(quoteId); }
    public List<InspectionVO> inspections(long id) { return mapper.selectInspections(id); }
    public List<Map<String, Object>> docs(long id) { return mapper.selectDocs(id); }
    public List<Map<String, Object>> contracts(long id) { return mapper.selectContracts(id); }
    public List<AssetVO> assets(long id) { return mapper.selectAssetsByPurchase(id); }

    /** 지급 한도: 계약이 있으면 계약 금액 합, 없으면 선정한 견적 금액 */
    public long payLimit(PurchaseVO p) {
        if (mapper.countContract(p.getPurchaseId()) > 0) {
            return mapper.sumContract(p.getPurchaseId());
        }
        return p.getSelectedAmount() == null ? 0 : p.getSelectedAmount();
    }

    public long paid(long id) {
        return mapper.sumPaid(id, null);
    }

    @Transactional
    public Result create(String title, String purType, Long estimated, Long budgetId, String note, LoginUser me) {
        if (me.dept().isEmpty()) {
            return Result.fail("부서가 정해진 직원만 구매 건을 만들 수 있습니다.");
        }
        String t = trim(title);
        if (t == null || t.length() > 300) {
            return Result.fail("구매 건 이름을 300자 이내로 입력하세요.");
        }
        if (purType == null || !PurchaseVO.TYPES.contains(purType)) {
            return Result.fail("종류(물품·용역·공사)를 고르세요.");
        }
        if (estimated == null || estimated <= 0 || estimated > FinanceService.MAX_AMOUNT) {
            return Result.fail("추정 금액을 원 단위 숫자로 입력하세요.");
        }
        String n = trim(note);
        if (n != null && n.length() > 1000) {
            return Result.fail("내용은 1,000자 이내로 입력하세요.");
        }
        if (budgetId != null) {
            BudgetVO b = finance.get(budgetId);
            if (b == null || !finance.canExecute(b, me)) {
                return Result.fail("관련 예산은 우리 부서의 승인된 세출예산에서 고르세요.");
            }
        }
        PurchaseVO p = new PurchaseVO();
        p.setPurchaseId(mapper.nextId());
        p.setPurYear(LocalDate.now().getYear());
        p.setPurNo("구매-" + p.getPurYear() + "-" + String.format("%05d", p.getPurchaseId()));
        p.setTitle(t);
        p.setPurType(purType);
        p.setDeptName(me.dept());
        p.setBudgetId(budgetId);
        p.setEstimatedAmount(estimated);
        p.setNote(n);
        p.setCreatedBy(me.getLoginId());
        p.setCreatedName(me.getUserName());
        p.setCreatedAt(LocalDateTime.now());
        mapper.insert(p);
        return Result.ok("구매 건을 만들었습니다. " + p.getPurNo() + " — 견적서를 받아 등록하세요.", p.getPurchaseId());
    }

    /** 견적서를 올릴 수 없는 이유 (없으면 null) */
    public String checkFile(MultipartFile file) {
        return file == null || file.isEmpty() ? null : storage.check(file);
    }

    @Transactional
    public Result addQuote(long id, String vendor, String bizNo, Long amount, LocalDate quoteDate, String note, MultipartFile file,
                           LoginUser me) throws IOException {
        mapper.lock(id);
        PurchaseVO p = get(id);
        if (!canEdit(p, me) || !PurchaseVO.QUOTING.equals(p.getStatus())) {
            return Result.fail("견적을 받는 중인 우리 부서 구매 건에만 견적을 넣을 수 있습니다.");
        }
        List<QuoteVO> existing = quotes(id);
        if (existing.size() >= MAX_QUOTES) {
            return Result.fail("견적은 " + MAX_QUOTES + "건까지 넣을 수 있습니다.");
        }
        String v = trim(vendor);
        if (v == null || v.length() > 200) {
            return Result.fail("업체 이름을 200자 이내로 입력하세요.");
        }
        String biz = trim(bizNo);
        if (biz != null) {
            if (!biz.matches("\\d{3}-?\\d{2}-?\\d{5}")) {
                return Result.fail("사업자등록번호는 000-00-00000 형식으로 입력하세요.");
            }
            String digits = biz.replace("-", "");
            biz = digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
            String b = biz;
            if (existing.stream().anyMatch(q -> b.equals(q.getBizNo()))) {
                return Result.fail("같은 사업자의 견적이 이미 있습니다. 한 업체는 한 번만 넣으세요.");
            }
        }
        if (amount == null || amount <= 0 || amount > FinanceService.MAX_AMOUNT) {
            return Result.fail("견적 금액을 원 단위 숫자로 입력하세요.");
        }
        if (quoteDate == null || quoteDate.isAfter(LocalDate.now())) {
            return Result.fail("견적일은 오늘 이전 날짜로 입력하세요.");
        }
        String n = trim(note);
        if (n != null && n.length() > 500) {
            return Result.fail("비고는 500자 이내로 입력하세요.");
        }
        String problem = checkFile(file);
        if (problem != null) {
            return Result.fail(problem);
        }
        QuoteVO q = new QuoteVO();
        q.setQuoteId(mapper.nextQuoteId());
        q.setPurchaseId(id);
        q.setVendor(v);
        q.setBizNo(biz);
        q.setAmount(amount);
        q.setQuoteDate(quoteDate);
        q.setNote(n);
        if (file != null && !file.isEmpty()) {
            String stored = storage.save(file);
            deleteOnRollback(stored);
            q.setOriginalName(FileStorage.cleanName(file.getOriginalFilename()));
            q.setStoredName(stored);
            q.setFileSize(file.getSize());
        }
        q.setCreatedBy(me.getLoginId());
        q.setCreatedAt(LocalDateTime.now());
        mapper.insertQuote(q);
        return Result.ok("견적을 넣었습니다: " + v + " " + String.format("%,d", amount) + "원", q.getQuoteId());
    }

    @Transactional
    public Result deleteQuote(long id, long quoteId, LoginUser me) {
        mapper.lock(id);
        PurchaseVO p = get(id);
        QuoteVO q = quote(quoteId);
        if (!canEdit(p, me) || q == null || q.getPurchaseId() != id || !PurchaseVO.QUOTING.equals(p.getStatus())) {
            return Result.fail("업체를 고르기 전의 우리 부서 견적만 뺄 수 있습니다.");
        }
        if (mapper.deleteQuote(quoteId) != 1) {
            return Result.fail("견적을 뺄 수 없습니다.");
        }
        if (q.getStoredName() != null) {
            String stored = q.getStoredName();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.deleteQuietly(stored);
                }
            });
        }
        return Result.ok("견적을 뺐습니다: " + q.getVendor(), id);
    }

    /**
     * 업체 선정. 견적이 한 곳뿐이거나, 가장 싼 곳이 아니거나, 추정 금액을 넘으면 사유를 적어야 한다.
     */
    @Transactional
    public Result select(long id, Long quoteId, String reason, LoginUser me) {
        mapper.lock(id);
        PurchaseVO p = get(id);
        if (!canEdit(p, me) || !PurchaseVO.QUOTING.equals(p.getStatus())) {
            return Result.fail("견적을 받는 중인 우리 부서 구매 건에서만 업체를 고를 수 있습니다.");
        }
        List<QuoteVO> qs = quotes(id);
        QuoteVO chosen = qs.stream().filter(q -> q.getQuoteId().equals(quoteId)).findFirst().orElse(null);
        if (chosen == null) {
            return Result.fail("이 구매 건에 넣은 견적 가운데서 고르세요.");
        }
        String r = trim(reason);
        if (r != null && r.length() > 500) {
            return Result.fail("선정 사유는 500자 이내로 입력하세요.");
        }
        long lowest = qs.stream().mapToLong(QuoteVO::getAmount).min().orElse(chosen.getAmount());
        if (r == null) {
            if (qs.size() == 1) {
                return Result.fail("견적이 한 곳뿐입니다. 1인 견적으로 하는 근거와 사유를 적으세요.");
            }
            if (chosen.getAmount() > lowest) {
                return Result.fail("가장 싼 견적이 아닙니다. 이 업체를 고른 사유를 적으세요.");
            }
            if (chosen.getAmount() > p.getEstimatedAmount()) {
                return Result.fail("추정 금액보다 비쌉니다. 사유를 적으세요.");
            }
        }
        if (mapper.select(id, chosen.getQuoteId(), r) != 1) {
            return Result.fail("업체를 고를 수 없습니다.");
        }
        return Result.ok("업체를 골랐습니다: " + chosen.getVendor() + ". 계약(필요하면)과 지출품의를 진행하세요.", id);
    }

    @Transactional
    public Result unselect(long id, LoginUser me) {
        mapper.lock(id);
        PurchaseVO p = get(id);
        if (!canEdit(p, me) || !PurchaseVO.SELECTED.equals(p.getStatus())) {
            return Result.fail("업체를 고른 우리 부서 구매 건만 되돌릴 수 있습니다.");
        }
        if (!inspections(id).isEmpty() || mapper.countContract(id) > 0 || mapper.countLiveDocs(id) > 0) {
            return Result.fail("이미 계약·결재·검수가 진행되어 업체 선정을 되돌릴 수 없습니다.");
        }
        mapper.unselect(id);
        return Result.ok("업체 선정을 되돌렸습니다. 견적을 다시 고르세요.", id);
    }

    /** 검수(검사) 기록. 구매를 맡은 직원이 아닌 같은 부서 직원이 한다 */
    @Transactional
    public Result inspect(long id, LocalDate notified, LocalDate inspected, String result, String note, LoginUser me) {
        mapper.lock(id);
        PurchaseVO p = get(id);
        if (!canEdit(p, me) || !PurchaseVO.SELECTED.equals(p.getStatus())) {
            return Result.fail("업체를 고르고 납품을 기다리는 우리 부서 구매 건만 검수할 수 있습니다.");
        }
        if (me.getLoginId().equals(p.getCreatedBy())) {
            return Result.fail("구매를 맡은 직원은 검수할 수 없습니다. 같은 부서의 다른 직원이 검수하세요.");
        }
        if (!InspectionVO.PASS.equals(result) && !InspectionVO.FAIL.equals(result)) {
            return Result.fail("검수 결과(합격·불합격)를 고르세요.");
        }
        LocalDate today = LocalDate.now();
        if (notified == null || inspected == null || notified.isAfter(today) || inspected.isAfter(today)) {
            return Result.fail("완료 신고(납품)일과 검수일을 오늘 이전 날짜로 입력하세요.");
        }
        if (inspected.isBefore(notified)) {
            return Result.fail("검수일이 완료 신고일보다 빠릅니다.");
        }
        String n = trim(note);
        if (n != null && n.length() > 1000) {
            return Result.fail("검수 내용은 1,000자 이내로 입력하세요.");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(notified, inspected);
        if (days > INSPECT_DAYS && n == null) {
            return Result.fail("완료 신고를 받은 날부터 " + INSPECT_DAYS + "일이 지났습니다(" + days + "일). 늦어진 사유를 적으세요.");
        }
        if (InspectionVO.FAIL.equals(result) && n == null) {
            return Result.fail("불합격이면 그 내용(보완 요구 사항)을 적으세요.");
        }
        InspectionVO i = new InspectionVO();
        i.setInspId(mapper.nextInspectionId());
        i.setPurchaseId(id);
        i.setNotifiedDate(notified);
        i.setInspectedDate(inspected);
        i.setResult(result);
        i.setNote(n);
        i.setInspectorId(me.getLoginId());
        i.setInspectorName(me.getUserName());
        i.setCreatedAt(LocalDateTime.now());
        mapper.insertInspection(i);
        if (InspectionVO.PASS.equals(result)) {
            mapper.markInspected(id);
            return Result.ok("검수 합격으로 기록했습니다. 이제 지출결의로 대금을 지급할 수 있습니다"
                    + ("물품".equals(p.getPurType()) ? " — 물품대장에도 올리세요." : "."), id);
        }
        return Result.ok("검수 불합격으로 기록했습니다. 보완 납품을 받은 뒤 다시 검수하세요.", id);
    }

    @Transactional
    public Result cancel(long id, String reason, LoginUser me) {
        mapper.lock(id);
        PurchaseVO p = get(id);
        if (!canEdit(p, me) || !p.isOpen()) {
            return Result.fail("검수 전의 우리 부서 구매 건만 취소할 수 있습니다.");
        }
        String r = trim(reason);
        if (r == null || r.length() > 500) {
            return Result.fail("취소 사유를 500자 이내로 입력하세요.");
        }
        if (mapper.countContract(id) > 0 || mapper.countLiveDocs(id) > 0) {
            return Result.fail("결재 중·완료된 품의·결의나 계약이 있습니다. 먼저 정리한 뒤 취소하세요.");
        }
        mapper.cancel(id, r, LocalDateTime.now());
        return Result.ok("구매 건을 취소했습니다.", id);
    }

    /**
     * 결재 문서에 구매 건을 연결할 때 확인 (없으면 null).
     * @param spending 지출결의이면 true (검수 합격·지급 한도를 본다)
     * @param submit 상신할 때만 금액·검수를 본다 (임시저장은 연결만 확인)
     */
    public String checkDoc(Long purchaseId, boolean spending, boolean submit, Long amount, String dept, Long exceptDocId) {
        if (purchaseId == null) {
            return null;
        }
        PurchaseVO p = get(purchaseId);
        if (p == null || dept == null || !dept.equals(p.getDeptName()) || PurchaseVO.CANCELED.equals(p.getStatus())) {
            return "우리 부서의 취소되지 않은 구매 건을 고르세요.";
        }
        if (!spending || !submit) {
            return null;
        }
        if (!PurchaseVO.INSPECTED.equals(p.getStatus())) {
            return "구매 건 " + p.getPurNo() + "은(는) 아직 검수 합격 전입니다. 검수를 마친 뒤 지출결의를 올리세요.";
        }
        if (amount == null) {
            return null;
        }
        long limit = payLimit(p);
        long left = limit - mapper.sumPaid(purchaseId, exceptDocId);
        if (amount > left) {
            return String.format("구매 건에서 더 지급할 수 있는 금액은 %,d원입니다 (%s %,d원 중 이미 결의한 금액 제외).", Math.max(left, 0),
                    mapper.countContract(purchaseId) > 0 ? "계약 금액" : "선정 금액", limit);
        }
        return null;
    }

    // ===== 물품대장 =====

    public List<AssetVO> assetList(LoginUser me, String dept, String status, String q) {
        return mapper.selectAssets(scope(me, dept), assetStatus(status), like(q));
    }

    public AssetVO asset(long id) {
        return mapper.selectAsset(id);
    }

    public boolean canViewAsset(AssetVO a, LoginUser me) {
        return a != null && (finance.canSeeAllExp(me) || me.dept().equals(a.getDeptName()));
    }

    public boolean canEditAsset(AssetVO a, LoginUser me) {
        return a != null && !me.dept().isEmpty() && me.dept().equals(a.getDeptName()) && !AssetVO.DISPOSED.equals(a.getStatus());
    }

    @Transactional
    public Result registerAsset(AssetVO in, LoginUser me) {
        if (me.dept().isEmpty()) {
            return Result.fail("부서가 정해진 직원만 물품을 올릴 수 있습니다.");
        }
        String name = trim(in.getName());
        if (name == null || name.length() > 200) {
            return Result.fail("품명을 200자 이내로 입력하세요.");
        }
        String spec = trim(in.getSpec());
        if (spec != null && spec.length() > 300) {
            return Result.fail("규격은 300자 이내로 입력하세요.");
        }
        if (in.getQuantity() <= 0 || in.getQuantity() > 100_000) {
            return Result.fail("수량을 1 이상으로 입력하세요.");
        }
        if (in.getUnitPrice() < 0 || in.getUnitPrice() > FinanceService.MAX_AMOUNT / in.getQuantity()) {
            return Result.fail("단가를 원 단위 숫자로 입력하세요.");
        }
        if (in.getAcquiredDate() == null || in.getAcquiredDate().isAfter(LocalDate.now())) {
            return Result.fail("취득일은 오늘 이전 날짜로 입력하세요.");
        }
        String loc = trim(in.getLocation());
        String user = trim(in.getUserName());
        if ((loc != null && loc.length() > 200) || (user != null && user.length() > 100)) {
            return Result.fail("위치는 200자, 사용자는 100자 이내로 입력하세요.");
        }
        if (in.getPurchaseId() != null) {
            mapper.lock(in.getPurchaseId());
            PurchaseVO p = get(in.getPurchaseId());
            if (!canEdit(p, me) || !PurchaseVO.INSPECTED.equals(p.getStatus()) || !"물품".equals(p.getPurType())) {
                return Result.fail("물품대장에는 검수 합격한 우리 부서의 물품 구매 건을 연결하세요.");
            }
            long limit = payLimit(p);
            long total = in.getUnitPrice() * in.getQuantity();
            if (mapper.sumAssets(p.getPurchaseId()) + total > limit) {
                return Result.fail(String.format("이 구매 건으로 올린 물품 금액이 구매 금액(%,d원)을 넘습니다.", limit));
            }
        }
        AssetVO a = new AssetVO();
        a.setAssetId(mapper.nextAssetId());
        a.setAssetNo("물품-" + in.getAcquiredDate().getYear() + "-" + String.format("%05d", a.getAssetId()));
        a.setName(name);
        a.setSpec(spec);
        a.setQuantity(in.getQuantity());
        a.setUnitPrice(in.getUnitPrice());
        a.setAcquiredDate(in.getAcquiredDate());
        a.setPurchaseId(in.getPurchaseId());
        a.setDeptName(me.dept());
        a.setLocation(loc);
        a.setUserName(user);
        a.setCreatedBy(me.getLoginId());
        a.setCreatedAt(LocalDateTime.now());
        mapper.insertAsset(a);
        return Result.ok("물품대장에 올렸습니다. " + a.getAssetNo(), a.getAssetId());
    }

    @Transactional
    public Result moveAsset(long id, String location, String userName, LoginUser me) {
        AssetVO a = asset(id);
        if (!canEditAsset(a, me)) {
            return Result.fail("처분하지 않은 우리 부서 물품만 고칠 수 있습니다.");
        }
        String loc = trim(location);
        String user = trim(userName);
        if ((loc != null && loc.length() > 200) || (user != null && user.length() > 100)) {
            return Result.fail("위치는 200자, 사용자는 100자 이내로 입력하세요.");
        }
        mapper.updatePlace(id, loc, user);
        return Result.ok("위치·사용자를 고쳤습니다.", id);
    }

    /** 재물조사: 실물을 확인했다고 기록한다 */
    @Transactional
    public Result checkAsset(long id, LoginUser me) {
        AssetVO a = asset(id);
        if (!canEditAsset(a, me)) {
            return Result.fail("처분하지 않은 우리 부서 물품만 확인할 수 있습니다.");
        }
        mapper.check(id, LocalDate.now(), me.getUserName());
        return Result.ok(a.getAssetNo() + " 실물을 확인했습니다.", id);
    }

    /**
     * 사용 ↔ 불용. 불용은 전자결재 '물품 불용결정'으로만 정하고(결재가 끝나면 자동 반영),
     * 여기서는 불용을 되돌려 다시 쓰는 것만 한다.
     */
    @Transactional
    public Result setAssetStatus(long id, String status, LoginUser me) {
        AssetVO a = asset(id);
        if (AssetVO.IDLE.equals(status)) {
            return Result.fail("불용은 전자결재 '물품 불용결정'으로 정합니다. 물품을 골라 '불용 결정 기안'을 누르세요.");
        }
        if (!canEditAsset(a, me) || !AssetVO.USE.equals(status) || !AssetVO.IDLE.equals(a.getStatus())) {
            return Result.fail("불용인 우리 부서 물품만 다시 사용으로 바꿀 수 있습니다.");
        }
        mapper.reuse(id);
        return Result.ok(a.getAssetNo() + "을(를) 다시 사용으로 바꿨습니다. 처분하려면 불용 결정을 다시 받아야 합니다.", id);
    }

    /** 처분: 결재로 불용을 결정한 물품만 (공유재산 및 물품 관리법: 불용 결정 뒤 관리전환·매각·양여·폐기) */
    @Transactional
    public Result disposeAsset(long id, String method, String reason, LoginUser me) {
        AssetVO a = asset(id);
        if (!canEditAsset(a, me) || !AssetVO.IDLE.equals(a.getStatus()) || a.getIdleDocId() == null) {
            return Result.fail("결재로 불용을 결정한 우리 부서 물품만 처분할 수 있습니다.");
        }
        if (method == null || !AssetVO.DISPOSE_METHODS.contains(method)) {
            return Result.fail("처분 방법(폐기·매각·양여·관리전환)을 고르세요.");
        }
        String r = trim(reason);
        if (r == null || r.length() > 500) {
            return Result.fail("처분 내용(폐기 업체·매각 금액·받는 곳 등)을 500자 이내로 입력하세요.");
        }
        if (mapper.dispose(id, LocalDate.now(), method, r) != 1) {
            return Result.fail("처분할 수 없습니다.");
        }
        return Result.ok(a.getAssetNo() + " " + method + " 처분을 기록했습니다.", id);
    }

    // ===== 불용 결정 (전자결재) =====

    /** 결재 문서 본문에 적힌 물품번호 */
    private static final java.util.regex.Pattern ASSET_NO = java.util.regex.Pattern.compile("물품-\\d{4}-\\d{5}");

    public static java.util.Set<String> assetNumbersIn(String body) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (body != null) {
            java.util.regex.Matcher m = ASSET_NO.matcher(body);
            while (m.find()) {
                out.add(m.group());
            }
        }
        return out;
    }

    /** 불용 결정 기안을 올릴 수 없는 이유 (없으면 null): 본문의 물품이 모두 우리 부서의 쓰는 중인 물품이어야 한다 */
    public String checkIdleDecision(String body, String dept) {
        java.util.Set<String> nos = assetNumbersIn(body);
        if (nos.isEmpty()) {
            return "본문에 불용으로 정할 물품번호(물품-연도-번호)를 적으세요. 물품대장에서 물품을 골라 '불용 결정 기안'을 누르면 자동으로 들어갑니다.";
        }
        if (nos.size() > 200) {
            return "한 문서에는 물품을 200건까지 넣을 수 있습니다.";
        }
        List<AssetVO> found = mapper.selectAssetsByNo(nos);
        for (String no : nos) {
            AssetVO a = found.stream().filter(x -> no.equals(x.getAssetNo())).findFirst().orElse(null);
            if (a == null || dept == null || !dept.equals(a.getDeptName())) {
                return no + "은(는) 우리 부서 물품대장에 없습니다.";
            }
            if (!AssetVO.USE.equals(a.getStatus())) {
                return no + "은(는) 이미 " + a.getStatusLabel() + " 상태입니다.";
            }
        }
        return null;
    }

    /** 결재가 끝난 불용 결정 문서를 물품대장에 반영한다. 반영한 건수 */
    @Transactional
    public int applyIdleDecision(long docId, String body, String dept) {
        int n = 0;
        for (String no : assetNumbersIn(body)) {
            n += mapper.markIdle(no, dept, docId);
        }
        return n;
    }

    /** 불용 결정 기안 본문 (물품대장에서 고른 물품) */
    public String idleDraftBody(List<Long> ids, LoginUser me) {
        StringBuilder b = new StringBuilder("아래 물품을 불용으로 결정하고자 합니다.\n\n");
        b.append("| 물품번호 | 품명 | 규격 | 수량 | 취득일 | 금액(원) | 불용 사유 |\n");
        b.append("|---|---|---|---|---|---|---|\n");
        int n = 0;
        for (Long id : ids == null ? List.<Long>of() : ids.stream().distinct().limit(200).toList()) {
            AssetVO a = asset(id);
            if (a == null || !canEditAsset(a, me) || !AssetVO.USE.equals(a.getStatus())) {
                continue;
            }
            b.append("| ").append(a.getAssetNo()).append(" | ").append(cell(a.getName())).append(" | ").append(cell(a.getSpec()))
                    .append(" | ").append(a.getQuantity()).append(" | ").append(a.getAcquiredDate()).append(" | ")
                    .append(String.format("%,d", a.getTotal())).append(" | (예: 파손, 수리 불가) |\n");
            n++;
        }
        if (n == 0) {
            return null;
        }
        b.append("\n처분 방법(예정): 폐기\n\n붙임 사진 등  1부.  끝.");
        return b.toString();
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "/").replace("\n", " ");
    }

    private void deleteOnRollback(String stored) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.deleteQuietly(stored);
                    }
                }
            });
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
