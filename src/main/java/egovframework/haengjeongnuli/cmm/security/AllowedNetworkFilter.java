package egovframework.haengjeongnuli.cmm.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 허용한 네트워크(기본: 이 PC와 집 공유기 내부망)에서 온 접속만 받는다.
 * 접속한 기기의 실제 주소(remoteAddr)만 보고, 요청에 적힌 X-Forwarded-For 같은 값은 믿지 않는다.
 */
public class AllowedNetworkFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AllowedNetworkFilter.class);

    private final List<IpAddressMatcher> allowed;

    public AllowedNetworkFilter(String allowedCsv) {
        this.allowed = Arrays.stream(allowedCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public boolean isAllowed(String address) {
        if (address == null) {
            return false;
        }
        // IPv6 주소에 붙는 영역 표시(%eth0 등)는 떼고 비교
        String clean = address.contains("%") ? address.substring(0, address.indexOf('%')) : address;
        try {
            return allowed.stream().anyMatch(m -> m.matches(clean));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String addr = request.getRemoteAddr();
        if (isAllowed(addr)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("허용하지 않은 곳에서 접속을 막았습니다: {} {}", addr, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"><title>접속 불가</title></head>"
                + "<body style=\"font-family:sans-serif;padding:40px\"><h1>접속할 수 없습니다</h1>"
                + "<p>행정누리는 허용된 장소에서만 접속할 수 있습니다.</p></body></html>");
    }
}
