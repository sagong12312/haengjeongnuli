package egovframework.haengjeongnuli.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 시간 기반 일회용 비밀번호 (RFC 6238 TOTP, HMAC-SHA1, 30초, 6자리).
 * Google Authenticator·Microsoft Authenticator 등 휴대폰 인증 앱과 같은 방식.
 */
public final class Totp {

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public static final int PERIOD = 30;

    private Totp() {
    }

    /** 새 비밀 키 (160비트, Base32 32글자) */
    public static String newSecret() {
        byte[] b = new byte[20];
        new SecureRandom().nextBytes(b);
        return base32(b);
    }

    public static long step(long epochSeconds) {
        return epochSeconds / PERIOD;
    }

    /** 그 시간 칸의 6자리 번호 */
    public static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 번호가 맞으면 맞은 시간 칸, 아니면 -1. 휴대폰 시계가 조금 틀려도 되게 앞뒤 한 칸(±30초)까지 본다.
     * lastStep 보다 같거나 이른 칸은 받지 않는다 (같은 번호 다시 쓰기 막기).
     */
    public static long verify(String secret, String code, long nowStep, long lastStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return -1;
        }
        for (long s = nowStep - 1; s <= nowStep + 1; s++) {
            if (s > lastStep && java.security.MessageDigest.isEqual(code(secret, s).getBytes(), code.getBytes())) {
                return s;
            }
        }
        return -1;
    }

    /** 인증 앱에 넣는 주소 (휴대폰에서 누르면 앱이 열린다) */
    public static String uri(String issuer, String account, String secret) {
        String i = java.net.URLEncoder.encode(issuer, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String a = java.net.URLEncoder.encode(account, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + i + ":" + a + "?secret=" + secret + "&issuer=" + i + "&digits=6&period=" + PERIOD;
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte b : data) {
            buf = (buf << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buf >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(B32.charAt((buf << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String t = s.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8);
        int buf = 0, bits = 0;
        for (char c : t.toCharArray()) {
            int v = B32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Base32 글자가 아닙니다");
            }
            buf = (buf << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buf >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }
}
