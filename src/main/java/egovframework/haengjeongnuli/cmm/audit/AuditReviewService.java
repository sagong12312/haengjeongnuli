package egovframework.haengjeongnuli.cmm.audit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 접속기록 위·변조 확인과 정기 점검 (개인정보의 안전성 확보조치 기준 제8조).
 * 보관: 기록을 지우는 기능이 없고 DB 계정도 추가만 할 수 있다. 1년 이상(고유식별정보 처리 시스템은 2년 이상) 보관한다.
 * 점검 주기·방법은 기관 내부관리계획을 따르며, 여기서는 기간을 골라 이상 징후를 보고 결과를 남긴다.
 */
@Service
public class AuditReviewService {

    public record ChainCheck(boolean ok, long checked, int unchained, Long brokenAt, String message) {}

    public record ActorCount(String actorId, long count, boolean alert) {}

    public record Report(LocalDate from, LocalDate to, List<ActorCount> downloads, List<ActorCount> personalViews,
                         List<ActorCount> loginFails, List<AuditLogVO> afterHours, int afterHoursTotal) {}

    public static final List<String> RESULTS = List.of("이상 없음", "확인 필요", "조치함");

    private final AuditMapper mapper;
    private final int downloadAlert;
    private final int viewAlert;
    private final int failAlert;

    public AuditReviewService(AuditMapper mapper,
                              @Value("${app.audit.download-alert:100}") int downloadAlert,
                              @Value("${app.audit.view-alert:300}") int viewAlert,
                              @Value("${app.audit.login-fail-alert:5}") int failAlert) {
        this.mapper = mapper;
        this.downloadAlert = downloadAlert;
        this.viewAlert = viewAlert;
        this.failAlert = failAlert;
    }

    public int getDownloadAlert() { return downloadAlert; }
    public int getViewAlert() { return viewAlert; }
    public int getFailAlert() { return failAlert; }

    /** 해시 연결을 처음부터 다시 계산해 고치거나 지운 기록이 있는지 본다 */
    public ChainCheck verify() {
        long after = 0;
        long checked = 0;
        String prev = null;
        boolean first = true;
        while (true) {
            List<AuditLogVO> page = mapper.selectChained(after, 1000);
            if (page.isEmpty()) {
                break;
            }
            for (AuditLogVO v : page) {
                if (!first && !Objects.equals(prev, v.getPrevHash())) {
                    return broken(checked, v.getLogId(), "앞 기록과 이어지지 않습니다 (중간 기록이 지워졌거나 바뀌었습니다)");
                }
                if (!AuditService.hash(v.getPrevHash(), v).equals(v.getRowHash())) {
                    return broken(checked, v.getLogId(), "기록 내용이 해시와 맞지 않습니다 (기록이 바뀌었습니다)");
                }
                prev = v.getRowHash();
                first = false;
                checked++;
                after = v.getLogId();
            }
        }
        Map<String, Object> chain = mapper.selectChain();
        Object last = chain == null ? null : chain.get("lastHash");
        if (!Objects.equals(last, prev)) {
            return broken(checked, null, "마지막 기록이 저장된 마지막 해시와 다릅니다 (끝부분 기록이 지워졌습니다)");
        }
        int unchained = mapper.countUnchained();
        return new ChainCheck(true, checked, unchained, null, "기록 " + checked + "건의 해시 연결이 모두 맞습니다."
                + (unchained > 0 ? " (해시 연결 전 기록 " + unchained + "건은 확인 대상이 아닙니다)" : ""));
    }

    private ChainCheck broken(long checked, Long at, String why) {
        return new ChainCheck(false, checked, mapper.countUnchained(), at, why + (at == null ? "" : " — 기록 번호 " + at));
    }

    public Report report(LocalDate from, LocalDate to) {
        List<AuditLogVO> rows = mapper.selectForReview(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<String, long[]> down = new TreeMap<>(), view = new TreeMap<>(), fail = new TreeMap<>();
        List<AuditLogVO> late = new ArrayList<>();
        for (AuditLogVO v : rows) {
            String who = v.getActorId() == null ? "(알 수 없음)" : v.getActorId();
            switch (v.getAction()) {
                case FILE_DOWNLOAD, RECORD_TRANSFER_LIST -> down.computeIfAbsent(who, k -> new long[1])[0]++;
                case CIVIL_VIEW, WELFARE_VIEW -> view.computeIfAbsent(who, k -> new long[1])[0]++;
                case LOGIN_FAIL -> fail.computeIfAbsent(who, k -> new long[1])[0]++;
                default -> { }
            }
            if (v.getAction() != AuditAction.LOGIN_FAIL && isAfterHours(v.getOccurredAt())) {
                late.add(v);
            }
        }
        return new Report(from, to, top(down, downloadAlert), top(view, viewAlert), top(fail, failAlert),
                late.stream().limit(200).toList(), late.size());
    }

    /** 근무시간 밖: 오전 7시 전, 오후 10시 이후, 토·일요일 */
    static boolean isAfterHours(LocalDateTime t) {
        int h = t.getHour();
        DayOfWeek d = t.getDayOfWeek();
        return h < 7 || h >= 22 || d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private static List<ActorCount> top(Map<String, long[]> m, int alert) {
        return m.entrySet().stream().map(e -> new ActorCount(e.getKey(), e.getValue()[0], e.getValue()[0] >= alert))
                .sorted(Comparator.comparingLong(ActorCount::count).reversed()).limit(30).toList();
    }

    @Transactional
    public String save(LocalDate from, LocalDate to, String result, String note, LoginUser me) {
        if (from == null || to == null || to.isBefore(from)) {
            return "점검 기간을 확인하세요.";
        }
        if (!RESULTS.contains(result)) {
            return "점검 결과를 고르세요.";
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (!"이상 없음".equals(result) && n == null) {
            return "확인·조치 내용을 적으세요.";
        }
        if (n != null && n.length() > 1300) {
            return "내용은 1,300자 이내로 적으세요.";
        }
        AuditReviewVO r = new AuditReviewVO();
        r.setReviewId(mapper.nextReviewId());
        r.setPeriodFrom(from);
        r.setPeriodTo(to);
        r.setResult(result);
        r.setChainOkYn(verify().ok() ? "Y" : "N");
        r.setNote(n);
        r.setReviewedBy(me.getLoginId());
        r.setReviewedName(me.getUserName());
        r.setReviewedAt(LocalDateTime.now());
        mapper.insertReview(r);
        return null;
    }

    public List<AuditReviewVO> reviews() {
        return mapper.selectReviews();
    }
}
