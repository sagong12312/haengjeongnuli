package egovframework.haengjeongnuli.hygiene.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.hygiene.service.HygieneService.Result;
import egovframework.haengjeongnuli.hygiene.service.impl.HygieneMapper;
import egovframework.haengjeongnuli.hygiene.service.impl.SanctionMapper;

/**
 * 위생 행정처분 대장. 처분은 결재(처분 문서)를 마친 뒤 그 결과를 여기에 남긴다.
 * 공개(전자민원 행정처분 공개)는 담당자가 고른 처분만, 처분일부터 정한 기간 동안.
 */
@Service
public class SanctionService {

    /** 영업정지·사용중지 최대 기간 (식품위생법 제75조, 공중위생관리법 제11조: 6개월 이내) */
    public static final int MAX_MONTHS = 6;
    /** 식품위생법 제82조: 과징금 10억원 이하 */
    public static final long FOOD_FINE_MAX = 1_000_000_000L;
    /** 행정처분 공개 기간 (처분일부터) */
    public static final int PUBLIC_DAYS = 365;

    private final SanctionMapper mapper;
    private final HygieneMapper hygieneMapper;
    private final HygieneService hygiene;

    public SanctionService(SanctionMapper mapper, HygieneMapper hygieneMapper, HygieneService hygiene) {
        this.mapper = mapper;
        this.hygieneMapper = hygieneMapper;
        this.hygiene = hygiene;
    }

    public List<SanctionVO> byBusiness(long bizId) { return mapper.selectByBiz(bizId); }
    public SanctionVO get(long id) { return mapper.selectById(id); }

    public List<SanctionVO> range(int year, String law) {
        String l = "식품위생법".equals(law) || "공중위생관리법".equals(law) ? law : null;
        return mapper.selectRange(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), l);
    }

    /** 처분 근거 (화면에 미리 넣는 값, 담당자가 고칠 수 있다) */
    public static String defaultBasis(String law, String kind) {
        boolean food = "식품위생법".equals(law);
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case "영업정지", "일부 시설 사용중지", "영업소 폐쇄", "허가·등록 취소" -> food ? "식품위생법 제75조" : "공중위생관리법 제11조";
            case "과징금" -> food ? "식품위생법 제82조" : "공중위생관리법 제11조의2";
            default -> "";
        };
    }

    @Transactional
    public Result record(long bizId, String kind, String violation, String basis, LocalDate decidedOn, LocalDate start, LocalDate end,
                         Long amount, Long checkId, boolean publish, LoginUser me) {
        FoodBusinessVO b = hygiene.get(bizId);
        if (b == null || !hygiene.canEdit(me)) {
            return new Result(false, "행정처분은 위생 담당 부서 직원과 관리자만 기록합니다.", null);
        }
        if (kind == null || !SanctionVO.KINDS.contains(kind)) {
            return new Result(false, "처분 종류를 고르세요.", null);
        }
        String v = trim(violation);
        if (v == null || v.length() > 1000) {
            return new Result(false, "위반 내용을 1,000자 이내로 적으세요.", null);
        }
        String law = b.getLawName();
        String bs = trim(basis);
        if (bs == null) {
            bs = trim(defaultBasis(law, kind));
        }
        if (bs == null || bs.length() > 200) {
            return new Result(false, "처분 근거(법 조항)를 200자 이내로 적으세요.", null);
        }
        LocalDate today = LocalDate.now();
        if (decidedOn == null || decidedOn.isAfter(today) || decidedOn.isBefore(b.getReportedOn())) {
            return new Result(false, "처분일은 신고일과 오늘 사이여야 합니다.", null);
        }
        boolean period = SanctionVO.PERIOD_KINDS.contains(kind);
        if (period) {
            if (!b.isOpen()) {
                return new Result(false, "폐업한 영업소에는 영업정지를 할 수 없습니다.", null);
            }
            if (start == null || end == null || end.isBefore(start) || start.isBefore(decidedOn)) {
                return new Result(false, "정지 기간(시작일·끝나는 날)을 처분일 이후로 바르게 입력하세요.", null);
            }
            if (!end.isBefore(start.plusMonths(MAX_MONTHS))) {
                return new Result(false, "영업정지·사용중지는 " + MAX_MONTHS + "개월 이내로 정합니다 (" + law + ").", null);
            }
        } else {
            start = null;
            end = null;
        }
        boolean money = SanctionVO.AMOUNT_KINDS.contains(kind);
        if (money) {
            if (amount == null || amount <= 0 || amount > 999_999_999_999L) {
                return new Result(false, "금액을 원 단위로 입력하세요.", null);
            }
            if ("과징금".equals(kind) && "식품위생법".equals(law) && amount > FOOD_FINE_MAX) {
                return new Result(false, "식품위생법 과징금은 10억원 이하입니다 (제82조).", null);
            }
        } else {
            amount = null;
        }
        if (checkId != null && hygiene.checks(bizId).stream().noneMatch(c -> checkId.equals(c.getCheckId()))) {
            return new Result(false, "이 영업소의 점검 기록에서 고르세요.", null);
        }
        SanctionVO s = new SanctionVO();
        s.setSanctionId(mapper.nextId());
        s.setSanctionNo("처분-" + decidedOn.getYear() + "-" + String.format("%05d", s.getSanctionId()));
        s.setBizId(bizId);
        s.setLawName(law);
        s.setKind(kind);
        s.setViolation(v);
        s.setBasis(bs);
        s.setDecidedOn(decidedOn);
        s.setStartDate(start);
        s.setEndDate(end);
        s.setAmount(amount);
        s.setCheckId(checkId);
        s.setPublicYn(publish ? "Y" : "N");
        s.setCreatedBy(me.getLoginId());
        s.setCreatedName(me.getUserName());
        s.setCreatedAt(LocalDateTime.now());
        mapper.insert(s);
        String extra = "";
        // 영업소 폐쇄·허가 취소는 대장에서도 폐업으로
        if (("영업소 폐쇄".equals(kind) || "허가·등록 취소".equals(kind)) && b.isOpen()) {
            hygieneMapper.closeBusiness(bizId, decidedOn);
            extra = " 영업소 대장도 폐업으로 바꿨습니다.";
        }
        return new Result(true, "행정처분을 기록했습니다. " + s.getSanctionNo() + extra, s.getSanctionId());
    }

    /** 처분 취소 (행정심판·소송 결과, 직권 취소 등). 지우지 않고 표시한다 */
    @Transactional
    public Result cancel(long id, String reason, LoginUser me) {
        SanctionVO s = get(id);
        if (s == null || !hygiene.canEdit(me)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        String r = trim(reason);
        if (r == null || r.length() > 500) {
            return new Result(false, "취소 사유(행정심판 인용 등)를 500자 이내로 적으세요.", null);
        }
        return mapper.cancel(id, r) == 1 ? new Result(true, "처분을 취소로 표시했습니다. 공개도 내렸습니다.", s.getBizId())
                : new Result(false, "이미 취소한 처분입니다.", null);
    }

    @Transactional
    public Result setPublic(long id, boolean on, LoginUser me) {
        SanctionVO s = get(id);
        if (s == null || !hygiene.canEdit(me)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        return mapper.setPublic(id, on ? "Y" : "N") == 1 ? new Result(true, on ? "행정처분 공개에 올렸습니다." : "공개에서 내렸습니다.", s.getBizId())
                : new Result(false, "취소한 처분은 공개할 수 없습니다.", null);
    }

    /** 전자민원 행정처분 공개 (처분일부터 공개 기간 안, 공개로 고른 처분) */
    public record PublicPage(int total, int page, int totalPages, List<SanctionVO> items) {}

    public PublicPage publicPage(int page, String q) {
        LocalDate since = LocalDate.now().minusDays(PUBLIC_DAYS);
        String like = trim(q) == null ? null : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        int total = mapper.countPublic(since, like);
        int pages = Math.max(1, (total + 19) / 20);
        int p = Math.min(Math.max(page, 1), pages);
        return new PublicPage(total, p, pages, mapper.selectPublic(since, like, (p - 1) * 20, p * 20));
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
