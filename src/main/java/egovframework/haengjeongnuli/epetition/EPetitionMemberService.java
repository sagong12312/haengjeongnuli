package egovframework.haengjeongnuli.epetition;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.cmm.text.PhoneNumbers;
import egovframework.haengjeongnuli.epetition.EPetitionService.Problem;

/**
 * 전자민원 회원. 아이디·비밀번호로 가입하며 실명 확인(본인인증)은 하지 않는다.
 * 회원이면 접수번호·조회 비밀번호 없이 '내 민원'에서 처리상황을 보고, 신청서에 이름·연락처가 미리 채워진다.
 * 비밀번호는 BCrypt 로만 저장, 5번 틀리면 30분 잠금, 탈퇴하면 회원 정보를 바로 지운다
 * (이미 낸 민원은 민원 보존기간 동안 남고 회원과의 연결만 끊는다).
 */
@Service
public class EPetitionMemberService {

    /** 기관·관리자로 오해할 수 있는 아이디 */
    static final java.util.Set<String> RESERVED = java.util.Set.of("root", "system", "manager", "master", "operator", "webmaster",
            "support", "staff", "official", "gov", "minwon", "epetition", "nuri");

    private static final Pattern LOGIN_ID = Pattern.compile("[a-z][a-z0-9_]{4,19}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final EPetitionPortalMapper mapper;
    private final PasswordEncoder encoder;
    private final CivilProcessService process;
    private final EPetitionService ePetition;
    private final int maxFails;
    private final int lockMinutes;
    private final String dummyHash;

    public EPetitionMemberService(EPetitionPortalMapper mapper, PasswordEncoder encoder, CivilProcessService process,
                                  EPetitionService ePetition,
                                  @Value("${app.epetition.member-max-fails:5}") int maxFails,
                                  @Value("${app.epetition.member-lock-minutes:30}") int lockMinutes) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.process = process;
        this.ePetition = ePetition;
        this.maxFails = maxFails;
        this.lockMinutes = lockMinutes;
        this.dummyHash = encoder.encode("dummy-" + java.util.UUID.randomUUID());
    }

    public record SignupRequest(String loginId, String password, String name, String mobile, String email, String address,
                                Boolean termsConsent, Boolean privacyConsent) {}

    public record LoginRequest(String loginId, String password) {}

    public record ProfileRequest(String name, String mobile, String email, String address) {}

    public record PasswordRequest(String currentPassword, String newPassword) {}

    /** 영문·숫자·특수문자 가운데 2종류 이상으로 10자 이상, 또는 3종류로 8자 이상 (64자까지) */
    static String passwordProblem(String pw, String loginId) {
        if (pw == null || pw.length() < 8 || pw.length() > 64) {
            return "비밀번호는 8~64자로 정하세요.";
        }
        int kinds = (pw.matches(".*[A-Za-z].*") ? 1 : 0) + (pw.matches(".*\\d.*") ? 1 : 0)
                + (pw.matches(".*[^A-Za-z0-9].*") ? 1 : 0);
        if (!(kinds >= 3 || (kinds >= 2 && pw.length() >= 10))) {
            return "비밀번호는 영문·숫자·특수문자 가운데 2종류 이상이면 10자 이상, 3종류이면 8자 이상으로 정하세요.";
        }
        if (loginId != null && !loginId.isEmpty() && pw.toLowerCase().contains(loginId.toLowerCase())) {
            return "비밀번호에 아이디를 넣을 수 없습니다.";
        }
        if (pw.matches(".*(.)\\1\\1\\1.*")) {
            return "같은 글자를 4번 넘게 잇달아 쓸 수 없습니다.";
        }
        return null;
    }

    private record Profile(String name, String mobile, String email, String address) {}

    private static Profile profile(String name, String mobile, String email, String address) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            throw new Problem(422, "invalid_input", "이름을 100자 이내로 적으세요.");
        }
        String m = null;
        if (mobile != null && !mobile.isBlank()) {
            m = PhoneNumbers.mobile(mobile);
            if (m == null) {
                throw new Problem(422, "invalid_input", "휴대전화 번호를 확인하세요 (예: 010-1234-5678).");
            }
        }
        String e = null;
        if (email != null && !email.isBlank()) {
            e = email.trim();
            if (e.length() > 200 || !EMAIL.matcher(e).matches()) {
                throw new Problem(422, "invalid_input", "이메일 주소를 확인하세요.");
            }
        }
        String a = address == null || address.isBlank() ? null : address.trim();
        if (a != null && a.length() > 500) {
            throw new Problem(422, "invalid_input", "주소는 500자 이내로 적으세요.");
        }
        return new Profile(n, m, e, a);
    }

    @Transactional
    public Map<String, Object> signup(SignupRequest r) {
        if (r == null) {
            throw new Problem(400, "bad_request", "요청 본문이 없습니다.");
        }
        if (!Boolean.TRUE.equals(r.termsConsent()) || !Boolean.TRUE.equals(r.privacyConsent())) {
            throw new Problem(422, "consent_required", "이용약관과 개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
        }
        String id = r.loginId() == null ? "" : r.loginId().trim();
        if (RESERVED.contains(id) || id.startsWith("admin")) {
            throw new Problem(422, "invalid_login_id", "쓸 수 없는 아이디입니다 (관리자로 오해할 수 있는 이름). 다른 아이디를 정하세요.");
        }
        if (!LOGIN_ID.matcher(id).matches()) {
            throw new Problem(422, "invalid_login_id", "아이디는 영문 소문자로 시작하고 영문 소문자·숫자·밑줄(_)로 5~20자입니다.");
        }
        String bad = passwordProblem(r.password(), id);
        if (bad != null) {
            throw new Problem(422, "invalid_password", bad);
        }
        Profile p = profile(r.name(), r.mobile(), r.email(), r.address());
        if (mapper.loginIdUsed(id) > 0) {
            throw new Problem(409, "login_id_taken", "이미 쓰고 있는 아이디입니다. 다른 아이디를 정하세요.");
        }
        long mid = mapper.nextMemberId();
        try {
            mapper.insertMember(mid, id, encoder.encode(r.password()), p.name(), p.mobile(), p.email(), p.address(), LocalDateTime.now());
        } catch (DuplicateKeyException e) {
            throw new Problem(409, "login_id_taken", "이미 쓰고 있는 아이디입니다. 다른 아이디를 정하세요.");
        }
        return view(mapper.member(mid));
    }

    /** 로그인. 없는 아이디와 틀린 비밀번호는 같은 답 */
    @Transactional(noRollbackFor = Problem.class)
    public Map<String, Object> login(LoginRequest r) {
        return login(r, null);
    }

    /** 같은 국민 IP 에서 10분에 10번 넘게 틀리면 잠시 막는다 (여러 아이디에 비밀번호를 넣어 보는 공격) */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Deque<Long>> ipFails = new java.util.concurrent.ConcurrentHashMap<>();
    static final int IP_MAX_FAILS = 10;
    static final long IP_WINDOW_MILLIS = 10 * 60_000L;

    private int recentFails(String ip, long now) {
        java.util.Deque<Long> q = ip == null ? null : ipFails.get(ip);
        if (q == null) {
            return 0;
        }
        while (!q.isEmpty() && q.peekFirst() < now - IP_WINDOW_MILLIS) {
            q.pollFirst();
        }
        return q.size();
    }

    @Transactional(noRollbackFor = Problem.class)
    public Map<String, Object> login(LoginRequest r, String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? null : clientIp.trim();
        long nowMs = System.currentTimeMillis();
        if (ip != null && recentFails(ip, nowMs) >= IP_MAX_FAILS) {
            throw new Problem(429, "too_many_attempts", "로그인을 여러 번 틀렸습니다. 10분 뒤 다시 시도해 주세요.");
        }
        try {
            return doLogin(r);
        } catch (Problem p) {
            if (ip != null && ("not_matched".equals(p.code()) || "locked".equals(p.code()))) {
                if (ipFails.size() > 50_000) {
                    ipFails.clear();
                }
                ipFails.computeIfAbsent(ip, k -> new java.util.concurrent.ConcurrentLinkedDeque<>()).addLast(nowMs);
            }
            throw p;
        }
    }

    private Map<String, Object> doLogin(LoginRequest r) {
        String id = r == null || r.loginId() == null ? "" : r.loginId().trim();
        String pw = r == null ? null : r.password();
        if (id.isEmpty() || id.length() > 30 || pw == null || pw.length() > 64) {
            throw new Problem(401, "not_matched", "아이디 또는 비밀번호가 맞지 않습니다.");
        }
        Map<String, Object> m = mapper.memberForLogin(id);
        if (m == null) {
            encoder.matches(pw, dummyHash);
            throw new Problem(401, "not_matched", "아이디 또는 비밀번호가 맞지 않습니다.");
        }
        long mid = ((Number) m.get("id")).longValue();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime locked = EPetitionPortalService.dt(m.get("lockedUntil"));
        if (locked != null && locked.isAfter(now)) {
            throw new Problem(423, "locked", "비밀번호를 여러 번 틀려 " + locked.format(HM) + "까지 로그인할 수 없습니다.");
        }
        if (!encoder.matches(pw, (String) m.get("hash"))) {
            int n = ((Number) m.get("fails")).intValue() + 1;
            mapper.setMemberFails(mid, n >= maxFails ? 0 : n, n >= maxFails ? now.plusMinutes(lockMinutes) : null);
            throw new Problem(401, "not_matched", "아이디 또는 비밀번호가 맞지 않습니다.");
        }
        mapper.memberLoggedIn(mid, now);
        Map<String, Object> out = view(mapper.member(mid));
        Object last = m.get("lastLoginAt");
        out.put("previousLoginAt", EPetitionService.time(last));
        return out;
    }

    private Map<String, Object> mustGet(long memberId) {
        Map<String, Object> m = mapper.member(memberId);
        if (m == null) {
            throw new Problem(404, "member_not_found", "회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요.");
        }
        return m;
    }

    public Map<String, Object> get(long memberId) {
        return view(mustGet(memberId));
    }

    @Transactional
    public Map<String, Object> update(long memberId, ProfileRequest r) {
        mustGet(memberId);
        if (r == null) {
            throw new Problem(400, "bad_request", "요청 본문이 없습니다.");
        }
        Profile p = profile(r.name(), r.mobile(), r.email(), r.address());
        mapper.updateMember(memberId, p.name(), p.mobile(), p.email(), p.address());
        return view(mapper.member(memberId));
    }

    @Transactional
    public Map<String, Object> changePassword(long memberId, PasswordRequest r) {
        Map<String, Object> m = mustGet(memberId);
        if (r == null || r.currentPassword() == null || !encoder.matches(r.currentPassword(), (String) m.get("hash"))) {
            throw new Problem(422, "wrong_password", "지금 비밀번호가 맞지 않습니다.");
        }
        String bad = passwordProblem(r.newPassword(), (String) m.get("loginId"));
        if (bad != null) {
            throw new Problem(422, "invalid_password", bad);
        }
        if (encoder.matches(r.newPassword(), (String) m.get("hash"))) {
            throw new Problem(422, "invalid_password", "지금과 다른 비밀번호로 바꾸세요.");
        }
        mapper.setMemberPassword(memberId, encoder.encode(r.newPassword()), LocalDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", true);
        return out;
    }

    /** 탈퇴: 비밀번호를 확인하고 회원 정보를 지운다 */
    @Transactional
    public Map<String, Object> delete(long memberId, String password) {
        Map<String, Object> m = mustGet(memberId);
        if (password == null || !encoder.matches(password, (String) m.get("hash"))) {
            throw new Problem(422, "wrong_password", "비밀번호가 맞지 않습니다.");
        }
        int linked = mapper.detachMember(memberId);
        mapper.deleteMember(memberId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", true);
        out.put("complaints", linked);
        return out;
    }

    /** 내 민원 목록 */
    public Map<String, Object> complaints(long memberId, int page) {
        mustGet(memberId);
        int size = EPetitionService.PAGE_SIZE;
        int total = mapper.countMemberComplaints(memberId);
        int pages = Math.max(1, (total + size - 1) / size);
        int p = Math.min(Math.max(page, 1), pages);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> c : mapper.memberComplaints(memberId, (p - 1) * size, p * size)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("receiptNo", c.get("receiptNo"));
            it.put("kindLabel", EPetitionService.kindLabel((String) c.get("kind")));
            it.put("title", c.get("title"));
            CivilStatus st;
            try {
                st = CivilStatus.valueOf((String) c.get("status"));
            } catch (Exception e) {
                st = null;
            }
            it.put("status", st == null ? null : st.name());
            it.put("statusLabel", "WITHDRAWN".equals(c.get("resultType")) ? "취하" : EPetitionService.statusLabel(st));
            it.put("receivedAt", EPetitionService.time(c.get("receivedAt")));
            it.put("deadlineAt", EPetitionService.time(c.get("deadlineAt")));
            it.put("completedAt", EPetitionService.time(c.get("completedAt")));
            items.add(it);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", p);
        out.put("totalPages", pages);
        out.put("items", items);
        return out;
    }

    /** 내 민원 한 건 (처리상황 조회와 같은 내용, 취하·추가 의견·만족도에 쓸 표 포함) */
    public Map<String, Object> complaint(long memberId, String receiptNo) {
        mustGet(memberId);
        Long id = receiptNo == null || !receiptNo.matches("\\d{4}-\\d{6}") ? null : mapper.memberComplaintId(memberId, receiptNo);
        if (id == null) {
            throw new Problem(404, "not_found", "내 민원 가운데 그 접수번호가 없습니다.");
        }
        return ePetition.detail(process.get(id), true);
    }

    private static Map<String, Object> view(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("memberId", ((Number) m.get("id")).longValue());
        out.put("loginId", m.get("loginId"));
        out.put("name", m.get("name"));
        out.put("mobile", m.get("mobile"));
        out.put("email", m.get("email"));
        out.put("address", m.get("address"));
        out.put("createdAt", EPetitionService.time(m.get("createdAt")));
        out.put("pwChangedAt", EPetitionService.time(m.get("pwChangedAt")));
        return out;
    }
}
