package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 화면의 POST 폼은 모두 th:action 을 써야 한다.
 * Thymeleaf 는 th:action 이 있는 폼에만 CSRF 토큰을 넣으므로, 없으면 실제 브라우저에서 403 이 난다
 * (MockMvc 테스트는 csrf() 를 직접 붙이므로 이 문제를 잡지 못한다).
 */
class FormCsrfTest {

    @Test
    void POST_폼에는_th_action_이_있다() throws IOException {
        Pattern form = Pattern.compile("<form\\b[^>]*>", Pattern.CASE_INSENSITIVE);
        List<String> bad;
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            bad = files.filter(p -> p.toString().endsWith(".html")).flatMap(p -> {
                try {
                    Matcher m = form.matcher(Files.readString(p));
                    Stream.Builder<String> out = Stream.builder();
                    while (m.find()) {
                        String tag = m.group();
                        if (tag.toLowerCase().contains("method=\"post\"") && !tag.contains("th:action")) {
                            out.add(p + ": " + tag);
                        }
                    }
                    return out.build();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
        assertThat(bad).isEmpty();
    }
}
