package egovframework.haengjeongnuli;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 시작하기 전에 위험한 설정을 막는다.
 * <ul>
 *   <li>프로필을 정하지 않으면 시작하지 않는다 (운영 서버에서 빠뜨려 개발용 H2·넓은 접속 허용으로 뜨는 것을 막는다).</li>
 *   <li>local 이 아닌 프로필에서 H2 콘솔을 켜면 시작하지 않는다 (콘솔에서는 SQL로 서버 명령까지 실행할 수 있다).</li>
 *   <li>운영 프로필에서 세션 쿠키 secure 를 끄면 경고한다 (HTTP로 운영하면 세션을 가로챌 수 있다).</li>
 * </ul>
 */
public class StartupGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final List<String> PROFILES = List.of("local", "oracle", "tibero");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        check(event.getEnvironment());
    }

    static void check(ConfigurableEnvironment env) {
        List<String> active = Arrays.asList(env.getActiveProfiles());
        if (active.stream().noneMatch(PROFILES::contains)) {
            throw new IllegalStateException("실행 프로필을 정하세요: SPRING_PROFILES_ACTIVE=oracle (또는 tibero, PC 시험용은 local). "
                    + "프로필 없이 시작하면 개발용 설정으로 뜰 수 있어 시작하지 않습니다.");
        }
        boolean local = active.contains("local");
        if (!local && env.getProperty("spring.h2.console.enabled", Boolean.class, false)) {
            throw new IllegalStateException("운영 프로필에서는 H2 콘솔을 켤 수 없습니다 (H2_CONSOLE_ENABLED 를 지우세요).");
        }
        if (!local && !env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            System.err.println("[경고] 세션 쿠키 secure 가 꺼져 있습니다. 운영은 HTTPS 로 하고 APP_COOKIE_SECURE=true 로 실행하세요.");
        }
    }
}
