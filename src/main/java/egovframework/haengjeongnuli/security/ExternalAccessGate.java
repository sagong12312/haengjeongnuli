package egovframework.haengjeongnuli.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 집·사무실 내부망 밖(인터넷)에서 들어온 직원은 2단계 인증(OTP)을 켠 사람만 쓸 수 있다.
 * 비밀번호가 새어도 밖에서는 인증 앱 번호 없이 들어오지 못하게 하려는 것이다.
 * OTP 는 내부망에서 먼저 켜야 한다 (밖에서 처음 등록하면, 비밀번호를 훔친 사람이 자기 휴대폰을 등록할 수 있으므로).
 * 내부망 목록(app.security.internal-networks)을 비워 두면 이 검사는 하지 않는다.
 */
@Component
public class ExternalAccessGate implements HandlerInterceptor {

    private final List<IpAddressMatcher> internal;
    private final SecurityService security;

    public ExternalAccessGate(@Value("${app.security.internal-networks:}") String internalCsv, SecurityService security) {
        this.internal = Arrays.stream(internalCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
        this.security = security;
    }

    public boolean enabled() {
        return !internal.isEmpty();
    }

    public boolean isInternal(String addr) {
        if (internal.isEmpty() || addr == null) {
            return true;
        }
        String clean = addr.contains("%") ? addr.substring(0, addr.indexOf('%')) : addr;
        try {
            return internal.stream().anyMatch(m -> m.matches(clean));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (internal.isEmpty() || isInternal(request.getRemoteAddr())) {
            return true;
        }
        LoginUser u = SecurityWeb.me();
        if (u == null) {
            return true;
        }
        String p = request.getRequestURI().substring(request.getContextPath().length());
        if (p.equals("/logout") || p.startsWith("/login") || SecurityWeb.staticPath(p)) {
            return true;
        }
        if (security.otpEnabled(u.getLoginId())) {
            return true;
        }
        SecurityWeb.logout(request);
        response.sendRedirect(request.getContextPath() + "/login?error=external_otp");
        return false;
    }
}
