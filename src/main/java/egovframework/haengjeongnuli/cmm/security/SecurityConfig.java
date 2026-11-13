package egovframework.haengjeongnuli.cmm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/** 로그인·권한 설정 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 기본 BCrypt. 저장 값 앞에 {bcrypt} 가 붙어 나중에 방식을 바꿔도 이전 값을 읽을 수 있다.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AntPathRequestMatcher h2 = new AntPathRequestMatcher("/h2-console/**");
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(h2).hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .defaultSuccessUrl("/", false)
                .failureHandler(failureHandler())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            // H2 콘솔(로컬 전용)은 자체 화면이 frame을 쓰고 CSRF 토큰을 모른다
            .csrf(csrf -> csrf.ignoringRequestMatchers(h2))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    /** 실패 이유에 따라 로그인 화면 안내를 다르게 한다 */
    private AuthenticationFailureHandler failureHandler() {
        return (request, response, ex) -> {
            String reason = ex instanceof LockedException ? "locked"
                    : ex instanceof DisabledException ? "disabled"
                    : "bad";
            response.sendRedirect(request.getContextPath() + "/login?error=" + reason);
        };
    }
}
