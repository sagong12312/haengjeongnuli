package egovframework.haengjeongnuli.user.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.impl.PasswordResetMapper;

/**
 * 비밀번호 분실: 로그인 화면에서 아이디·이름으로 요청하면 관리자가 본인을 확인하고 임시 비밀번호를 발급한다.
 * 요청한 계정이 있는지는 화면에 알리지 않는다(계정 존재 여부 노출 방지).
 */
@Service
public class PasswordResetService {

    /** 같은 접속 주소에서 1시간에 받을 요청 수 */
    static final int MAX_PER_IP_HOUR = 5;

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "!@#$%^&*";

    private final PasswordResetMapper mapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetMapper mapper, UserService userService, NotificationService notificationService) {
        this.mapper = mapper;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public record Issued(String loginId, String userName, String tempPassword) {}

    /**
     * 요청을 받는다. 아이디와 이름이 맞는 사용 중 계정이고 처리 대기 요청이 없을 때만 남긴다.
     * 화면에는 결과와 상관없이 같은 안내를 보여 준다. 남겼으면 true.
     */
    @Transactional
    public boolean request(String loginId, String userName, String contact, String ip) {
        String id = loginId == null ? "" : loginId.trim();
        String name = userName == null ? "" : userName.trim();
        if (id.isEmpty() || name.isEmpty() || id.length() > 50 || name.length() > 100
                || (contact != null && contact.trim().length() > 100)) {
            return false;
        }
        if (ip != null && mapper.countFromIp(ip, LocalDateTime.now().minusHours(1)) >= MAX_PER_IP_HOUR) {
            return false;
        }
        LoginUser u = userService.findByLoginId(id);
        if (u == null || !u.isEnabled() || !name.equals(u.getUserName()) || mapper.countOpen(id) > 0) {
            return false;
        }
        PasswordResetVO v = new PasswordResetVO();
        v.setReqId(mapper.nextId());
        v.setLoginId(id);
        v.setUserName(name);
        v.setContact(contact == null || contact.isBlank() ? null : contact.trim());
        v.setRequestIp(ip);
        v.setRequestedAt(LocalDateTime.now());
        mapper.insert(v);
        for (LoginUser admin : userService.list()) {
            if (admin.isEnabled() && admin.isAdmin()) {
                notificationService.notify(admin.getLoginId(),
                        name + "(" + id + ") 직원이 비밀번호 분실로 임시 비밀번호를 요청했습니다.", "/admin/users#pw-requests");
            }
        }
        return true;
    }

    public List<PasswordResetVO> recent(int limit) {
        return mapper.selectRecent(limit);
    }

    /** 임시 비밀번호를 만들어 계정에 넣는다. 다음 로그인 때 본인이 바꿔야 한다. */
    @Transactional
    public Issued issue(long reqId, LoginUser admin) {
        PasswordResetVO r = mapper.selectById(reqId);
        if (r == null || !PasswordResetVO.OPEN.equals(r.getStatus())) {
            return null;
        }
        LoginUser u = userService.findByLoginId(r.getLoginId());
        if (u == null) {
            return null;
        }
        String temp = tempPassword(u.getLoginId());
        userService.resetPassword(u.getUserId(), temp);
        mapper.closeAllFor(u.getLoginId(), admin.getLoginId(), LocalDateTime.now());
        return new Issued(u.getLoginId(), u.getUserName(), temp);
    }

    @Transactional
    public boolean reject(long reqId, LoginUser admin) {
        return mapper.close(reqId, PasswordResetVO.REJECTED, admin.getLoginId(), LocalDateTime.now()) == 1;
    }

    /** 영문 대·소문자, 숫자, 특수문자를 섞은 12자 (헷갈리는 0·O·1·l·I 제외) */
    String tempPassword(String loginId) {
        while (true) {
            List<Character> cs = new ArrayList<>();
            for (String set : new String[] {UPPER, LOWER, DIGIT, SPECIAL}) {
                cs.add(set.charAt(random.nextInt(set.length())));
            }
            String all = UPPER + LOWER + DIGIT + SPECIAL;
            while (cs.size() < 12) {
                cs.add(all.charAt(random.nextInt(all.length())));
            }
            Collections.shuffle(cs, random);
            StringBuilder sb = new StringBuilder();
            cs.forEach(sb::append);
            if (PasswordPolicy.check(sb.toString(), loginId) == null) {
                return sb.toString();
            }
        }
    }
}
