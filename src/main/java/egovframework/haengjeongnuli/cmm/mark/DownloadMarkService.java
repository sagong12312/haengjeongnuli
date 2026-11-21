package egovframework.haengjeongnuli.cmm.mark;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 내려받기마다 추적 번호를 새로 만들고, 누가·언제·어디서·무엇을·왜 받았는지 적는다 */
@Service
public class DownloadMarkService {

    private final DownloadMarkMapper mapper;

    public DownloadMarkService(DownloadMarkMapper mapper) {
        this.mapper = mapper;
    }

    /** 지금 로그인한 직원의 내려받기로 기록하고 추적 번호를 돌려준다. 로그인 정보가 없으면 null */
    public String issue(String kind, String fileName) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof LoginUser u)) {
            return null;
        }
        String ip = null;
        String path = null;
        String reason = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            ip = sra.getRequest().getRemoteAddr();
            path = sra.getRequest().getRequestURI();
            reason = sra.getRequest().getParameter("reason");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = DownloadMark.newCode();
            try {
                mapper.insert(mapper.nextId(), code, u.getLoginId(), u.getUserName(), u.dept().isEmpty() ? null : u.dept(), cut(ip, 64), kind,
                        cut(path, 300), cut(fileName, 300), cut(reason == null ? null : reason.strip(), 200), LocalDateTime.now());
                return code;
            } catch (DuplicateKeyException e) {
                // 아주 드물게 번호가 겹치면 다시 만든다
            }
        }
        return null;
    }

    public List<Map<String, Object>> lookup(java.util.Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return mapper.byCodes(new ArrayList<>(codes).subList(0, Math.min(50, codes.size())));
    }

    public List<Map<String, Object>> recent(String q) {
        String like = null;
        if (q != null && !q.isBlank()) {
            String t = q.strip();
            t = t.length() > 50 ? t.substring(0, 50) : t;
            like = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        }
        return mapper.recent(like);
    }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
