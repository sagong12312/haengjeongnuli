package egovframework.haengjeongnuli.cmm.secapp;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 프로그램 설치 안내: 관리자가 업무용 PC에 설치할 프로그램(백신, 보안패치 관리, PC 점검, 개인정보 검출 등)과
 * 내려받는 곳을 적고, 직원은 설치한 뒤 '설치했습니다'를 누른다.
 * 웹 화면은 PC에 무엇이 깔렸는지 직접 알 수 없으므로 직원의 확인을 기록하는 방식이다. 실제 설치 여부는 기관의 PC 관리 시스템으로 점검한다.
 */
@Service
public class SecurityAppService {

    private final SecurityAppMapper mapper;

    public SecurityAppService(SecurityAppMapper mapper) {
        this.mapper = mapper;
    }

    public List<SecurityAppVO> forUser(String loginId) { return mapper.selectForUser(loginId); }
    public int pending(String loginId) { return loginId == null ? 0 : mapper.countPending(loginId); }
    public List<SecurityAppVO> all() { return mapper.selectAll(); }
    public List<SecurityAppVO> forCitizen() { return mapper.selectForCitizen(); }

    /** 화면 보안 정책(connect-src)에 열어 줄 설치 확인 주소의 출처 (이 PC 주소만). 1분 동안 기억 */
    private volatile List<String> originsCache = List.of();
    private volatile long originsAt;

    public List<String> checkOrigins() {
        long now = System.currentTimeMillis();
        if (now - originsAt > 60_000) {
            java.util.Set<String> out = new java.util.TreeSet<>();
            for (String u : mapper.selectCheckUrls()) {
                if (safeCheckUrl(u)) {
                    URI uri = URI.create(u);
                    out.add(uri.getScheme().toLowerCase() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
                }
            }
            originsCache = List.copyOf(out);
            originsAt = now;
        }
        return originsCache;
    }

    void forgetOrigins() {
        originsAt = 0;
    }

    /** 설치 확인 주소: 보안 프로그램이 이 PC 에 띄우는 주소만 (http(s)://127.0.0.1 또는 localhost) */
    static boolean safeCheckUrl(String u) {
        if (u == null || u.length() > 300 || !safeUrl(u)) {
            return false;
        }
        String host = URI.create(u).getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }
    public int activeUsers() { return mapper.countActiveUsers(); }
    public SecurityAppVO get(long id) { return mapper.selectById(id); }
    public List<Map<String, Object>> notAcked(long id) { return mapper.selectNotAcked(id); }

    /** 저장. 문제가 있으면 그 이유 */
    @Transactional
    public String save(Long id, String name, String purpose, String guide, String url, boolean required, Integer sortNo, String by) {
        return save(id, name, purpose, guide, url, required, sortNo, "STAFF", null, by);
    }

    @Transactional
    public String save(Long id, String name, String purpose, String guide, String url, boolean required, Integer sortNo,
                       String target, String checkUrl, String by) {
        String n = trim(name), p = trim(purpose), g = trim(guide), u = trim(url), c = trim(checkUrl);
        String t = target == null ? "STAFF" : target.trim();
        if (!t.equals("STAFF") && !t.equals("CITIZEN") && !t.equals("BOTH")) {
            return "대상을 고르세요.";
        }
        if (c != null && !safeCheckUrl(c)) {
            return "설치 확인 주소는 이 PC 주소(http://127.0.0.1:포트/… 또는 https://localhost:포트/…)로만 적을 수 있습니다.";
        }
        if (n == null || n.length() > 100) {
            return "프로그램 이름을 100자 이내로 입력하세요.";
        }
        if (p == null || p.length() > 300) {
            return "용도를 300자 이내로 입력하세요.";
        }
        if (g != null && g.length() > 1300) {
            return "설치 방법은 1,300자 이내로 입력하세요.";
        }
        // 행정누리가 직접 내려주는 파일(예: /security-apps/site-block.ps1)도 받는다
        if (u != null && !safeUrl(u) && !u.matches("/security-apps/[a-z0-9.-]{1,60}")) {
            return "내려받는 곳은 http:// 또는 https:// 로 시작하는 500자 이내 주소로 입력하세요.";
        }
        if (sortNo != null && (sortNo < 0 || sortNo > 9999)) {
            return "순서는 0~9999로 입력하세요.";
        }
        SecurityAppVO a = id == null ? new SecurityAppVO() : get(id);
        if (a == null) {
            return "없는 항목입니다.";
        }
        a.setName(n);
        a.setPurpose(p);
        a.setGuide(g);
        a.setUrl(u);
        a.setRequiredYn(required ? "Y" : "N");
        a.setTarget(t);
        a.setCheckUrl(c);
        forgetOrigins();
        a.setSortNo(sortNo == null ? 100 : sortNo);
        a.setUpdatedBy(by);
        a.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            a.setAppId(mapper.nextId());
            mapper.insert(a);
        } else {
            mapper.update(a);
        }
        return null;
    }

    /** 새 판이 나왔거나 다시 설치해야 할 때: 모든 직원이 다시 확인해야 한다 */
    @Transactional
    public boolean bump(long id, String by) {
        return mapper.bump(id, by, LocalDateTime.now()) == 1;
    }

    @Transactional
    public boolean setUse(long id, boolean use, String by) {
        forgetOrigins();
        return mapper.setUse(id, use ? "Y" : "N", by, LocalDateTime.now()) == 1;
    }

    /** 직원의 설치 확인 (사용 중인 항목의 지금 판) */
    @Transactional
    public boolean ack(long id, String loginId) {
        SecurityAppVO a = get(id);
        if (a == null || !a.isInUse() || !a.isForStaff()) {
            return false;
        }
        mapper.deleteAck(id, loginId);
        mapper.insertAck(id, loginId, a.getVersion(), LocalDateTime.now());
        return true;
    }

    /** 링크로 걸어도 되는 주소: http(s) 만, 사용자 정보(아이디:비밀번호@) 없이 */
    static boolean safeUrl(String u) {
        if (u.length() > 500 || u.chars().anyMatch(c -> c < 0x21 || c == '"' || c == '<' || c == '>' || c == '\\')) {
            return false;
        }
        try {
            URI uri = new URI(u);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
