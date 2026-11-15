package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Oracle·Tibero 운영 준비 상태 점검 (실제 DB 없이 확인할 수 있는 것) */
class OracleReadinessTest {

    private static final Path ORACLE = Path.of("src/main/resources/db/oracle");

    private static List<String> all(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return Stream.iterate(m.find(), b -> b, b -> m.find()).map(b -> m.group(1)).toList();
    }

    @Test
    void 운영_프로필은_안전한_기본값을_쓴다() throws Exception {
        for (String profile : List.of("oracle", "tibero")) {
            PropertySource<?> p = new YamlPropertySourceLoader()
                    .load(profile, new ClassPathResource("application-" + profile + ".yml")).get(0);
            assertThat(p.getProperty("spring.h2.console.enabled")).as(profile).isEqualTo(false);
            assertThat(p.getProperty("app.init-admin.require-password")).as(profile).isEqualTo(true);
            assertThat(String.valueOf(p.getProperty("app.access.allowed-networks"))).as(profile)
                    .contains("APP_ALLOWED_NETWORKS:127.0.0.1/32,::1/128}");
            assertThat(String.valueOf(p.getProperty("spring.datasource.hikari.connection-init-sql"))).as(profile)
                    .startsWith("ALTER SESSION SET CURRENT_SCHEMA=");
            assertThat(String.valueOf(p.getProperty("spring.datasource.password"))).as(profile).isEqualTo("${DB_PASSWORD}");
        }
    }

    @Test
    void 글자_컬럼은_모두_글자_단위이고_4000바이트를_넘지_않는다() throws Exception {
        String schema = Files.readString(ORACLE.resolve("schema.sql"));
        assertThat(schema).doesNotContainPattern("VARCHAR2\\(\\d+\\)");
        for (String n : all(Pattern.compile("VARCHAR2\\((\\d+) CHAR\\)"), schema)) {
            assertThat(Integer.parseInt(n) * 3).as("한글 3바이트 기준 " + n + "자").isLessThanOrEqualTo(4000);
        }
    }

    @Test
    void 프로그램_계정에_모든_테이블과_시퀀스_권한을_준다() throws Exception {
        String schema = Files.readString(ORACLE.resolve("schema.sql"));
        String grants = Files.readString(ORACLE.resolve("accounts_and_grants.sql"));
        for (String t : all(Pattern.compile("CREATE TABLE (\\w+) \\("), schema)) {
            if (t.equals("AUDIT_LOG") || t.equals("AUDIT_REVIEW")) {
                // 접속기록·점검 결과는 읽기·추가만 (개인정보의 안전성 확보조치 기준 제8조: 위·변조 방지)
                assertThat(grants).as(t).contains("GRANT SELECT, INSERT ON " + t + " TO HJN_APP;")
                        .doesNotContain("UPDATE, DELETE ON " + t + " ").doesNotContain("DELETE ON " + t + " ");
            } else if (t.equals("AUDIT_CHAIN")) {
                assertThat(grants).as(t).contains("GRANT SELECT, UPDATE ON AUDIT_CHAIN TO HJN_APP;");
            } else if (t.equals("DOC_REGISTRY_FIX")) {
                // 수정 이력은 지울 수 없다 (상태만 바꾼다)
                assertThat(grants).as(t).contains("GRANT SELECT, INSERT, UPDATE ON " + t + " TO HJN_APP;")
                        .doesNotContain("DELETE ON " + t + " ");
            } else if (t.startsWith("DOC_REGISTRY")) {
                // 기록물 등록정보는 읽기·추가만, 확인된 수정은 정해진 칸만 (시행령 제20조 제2항, 시행규칙 제6조)
                assertThat(grants).as(t).contains("GRANT SELECT, INSERT ON " + t + " TO HJN_APP;")
                        .doesNotContain("UPDATE, DELETE ON " + t + " ").doesNotContain("DELETE ON " + t + " ");
                if (t.equals("DOC_REGISTRY")) {
                    assertThat(grants).contains("GRANT UPDATE (TITLE, COUNTERPART, COUNTERPART_NO, MEDIUM, PAGES, RETENTION, DISCLOSURE, NOTE, CANCELED_YN) ON DOC_REGISTRY TO HJN_APP;");
                }
            } else {
                assertThat(grants).as(t).contains("GRANT SELECT, INSERT, UPDATE, DELETE ON " + t + " TO HJN_APP;");
            }
        }
        for (String s : all(Pattern.compile("CREATE SEQUENCE (\\w+)"), schema)) {
            assertThat(grants).as(s).contains("GRANT SELECT ON " + s + " TO HJN_APP;");
        }
        // 프로그램 계정에는 접속 권한 말고 시스템 권한을 주지 않는다
        assertThat(grants).doesNotContain("TO HJN_APP WITH").doesNotContain("DBA TO HJN_APP").doesNotContain("RESOURCE TO HJN_APP");
    }

    @Test
    void 마이그레이션은_빠진_번호_없이_이어진다() throws Exception {
        try (Stream<Path> files = Files.list(ORACLE.resolve("migration"))) {
            List<Integer> nums = files.map(p -> p.getFileName().toString()).filter(n -> n.matches("\\d{3}_.*\\.sql"))
                    .map(n -> Integer.parseInt(n.substring(0, 3))).sorted().toList();
            for (int i = 1; i < nums.size(); i++) {
                assertThat(nums.get(i)).isEqualTo(nums.get(i - 1) + 1);
            }
        }
    }
}
