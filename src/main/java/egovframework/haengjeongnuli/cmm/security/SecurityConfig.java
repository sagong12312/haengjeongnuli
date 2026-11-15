package egovframework.haengjeongnuli.cmm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/** 로그인·권한 설정 */
@Configuration
public class SecurityConfig {

    /** 스크립트·폰트·이미지는 이 서버에서만, 다른 사이트의 frame·form 전송 금지 (style 속성은 화면에서 써서 허용) */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";

    /** 비정상 URL(이중 슬래시, 인코딩된 경로 조작 등)을 막을 때 점수를 매긴다 */
    @Bean
    public org.springframework.security.web.firewall.RequestRejectedHandler requestRejectedHandler(AbuseGuard guard) {
        return (request, response, ex) -> {
            guard.flag(request.getRemoteAddr(), 5, "비정상 URL");
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 기본 BCrypt. 저장 값 앞에 {bcrypt} 가 붙어 나중에 방식을 바꿔도 이전 값을 읽을 수 있다.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.access.allowed-networks}") String allowedNetworks,
            egovframework.haengjeongnuli.user.service.UserService userService,
            egovframework.haengjeongnuli.cmm.audit.AuditService auditService,
            egovframework.haengjeongnuli.cmm.org.OrgService orgService,
            LoginThrottle loginThrottle, AbuseGuard abuseGuard) throws Exception {
        AntPathRequestMatcher h2 = new AntPathRequestMatcher("/h2-console/**");
        http
            // 허용한 네트워크(기본: 이 PC + 집 내부망)가 아니면 로그인 화면조차 보여주지 않는다
            .addFilterBefore(new AllowedNetworkFilter(allowedNetworks), DisableEncodeUrlFilter.class)
            // 이상한 요청을 보낸 주소는 잠시 막고 차단 안내(이의 신청) 화면으로 보낸다
            .addFilterBefore(new AbuseFilter(abuseGuard), org.springframework.security.web.csrf.CsrfFilter.class)
            // 같은 주소에서 로그인을 너무 많이 틀리면 잠시 막는다
            .addFilterBefore(loginThrottle.filter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            // 로그인 중인 세션이 계정의 지금 상태(사용 중지·권한·비밀번호)와 맞는지 요청마다 확인
            .addFilterBefore(new SessionRevalidationFilter(userService, auditService, orgService),
                    org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/password-help", "/blocked", "/blocked/**", "/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(h2).hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .defaultSuccessUrl("/", false)
                .failureHandler(failureHandler(loginThrottle))
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            // 요청 위조(CSRF) 실패·권한 없는 접근은 점수를 매긴 뒤 403
            .exceptionHandling(e -> e.accessDeniedHandler((request, response, ex) -> {
                boolean csrf = ex instanceof org.springframework.security.web.csrf.CsrfException;
                abuseGuard.flag(request.getRemoteAddr(), csrf ? 3 : 2,
                        (csrf ? "요청 위조 확인 실패 " : "권한 없는 접근 ") + request.getRequestURI());
                new org.springframework.security.web.access.AccessDeniedHandlerImpl().handle(request, response, ex);
            }))
            // H2 콘솔(로컬 전용)은 자체 화면이 frame을 쓰고 CSRF 토큰을 모른다
            .csrf(csrf -> csrf.ignoringRequestMatchers(h2))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())
                    // 2차 방어: 이 서버의 스크립트만 실행 (H2 콘솔은 자체 화면이 인라인 스크립트를 써서 뺀다)
                    .addHeaderWriter(new org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter(
                            new org.springframework.security.web.util.matcher.NegatedRequestMatcher(h2),
                            new org.springframework.security.web.header.writers.StaticHeadersWriter("Content-Security-Policy", CSP)))
                    .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /** 실패 이유에 따라 로그인 화면 안내를 다르게 한다 */
    private AuthenticationFailureHandler failureHandler(LoginThrottle throttle) {
        return (request, response, ex) -> {
            throttle.recordFailure(request.getRemoteAddr());
            String reason = ex instanceof LockedException ? "locked"
                    : ex instanceof DisabledException ? "disabled"
                    : "bad";
            response.sendRedirect(request.getContextPath() + "/login?error=" + reason);
        };
    }
}
