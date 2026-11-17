package egovframework.haengjeongnuli.epetition;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 민원인이 처리상황을 확인한 뒤(조회 비밀번호 또는 회원 로그인) 잠깐 쓰는 표.
 * 취하·추가 의견·만족도 응답은 이 표로만 한다 (비밀번호를 다시 보내지 않게).
 * 서버 메모리에만 두며 20분이 지나면 못 쓴다. 서버를 다시 켜면 모두 없어진다.
 */
@Component
public class ActionTokens {

    static final long TTL_MILLIS = 20 * 60_000L;
    private static final int MAX = 20_000;

    private record Entry(long complaintId, long expires) {}

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(long complaintId) {
        long now = System.currentTimeMillis();
        if (tokens.size() >= MAX) {
            tokens.values().removeIf(e -> e.expires() < now);
            if (tokens.size() >= MAX) {
                tokens.clear();
            }
        }
        byte[] b = new byte[24];
        random.nextBytes(b);
        String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        tokens.put(t, new Entry(complaintId, now + TTL_MILLIS));
        return t;
    }

    /** 표가 가리키는 민원 번호 (없거나 지났으면 null) */
    public Long resolve(String token) {
        if (token == null || token.length() != 32) {
            return null;
        }
        Entry e = tokens.get(token);
        if (e == null || e.expires() < System.currentTimeMillis()) {
            if (e != null) {
                tokens.remove(token);
            }
            return null;
        }
        return e.complaintId();
    }
}
