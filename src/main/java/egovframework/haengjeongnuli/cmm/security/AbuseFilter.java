package egovframework.haengjeongnuli.cmm.security;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 차단된 주소는 차단 안내(이의 신청) 화면으로 보내고, 요청에 공격 문구·탐색 주소가 있으면 점수를 준다 */
public class AbuseFilter extends OncePerRequestFilter {

    private final AbuseGuard guard;

    public AbuseFilter(AbuseGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (guard.isBlocked(ip)) {
            boolean allowed = path.startsWith("/blocked") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/");
            if (!allowed) {
                response.sendRedirect(request.getContextPath() + "/blocked");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (AbuseGuard.isProbe(path)) {
            guard.flag(ip, 4, "탐색 주소 " + cut(path));
        } else {
            String found = AbuseGuard.attackIn(decode(path + "?" + (request.getQueryString() == null ? "" : request.getQueryString())));
            if (found == null && !isMultipart(request)) {
                for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
                    for (String v : e.getValue()) {
                        found = AbuseGuard.attackIn(v);
                        if (found != null) {
                            break;
                        }
                    }
                    if (found != null) {
                        break;
                    }
                }
            }
            if (found != null) {
                guard.flag(ip, 4, found + " " + cut(path));
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isMultipart(HttpServletRequest r) {
        return r.getContentType() != null && r.getContentType().toLowerCase().startsWith("multipart/");
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static String cut(String s) {
        return s.length() > 80 ? s.substring(0, 80) : s;
    }
}
