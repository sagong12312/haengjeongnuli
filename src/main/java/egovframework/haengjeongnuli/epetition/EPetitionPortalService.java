package egovframework.haengjeongnuli.epetition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilHistoryVO;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.civil.service.impl.CivilMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;
import egovframework.haengjeongnuli.epetition.EPetitionService.Problem;

/**
 * 전자민원 포털 기능.
 * - 공지사항·자주 묻는 질문: 민원 창구 직원·관리자가 행정누리에서 쓰고, 전자민원이 읽어 간다.
 * - 공개 상담: 종류·정렬, 조회수(같은 사람은 하루 한 번), 공감(같은 사람은 한 번), 비슷한 상담.
 * - 처리 통계: 최근 12개월 숫자만 (개인정보 없음).
 * - 민원인이 하는 일 (조회한 뒤 받은 표로만): 처리 끝나기 전 취하, 추가 의견, 처리 뒤 만족도.
 */
@Service
public class EPetitionPortalService {

    static final int MAX_ADDENDA = 5;
    static final int ADDENDUM_MAX_CHARS = 1000;
    static final int PAGE_SIZE = EPetitionService.PAGE_SIZE;
    /** 직원이 할 일로 보이는 행위자 (전자민원 시스템) */
    private static final String ACTOR_ID = EPetitionService.REGISTER_ID;
    private static final String ACTOR_NAME = "민원인(전자민원)";

    private final EPetitionPortalMapper mapper;
    private final CivilProcessService process;
    private final CivilMapper civilMapper;
    private final ActionTokens tokens;
    private final NotificationService notifications;
    private final UserService users;
    private final byte[] voterSalt;
    /** 조회수: 같은 사람(IP)이 같은 글을 하루에 여러 번 열어도 한 번만 센다 */
    private final Map<String, LocalDate> seen = new ConcurrentHashMap<>();

    public EPetitionPortalService(EPetitionPortalMapper mapper, CivilProcessService process, CivilMapper civilMapper,
                                  ActionTokens tokens, NotificationService notifications, UserService users,
                                  @Value("${app.epetition.secret:}") String secret) {
        this.mapper = mapper;
        this.process = process;
        this.civilMapper = civilMapper;
        this.tokens = tokens;
        this.notifications = notifications;
        this.users = users;
        // IP 를 그대로 남기지 않고 비밀값을 섞은 해시로만 남긴다
        this.voterSalt = ("ep-like:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public record Result(boolean ok, String message, Long id) {
        static Result fail(String m) { return new Result(false, m, null); }
    }

    static String like(String q) {
        return q == null || q.isBlank() || q.length() > 100 ? null
                : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static Map<String, Object> page(int total, int page, List<Map<String, Object>> items) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", page);
        out.put("totalPages", Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
        out.put("items", items);
        return out;
    }

    private static int clampPage(int page, int total) {
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        return Math.min(Math.max(page, 1), pages);
    }

    private boolean firstViewToday(String key) {
        LocalDate today = LocalDate.now();
        if (seen.size() > 100_000) {
            seen.clear();
        }
        return !today.equals(seen.put(key, today));
    }

    /* ================================================================ 공지사항 */

    public Map<String, Object> notices(int page, String q) {
        String l = like(q);
        int total = mapper.countNotices(l);
        int p = clampPage(page, total);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : mapper.selectNotices(l, (p - 1) * PAGE_SIZE, p * PAGE_SIZE)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", ((Number) m.get("id")).longValue());
            it.put("title", m.get("title"));
            it.put("pinned", "Y".equals(m.get("pinned")));
            it.put("views", ((Number) m.get("views")).longValue());
            it.put("createdAt", EPetitionService.time(m.get("createdAt")));
            items.add(it);
        }
        return page(total, p, items);
    }

    /** 국민이 보는 공지 한 건 (조회수를 센다) */
    @Transactional
    public Map<String, Object> notice(long id, String clientIp) {
        if (mapper.notice(id) == null) {
            throw new Problem(404, "not_found", "공지사항을 찾을 수 없습니다.");
        }
        if (firstViewToday("n:" + id + ":" + clientIp)) {
            mapper.noticeViewed(id);
        }
        Map<String, Object> m = mapper.notice(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("title", m.get("title"));
        out.put("content", clob(m.get("content")));
        out.put("pinned", "Y".equals(m.get("pinned")));
        out.put("views", ((Number) m.get("views")).longValue());
        out.put("createdAt", EPetitionService.time(m.get("createdAt")));
        out.put("updatedAt", EPetitionService.time(m.get("updatedAt")));
        return out;
    }

    /** 직원 화면용 (조회수 세지 않음) */
    public Map<String, Object> noticeForStaff(long id) {
        Map<String, Object> m = mapper.notice(id);
        if (m != null) {
            m.put("content", clob(m.get("content")));
        }
        return m;
    }

    @Transactional
    public Result saveNotice(Long id, String title, String content, boolean pinned, LoginUser me) {
        if (!canManage(me)) {
            return Result.fail("민원 창구 직원과 관리자만 공지사항을 쓸 수 있습니다.");
        }
        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.trim();
        if (t.isEmpty() || t.length() > 300) {
            return Result.fail("제목을 300자 이내로 적으세요.");
        }
        if (c.isEmpty() || c.length() > 20000) {
            return Result.fail("내용을 20,000자 이내로 적으세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            long nid = mapper.nextNoticeId();
            mapper.insertNotice(nid, t, c, pinned ? "Y" : "N", me.getLoginId(), now);
            return new Result(true, "공지사항을 올렸습니다. 전자민원에 바로 보입니다.", nid);
        }
        if (mapper.updateNotice(id, t, c, pinned ? "Y" : "N", now) != 1) {
            return Result.fail("공지사항을 찾을 수 없습니다.");
        }
        return new Result(true, "공지사항을 고쳤습니다.", id);
    }

    @Transactional
    public Result deleteNotice(long id, LoginUser me) {
        if (!canManage(me)) {
            return Result.fail("민원 창구 직원과 관리자만 지울 수 있습니다.");
        }
        return mapper.deleteNotice(id) == 1 ? new Result(true, "공지사항을 지웠습니다.", id) : Result.fail("공지사항을 찾을 수 없습니다.");
    }

    /* ================================================================ 자주 묻는 질문 */

    /** 국민용: 쓰는 것만, 분류별로 묶어서 */
    public Map<String, Object> faqs() {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> m : mapper.faqs(true)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", ((Number) m.get("id")).longValue());
            it.put("question", m.get("question"));
            it.put("answer", m.get("answer"));
            groups.computeIfAbsent((String) m.get("category"), k -> new ArrayList<>()).add(it);
        }
        List<Map<String, Object>> cats = new ArrayList<>();
        groups.forEach((k, v) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("category", k);
            g.put("items", v);
            cats.add(g);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", cats);
        return out;
    }

    public List<Map<String, Object>> faqsForStaff() {
        return mapper.faqs(false);
    }

    public Map<String, Object> faq(long id) {
        return mapper.faq(id);
    }

    @Transactional
    public Result saveFaq(Long id, String category, String question, String answer, Integer sort, boolean use, LoginUser me) {
        if (!canManage(me)) {
            return Result.fail("민원 창구 직원과 관리자만 자주 묻는 질문을 쓸 수 있습니다.");
        }
        String cat = category == null ? "" : category.trim();
        String q = question == null ? "" : question.trim();
        String a = answer == null ? "" : answer.trim();
        if (cat.isEmpty() || cat.length() > 50) {
            return Result.fail("분류를 50자 이내로 적으세요 (예: 민원 신청, 처리상황 조회).");
        }
        if (q.isEmpty() || q.length() > 500) {
            return Result.fail("질문을 500자 이내로 적으세요.");
        }
        if (a.isEmpty() || a.length() > 1300) {
            return Result.fail("답변을 1,300자 이내로 적으세요.");
        }
        int so = sort == null ? 0 : Math.max(0, Math.min(sort, 9999));
        if (id == null) {
            long fid = mapper.nextFaqId();
            mapper.insertFaq(fid, cat, q, a, so, use ? "Y" : "N", me.getLoginId(), LocalDateTime.now());
            return new Result(true, "자주 묻는 질문을 올렸습니다.", fid);
        }
        if (mapper.updateFaq(id, cat, q, a, so, use ? "Y" : "N") != 1) {
            return Result.fail("자주 묻는 질문을 찾을 수 없습니다.");
        }
        return new Result(true, "자주 묻는 질문을 고쳤습니다.", id);
    }

    @Transactional
    public Result deleteFaq(long id, LoginUser me) {
        if (!canManage(me)) {
            return Result.fail("민원 창구 직원과 관리자만 지울 수 있습니다.");
        }
        return mapper.deleteFaq(id) == 1 ? new Result(true, "자주 묻는 질문을 지웠습니다.", id) : Result.fail("찾을 수 없습니다.");
    }

    public boolean canManage(LoginUser me) {
        return me != null && (me.isAdmin() || me.getRole() == LoginUser.Role.COUNTER);
    }

    /* ================================================================ 공개 상담 */

    public Map<String, Object> consultations(int page, String q, String kind, String sort) {
        return consultations(page, q, kind, null, sort);
    }

    public Map<String, Object> consultations(int page, String q, String kind, String category, String sort) {
        egovframework.haengjeongnuli.civil.service.CivilCategory cat = egovframework.haengjeongnuli.civil.service.CivilCategory.of(category);
        String g = cat == null ? null : cat.name();
        String l = like(q);
        String k = kind != null && EPetitionService.ONLINE_KINDS.stream().anyMatch(x -> x.name().equals(kind)) ? kind : null;
        String so = "views".equals(sort) || "likes".equals(sort) ? sort : "recent";
        int total = mapper.countConsultations(l, k, g);
        int p = clampPage(page, total);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : mapper.selectConsultations(l, k, g, so, (p - 1) * PAGE_SIZE, p * PAGE_SIZE)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", ((Number) m.get("id")).longValue());
            it.put("receiptNo", m.get("receiptNo"));
            it.put("kind", m.get("kind"));
            it.put("kindLabel", EPetitionService.kindLabel((String) m.get("kind")));
            it.put("category", m.get("category"));
            it.put("categoryLabel", categoryLabel((String) m.get("category")));
            it.put("title", m.get("title"));
            it.put("name", EPetitionService.mask((String) m.get("name")));
            it.put("receivedAt", EPetitionService.time(m.get("receivedAt")));
            it.put("completedAt", EPetitionService.time(m.get("completedAt")));
            it.put("dept", m.get("dept"));
            it.put("views", ((Number) m.get("views")).longValue());
            it.put("likes", ((Number) m.get("likes")).longValue());
            items.add(it);
        }
        Map<String, Object> out = page(total, p, items);
        out.put("kind", k);
        out.put("category", g);
        out.put("sort", so);
        return out;
    }

    /** 공개 상담 한 건에 붙는 조회수·공감·비슷한 상담 (상세는 EPetitionService.publicDetail) */
    @Transactional
    public Map<String, Object> consultationExtras(long id, String clientIp) {
        if (firstViewToday("c:" + id + ":" + clientIp)) {
            mapper.consultationViewed(id);
        }
        Map<String, Object> c = mapper.consultationCounts(id);
        Map<String, Object> out = new LinkedHashMap<>();
        if (c == null) {
            return out;
        }
        out.put("views", ((Number) c.get("views")).longValue());
        out.put("likes", ((Number) c.get("likes")).longValue());
        out.put("liked", clientIp != null && mapper.liked(id, voter(clientIp)) > 0);
        List<Map<String, Object>> rel = new ArrayList<>();
        for (Map<String, Object> m : mapper.related(id, (String) c.get("kind"))) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", ((Number) m.get("id")).longValue());
            it.put("title", m.get("title"));
            it.put("completedAt", EPetitionService.time(m.get("completedAt")));
            rel.add(it);
        }
        out.put("related", rel);
        return out;
    }

    /** "도움이 됐어요". 같은 사람(IP)은 한 번만 */
    @Transactional
    public Map<String, Object> like(long id, String clientIp) {
        if (mapper.consultationCounts(id) == null) {
            throw new Problem(404, "not_found", "공개된 상담이 아닙니다.");
        }
        if (clientIp == null || clientIp.isBlank()) {
            throw new Problem(400, "bad_request", "누가 눌렀는지 알 수 없습니다.");
        }
        boolean added = false;
        String v = voter(clientIp);
        if (mapper.liked(id, v) == 0) {
            try {
                mapper.insertLike(id, v, LocalDateTime.now());
                mapper.addLike(id);
                added = true;
            } catch (DuplicateKeyException e) {
                // 거의 같은 때 두 번 누름
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("likes", ((Number) mapper.consultationCounts(id).get("likes")).longValue());
        return out;
    }

    private String voter(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(voterSalt);
            md.update(ip.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /* ================================================================ 민원 안내 · 처리 통계 */

    /** 전자민원으로 신청할 수 있는 민원 종류와 처리기간 */
    public Map<String, Object> info() {
        List<Map<String, Object>> kinds = new ArrayList<>();
        for (CivilKind k : CivilKind.values()) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("kind", k.name());
            it.put("label", k.getLabel());
            it.put("description", k.getDescription());
            it.put("period", k.getPeriodText());
            it.put("online", EPetitionService.ONLINE_KINDS.contains(k));
            kinds.add(it);
        }
        List<Map<String, Object>> cats = new ArrayList<>();
        for (egovframework.haengjeongnuli.civil.service.CivilCategory g : egovframework.haengjeongnuli.civil.service.CivilCategory.values()) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("category", g.name());
            it.put("label", g.getLabel());
            it.put("examples", g.getExamples());
            cats.add(it);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kinds", kinds);
        out.put("categories", cats);
        out.put("maxAddenda", MAX_ADDENDA);
        return out;
    }

    /** 최근 12개월 처리 통계 (숫자만) */
    public Map<String, Object> stats() {
        YearMonth now = YearMonth.now();
        YearMonth first = now.minusMonths(11);
        LocalDateTime from = first.atDay(1).atStartOfDay();
        Map<YearMonth, int[]> months = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            months.put(first.plusMonths(i), new int[2]);
        }
        Map<String, int[]> kinds = new LinkedHashMap<>();
        for (CivilKind k : CivilKind.values()) {
            kinds.put(k.name(), new int[2]);
        }
        Map<String, int[]> cats = new LinkedHashMap<>();
        for (egovframework.haengjeongnuli.civil.service.CivilCategory g : egovframework.haengjeongnuli.civil.service.CivilCategory.values()) {
            cats.put(g.name(), new int[2]);
        }
        int received = 0, done = 0, onTime = 0, online = 0, satisCount = 0, satisSum = 0, withdrawn = 0;
        long daysSum = 0;
        int[] satisDist = new int[6];
        for (Map<String, Object> r : mapper.statRows(from)) {
            LocalDateTime rec = dt(r.get("receivedAt"));
            LocalDateTime comp = dt(r.get("completedAt"));
            boolean isWithdrawn = "WITHDRAWN".equals(r.get("resultType"));
            if (rec != null && !rec.isBefore(from)) {
                received++;
                int[] m = months.get(YearMonth.from(rec));
                if (m != null) {
                    m[0]++;
                }
                int[] k = kinds.get(String.valueOf(r.get("kind")));
                if (k != null) {
                    k[0]++;
                }
                int[] g = cats.get(String.valueOf(r.get("category")));
                if (g != null) {
                    g[0]++;
                }
                if ("Y".equals(r.get("online"))) {
                    online++;
                }
            }
            if ("DONE".equals(r.get("status")) && comp != null && !comp.isBefore(from)) {
                if (isWithdrawn) {
                    withdrawn++;
                    continue;
                }
                done++;
                int[] m = months.get(YearMonth.from(comp));
                if (m != null) {
                    m[1]++;
                }
                int[] k = kinds.get(String.valueOf(r.get("kind")));
                if (k != null) {
                    k[1]++;
                }
                int[] g = cats.get(String.valueOf(r.get("category")));
                if (g != null) {
                    g[1]++;
                }
                if (rec != null) {
                    daysSum += Math.max(0, ChronoUnit.DAYS.between(rec.toLocalDate(), comp.toLocalDate()));
                }
                LocalDateTime dl = dt(r.get("deadlineAt"));
                LocalDate d = dl != null ? null : date(r.get("deadline"));
                if ((dl != null && !comp.isAfter(dl)) || (d != null && !comp.toLocalDate().isAfter(d)) || (dl == null && d == null)) {
                    onTime++;
                }
                Object s = r.get("satis");
                if (s instanceof Number n && n.intValue() >= 1 && n.intValue() <= 5) {
                    satisCount++;
                    satisSum += n.intValue();
                    satisDist[n.intValue()]++;
                }
            }
        }
        List<Map<String, Object>> monthList = new ArrayList<>();
        months.forEach((ym, v) -> {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("month", ym.toString());
            it.put("received", v[0]);
            it.put("done", v[1]);
            monthList.add(it);
        });
        List<Map<String, Object>> kindList = new ArrayList<>();
        kinds.forEach((k, v) -> {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("kind", k);
            it.put("label", EPetitionService.kindLabel(k));
            it.put("received", v[0]);
            it.put("done", v[1]);
            kindList.add(it);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", first.toString());
        out.put("to", now.toString());
        out.put("received", received);
        out.put("done", done);
        out.put("withdrawn", withdrawn);
        out.put("online", online);
        out.put("avgDays", done == 0 ? null : Math.round(daysSum * 10.0 / done) / 10.0);
        out.put("onTimeRate", done == 0 ? null : Math.round(onTime * 1000.0 / done) / 10.0);
        out.put("satisfactionCount", satisCount);
        out.put("satisfactionAvg", satisCount == 0 ? null : Math.round(satisSum * 100.0 / satisCount) / 100.0);
        List<Integer> dist = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            dist.add(satisDist[i]);
        }
        out.put("satisfactionDist", dist);
        out.put("months", monthList);
        out.put("kinds", kindList);
        List<Map<String, Object>> catList = new ArrayList<>();
        cats.forEach((g, v) -> {
            if (v[0] == 0 && v[1] == 0) {
                return; // 없는 분야는 빼서 짧게
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("category", g);
            it.put("label", categoryLabel(g));
            it.put("received", v[0]);
            it.put("done", v[1]);
            catList.add(it);
        });
        out.put("categories", catList);
        return out;
    }

    /* ================================================================ 민원인이 하는 일 */

    private CivilComplaintVO byToken(String token) {
        Long id = tokens.resolve(token);
        if (id == null) {
            throw new Problem(401, "token_expired", "조회한 지 오래되었습니다. 처리상황을 다시 조회해 주세요.");
        }
        CivilComplaintVO c = process.get(id);
        if (c == null) {
            throw new Problem(404, "not_found", "민원을 찾을 수 없습니다.");
        }
        return c;
    }

    private void history(long id, String action, String detail) {
        CivilHistoryVO h = new CivilHistoryVO();
        h.setHistId(civilMapper.nextHistoryId());
        h.setComplaintId(id);
        h.setAction(action);
        h.setDetail(detail.length() > 1300 ? detail.substring(0, 1300) : detail);
        h.setActorId(ACTOR_ID);
        h.setActorName(ACTOR_NAME);
        h.setActedAt(LocalDateTime.now());
        civilMapper.insertHistory(h);
    }

    /** 담당자(없으면 창구 직원·관리자)에게 알림 */
    private void tellStaff(CivilComplaintVO c, String message) {
        String link = "/civil/" + c.getComplaintId();
        if (c.getAssigneeId() != null && !c.getAssigneeId().isBlank()) {
            notifications.notify(c.getAssigneeId(), message, link);
            return;
        }
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && (u.getRole() == LoginUser.Role.COUNTER || u.isAdmin())) {
                notifications.notify(u.getLoginId(), message, link);
            }
        }
    }

    private static String clean(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", "\n").trim();
        return t.length() > max ? null : t;
    }

    /** 처리 완료 전에 민원인이 민원을 거둬들인다 */
    @Transactional
    public Map<String, Object> withdraw(String token, String reason) {
        CivilComplaintVO c = byToken(token);
        String r = clean(reason, 500);
        if (r == null) {
            throw new Problem(422, "invalid_input", "취하 이유는 500자 이내로 적어 주세요.");
        }
        if (c.getStatus() == CivilStatus.DONE) {
            throw new Problem(409, "already_done", "처리가 끝난 민원은 취하할 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        String text = "민원인이 전자민원으로 민원을 취하했습니다." + (r.isEmpty() ? "" : "\n취하 이유: " + r);
        if (mapper.withdraw(c.getComplaintId(), text, ACTOR_ID, now) != 1) {
            throw new Problem(409, "already_done", "처리가 끝난 민원은 취하할 수 없습니다.");
        }
        history(c.getComplaintId(), "WITHDRAW", r.isEmpty() ? "취하" : "취하 이유: " + r);
        tellStaff(c, "민원 " + c.getReceiptNo() + " 을(를) 민원인이 취하했습니다. 처리를 멈추세요.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptNo", c.getReceiptNo());
        out.put("withdrawnAt", EPetitionService.time(now));
        return out;
    }

    /** 처리 중에 민원인이 덧붙이는 의견 (처리 이력에 남고 담당자에게 알림) */
    @Transactional
    public Map<String, Object> addendum(String token, String text) {
        CivilComplaintVO c = byToken(token);
        String t = clean(text, ADDENDUM_MAX_CHARS);
        if (t == null || t.isEmpty()) {
            throw new Problem(422, "invalid_input", "추가 의견을 " + ADDENDUM_MAX_CHARS + "자 이내로 적어 주세요.");
        }
        if (c.getStatus() == CivilStatus.DONE) {
            throw new Problem(409, "already_done", "처리가 끝난 민원에는 의견을 덧붙일 수 없습니다. 새 민원으로 신청해 주세요.");
        }
        if (mapper.addendumCount(c.getComplaintId()) >= MAX_ADDENDA) {
            throw new Problem(409, "too_many", "추가 의견은 " + MAX_ADDENDA + "번까지 낼 수 있습니다.");
        }
        history(c.getComplaintId(), "ADDENDUM", t);
        tellStaff(c, "민원 " + c.getReceiptNo() + " 에 민원인이 추가 의견을 냈습니다.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptNo", c.getReceiptNo());
        out.put("count", mapper.addendumCount(c.getComplaintId()));
        return out;
    }

    /** 처리 결과 만족도 (1~5점, 한 번만) */
    @Transactional
    public Map<String, Object> rate(String token, Integer score, String comment) {
        CivilComplaintVO c = byToken(token);
        if (score == null || score < 1 || score > 5) {
            throw new Problem(422, "invalid_input", "만족도를 1~5점 가운데 골라 주세요.");
        }
        String cm = clean(comment, 1000);
        if (cm == null) {
            throw new Problem(422, "invalid_input", "의견은 1,000자 이내로 적어 주세요.");
        }
        if (mapper.rate(c.getComplaintId(), score, cm.isEmpty() ? null : cm, LocalDateTime.now()) != 1) {
            throw new Problem(409, "cannot_rate", "처리가 끝난 민원에만, 한 번만 응답할 수 있습니다.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptNo", c.getReceiptNo());
        out.put("score", score);
        return out;
    }

    /** 직원 화면: 최근 만족도 응답과 통계 */
    public List<Map<String, Object>> recentSatisfaction() {
        return mapper.recentSatisfaction(30);
    }

    public Map<String, Object> satisfactionOf(long complaintId) {
        return mapper.satisfaction(complaintId);
    }

    /* ================================================================ 도움 */

    static String categoryLabel(String name) {
        egovframework.haengjeongnuli.civil.service.CivilCategory g = egovframework.haengjeongnuli.civil.service.CivilCategory.of(name);
        return (g == null ? egovframework.haengjeongnuli.civil.service.CivilCategory.ETC : g).getLabel();
    }

    static String clob(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Clob c) {
            try {
                return c.getSubString(1, (int) c.length());
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        return o.toString();
    }

    static LocalDateTime dt(Object o) {
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (o instanceof LocalDateTime l) {
            return l;
        }
        return null;
    }

    static LocalDate date(Object o) {
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (o instanceof LocalDate l) {
            return l;
        }
        if (o instanceof LocalDateTime l) {
            return l.toLocalDate();
        }
        return null;
    }
}
