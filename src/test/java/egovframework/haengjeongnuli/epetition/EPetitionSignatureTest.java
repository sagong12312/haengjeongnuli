package egovframework.haengjeongnuli.epetition;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 인수인계 문서(docs/epetition-api.md)의 서명 예시와 같은 값이 나오는지 (다른 언어 구현이 맞춰 볼 기준) */
class EPetitionSignatureTest {

    @Test
    void 문서의_서명_예시와_같다() {
        String sig = EPetitionAuthFilter.sign("example-secret-0123456789-abcdefghij".getBytes(StandardCharsets.UTF_8), "POST",
                "/api/epetition/v1/complaints/lookup", "1763280000", "3f9c2a7e5b1d4c6f8a0e2b4d6f8a1c3e",
                "{\"receiptNo\":\"2026-000123\",\"password\":\"abc12345\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(sig).isEqualTo("023b7f4b0d86966d4097b5559e801b09d4d2672414b2d895187bf61945e86a2d");
    }
}
