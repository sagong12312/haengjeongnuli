package egovframework.haengjeongnuli.finance.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.hwp.HwpViewSupport;
import egovframework.haengjeongnuli.cmm.text.Csv;
import egovframework.haengjeongnuli.finance.service.AssetVO;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.finance.service.FinanceService.Result;
import egovframework.haengjeongnuli.finance.service.PurchaseService;
import egovframework.haengjeongnuli.finance.service.PurchaseVO;
import egovframework.haengjeongnuli.finance.service.QuoteVO;

/** 재정 › 구매(견적·업체 선정·검수)와 물품대장 */
@Controller
public class PurchaseController {

    private final PurchaseService purchases;
    private final FinanceService finance;
    private final AuditService audit;
    private final FileStorage storage;
    private final HwpViewSupport hwpView;

    public PurchaseController(PurchaseService purchases, FinanceService finance, AuditService audit, FileStorage storage,
                              HwpViewSupport hwpView) {
        this.purchases = purchases;
        this.finance = finance;
        this.audit = audit;
        this.storage = storage;
        this.hwpView = hwpView;
    }

    // ===== 구매 건 =====

    @GetMapping("/finance/purchases")
    public String list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false) String dept, @RequestParam(defaultValue = "1") int page, Model model) {
        int y = year(year);
        int now = Year.now().getValue();
        PurchaseService.Page<PurchaseVO> pg = purchases.page(me, y, dept, page);
        model.addAttribute("year", y);
        model.addAttribute("years", List.of(now - 2, now - 1, now));
        model.addAttribute("rows", pg.rows());
        model.addAttribute("pg", pg);
        model.addAttribute("scope", purchases.scope(me, dept));
        model.addAttribute("seeAll", finance.canSeeAllExp(me));
        model.addAttribute("types", PurchaseVO.TYPES);
        model.addAttribute("budgets", finance.spendable(me, now));
        return "finance/purchases";
    }

    @GetMapping("/finance/purchases.csv")
    public ResponseEntity<byte[]> csv(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) String dept) {
        int y = year(year);
        Csv csv = Csv.of("구매번호", "구매 건", "종류", "부서", "추정 금액", "견적 수", "선정 업체", "선정 금액", "선정 사유", "상태", "담당", "만든 날");
        for (PurchaseVO p : purchases.list(me, y, dept)) {
            csv.row(p.getPurNo(), p.getTitle(), p.getPurType(), p.getDeptName(), p.getEstimatedAmount(), p.getQuoteCount(), p.getVendor(),
                    p.getSelectedAmount(), p.getSelectReason(), p.getStatusLabel(), p.getCreatedName(), p.getCreatedAt().toLocalDate());
        }
        audit.record(AuditAction.FIN_EXPORT, "구매 목록 " + y, dept);
        return csv.download(y + "_구매.csv");
    }

    @PostMapping("/finance/purchases")
    public String create(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                         @RequestParam(required = false) String purType, @RequestParam(required = false) String estimatedAmount,
                         @RequestParam(required = false) Long budgetId, @RequestParam(required = false) String note,
                         RedirectAttributes ra) {
        Result r = purchases.create(title, purType, money(estimatedAmount), budgetId, note, me);
        ra.addFlashAttribute("message", r.message());
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + r.id(), "등록: " + title);
            return "redirect:/finance/purchases/" + r.id();
        }
        return "redirect:/finance/purchases";
    }

    @GetMapping("/finance/purchases/{id:\\d+}")
    public String view(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        PurchaseVO p = visible(id, me);
        model.addAttribute("p", p);
        model.addAttribute("quotes", purchases.quotes(id));
        model.addAttribute("inspections", purchases.inspections(id));
        model.addAttribute("docs", purchases.docs(id));
        model.addAttribute("contracts", purchases.contracts(id));
        model.addAttribute("assets", purchases.assets(id));
        model.addAttribute("budget", p.getBudgetId() == null ? null : finance.get(p.getBudgetId()));
        model.addAttribute("canEdit", purchases.canEdit(p, me));
        model.addAttribute("canInspect", purchases.canEdit(p, me) && !me.getLoginId().equals(p.getCreatedBy()));
        long limit = purchases.payLimit(p);
        long paid = purchases.paid(id);
        model.addAttribute("limit", limit);
        model.addAttribute("paid", paid);
        model.addAttribute("inspectDays", PurchaseService.INSPECT_DAYS);
        model.addAttribute("today", LocalDate.now());
        return "finance/purchase-view";
    }

    @PostMapping("/finance/purchases/{id:\\d+}/quotes")
    public String addQuote(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String vendor,
                           @RequestParam(required = false) String bizNo, @RequestParam(required = false) String amount,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quoteDate,
                           @RequestParam(required = false) String note, @RequestParam(required = false) MultipartFile file,
                           RedirectAttributes ra) throws IOException {
        visible(id, me);
        Result r = purchases.addQuote(id, vendor, bizNo, money(amount), quoteDate, note, file, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "견적: " + vendor + " " + amount);
        }
        return back(id, r, ra);
    }

    @PostMapping("/finance/purchases/{id:\\d+}/quotes/{quoteId:\\d+}/delete")
    public String deleteQuote(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable long quoteId,
                              RedirectAttributes ra) {
        visible(id, me);
        Result r = purchases.deleteQuote(id, quoteId, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "견적 뺌: " + quoteId);
        }
        return back(id, r, ra);
    }

    @PostMapping("/finance/purchases/{id:\\d+}/select")
    public String select(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) Long quoteId,
                         @RequestParam(required = false) String reason, RedirectAttributes ra) {
        visible(id, me);
        Result r = purchases.select(id, quoteId, reason, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "업체 선정: 견적 " + quoteId + (reason == null ? "" : " " + reason));
        }
        return back(id, r, ra);
    }

    @PostMapping("/finance/purchases/{id:\\d+}/unselect")
    public String unselect(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        visible(id, me);
        Result r = purchases.unselect(id, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "업체 선정 되돌림");
        }
        return back(id, r, ra);
    }

    @PostMapping("/finance/purchases/{id:\\d+}/inspect")
    public String inspect(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate notifiedDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectedDate,
                          @RequestParam(required = false) String result, @RequestParam(required = false) String note,
                          RedirectAttributes ra) {
        visible(id, me);
        Result r = purchases.inspect(id, notifiedDate, inspectedDate, result, note, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "검수 " + result + " " + inspectedDate);
        }
        return back(id, r, ra);
    }

    @PostMapping("/finance/purchases/{id:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        visible(id, me);
        Result r = purchases.cancel(id, reason, me);
        if (r.ok()) {
            audit.record(AuditAction.PURCHASE_SAVE, "구매 " + id, "취소: " + reason);
        }
        return back(id, r, ra);
    }

    /** 견적서 내려받기 (구매 건을 볼 수 있는 직원만) */
    @GetMapping("/finance/purchases/quotes/{quoteId:\\d+}/file")
    public ResponseEntity<Resource> quoteFile(@AuthenticationPrincipal LoginUser me, @PathVariable long quoteId) {
        QuoteVO q = purchases.quote(quoteId);
        PurchaseVO p = q == null ? null : purchases.get(q.getPurchaseId());
        if (q == null || q.getStoredName() == null || !purchases.canView(p, me)) {
            return ResponseEntity.notFound().build();
        }
        Path path = storage.path(q.getStoredName());
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        audit.record(AuditAction.FILE_DOWNLOAD, "구매 " + p.getPurNo() + " 견적서 " + quoteId, q.getOriginalName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(q.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(q.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    @GetMapping("/finance/purchases/quotes/{quoteId:\\d+}/view")
    public String quoteView(@AuthenticationPrincipal LoginUser me, @PathVariable long quoteId, Model model) {
        QuoteVO q = purchases.quote(quoteId);
        PurchaseVO p = q == null ? null : purchases.get(q.getPurchaseId());
        boolean ok = q != null && q.getStoredName() != null && purchases.canView(p, me);
        return hwpView.view(ok ? q.getStoredName() : null, ok ? q.getOriginalName() : "", ok ? "구매 " + p.getPurNo() + " 견적서 " + quoteId : "",
                "/finance/purchases/quotes/" + quoteId + "/file", ok ? "/finance/purchases/" + p.getPurchaseId() : "/finance/purchases",
                "finance", model);
    }

    // ===== 물품대장 =====

    @GetMapping("/finance/assets")
    public String assets(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String dept,
                         @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                         @RequestParam(required = false) Long purchase, @RequestParam(defaultValue = "1") int page, Model model) {
        PurchaseService.AssetPage ap = purchases.assetPage(me, dept, status, q, page);
        model.addAttribute("rows", ap.page().rows());
        model.addAttribute("pg", ap.page());
        model.addAttribute("total", ap.totalAmount());
        model.addAttribute("scope", purchases.scope(me, dept));
        model.addAttribute("seeAll", finance.canSeeAllExp(me));
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("purchaseOptions", purchases.openForDept(me.dept()).stream()
                .filter(p -> PurchaseVO.INSPECTED.equals(p.getStatus()) && "물품".equals(p.getPurType())).toList());
        model.addAttribute("purchaseId", purchase);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("checkedThisYear", ap.checkedThisYear());
        return "finance/assets";
    }

    @GetMapping("/finance/assets.csv")
    public ResponseEntity<byte[]> assetsCsv(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String dept,
                                            @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        Csv csv = Csv.of("물품번호", "품명", "규격", "수량", "단가", "금액", "취득일", "구매번호", "부서", "위치", "사용자", "상태",
                "최근 재물조사", "확인한 사람", "처분일", "처분 사유");
        for (AssetVO a : purchases.assetList(me, dept, status, q)) {
            csv.row(a.getAssetNo(), a.getName(), a.getSpec(), a.getQuantity(), a.getUnitPrice(), a.getTotal(), a.getAcquiredDate(), a.getPurNo(),
                    a.getDeptName(), a.getLocation(), a.getUserName(), a.getStatusLabel(), a.getLastCheckedAt(), a.getLastCheckedBy(),
                    a.getDisposedAt(), a.getDisposeReason());
        }
        audit.record(AuditAction.FIN_EXPORT, "물품대장", dept);
        return csv.download("물품대장.csv");
    }

    @PostMapping("/finance/assets")
    public String addAsset(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                           @RequestParam(required = false) String spec, @RequestParam(required = false) String quantity,
                           @RequestParam(required = false) String unitPrice,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquiredDate,
                           @RequestParam(required = false) Long purchaseId, @RequestParam(required = false) String location,
                           @RequestParam(required = false) String userName, RedirectAttributes ra) {
        AssetVO in = new AssetVO();
        in.setName(name);
        in.setSpec(spec);
        Long qty = money(quantity);
        in.setQuantity(qty == null || qty > Integer.MAX_VALUE ? -1 : qty.intValue());
        Long price = money(unitPrice);
        in.setUnitPrice(price == null ? -1 : price);
        in.setAcquiredDate(acquiredDate);
        in.setPurchaseId(purchaseId);
        in.setLocation(location);
        in.setUserName(userName);
        Result r = purchases.registerAsset(in, me);
        if (r.ok()) {
            audit.record(AuditAction.ASSET_SAVE, "물품 " + r.id(), "등록: " + name + " " + quantity);
        }
        ra.addFlashAttribute("message", r.message());
        return r.ok() && purchaseId != null ? "redirect:/finance/purchases/" + purchaseId : "redirect:/finance/assets";
    }

    @PostMapping("/finance/assets/{id:\\d+}/{action:place|check|status|dispose}")
    public String assetAction(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                              @RequestParam(required = false) String location, @RequestParam(required = false) String userName,
                              @RequestParam(required = false) String status, @RequestParam(required = false) String reason,
                              RedirectAttributes ra) {
        AssetVO a = purchases.asset(id);
        if (!purchases.canViewAsset(a, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Result r = switch (action) {
            case "place" -> purchases.moveAsset(id, location, userName, me);
            case "check" -> purchases.checkAsset(id, me);
            case "status" -> purchases.setAssetStatus(id, status, me);
            default -> purchases.disposeAsset(id, reason, me);
        };
        if (r.ok()) {
            audit.record(AuditAction.ASSET_SAVE, "물품 " + a.getAssetNo(), action + " " + nz(location) + nz(userName) + nz(status) + nz(reason));
        }
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/assets";
    }

    private PurchaseVO visible(long id, LoginUser me) {
        PurchaseVO p = purchases.get(id);
        if (!purchases.canView(p, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return p;
    }

    private static String back(long id, Result r, RedirectAttributes ra) {
        ra.addFlashAttribute("message", r.message());
        return "redirect:/finance/purchases/" + id;
    }

    private static int year(Integer year) {
        int now = Year.now().getValue();
        return year == null || year < 2000 || year > now ? now : year;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "" : s.trim() + " ";
    }

    private static Long money(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.replace(",", "").replace("원", "").trim();
        return t.matches("\\d{1,15}") ? Long.valueOf(t) : -1L;
    }
}
