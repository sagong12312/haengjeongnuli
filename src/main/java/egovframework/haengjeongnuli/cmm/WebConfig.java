package egovframework.haengjeongnuli.cmm;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import egovframework.haengjeongnuli.cmm.security.PasswordAgeInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PasswordAgeInterceptor passwordAgeInterceptor;
    private final egovframework.haengjeongnuli.security.SecurityWeb.OtpGate otpGate;
    private final egovframework.haengjeongnuli.security.SecurityWeb.Reauth reauth;
    private final egovframework.haengjeongnuli.security.SecurityWeb.DownloadReason downloadReason;

    public WebConfig(PasswordAgeInterceptor passwordAgeInterceptor, egovframework.haengjeongnuli.security.SecurityWeb.OtpGate otpGate,
                     egovframework.haengjeongnuli.security.SecurityWeb.Reauth reauth,
                     egovframework.haengjeongnuli.security.SecurityWeb.DownloadReason downloadReason) {
        this.passwordAgeInterceptor = passwordAgeInterceptor;
        this.otpGate = otpGate;
        this.reauth = reauth;
        this.downloadReason = downloadReason;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 2단계 인증을 마치기 전에는 아무 화면도 못 쓴다 (비밀번호 만료 안내보다 먼저)
        registry.addInterceptor(otpGate).addPathPatterns("/**");
        // 시스템 관리 화면에서 바꾸는 요청은 최근에 비밀번호를 다시 확인했어야 한다
        registry.addInterceptor(reauth).addPathPatterns("/admin/**");
        // 개인정보가 든 파일은 사유를 적어야 내려받는다
        registry.addInterceptor(downloadReason).addPathPatterns("/civil/certs.csv", "/hr/pay/summary.csv", "/office/weekly/dept.csv");
        registry.addInterceptor(passwordAgeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/mypage/password", "/login", "/login/otp", "/logout", "/error", "/error/**",
                        "/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/h2-console/**");
    }
}
