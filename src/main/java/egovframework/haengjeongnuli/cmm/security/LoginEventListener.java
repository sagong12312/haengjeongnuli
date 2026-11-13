package egovframework.haengjeongnuli.cmm.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserService;

/** 비밀번호를 틀린 횟수를 세고, 로그인에 성공하면 초기화한다 */
@Component
public class LoginEventListener {

    private final UserService userService;

    public LoginEventListener(UserService userService) {
        this.userService = userService;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        Object name = event.getAuthentication().getPrincipal();
        if (name instanceof String loginId) {
            userService.recordFailure(loginId);
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof LoginUser user) {
            userService.recordSuccess(user.getLoginId());
        }
    }
}
