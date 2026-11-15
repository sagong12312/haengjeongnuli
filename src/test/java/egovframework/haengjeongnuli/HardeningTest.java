package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import egovframework.haengjeongnuli.cmm.security.InitialAdminRunner;
import egovframework.haengjeongnuli.user.service.UserService;

/** 쿠키·헤더·운영 시작 조건 (보안 점검 권장 사항) */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HardeningTest {

    @LocalServerPort int port;

    @Test
    void 세션_쿠키는_HttpOnly_SameSite_Lax() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<String> cookies = r.headers().allValues("Set-Cookie");
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("JSESSIONID=").contains("HttpOnly").contains("SameSite=Lax"));
        assertThat(r.headers().firstValue("Referrer-Policy")).hasValue("same-origin");
        assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(r.headers().firstValue("X-Frame-Options")).hasValue("SAMEORIGIN");
    }

    @Test
    void 운영에서는_첫_관리자_비밀번호_없이_시작하지_않는다() {
        UserService none = Mockito.mock(UserService.class);
        Mockito.when(none.hasAnyUser()).thenReturn(false);
        assertThatThrownBy(() -> new InitialAdminRunner(none, "", true).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_INIT_ADMIN_PASSWORD");
        // 약한 비밀번호도 거부
        assertThatThrownBy(() -> new InitialAdminRunner(none, "1234", true).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("비밀번호 규칙");
        Mockito.verify(none, Mockito.never()).create(Mockito.any());
    }
}
