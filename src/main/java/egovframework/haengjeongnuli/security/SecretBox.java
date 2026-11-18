package egovframework.haengjeongnuli.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB 에 넣는 비밀값(2단계 인증 비밀 키)을 AES-256-GCM 으로 암호화한다.
 * DB 가 통째로 새어 나가도 인증 앱 번호를 만들 수 없게 하려는 것이다.
 * 키는 환경변수 APP_SECRET_KEY 가 있으면 그것을, 없으면 키 파일(처음 한 번 무작위로 만듦)을 쓴다.
 * 키 파일은 DB 와 다른 곳에 두고 따로 백업해야 한다 (잃으면 직원들이 2단계 인증을 다시 설정해야 함).
 */
@Component
public class SecretBox {

    static final String PREFIX = "enc1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    @org.springframework.beans.factory.annotation.Autowired
    public SecretBox(@Value("${app.security.secret-key:}") String keyText,
                     @Value("${app.security.secret-key-file:./data/secret.key}") String keyFile) {
        this.key = keyText != null && !keyText.isBlank() ? sha256(keyText.trim()) : fromFile(Path.of(keyFile));
    }

    SecretBox(byte[] key) {
        this.key = key.clone();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized byte[] fromFile(Path p) {
        try {
            if (Files.isReadable(p)) {
                byte[] k = Base64.getDecoder().decode(Files.readString(p).trim());
                if (k.length != 32) {
                    throw new IllegalStateException("키 파일 길이가 맞지 않습니다: " + p);
                }
                return k;
            }
            byte[] k = new byte[32];
            RANDOM.nextBytes(k);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, Base64.getEncoder().encodeToString(k));
            try {
                Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                // 윈도우: 폴더 권한으로 지킨다
            }
            return k;
        } catch (IOException e) {
            throw new IllegalStateException("비밀값 암호화 키 파일을 읽거나 만들 수 없습니다: " + p, e);
        }
    }

    public String seal(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 암호화 전에 넣은 값(접두어 없음)은 그대로 돌려준다. 키가 다르거나 값이 바뀌었으면 null */
    public String open(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, in, 0, 12));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean sealed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
