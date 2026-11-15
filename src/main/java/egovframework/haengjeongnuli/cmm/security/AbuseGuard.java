package egovframework.haengjeongnuli.cmm.security;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/**
 * 이상한 요청을 보낸 접속 주소를 잠시 막는다.
 * 공격 문구·탐색 주소·요청 위조·권한 없는 접근·형식을 속인 파일 등에 점수를 매기고, 정해진 시간 안에 점수가 쌓이면
 * 그 주소를 일정 시간 차단한다. 차단은 DB에 남기고(서버 여러 대·재시작에도 유지), 차단된 사람은 이의를 신청할 수 있다.
 */
@Component
public class AbuseGuard {

    private static final Logger log = LoggerFactory.getLogger(AbuseGuard.class);

    /** 요청 주소·값에 들어 있으면 공격 시도로 보는 문구 */
    static final Pattern ATTACK = Pattern.compile("(?i)(union\\s+(all\\s+)?select|'\\s*or\\s*'?\\d+'?\\s*=\\s*'?\\d|;\\s*(drop|delete|truncate)\\s+table"
            + "|<script|javascript:|onerror\\s*=|onload\\s*=|\\.\\./|\\.\\.\\\\|%00|\\$\\{jndi:|/etc/passwd|\\bsleep\\(\\s*\\d+\\s*\\)|benchmark\\(|xp_cmdshell)");
    /** 해킹 도구가 흔히 찾아보는 주소 */
    static final Pattern PROBE = Pattern.compile("(?i)(\\.php|\\.asp|\\.aspx|\\.jsp|\\.cgi|\\.env|/wp-|phpmyadmin|/\\.git|/\\.svn|cgi-bin|/actuator|/manager/html|/console)");

    public record Event(LocalDateTime at, String ip, int points, String reason) {}

    private final AbuseMapper mapper;
    private final AuditService audit;
    private final int threshold;
    private final long windowMillis;
    private final int blockMinutes;
    private final List<IpAddressMatcher> exempt;
    private final Map<String, Deque<long[]>> scores = new ConcurrentHashMap<>();
    private final Map<String, List<String>> reasons = new ConcurrentHashMap<>();
    private final Deque<Event> recent = new ArrayDeque<>();
    /** 차단 중인 주소 → 끝나는 시각(ms). DB와 30초마다 맞춘다 */
    private volatile Map<String, Long> blocked = Map.of();
    private volatile long loadedAt;

    public AbuseGuard(AbuseMapper mapper, AuditService audit,
                      @Value("${app.abuse.threshold:10}") int threshold,
                      @Value("${app.abuse.window-minutes:10}") int windowMinutes,
                      @Value("${app.abuse.block-minutes:15}") int blockMinutes,
                      @Value("${app.abuse.exempt:127.0.0.1/32,::1/128}") String exempt) {
        this.mapper = mapper;
        this.audit = audit;
        this.threshold = threshold;
        this.windowMillis = windowMinutes * 60_000L;
        this.blockMinutes = blockMinutes;
        this.exempt = Arrays.stream(exempt.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public int getBlockMinutes() { return blockMinutes; }
    public int getThreshold() { return threshold; }

    /** 지금 요청을 보낸 주소에 점수를 준다 */
    public void flag(int points, String reason) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            flag(sra.getRequest().getRemoteAddr(), points, reason);
        }
    }

    public void flag(String ip, int points, String reason) {
        if (ip == null || isExempt(ip) || isBlocked(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (recent) {
            recent.addFirst(new Event(LocalDateTime.now(), ip, points, reason));
            while (recent.size() > 200) {
                recent.removeLast();
            }
        }
        if (scores.size() > 10_000) {
            scores.clear();
            reasons.clear();
        }
        Deque<long[]> d = scores.computeIfAbsent(ip, k -> new ArrayDeque<>());
        int total;
        synchronized (d) {
            d.addLast(new long[] {now, points});
            while (!d.isEmpty() && d.peekFirst()[0] < now - windowMillis) {
                d.pollFirst();
            }
            total = d.stream().mapToInt(e -> (int) e[1]).sum();
        }
        List<String> rs = reasons.computeIfAbsent(ip, k -> new ArrayList<>());
        synchronized (rs) {
            rs.add(reason);
            if (rs.size() > 20) {
                rs.remove(0);
            }
        }
        if (total >= threshold) {
            block(ip, String.join(", ", new java.util.LinkedHashSet<>(rs)));
            scores.remove(ip);
            reasons.remove(ip);
        }
    }

    @Transactional
    public void block(String ip, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String r = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        mapper.insertBlock(mapper.nextBlockId(), ip, r, now, now.plusMinutes(blockMinutes));
        audit.recordAs(null, AuditAction.ABUSE_BLOCK, ip, r);
        log.warn("이상한 요청이 쌓여 {} 를 {}분 동안 막았습니다: {}", ip, blockMinutes, r);
        loadedAt = 0;
    }

    public boolean isExempt(String ip) {
        String clean = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;
        try {
            return exempt.stream().anyMatch(m -> m.matches(clean));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isBlocked(String ip) {
        Long until = active().get(ip);
        return until != null && until > System.currentTimeMillis();
    }

    /** 이 주소의 지금 차단 정보 (없으면 null) */
    public Map<String, Object> blockOf(String ip) {
        return mapper.selectActive(LocalDateTime.now()).stream().filter(m -> ip.equals(m.get("ip"))).findFirst().orElse(null);
    }

    private Map<String, Long> active() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > 30_000) {
            Map<String, Long> m = new ConcurrentHashMap<>();
            for (Map<String, Object> row : mapper.selectActive(LocalDateTime.now())) {
                Object u = row.get("untilAt");
                LocalDateTime until = u instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) u;
                m.merge((String) row.get("ip"), until.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), Math::max);
            }
            blocked = m;
            loadedAt = now;
        }
        return blocked;
    }

    @Transactional
    public boolean release(String ip, String by) {
        boolean ok = mapper.release(ip, by, LocalDateTime.now()) > 0;
        loadedAt = 0;
        return ok;
    }

    public List<Event> recentEvents() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /** 요청 주소·쿼리·값에서 공격 문구를 찾는다 (찾으면 그 이유) */
    public static String attackIn(String text) {
        if (text == null) {
            return null;
        }
        var m = ATTACK.matcher(text);
        return m.find() ? "공격 문구(" + m.group(1).strip() + ")" : null;
    }

    public static boolean isProbe(String path) {
        return path != null && PROBE.matcher(path).find();
    }
}
