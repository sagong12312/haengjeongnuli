package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 시작 전 설정 검사, 보안 헤더(CSP·HSTS), 인라인 스크립트 없음 */
@SpringBootTest
class StartupAndHeaderTest {

    @Autowired WebApplicationContext ctx;

    @Test
    void 프로필이_없으면_시작하지_않는다() {
        assertThatThrownBy(() -> StartupGuard.check(new MockEnvironment()))
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        StartupGuard.check(local);
    }

    @Test
    void 운영_프로필에서는_H2_콘솔을_켤_수_없다() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("oracle");
        prod.setProperty("spring.h2.console.enabled", "true");
        assertThatThrownBy(() -> StartupGuard.check(prod)).hasMessageContaining("H2 콘솔");
        prod.setProperty("spring.h2.console.enabled", "false");
        prod.setProperty("server.servlet.session.cookie.secure", "true");
        StartupGuard.check(prod);
    }

    @Test
    void 화면에_CSP_헤더가_붙고_HTTPS면_HSTS도_붙는다() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        mvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        mvc.perform(get("/login").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
    }

    @Test
    void 화면_파일에_인라인_스크립트와_이벤트_처리기가_없다() throws Exception {
        Pattern handler = Pattern.compile("\\son[a-z]+=\"", Pattern.CASE_INSENSITIVE);
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
        try (var files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".html"))::iterator) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                assertThat(handler.matcher(s).find()).as(p + " 인라인 이벤트 처리기").isFalse();
                assertThat(inlineScript.matcher(s).find()).as(p + " 인라인 script").isFalse();
            }
        }
    }
}
