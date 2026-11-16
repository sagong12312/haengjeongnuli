package egovframework.haengjeongnuli.epetition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import egovframework.haengjeongnuli.cmm.security.AllowedNetworkFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 전자민원 서버만 연동 API를 부를 수 있게 한다.
 * <ol>
 * <li>기능이 켜져 있고 비밀값이 32자 이상이어야 한다 (아니면 404: 있는지조차 알리지 않음)</li>
 * <li>보낸 곳이 정한 주소(전자민원 서버)여야 한다</li>
 * <li>서명: X-EP-Timestamp(초), X-EP-Nonce(한 번만 쓰는 값), X-EP-Signature =
 *     hex(HMAC-SHA256(비밀값, 메서드 + "\n" + 경로와 쿼리 + "\n" + 시각 + "\n" + 논스 + "\n" + hex(SHA-256(본문))))</li>
 * <li>시각은 ±오차 안, 논스는 그 시간 안에 다시 쓸 수 없다 (재전송 방지)</li>
 * </ol>
 */
public class EPetitionAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EPetitionAuthFilter.class);
    static final int MAX_BODY = 256 * 1024;

    private final boolean enabled;
    private final byte[] secret;
    private final AllowedNetworkFilter networks;
    private final long skewSeconds;
    private final boolean requireHttps;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    public EPetitionAuthFilter(boolean enabled, String secret, String networksCsv, long skewSeconds) {
        this(enabled, secret, networksCsv, skewSeconds, false);
    }

    public EPetitionAuthFilter(boolean enabled, String secret, String networksCsv, long skewSeconds, boolean requireHttps) {
        this.requireHttps = requireHttps;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        // 전자민원과 같은 기준: 영문·숫자·기호(ASCII)만, 32자 이상 (한글이 섞이면 양쪽이 길이를 다르게 센다)
        this.enabled = enabled && secret != null && secret.length() >= 32 && secret.matches("[\\x21-\\x7E]+");
        this.networks = new AllowedNetworkFilter(networksCsv == null ? "" : networksCsv);
        this.skewSeconds = skewSeconds;
        if (enabled && !this.enabled) {
            log.error("전자민원 연동이 켜져 있지만 APP_EPETITION_SECRET 이 영문·숫자·기호 32자 이상이 아니라 연동 API를 열지 않습니다.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (requireHttps && !request.isSecure()) {
            // 이름·연락처·조회 비밀번호가 평문으로 오가지 않게 한다
            log.warn("전자민원 연동 API: HTTPS가 아닌 요청을 막았습니다 {}", request.getRemoteAddr());
            deny(response, 403, "https_required");
            return;
        }
        if (!networks.isAllowed(request.getRemoteAddr())) {
            log.warn("전자민원 연동 API: 허용하지 않은 주소 {}", request.getRemoteAddr());
            deny(response, 403, "forbidden_network");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            deny(response, 413, "body_too_large");
            return;
        }
        String ts = request.getHeader("X-EP-Timestamp");
        String nonce = request.getHeader("X-EP-Nonce");
        String sig = request.getHeader("X-EP-Signature");
        long now = Instant.now().getEpochSecond();
        long t;
        try {
            t = Long.parseLong(ts);
        } catch (Exception e) {
            deny(response, 401, "bad_timestamp");
            return;
        }
        if (Math.abs(now - t) > skewSeconds || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,64}") || sig == null) {
            deny(response, 401, "bad_signature");
            return;
        }
        String pathAndQuery = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String expected = sign(secret, request.getMethod(), pathAndQuery, ts, nonce, body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), sig.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            log.warn("전자민원 연동 API: 서명이 맞지 않습니다 {}", request.getRemoteAddr());
            deny(response, 401, "bad_signature");
            return;
        }
        usedNonces.values().removeIf(exp -> exp < now);
        if (usedNonces.putIfAbsent(nonce, now + 2 * skewSeconds) != null) {
            deny(response, 401, "replayed");
            return;
        }
        chain.doFilter(new BodyRequest(request, body), response);
    }

    /** 서명 값 (전자민원 서버도 똑같이 계산한다) */
    public static String sign(byte[] secret, String method, String pathAndQuery, String ts, String nonce, byte[] body) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String canonical = method.toUpperCase() + "\n" + pathAndQuery + "\n" + ts + "\n" + nonce + "\n" + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deny(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"ok\":false,\"error\":\"" + code + "\"}");
    }

    /** 읽어 둔 본문을 다시 읽을 수 있게 한다 */
    static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BodyRequest(HttpServletRequest r, byte[] body) {
            super(r);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public int getContentLength() { return body.length; }

        @Override
        public long getContentLengthLong() { return body.length; }
    }
}
