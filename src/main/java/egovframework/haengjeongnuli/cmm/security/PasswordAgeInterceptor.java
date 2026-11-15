package egovframework.haengjeongnuli.cmm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 비밀번호를 바꿔야 하는 직원(처음 받은 비밀번호, 또는 maxDays 일 지남)은
 * 비밀번호 변경 화면 말고는 쓰지 못하게 그 화면으로 보낸다.
 */
@Component
public class PasswordAgeInterceptor implements HandlerInterceptor {

    private final int maxDays;

    public PasswordAgeInterceptor(@Value("${app.password.max-age-days:90}") int maxDays) {
        this.maxDays = maxDays;
    }

    public int getMaxDays() {
        return maxDays;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof LoginUser u && u.isPasswordExpired(maxDays)) {
            response.sendRedirect(request.getContextPath() + "/mypage/password?expired");
            return false;
        }
        return true;
    }
}
