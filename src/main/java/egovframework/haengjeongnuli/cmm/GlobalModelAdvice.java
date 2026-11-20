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

    private static final boolean FONT = new org.springframework.core.io.ClassPathResource("static/fonts/MDotum.ttf").exists();

    /** 기관 글꼴 파일을 넣었는지 (없으면 글꼴을 부르지 않아 404 가 나지 않게) */
    public boolean fontAvailable() {
        return FONT;
    }

    /** 로그인한 직원. 로그인 화면에서는 null */
    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser user) {
        return user;
    }

    /** 자동 로그아웃까지의 시간(초): 머리글 남은 시간 표시용 */
    @ModelAttribute("sessionTimeoutSeconds")
    public int sessionTimeoutSeconds(@AuthenticationPrincipal LoginUser user, jakarta.servlet.http.HttpServletRequest request) {
        if (user == null) {
            return 0;
        }
        jakarta.servlet.http.HttpSession s = request.getSession(false);
        return s == null ? 0 : s.getMaxInactiveInterval();
    }

    /** 화면·출력물 워터마크 (누가 언제 본 화면인지 흐리게 깐다) */
    @ModelAttribute("watermark")
    public String watermark(@AuthenticationPrincipal LoginUser user, jakarta.servlet.http.HttpServletRequest request) {
        return user == null ? null : egovframework.haengjeongnuli.security.SecurityService.watermarkSvg(
                egovframework.haengjeongnuli.security.SecurityService.watermarkText(user, request.getRemoteAddr()));
    }

    /** 머리글 알림 버튼에 표시할 읽지 않은 알림 수 */
    @ModelAttribute("unreadNotifications")
    public int unreadNotifications(@AuthenticationPrincipal LoginUser user) {
        return user == null ? 0 : notificationService.unreadCount(user.getLoginId());
    }
}
