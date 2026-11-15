package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/** 실제 서버로 띄워 오류 화면에 내부 정보(스택 트레이스·클래스·경로)가 나오지 않는지 확인한다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageTest {

    @LocalServerPort int port;
    @Autowired UserService userService;

    private HttpClient client;

    @BeforeEach
    void login() throws Exception {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();
        String id = "e" + UUID.randomUUID().toString().substring(0, 8);
        UserForm f = new UserForm();
        f.setLoginId(id);
        f.setUserName("오류점검");
        f.setRole(LoginUser.Role.GENERAL);
        f.setPassword("First-pass-12!");
        LoginUser u = userService.create(f);
        userService.changeOwnPassword(u.getUserId(), "First-pass-12!", "Second-pass-34!");
        String page = get("/login").body();
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page);
        assertThat(m.find()).isTrue();
        String form = "loginId=" + id + "&password=" + URLEncoder.encode("Second-pass-34!", StandardCharsets.UTF_8)
                + "&_csrf=" + m.group(1);
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void noInternals(String body) {
        assertThat(body).doesNotContain("Exception").doesNotContain("java.").doesNotContain("org.springframework")
                .doesNotContain("egovframework.").doesNotContain("at ").doesNotContain("Whitelabel")
                .doesNotContain("trace");
    }

    @Test
    void 없는_주소와_잘못된_값은_안내_화면만_보인다() throws Exception {
        HttpResponse<String> notFound = get("/no-such-page");
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(notFound.body()).contains("찾을 수 없습니다");
        noInternals(notFound.body());

        HttpResponse<String> missing = get("/civil/987654321");
        assertThat(missing.statusCode()).isEqualTo(404);
        noInternals(missing.body());

        HttpResponse<String> bad = get("/approval/abc");
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(bad.body()).contains("요청을 처리할 수 없습니다");
        noInternals(bad.body());

        HttpResponse<String> badParam = get("/board?page=abc");
        assertThat(badParam.statusCode()).isEqualTo(400);
        noInternals(badParam.body());
    }

    @Test
    void JSON_오류_응답에도_메시지와_예외가_없다() throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/civil/api/deadline?kind=XXX"))
                .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).doesNotContain("exception").doesNotContain("trace").doesNotContain("message");
    }
}
