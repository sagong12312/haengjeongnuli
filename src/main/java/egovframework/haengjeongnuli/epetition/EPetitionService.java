package egovframework.haengjeongnuli.epetition;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilForm;
import egovframework.haengjeongnuli.civil.service.CivilHistoryVO;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.civil.service.ResultType;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * 행정누리 전자민원(국민용 별도 서버)과의 연동.
 * 접수: 질의·건의·기타·고충 민원만 받는다 (법정민원은 구비서류·수수료가 있어 창구·정부24로).
 * 조회: 접수번호와 민원인이 정한 비밀번호. 여러 번 틀리면 잠시 잠근다.
 * 공개 상담: 민원인이 공개에 동의했고, 처리가 끝났고, 담당 직원이 게시를 허락한 것만.
 */
@Service
public class EPetitionService {

    public static final String REGISTER_ID = "e-petition";
    static final Set<CivilKind> ONLINE_KINDS = Set.of(CivilKind.QUERY, CivilKind.SUGGESTION, CivilKind.OTHER, CivilKind.GRIEVANCE);
    static final int PAGE_SIZE = 20;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final CivilService civil;
    private final CivilProcessService process;
    private final EPetitionMapper mapper;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final NotificationService notifications;
    private final UserService users;
    private final int maxFails;
    private final int lockMinutes;
    private final int ipMaxFails;
    private final long ipWindowMillis;
    /** 국민 IP별 최근 조회 실패 시각 (여러 접수번호에 같은 비밀번호를 넣어 보는 공격 막기) */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Deque<Long>> ipFails = new java.util.concurrent.ConcurrentHashMap<>();
    /** 전체 조회 실패 (대량 시도 감지용) */
    private final java.util.Deque<Long> allFails = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EPetitionService.class);

    /** 없는 접수번호에도 같은 시간이 걸리게 비교할 값 */
    private final String dummyHash;

    public EPetitionService(CivilService civil, CivilProcessService process, EPetitionMapper mapper, PasswordEncoder encoder,
                            Validator validator, NotificationService notifications, UserService users,
                            @Value("${app.epetition.lookup-max-fails:5}") int maxFails,
                            @Value("${app.epetition.lookup-lock-minutes:30}") int lockMinutes,
                            @Value("${app.epetition.lookup-ip-max-fails:10}") int ipMaxFails,
                            @Value("${app.epetition.lookup-ip-window-minutes:10}") int ipWindowMinutes) {
        this.ipMaxFails = ipMaxFails;
        this.ipWindowMillis = ipWindowMinutes * 60_000L;
        this.civil = civil;
        this.process = process;
        this.mapper = mapper;
        this.encoder = encoder;
        this.validator = validator;
        this.notifications = notifications;
        this.users = users;
        this.maxFails = maxFails;
        this.lockMinutes = lockMinutes;
        this.dummyHash = encoder.encode("dummy-" + java.util.UUID.randomUUID());
    }

    /** 오류 응답 */
    public static class Problem extends RuntimeException {
        private final int status;
        private final String code;

        public Problem(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() { return status; }
        public String code() { return code; }
    }

    public record ReceiveRequest(String kind, Boolean lawInterpretation, String name, String mobile, String homePhone, String email,
                                 String address, String fax, String notifyMethod, String title, String content, String password,
                                 Boolean publicConsent, Boolean privacyConsent) {}

    /** 민원 접수 */
    @Transactional
    public Map<String, Object> receive(ReceiveRequest r) {
        if (r == null) {
            throw new Problem(400, "bad_request", "요청 본문이 없습니다.");
        }
        CivilKind kind;
        try {
            kind = CivilKind.valueOf(r.kind());
        } catch (Exception e) {
            kind = null;
        }
        if (kind == null || !ONLINE_KINDS.contains(kind)) {
            throw new Problem(422, "invalid_kind", "민원 종류는 QUERY(질의)·SUGGESTION(건의)·OTHER(기타)·GRIEVANCE(고충) 가운데 하나입니다.");
        }
        String pw = r.password();
        if (pw == null || pw.length() < 8 || pw.length() > 20 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
            throw new Problem(422, "invalid_password", "조회 비밀번호는 영문과 숫자를 섞어 8~20자로 정하세요.");
        }
        if (r.content() == null || r.content().length() > 20000) {
            throw new Problem(422, "invalid_content", "민원 내용은 20,000자 이내로 입력하세요.");
        }
        CivilForm f = new CivilForm();
        f.setKind(kind);
        f.setLawInterpretation(Boolean.TRUE.equals(r.lawInterpretation()));
        f.setRoute("인터넷");
        f.setName(r.name());
        boolean noMobile = r.mobile() == null || r.mobile().isBlank();
        f.setNoMobile(noMobile);
        f.setMobile(r.mobile());
        f.setNoHome(r.homePhone() == null || r.homePhone().isBlank());
        f.setHomePhone(r.homePhone());
        f.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim());
        f.setAddress(r.address());
        f.setFax(r.fax());
        f.setNotify(r.notifyMethod() == null ? (noMobile ? "이메일" : "문자") : r.notifyMethod());
        f.setTitle(r.title());
        f.setContent(r.content());
        f.setPrivacyConsent(Boolean.TRUE.equals(r.privacyConsent()));
        Set<ConstraintViolation<CivilForm>> v = validator.validate(f);
        if (!v.isEmpty()) {
            throw new Problem(422, "invalid_input", v.iterator().next().getMessage());
        }
        if ("이메일".equals(f.getNotify()) && f.getEmail() == null) {
            throw new Problem(422, "invalid_input", "이메일로 결과를 받으려면 이메일을 입력하세요.");
        }
        CivilComplaintVO c = civil.receive(f, REGISTER_ID);
        mapper.setLookup(c.getComplaintId(), encoder.encode(pw), Boolean.TRUE.equals(r.publicConsent()) ? "Y" : "N");
        // 창구 직원·관리자에게 배정할 민원이 왔다고 알린다
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && (u.getRole() == LoginUser.Role.COUNTER || u.isAdmin())) {
                notifications.notify(u.getLoginId(), "전자민원 " + c.getReceiptNo() + " 이(가) 접수되었습니다. 담당을 배정하세요.",
                        "/civil/" + c.getComplaintId());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptNo", c.getReceiptNo());
        out.put("kind", kind.name());
        out.put("kindLabel", kind.getLabel());
        out.put("receivedAt", c.getReceivedAt().format(DT));
        out.put("deadlineAt", c.getDeadlineAt() == null ? null : c.getDeadlineAt().format(DT));
        out.put("complaintId", c.getComplaintId());
        return out;
    }

    /** 처리상황 조회. 비밀번호가 틀리면 횟수를 세고, 정한 횟수를 넘으면 잠근다 (없는 번호와 틀린 비밀번호는 같은 답) */
    @Transactional(noRollbackFor = Problem.class)
    public Map<String, Object> lookup(String receiptNo, String password) {
        return lookup(receiptNo, password, null);
    }

    /** clientIp: 전자민원이 X-EP-Client-IP 로 알려 준 국민 IP (없으면 IP 제한 없이 접수번호 잠금만) */
    @Transactional(noRollbackFor = Problem.class)
    public Map<String, Object> lookup(String receiptNo, String password, String clientIp) {
        long nowMs = System.currentTimeMillis();
        String ip = clientIp == null || clientIp.isBlank() ? null : clientIp.trim();
        if (ip != null && recent(ipFails.get(ip), nowMs) >= ipMaxFails) {
            throw new Problem(429, "too_many_attempts", "조회 비밀번호를 여러 번 틀렸습니다. 잠시 뒤 다시 시도해 주세요.");
        }
        try {
            return doLookup(receiptNo, password);
        } catch (Problem p) {
            if ("not_matched".equals(p.code()) || "locked".equals(p.code())) {
                if (ip != null) {
                    if (ipFails.size() > 50_000) {
                        ipFails.clear();
                    }
                    ipFails.computeIfAbsent(ip, k -> new java.util.concurrent.ConcurrentLinkedDeque<>()).addLast(nowMs);
                }
                allFails.addLast(nowMs);
                if (recent(allFails, nowMs) == 300) {
                    log.error("전자민원 처리상황 조회 실패가 {}분 동안 300건을 넘었습니다. 비밀번호 대입 공격인지 접속기록을 확인하세요.",
                            ipWindowMillis / 60_000);
                }
            }
            throw p;
        }
    }

    /** 창 안의 실패 수 (오래된 것은 지운다) */
    private int recent(java.util.Deque<Long> q, long nowMs) {
        if (q == null) {
            return 0;
        }
        while (!q.isEmpty() && q.peekFirst() < nowMs - ipWindowMillis) {
            q.pollFirst();
        }
        return q.size();
    }

    private Map<String, Object> doLookup(String receiptNo, String password) {
        if (receiptNo == null || !receiptNo.matches("\\d{4}-\\d{6}") || password == null || password.length() > 64) {
            throw new Problem(401, "not_matched", "접수번호 또는 비밀번호가 맞지 않습니다.");
        }
        Map<String, Object> row = mapper.lockForLookup(receiptNo);
        if (row == null || row.get("hash") == null) {
            encoder.matches(password, dummyHash); // 시간 차로 접수번호가 있는지 알 수 없게
            throw new Problem(401, "not_matched", "접수번호 또는 비밀번호가 맞지 않습니다.");
        }
        long id = ((Number) row.get("id")).longValue();
        int fails = ((Number) row.get("fails")).intValue();
        Object lu = row.get("lockedUntil");
        LocalDateTime lockedUntil = lu instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : lu instanceof LocalDateTime l ? l : null;
        LocalDateTime now = LocalDateTime.now();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            throw new Problem(423, "locked", "비밀번호를 여러 번 틀려 " + lockedUntil.format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "까지 조회할 수 없습니다.");
        }
        if (!encoder.matches(password, (String) row.get("hash"))) {
            int n = fails + 1;
            mapper.setFails(id, n >= maxFails ? 0 : n, n >= maxFails ? now.plusMinutes(lockMinutes) : null);
            throw new Problem(401, "not_matched", "접수번호 또는 비밀번호가 맞지 않습니다.");
        }
        if (fails > 0 || lockedUntil != null) {
            mapper.setFails(id, 0, null);
        }
        return detail(process.get(id), true);
    }

    /** 공개 상담 목록 */
    public Map<String, Object> publicList(int page, String q) {
        String like = q == null || q.isBlank() ? null
                : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        int total = mapper.countPublic(like);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(page, 1), pages);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : mapper.selectPublic(like, (p - 1) * PAGE_SIZE, p * PAGE_SIZE)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", ((Number) m.get("id")).longValue());
            it.put("receiptNo", m.get("receiptNo"));
            it.put("kindLabel", kindLabel((String) m.get("kind")));
            it.put("title", m.get("title"));
            it.put("name", mask((String) m.get("name")));
            it.put("receivedAt", time(m.get("receivedAt")));
            it.put("completedAt", time(m.get("completedAt")));
            it.put("dept", m.get("dept"));
            items.add(it);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", p);
        out.put("totalPages", pages);
        out.put("items", items);
        return out;
    }

    /** 공개 상담 한 건 (공개 동의·처리 완료·게시 허락된 것만) */
    public Map<String, Object> publicDetail(long id) {
        if (mapper.publicId(id) == null) {
            throw new Problem(404, "not_found", "공개된 상담이 아닙니다.");
        }
        return detail(process.get(id), false);
    }

    /** 담당 직원이 공개 상담 게시를 켜고 끈다 */
    @Transactional
    public String publish(long id, boolean on, LoginUser me) {
        CivilComplaintVO c = process.get(id);
        if (c == null || !process.canProcess(c, me)) {
            return "이 민원을 처리하는 직원만 게시할 수 있습니다.";
        }
        if (mapper.setPublished(id, on ? "Y" : "N") != 1) {
            return "민원인이 공개에 동의했고 처리가 끝난 민원만 게시할 수 있습니다.";
        }
        return null;
    }

    /**
     * 조회 비밀번호를 잊은 민원인에게 새 임시 비밀번호를 정해 준다. 직원은 전화·방문으로 본인을 확인한 뒤 알려 준다.
     * 새 비밀번호는 화면에 한 번만 보이고 저장은 BCrypt 로만 한다. 처리 담당·창구 직원·관리자만.
     * 돌려주는 값: 새 비밀번호 (할 수 없으면 null)
     */
    @Transactional
    public String resetLookupPassword(long id, LoginUser me) {
        CivilComplaintVO c = process.get(id);
        if (c == null || !(process.canProcess(c, me) || me.getRole() == LoginUser.Role.COUNTER || me.isAdmin())) {
            return null;
        }
        String pw = randomPassword();
        return mapper.resetLookup(id, encoder.encode(pw)) == 1 ? pw : null;
    }

    /** 영문 소문자 6자 + 숫자 4자 (헷갈리는 l·o·0·1 은 뺌) */
    static String randomPassword() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        String letters = "abcdefghijkmnpqrstuvwxyz", digits = "23456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            b.append(letters.charAt(r.nextInt(letters.length())));
        }
        for (int i = 0; i < 4; i++) {
            b.append(digits.charAt(r.nextInt(digits.length())));
        }
        return b.toString();
    }

    public Map<String, Object> flags(long id) {
        return mapper.flags(id);
    }

    private Map<String, Object> detail(CivilComplaintVO c, boolean owner) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receiptNo", c.getReceiptNo());
        out.put("kindLabel", c.getKind() == null ? "" : c.getKind().getLabel());
        out.put("title", c.getTitle());
        out.put("content", c.getContent());
        out.put("name", owner ? c.getApplicantName() : mask(c.getApplicantName()));
        out.put("status", c.getStatus() == null ? null : c.getStatus().name());
        out.put("statusLabel", statusLabel(c.getStatus()));
        out.put("receivedAt", time(c.getReceivedAt()));
        out.put("deadlineAt", time(c.getDeadlineAt()));
        out.put("dept", c.getDept());
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("step", "접수");
        first.put("at", time(c.getReceivedAt()));
        steps.add(first);
        for (CivilHistoryVO h : process.history(c.getComplaintId())) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("step", h.getActionLabel());
            s.put("at", time(h.getActedAt()));
            steps.add(s); // 담당 직원 이름·내부 메모는 보내지 않는다
        }
        out.put("steps", steps);
        if (c.getStatus() == CivilStatus.DONE) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("type", resultLabel(c.getResultType()));
            res.put("text", c.getResultText());
            res.put("remedy", c.getRejectRemedy());
            res.put("completedAt", time(c.getCompletedAt()));
            res.put("notifiedBy", c.getResultNotify());
            out.put("result", res);
        }
        return out;
    }

    static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String n = name.trim();
        if (n.length() == 1) {
            return "*";
        }
        if (n.length() == 2) {
            return n.charAt(0) + "*";
        }
        return n.charAt(0) + "*".repeat(n.length() - 2) + n.charAt(n.length() - 1);
    }

    private static String statusLabel(CivilStatus s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case RECEIVED -> "접수";
            case IN_PROGRESS -> "처리 중";
            case DONE -> "처리 완료";
        };
    }

    private static String kindLabel(String k) {
        try {
            return CivilKind.valueOf(k).getLabel();
        } catch (Exception e) {
            return "";
        }
    }

    private static String resultLabel(String t) {
        try {
            return t == null ? "" : ResultType.valueOf(t).getLabel();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String time(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DT);
        }
        if (o instanceof LocalDateTime l) {
            return l.format(DT);
        }
        return o.toString();
    }
}
