package egovframework.haengjeongnuli.security;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 보안 보강.
 * 2단계 인증(OTP) · 비밀번호 재사용 금지 · 로그인 기록 · 새 주소 로그인 알림 · 장기 미사용 계정 잠금 · 권한 변경 알림 · 화면 워터마크 글자
 */
@Service
public class SecurityService {

    public record Result(boolean ok, String message) {}

    private final SecurityMapper mapper;
    private final UserService users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final int pwHistory;
    private final int dormantDays;
    private final boolean otpRequiredForAdmin;

    public SecurityService(SecurityMapper mapper, UserService users, PasswordEncoder encoder, AuditService audit,
                           NotificationService notifications,
                           @Value("${app.security.password-history:3}") int pwHistory,
                           @Value("${app.security.dormant-days:90}") int dormantDays,
                           @Value("${app.security.otp-required-for-admin:false}") boolean otpRequiredForAdmin) {
        this.mapper = mapper;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.pwHistory = pwHistory;
        this.dormantDays = dormantDays;
        this.otpRequiredForAdmin = otpRequiredForAdmin;
    }

    /* ================================================================ 2단계 인증 (OTP) */

    public boolean otpEnabled(String loginId) {
        Map<String, Object> o = mapper.otp(loginId);
        return o != null && "Y".equals(o.get("enabled"));
    }

    /** 관리자에게 OTP 를 의무로 했는데 아직 안 켰는지 */
    public boolean otpRequiredButMissing(LoginUser u) {
        return otpRequiredForAdmin && u.isAdmin() && !otpEnabled(u.getLoginId());
    }

    /** 설정 시작: 새 비밀 키를 만들어 둔다 (켜기 전까지는 로그인에 쓰지 않음) */
    @Transactional
    public String startOtp(LoginUser me) {
        if (otpEnabled(me.getLoginId())) {
            return null;
        }
        String secret = Totp.newSecret();
        mapper.deleteOtp(me.getLoginId());
        mapper.insertOtp(me.getLoginId(), secret, LocalDateTime.now());
        return secret;
    }

    public String pendingSecret(LoginUser me) {
        Map<String, Object> o = mapper.otp(me.getLoginId());
        return o == null || "Y".equals(o.get("enabled")) ? null : (String) o.get("secret");
    }

    /** 앱에 나온 번호가 맞으면 켠다 */
    @Transactional
    public Result enableOtp(LoginUser me, String code) {
        Map<String, Object> o = mapper.otp(me.getLoginId());
        if (o == null || "Y".equals(o.get("enabled"))) {
            return new Result(false, "먼저 '2단계 인증 설정 시작'을 누르세요.");
        }
        long step = Totp.verify((String) o.get("secret"), code == null ? null : code.trim(), Totp.step(nowSec()), 0);
        if (step < 0) {
            return new Result(false, "번호가 맞지 않습니다. 휴대폰 시계가 맞는지 확인하고 새 번호를 넣으세요.");
        }
        mapper.enableOtp(me.getLoginId(), step);
        audit.record(AuditAction.OTP_ENABLE, me.getLoginId(), null);
        return new Result(true, "2단계 인증을 켰습니다. 다음 로그인부터 인증 앱의 번호도 넣습니다.");
    }

    /** 끄기: 비밀번호를 다시 확인한다 (관리자에게 의무면 끌 수 없음) */
    @Transactional
    public Result disableOtp(LoginUser me, String password) {
        LoginUser db = users.findByLoginId(me.getLoginId());
        if (db == null || password == null || !encoder.matches(password, db.getPasswordHash())) {
            return new Result(false, "비밀번호가 맞지 않습니다.");
        }
        if (otpRequiredForAdmin && db.isAdmin()) {
            return new Result(false, "시스템 관리자는 2단계 인증을 끌 수 없습니다 (기관 설정).");
        }
        mapper.deleteOtp(me.getLoginId());
        audit.record(AuditAction.OTP_DISABLE, me.getLoginId(), null);
        return new Result(true, "2단계 인증을 껐습니다.");
    }

    /** 관리자: 휴대폰을 잃어버린 직원의 OTP 를 지운다 (본인 확인 뒤) */
    @Transactional
    public Result resetOtp(String loginId, LoginUser admin) {
        if (admin == null || !admin.isAdmin()) {
            return new Result(false, "시스템 관리자만 할 수 있습니다.");
        }
        mapper.deleteOtp(loginId);
        audit.record(AuditAction.OTP_DISABLE, loginId, "관리자 초기화");
        notifications.notify(loginId, "관리자가 2단계 인증을 초기화했습니다. 다시 설정하세요.", "/mypage/security");
        return new Result(true, "2단계 인증을 초기화했습니다. 본인이 다시 설정해야 합니다.");
    }

    /** 로그인 두 번째 단계: 맞으면 true. 같은 번호를 두 번 쓰지 못한다 */
    @Transactional
    public boolean checkOtp(String loginId, String code) {
        Map<String, Object> o = mapper.otp(loginId);
        if (o == null || !"Y".equals(o.get("enabled"))) {
            return false;
        }
        long last = ((Number) o.get("lastStep")).longValue();
        long step = Totp.verify((String) o.get("secret"), code == null ? null : code.trim(), Totp.step(nowSec()), last);
        boolean ok = step >= 0 && mapper.useStep(loginId, step) == 1;
        audit.recordAs(loginId, ok ? AuditAction.OTP_SUCCESS : AuditAction.OTP_FAIL, null, null);
        return ok;
    }

    static long nowSec() {
        return System.currentTimeMillis() / 1000;
    }

    /* ================================================================ 비밀번호 재사용 금지 */

    /** 최근 n개 비밀번호(지금 것 포함) 가운데 같은 것이 있으면 true */
    public boolean reused(LoginUser u, String newPassword) {
        LoginUser db = users.findByLoginId(u.getLoginId());
        if (db != null && db.getPasswordHash() != null && encoder.matches(newPassword, db.getPasswordHash())) {
            return true;
        }
        for (String h : mapper.recentPw(u.getLoginId(), pwHistory)) {
            if (encoder.matches(newPassword, h)) {
                return true;
            }
        }
        return false;
    }

    public int historySize() {
        return pwHistory;
    }

    /** 비밀번호가 바뀐 뒤: 바뀌기 전 해시를 이력에 남긴다 (오래된 것은 지움) */
    @Transactional
    public void remember(String loginId, String oldHash) {
        if (oldHash == null) {
            return;
        }
        mapper.insertPw(loginId, oldHash, LocalDateTime.now());
        // 최근 n개만 남긴다 (오래된 해시를 쌓아 두지 않게)
        List<LocalDateTime> keep = mapper.recentPwTimes(loginId, pwHistory);
        if (keep.size() >= pwHistory) {
            mapper.prunePw(loginId, keep.get(keep.size() - 1));
        }
    }

    /* ================================================================ 로그인 기록 · 새 주소 알림 */

    public List<Map<String, Object>> loginHistory(String loginId) {
        List<Map<String, Object>> out = mapper.loginHistory(loginId, 30);
        for (Map<String, Object> m : out) {
            Object at = m.get("at");
            if (at instanceof java.sql.Timestamp t) {
                m.put("at", t.toLocalDateTime());
            }
            try {
                m.put("label", AuditAction.valueOf((String) m.get("action")).getLabel());
            } catch (IllegalArgumentException e) {
                m.put("label", m.get("action"));
            }
        }
        return out;
    }

    /** 처음 보는 주소에서 로그인하면 본인에게 알린다 (예전 로그인 기록이 있는 계정만) */
    public boolean alertIfNewIp(LoginUser u, String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        // 방금 남긴 로그인 기록은 빼고 본다
        LocalDateTime before = now.minusSeconds(5);
        if (mapper.pastLogins(u.getLoginId(), before) == 0) {
            return false;
        }
        if (mapper.seenIp(u.getLoginId(), ip, now.minusDays(180), before) > 0) {
            return false;
        }
        notifications.notify(u.getLoginId(), "처음 보는 주소(" + ip + ")에서 로그인했습니다. 본인이 아니면 바로 비밀번호를 바꾸고 관리자에게 알리세요.",
                "/mypage/security");
        return true;
    }

    /* ================================================================ 장기 미사용 계정 잠금 */

    /** 매일 새벽: 정한 날수(기본 90일) 동안 로그인하지 않은 직원 계정을 사용 중지한다 (관리자 계정은 빼고 알림만) */
    @Scheduled(cron = "${app.security.dormant-cron:0 20 3 * * *}")
    public void dormantJob() {
        lockDormant();
    }

    @Transactional
    public List<String> lockDormant() {
        List<String> locked = new ArrayList<>();
        if (dormantDays <= 0) {
            return locked;
        }
        LocalDateTime limit = LocalDateTime.now().minusDays(dormantDays);
        List<LoginUser> admins = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && u.isAdmin()) {
                admins.add(u);
            }
        }
        for (LoginUser u : users.list()) {
            if (!u.isEnabled() || u.isAdmin()) {
                continue;
            }
            LocalDateTime last = u.getLastLoginAt() != null ? u.getLastLoginAt() : u.getCreatedAt();
            if (last != null && last.isBefore(limit)) {
                users.setEnabled(u.getUserId(), false);
                audit.recordAs("system", AuditAction.USER_DISABLE, u.getLoginId(), "장기 미사용 (" + dormantDays + "일)");
                locked.add(u.getUserName() + "(" + u.getLoginId() + ")");
            }
        }
        if (!locked.isEmpty()) {
            for (LoginUser a : admins) {
                notifications.notify(a.getLoginId(), dormantDays + "일 넘게 쓰지 않은 계정 " + locked.size() + "개를 사용 중지했습니다: "
                        + String.join(", ", locked.subList(0, Math.min(5, locked.size()))) + (locked.size() > 5 ? " 외" : ""), "/admin/users");
            }
        }
        return locked;
    }

    public int dormantDays() {
        return dormantDays;
    }

    /* ================================================================ 권한 변경 알림 */

    /** 권한이 바뀌면 본인과 모든 관리자에게 알린다 (몰래 권한을 주는 것을 막기 위해) */
    public void privilegeChanged(LoginUser target, String what, LoginUser by) {
        String msg = target.getUserName() + "(" + target.getLoginId() + ") 계정의 권한이 바뀌었습니다: " + what + " — 바꾼 사람 "
                + (by == null ? "-" : by.getUserName());
        notifications.notify(target.getLoginId(), "내 계정의 권한이 바뀌었습니다: " + what + ". 모르는 변경이면 관리자에게 알리세요.", "/mypage/security");
        for (LoginUser a : users.list()) {
            if (a.isEnabled() && a.isAdmin() && (by == null || !a.getLoginId().equals(by.getLoginId()))) {
                notifications.notify(a.getLoginId(), msg, "/admin/users");
            }
        }
    }

    /* ================================================================ 화면 워터마크 */

    private static final DateTimeFormatter WM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 화면·출력물에 흐리게 깔리는 글자 (누가 언제 본 화면인지) */
    public static String watermarkText(LoginUser u, String ip) {
        return u.getUserName() + " " + u.getLoginId() + " · " + LocalDateTime.now().format(WM) + (ip == null ? "" : " · " + ip);
    }

    /** CSS 배경으로 쓰는 SVG (data URI). 글자는 XML 이스케이프 */
    public static String watermarkSvg(String text) {
        String t = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='420' height='220'><text x='10' y='120' transform='rotate(-20 210 110)' "
                + "font-family='sans-serif' font-size='15' fill='rgba(30,40,60,0.07)'>" + t + "</text></svg>";
        return "data:image/svg+xml;charset=utf-8," + java.net.URLEncoder.encode(svg, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
