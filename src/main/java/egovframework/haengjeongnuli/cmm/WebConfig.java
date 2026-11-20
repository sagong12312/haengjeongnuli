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

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.cmm.secapp.SecurityAppGate securityAppGate;

    @org.springframework.beans.factory.annotation.Autowired
    private egovframework.haengjeongnuli.security.ExternalAccessGate externalAccessGate;

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
        // 내부망 밖(인터넷)에서 온 직원은 2단계 인증을 켠 사람만 (맨 먼저)
        registry.addInterceptor(externalAccessGate).addPathPatterns("/**");
        // 2단계 인증을 마치기 전에는 아무 화면도 못 쓴다 (비밀번호 만료 안내보다 먼저)
        registry.addInterceptor(otpGate).addPathPatterns("/**");
        // 시스템 관리 화면에서 바꾸는 요청은 최근에 비밀번호를 다시 확인했어야 한다
        registry.addInterceptor(reauth).addPathPatterns("/admin/**");
        // 개인정보가 든 파일은 사유를 적어야 내려받는다
        registry.addInterceptor(downloadReason).addPathPatterns("/reg/*/export.csv", "/civil/certs.csv", "/hr/pay/summary.csv", "/office/weekly/dept.csv",
                "/hygiene/sanctions.csv", "/vehicles/*/logs.csv");
        registry.addInterceptor(passwordAgeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/mypage/password", "/login", "/login/otp", "/logout", "/error", "/error/**",
                        "/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/h2-console/**");
        // 강제 보안 프로그램을 설치하지 않으면 업무 화면을 못 쓴다 (비밀번호 바꾸기가 먼저)
        registry.addInterceptor(securityAppGate).addPathPatterns("/**");
    }
}
