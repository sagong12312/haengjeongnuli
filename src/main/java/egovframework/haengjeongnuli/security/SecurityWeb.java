package egovframework.haengjeongnuli.security;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 로그인 뒤 2단계 인증, 민감한 관리 작업 전 비밀번호 재확인, 개인정보 내려받기 사유 */
public final class SecurityWeb {

    private SecurityWeb() {
    }

    /** 세션 표시 이름 */
    public static final String OTP_PENDING = "otp.pending";
    public static final String OTP_FAILS = "otp.fails";
    public static final String REAUTH_AT = "reauth.at";
    public static final String REAUTH_FAILS = "reauth.fails";
    /** 비밀번호를 넣고 나서 OTP 번호를 넣기까지 기다리는 시간 */
    static final long OTP_PENDING_SECONDS = 300;

    /** 세션을 끝내고 로그인 화면으로 */
    static void logout(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    static LoginUser me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof LoginUser u ? u : null;
    }

    static boolean staticPath(String p) {
        return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/fonts/") || p.equals("/favicon.ico") || p.startsWith("/error");
    }

    /** 로그인 성공 때: OTP 를 켠 직원은 두 번째 단계를 기다리게 하고, 처음 보는 주소면 알린다 */
    @Component
    public static class LoginHook {
        private final SecurityService service;

        public LoginHook(SecurityService service) {
            this.service = service;
        }

        @EventListener
        public void onSuccess(AuthenticationSuccessEvent event) {
            if (!(event.getAuthentication().getPrincipal() instanceof LoginUser u)) {
                return;
            }
            ServletRequestAttributes attrs = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s ? s : null;
            HttpServletRequest req = attrs == null ? null : attrs.getRequest();
            if (req != null && service.otpEnabled(u.getLoginId())) {
                req.getSession(true).setAttribute(OTP_PENDING, Instant.now());
            }
            if (req != null) {
                service.alertIfNewIp(u, req.getRemoteAddr());
            }
        }
    }

    /** 두 번째 단계(OTP)를 마치기 전에는 다른 화면을 못 쓰게 한다 */
    @Component
    public static class OtpGate implements HandlerInterceptor {
        private final SecurityService service;

        public OtpGate(SecurityService service) {
            this.service = service;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            HttpSession s = request.getSession(false);
            if (s == null || s.getAttribute(OTP_PENDING) == null) {
                // 기관이 관리자에게 2단계 인증을 의무로 했으면, 켜기 전에는 시스템 관리 화면을 못 쓴다
                String p = request.getRequestURI().substring(request.getContextPath().length());
                LoginUser u = me();
                if (u != null && p.startsWith("/admin/") && service.otpRequiredButMissing(u)) {
                    response.sendRedirect(request.getContextPath() + "/mypage/security?required=1#otp");
                    return false;
                }
                return true;
            }
            String p = request.getRequestURI().substring(request.getContextPath().length());
            if (staticPath(p)) {
                return true;
            }
            // 비밀번호만 맞힌 반쯤 로그인한 세션을 오래 두지 않는다
            if (!(s.getAttribute(OTP_PENDING) instanceof Instant at) || at.isBefore(Instant.now().minusSeconds(OTP_PENDING_SECONDS))) {
                logout(request);
                response.sendRedirect(request.getContextPath() + "/login?error=otp_timeout");
                return false;
            }
            if (p.equals("/login/otp") || p.equals("/logout")) {
                return true;
            }
            response.sendRedirect(request.getContextPath() + "/login/otp");
            return false;
        }
    }

    /**
     * 시스템 관리 화면에서 바꾸는 요청(POST)은 최근 N분 안에 비밀번호를 다시 확인했어야 한다.
     * 자리를 비운 사이 다른 사람이 계정·권한을 바꾸는 것을 막는다. N=0 이면 끈다.
     */
    @Component
    public static class Reauth implements HandlerInterceptor {
        private final int minutes;

        public Reauth(@Value("${app.security.reauth-minutes:10}") int minutes) {
            this.minutes = minutes;
        }

        public int minutes() {
            return minutes;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (minutes <= 0 || !"POST".equals(request.getMethod())) {
                return true;
            }
            HttpSession s = request.getSession(false);
            Object at = s == null ? null : s.getAttribute(REAUTH_AT);
            if (at instanceof Instant i && i.isAfter(Instant.now().minusSeconds(minutes * 60L))) {
                return true;
            }
            String back = request.getHeader("Referer");
            String path = request.getContextPath();
            String next = back != null && back.contains("/admin/") ? back.substring(back.indexOf("/admin/")) : "/admin/users";
            response.sendRedirect(path + "/reauth?next=" + java.net.URLEncoder.encode(next, java.nio.charset.StandardCharsets.UTF_8));
            return false;
        }
    }

    /**
     * 개인정보가 들어 있는 파일을 내려받을 때는 사유를 적게 하고 기록한다
     * (개인정보의 안전성 확보조치 기준: 개인정보 다운로드 사유 확인).
     */
    @Component
    public static class DownloadReason implements HandlerInterceptor {
        private final egovframework.haengjeongnuli.cmm.audit.AuditService audit;

        public DownloadReason(egovframework.haengjeongnuli.cmm.audit.AuditService audit) {
            this.audit = audit;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String reason = request.getParameter("reason");
            String r = reason == null ? "" : reason.trim();
            if (r.length() < 2 || r.length() > 200) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/html; charset=utf-8");
                response.getWriter().write("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>내려받기 사유</title></head><body>"
                        + "<p>개인정보가 들어 있는 파일이라 내려받는 사유를 적어야 합니다 (2~200자). 이전 화면으로 돌아가 사유를 적고 다시 누르세요.</p></body></html>");
                return false;
            }
            audit.record(egovframework.haengjeongnuli.cmm.audit.AuditAction.PRIVACY_DOWNLOAD,
                    request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString().replaceAll("reason=[^&]*&?", "")),
                    "사유: " + r);
            return true;
        }
    }
}
