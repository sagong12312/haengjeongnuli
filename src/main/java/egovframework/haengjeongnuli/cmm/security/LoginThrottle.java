package egovframework.haengjeongnuli.cmm.security;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 접속 주소(IP)별 로그인 실패 제한: 정해진 시간 안에 너무 많이 틀리면 그 주소의 로그인 시도를 잠시 막는다.
 * 남의 아이디로 일부러 틀려 계정을 잠그는 일과 비밀번호 대입을 줄인다. (서버 메모리에 두므로 다시 시작하면 지워진다)
 */
@Component
public class LoginThrottle {

    private final int maxFailures;
    private final long windowMillis;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(@Value("${app.login.ip-max-failures:20}") int maxFailures,
                         @Value("${app.login.ip-window-minutes:10}") int windowMinutes,
                         @Value("${app.login.lock-minutes:15}") int lockMinutes) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowMinutes * 60_000L;
        LoginUser.setLockMinutes(lockMinutes);
    }

    public int getWindowMinutes() {
        return (int) (windowMillis / 60_000L);
    }

    public void recordFailure(String ip) {
        if (ip == null) {
            return;
        }
        if (failures.size() > 10_000) {
            failures.clear();
        }
        Deque<Long> d = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(System.currentTimeMillis());
            trim(d);
        }
    }

    public void reset(String ip) {
        if (ip != null) {
            failures.remove(ip);
        }
    }

    public boolean isBlocked(String ip) {
        Deque<Long> d = ip == null ? null : failures.get(ip);
        if (d == null) {
            return false;
        }
        synchronized (d) {
            trim(d);
            return d.size() >= maxFailures;
        }
    }

    private void trim(Deque<Long> d) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        while (!d.isEmpty() && d.peekFirst() < cutoff) {
            d.pollFirst();
        }
    }

    /** 막힌 주소에서 온 로그인 요청은 확인하지 않고 돌려보낸다 */
    public OncePerRequestFilter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !("POST".equals(request.getMethod())
                        && (request.getContextPath() + "/login").equals(request.getRequestURI()));
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                if (isBlocked(request.getRemoteAddr())) {
                    response.sendRedirect(request.getContextPath() + "/login?error=ip");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }
}
