package egovframework.haengjeongnuli.cmm;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import egovframework.haengjeongnuli.cmm.security.PasswordAgeInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PasswordAgeInterceptor passwordAgeInterceptor;

    public WebConfig(PasswordAgeInterceptor passwordAgeInterceptor) {
        this.passwordAgeInterceptor = passwordAgeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordAgeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/mypage/password", "/login", "/logout", "/error", "/error/**",
                        "/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/h2-console/**");
    }
}
