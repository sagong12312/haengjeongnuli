package egovframework.haengjeongnuli.cmm;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 화면에서 쓰는 공통 모델 값 */
@ControllerAdvice
public class GlobalModelAdvice {

    /** 로그인한 직원. 로그인 화면에서는 null */
    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser user) {
        return user;
    }
}
