package egovframework.haengjeongnuli.cmm;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import egovframework.haengjeongnuli.cmm.notification.NotificationService;

/** 모든 화면에서 쓰는 공통 모델 값 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    public GlobalModelAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 로그인한 직원. 로그인 화면에서는 null */
    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser user) {
        return user;
    }

    /** 머리글 알림 버튼에 표시할 읽지 않은 알림 수 */
    @ModelAttribute("unreadNotifications")
    public int unreadNotifications(@AuthenticationPrincipal LoginUser user) {
        return user == null ? 0 : notificationService.unreadCount(user.getLoginId());
    }
}
