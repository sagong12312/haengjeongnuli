package egovframework.haengjeongnuli.cmm.notification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.org.OrgService;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 매일 아침 알림: 처리기한이 다가오거나 지난 민원은 담당자(없으면 처리 부서, 접수자)에게,
 * 오래 기다리는 결재 문서는 지금 결재할 차례인 사람에게 알린다. 같은 날 두 번 보내지 않는다.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    static final String LAST_RUN_KEY = "reminderLastRun";

    public record Sent(int civil, int approval) {}

    private final ReminderMapper mapper;
    private final NotificationService notifications;
    private final UserService users;
    private final OrgService org;
    private final int civilHours;
    private final int approvalDays;

    public ReminderService(ReminderMapper mapper, NotificationService notifications, UserService users, OrgService org,
                           @Value("${app.reminder.civil-hours:24}") int civilHours,
                           @Value("${app.reminder.approval-days:3}") int approvalDays) {
        this.mapper = mapper;
        this.notifications = notifications;
        this.users = users;
        this.org = org;
        this.civilHours = civilHours;
        this.approvalDays = approvalDays;
    }

    /** 평일 오전 8시 40분 (app.reminder.cron 으로 바꿀 수 있고, "-" 이면 끈다) */
    @Scheduled(cron = "${app.reminder.cron:0 40 8 * * MON-FRI}")
    public void daily() {
        LocalDate today = LocalDate.now();
        if (today.toString().equals(org.get(LAST_RUN_KEY))) {
            return;
        }
        org.put(LAST_RUN_KEY, today.toString(), "system");
        Sent s = run(LocalDateTime.now());
        log.info("아침 알림: 민원 {}건, 결재 {}건", s.civil(), s.approval());
    }

    public Sent run(LocalDateTime now) {
        int civil = 0;
        List<LoginUser> all = users.list();
        for (Map<String, Object> c : mapper.civilDue(now.plusHours(civilHours))) {
            LocalDateTime due = toTime(c.get("deadlineAt"));
            boolean overdue = due.isBefore(now);
            String msg = "[" + c.get("receiptNo") + "] '" + c.get("title") + "' 민원이 " + (overdue
                    ? "처리기한(" + fmt(due) + ")을 넘겼습니다."
                    : "처리기한 " + fmt(due) + "까지 " + Math.max(1, Duration.between(now, due).toHours()) + "시간 남았습니다.");
            for (String to : civilRecipients(c, all)) {
                notifications.notify(to, msg, "/civil/" + c.get("id"));
                civil++;
            }
        }
        int approval = 0;
        for (Map<String, Object> d : mapper.approvalWaiting(now.minusDays(approvalDays))) {
            LocalDateTime at = toTime(d.get("submittedAt"));
            notifications.notify((String) d.get("approverId"), "'" + d.get("title") + "' 문서가 " + fmt(at)
                    + "에 올라와 " + d.get("role") + "를 기다리고 있습니다.", "/approval/" + d.get("docId"));
            approval++;
        }
        return new Sent(civil, approval);
    }

    private static Set<String> civilRecipients(Map<String, Object> c, List<LoginUser> all) {
        Set<String> to = new LinkedHashSet<>();
        Object assignee = c.get("assigneeId");
        if (assignee != null) {
            to.add((String) assignee);
            return to;
        }
        Object dept = c.get("dept");
        if (dept != null) {
            all.stream().filter(u -> u.isEnabled() && dept.equals(u.getDeptName())).forEach(u -> to.add(u.getLoginId()));
        }
        if (to.isEmpty() && c.get("registerId") != null) {
            to.add((String) c.get("registerId"));
        }
        return to;
    }

    private static LocalDateTime toTime(Object o) {
        if (o instanceof LocalDateTime t) {
            return t;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(o).replace(' ', 'T'));
    }

    private static String fmt(LocalDateTime t) {
        return String.format("%d. %d. %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }
}
