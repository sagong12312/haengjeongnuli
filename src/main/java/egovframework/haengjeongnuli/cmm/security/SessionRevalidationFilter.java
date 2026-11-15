package egovframework.haengjeongnuli.cmm.security;

import java.io.IOException;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 중인 세션이 계정의 지금 상태와 맞는지 요청마다 확인한다.
 * <ul>
 *   <li>계정이 없어졌거나 사용 중지됐거나 비밀번호가 바뀌었으면(관리자 재설정 포함) 세션을 끊는다.</li>
 *   <li>권한·담당·부서가 바뀌었으면(보안 번호가 다름) 새 정보로 바로 바꾼다.</li>
 * </ul>
 * DB에서 확인하므로 서버를 여러 대 두어도 같이 동작한다.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    private static final SecurityContextRepository REPO = new HttpSessionSecurityContextRepository();

    private final UserService userService;
    private final AuditService auditService;
    private final egovframework.haengjeongnuli.cmm.org.OrgService org;

    public SessionRevalidationFilter(UserService userService, AuditService auditService,
                                     egovframework.haengjeongnuli.cmm.org.OrgService org) {
        this.userService = userService;
        this.auditService = auditService;
        this.org = org;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI().substring(request.getContextPath().length());
        return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/fonts/") || p.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof LoginUser me) {
            LoginUser db = userService.findByLoginId(me.getLoginId());
            boolean changed = db != null && db.getSecurityVersion() != me.getSecurityVersion();
            if (db == null || !db.isEnabled() || (changed && !Objects.equals(db.getPasswordHash(), me.getPasswordHash()))) {
                revoke(request, response, me, db == null ? "계정 없음" : !db.isEnabled() ? "사용 중지" : "비밀번호 변경");
                return;
            }
            if (changed) {
                replace(request, response, a, db);
            }
            // 자동 로그아웃 시간 (관리자가 바꾸면 다음 요청부터 적용)
            HttpSession s = request.getSession(false);
            if (s != null) {
                s.setMaxInactiveInterval(org.sessionTimeoutMinutes() * 60);
            }
        }
        chain.doFilter(request, response);
    }

    /** 세션의 로그인 정보를 DB의 최신 정보로 바꾼다 (본인 비밀번호 변경 뒤에도 쓴다) */
    public static void replace(HttpServletRequest request, HttpServletResponse response, Authentication old, LoginUser fresh) {
        UsernamePasswordAuthenticationToken t = UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities());
        t.setDetails(old == null ? null : old.getDetails());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(t);
        SecurityContextHolder.setContext(ctx);
        REPO.saveContext(ctx, request, response);
    }

    private void revoke(HttpServletRequest request, HttpServletResponse response, LoginUser me, String why) throws IOException {
        auditService.recordAs(me.getLoginId(), AuditAction.SESSION_REVOKED, null, why);
        SecurityContextHolder.clearContext();
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        if ("GET".equals(request.getMethod())) {
            response.sendRedirect(request.getContextPath() + "/login?error=revoked");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
