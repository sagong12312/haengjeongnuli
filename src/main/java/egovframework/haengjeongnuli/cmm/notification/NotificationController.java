package egovframework.haengjeongnuli.cmm.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 내 알림 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("notifications", notificationService.recent(me.getLoginId(), 100));
        return "cmm/notifications";
    }

    /** 알림을 열면 읽음 처리하고 해당 화면으로 이동 */
    @GetMapping("/{notiId}")
    public String open(@AuthenticationPrincipal LoginUser me, @PathVariable long notiId) {
        String link = notificationService.open(notiId, me.getLoginId());
        return "redirect:" + (link == null ? "/notifications" : link);
    }

    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal LoginUser me, RedirectAttributes ra) {
        notificationService.markAllRead(me.getLoginId());
        ra.addFlashAttribute("message", "알림을 모두 읽음으로 표시했습니다.");
        return "redirect:/notifications";
    }
}
