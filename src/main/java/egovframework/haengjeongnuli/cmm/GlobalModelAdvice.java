package egovframework.haengjeongnuli.cmm;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 화면에서 쓰는 공통 모델 값 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final LoginUser loginUser;

    public GlobalModelAdvice(LoginUser loginUser) {
        this.loginUser = loginUser;
    }

    @ModelAttribute("loginUser")
    public LoginUser loginUser() {
        return loginUser;
    }
}
