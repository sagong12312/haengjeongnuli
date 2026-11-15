package egovframework.haengjeongnuli.cmm.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.UserService;

/** 로그인 성공·실패·로그아웃을 기록하고, 비밀번호를 틀린 횟수를 센다 */
@Component
public class LoginEventListener {

    private final UserService userService;
    private final AuditService auditService;

    public LoginEventListener(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        Object name = event.getAuthentication().getPrincipal();
        if (name instanceof String loginId) {
            userService.recordFailure(loginId);
        }
    }

    /** 모든 로그인 실패 (비밀번호 틀림, 잠김, 사용 중지) */
    @EventListener
    public void onAnyFailure(AbstractAuthenticationFailureEvent event) {
        Object name = event.getAuthentication().getPrincipal();
        auditService.recordAs(name == null ? null : name.toString(), AuditAction.LOGIN_FAIL, null,
                event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof LoginUser user) {
            userService.recordSuccess(user.getLoginId());
            auditService.recordAs(user.getLoginId(), AuditAction.LOGIN_SUCCESS, null, null);
        }
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication() != null && event.getAuthentication().getPrincipal() instanceof LoginUser user) {
            auditService.recordAs(user.getLoginId(), AuditAction.LOGOUT, null, null);
        }
    }
}
