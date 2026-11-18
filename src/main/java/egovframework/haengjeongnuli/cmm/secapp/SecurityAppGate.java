package egovframework.haengjeongnuli.cmm.secapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 강제 보안 프로그램을 설치(확인)하지 않은 직원은 행정누리 업무 화면을 쓰지 못하고 설치 안내 화면으로 간다.
 * 설치 안내·로그아웃·비밀번호 변경·2단계 인증 화면만 쓸 수 있다. 관리자는 목록을 고칠 수 있게 관리 화면도 쓸 수 있다.
 * 다 설치했으면 1분 동안은 다시 묻지 않는다 (요청마다 DB 를 보지 않게).
 */
@Component
public class SecurityAppGate implements HandlerInterceptor {

    static final String OK_UNTIL = "secapp.okUntil";

    private final SecurityAppService service;
    private final boolean enforce;

    public SecurityAppGate(SecurityAppService service, @Value("${app.security-apps.enforce:true}") boolean enforce) {
        this.service = service;
        this.enforce = enforce;
    }

    static boolean open(String p, LoginUser u) {
        return p.startsWith("/security-apps") || p.equals("/logout") || p.startsWith("/login") || p.equals("/mypage/password")
                || p.equals("/session/ping") || p.startsWith("/error") || p.startsWith("/css/") || p.startsWith("/js/")
                || p.startsWith("/fonts/") || p.equals("/favicon.ico") || p.equals("/reauth")
                || (u.isAdmin() && (p.startsWith("/admin/security-apps") || p.startsWith("/admin/users")));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enforce) {
            return true;
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof LoginUser u)) {
            return true;
        }
        String p = request.getRequestURI().substring(request.getContextPath().length());
        if (open(p, u)) {
            return true;
        }
        HttpSession s = request.getSession(false);
        if (s != null && s.getAttribute(OK_UNTIL) instanceof Long until && until > System.currentTimeMillis()) {
            return true;
        }
        if (service.pending(u.getLoginId()) == 0) {
            if (s != null) {
                s.setAttribute(OK_UNTIL, System.currentTimeMillis() + 60_000);
            }
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/security-apps?required=1");
        return false;
    }
}
