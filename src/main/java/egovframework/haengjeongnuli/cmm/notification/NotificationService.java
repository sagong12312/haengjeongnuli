package egovframework.haengjeongnuli.cmm.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 직원에게 알림을 보내고 읽음 처리한다 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 알림을 남긴다. link 는 이 시스템 안의 주소(/로 시작)만 받는다.
     * 알림 저장에 실패해도 본 작업은 막지 않는다.
     */
    public void notify(String toLoginId, String message, String link) {
        if (toLoginId == null || toLoginId.isBlank()) {
            return;
        }
        NotificationVO n = new NotificationVO();
        n.setLoginId(toLoginId);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        n.setLink(isInternal(link) ? link : null);
        n.setCreatedAt(LocalDateTime.now());
        try {
            mapper.insertNotification(n);
        } catch (RuntimeException e) {
            log.error("알림 저장 실패: {} {}", toLoginId, message, e);
        }
    }

    public int unreadCount(String loginId) {
        return loginId == null ? 0 : mapper.countUnread(loginId);
    }

    public List<NotificationVO> recent(String loginId, int limit) {
        return mapper.selectRecent(loginId, Math.max(1, Math.min(limit, 200)));
    }

    /** 내 알림이면 읽음 처리하고 이동할 주소를 돌려준다. 남의 알림이거나 없으면 null */
    public String open(long notiId, String loginId) {
        NotificationVO n = mapper.selectOne(notiId);
        if (n == null || !n.getLoginId().equals(loginId)) {
            return null;
        }
        mapper.markRead(notiId, loginId);
        return isInternal(n.getLink()) ? n.getLink() : "/notifications";
    }

    public void markAllRead(String loginId) {
        mapper.markAllRead(loginId);
    }

    /** 다른 사이트로 보내는 주소를 막는다 */
    static boolean isInternal(String link) {
        return link != null && link.startsWith("/") && !link.startsWith("//") && !link.contains("\\");
    }
}
