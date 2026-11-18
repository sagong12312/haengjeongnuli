package egovframework.haengjeongnuli.cmm.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 암호화되지 않은(http) 연결은 이 PC 안에서 온 것만 받는다.
 * 같은 와이파이·내부망의 다른 기기가 http 로 오면 비밀번호·개인정보가 그대로 보여 가로챌 수 있으므로,
 * 화면 요청(GET)은 https 주소로 돌려보내고 그 밖의 요청(로그인 등 POST)은 받지 않는다.
 * 앞단 프록시가 TLS 를 끝내면 APP_FORWARD_HEADERS=native 로 두어 request.isSecure() 가 프록시의 X-Forwarded-Proto 를 따르게 한다.
 */
public class HttpsGuardFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public HttpsGuardFilter(boolean enabled) {
        this.enabled = enabled;
    }

    static boolean loopback(String addr) {
        return "127.0.0.1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr) || "::1".equals(addr);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || request.isSecure() || loopback(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        String host = request.getServerName();
        if ("GET".equals(request.getMethod()) && host != null && host.matches("[A-Za-z0-9.\\-:\\[\\]]{1,255}")) {
            String q = request.getQueryString();
            response.setStatus(308);
            response.setHeader("Location", "https://" + (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host)
                    + request.getRequestURI() + (q == null ? "" : "?" + q));
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"><title>암호화된 연결 필요</title></head>"
                + "<body style=\"font-family:sans-serif;padding:40px\"><h1>암호화된 연결이 필요합니다</h1>"
                + "<p>비밀번호와 개인정보를 보호하려고 https:// 로 시작하는 주소에서만 받습니다.</p></body></html>");
    }
}
