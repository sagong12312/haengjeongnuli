package egovframework.haengjeongnuli.cmm.org;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 정보: 화면 아래쪽 기관명·문의처, 시행문의 행정기관명·발신명의·주소·연락처, 관인 이미지.
 * 화면마다 읽으므로 메모리에 두고, 저장할 때 다시 읽는다.
 */
@Service("orgService")
public class OrgService {

    /** 설정 이름 → 화면 이름, 최대 길이 */
    public static final Map<String, Object[]> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("orgName", new Object[] {"행정기관명", 100});
        FIELDS.put("senderTitle", new Object[] {"발신명의 (기관장)", 100});
        FIELDS.put("zipCode", new Object[] {"우편번호", 10});
        FIELDS.put("address", new Object[] {"주소", 200});
        FIELDS.put("homepage", new Object[] {"홈페이지", 200});
        FIELDS.put("phone", new Object[] {"대표 전화", 30});
        FIELDS.put("fax", new Object[] {"팩스", 30});
        FIELDS.put("contact", new Object[] {"시스템 문의처", 100});
    }
    /** 관인 이미지 파일 (FileStorage 저장 이름) */
    public static final String SEAL_FILE = "sealFile";
    public static final String SEAL_TYPE = "sealType";
    /** 자동 로그아웃: 이 시간(분) 동안 아무것도 하지 않으면 로그아웃 */
    public static final String SESSION_TIMEOUT = "sessionTimeoutMinutes";
    public static final int TIMEOUT_DEFAULT = 30;
    public static final int TIMEOUT_MIN = 5;
    public static final int TIMEOUT_MAX = 120;

    public int sessionTimeoutMinutes() {
        try {
            int m = Integer.parseInt(get(SESSION_TIMEOUT, String.valueOf(TIMEOUT_DEFAULT)).trim());
            return Math.max(TIMEOUT_MIN, Math.min(TIMEOUT_MAX, m));
        } catch (NumberFormatException e) {
            return TIMEOUT_DEFAULT;
        }
    }

    /** 자동 로그아웃 시간을 바꾼다. 문제가 있으면 안내 문구 */
    @Transactional
    public String saveSessionTimeout(String minutes, String by) {
        int m;
        try {
            m = Integer.parseInt(minutes == null ? "" : minutes.trim());
        } catch (NumberFormatException e) {
            return "자동 로그아웃 시간은 분 단위 숫자로 입력하세요.";
        }
        if (m < TIMEOUT_MIN || m > TIMEOUT_MAX) {
            return "자동 로그아웃 시간은 " + TIMEOUT_MIN + "~" + TIMEOUT_MAX + "분 사이로 정하세요.";
        }
        put(SESSION_TIMEOUT, String.valueOf(m), by);
        return null;
    }

    private final OrgMapper mapper;
    private volatile Map<String, String> cache;

    public OrgService(OrgMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, String> all() {
        Map<String, String> c = cache;
        if (c == null) {
            c = new HashMap<>();
            for (Map<String, String> row : mapper.selectAll()) {
                c.put(row.get("k"), row.get("v"));
            }
            cache = c;
        }
        return c;
    }

    public String get(String key) {
        return all().get(key);
    }

    /** 값이 없으면 대신 쓸 글 */
    public String get(String key, String fallback) {
        String v = get(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    public boolean hasSeal() {
        return get(SEAL_FILE) != null;
    }

    /** 입력값을 검사해 저장한다. 문제가 있으면 안내 문구 */
    @Transactional
    public String save(Map<String, String> values, String by) {
        for (Map.Entry<String, Object[]> e : FIELDS.entrySet()) {
            String v = values.get(e.getKey());
            if (v != null && v.trim().length() > (Integer) e.getValue()[1]) {
                return e.getValue()[0] + "은(는) " + e.getValue()[1] + "자 이내로 적으세요.";
            }
        }
        for (String k : FIELDS.keySet()) {
            String v = values.get(k);
            put(k, v == null || v.isBlank() ? null : v.trim(), by);
        }
        cache = null;
        return null;
    }

    @Transactional
    public void put(String key, String value, String by) {
        LocalDateTime now = LocalDateTime.now();
        if (mapper.update(key, value, by, now) == 0) {
            mapper.insert(key, value, by, now);
        }
        cache = null;
    }
}
