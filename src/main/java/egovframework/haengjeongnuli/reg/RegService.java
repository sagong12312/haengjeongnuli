package egovframework.haengjeongnuli.reg;

import java.io.Reader;
import java.sql.Clob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.text.Csv;
import egovframework.haengjeongnuli.reg.RegDef.Field;
import egovframework.haengjeongnuli.reg.RegDef.Type;

/** 대장: 등록·수정·상태·삭제·복사·검색·기한·CSV·통계 */
@Service
public class RegService implements ApplicationRunner {

    public static final int PAGE_SIZE = 20;
    private static final Pattern PHONE = Pattern.compile("[0-9][0-9\\- ]{6,18}[0-9]");
    private static final Pattern MONEY = Pattern.compile("-?[0-9][0-9,]{0,18}");

    /** 보는 사람 (SQL 에서 v.admin · v.dept · v.me 로 쓴다) */
    public static final class Viewer {
        private final boolean admin;
        private final String dept;
        private final String me;
        private final String name;

        public Viewer(LoginUser u) {
            this.admin = u.isAdmin();
            this.dept = u.dept();
            this.me = u.getLoginId();
            this.name = u.getUserName();
        }

        public boolean isAdmin() { return admin; }
        public String getDept() { return dept; }
        public String getMe() { return me; }
        public String getName() { return name; }
    }

    public record Result(boolean ok, String message, Long id) {
        static Result fail(String m) { return new Result(false, m, null); }
    }

    private final RegMapper mapper;
    private final ObjectMapper json;

    public RegService(RegMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    /** 대장 정의에서 공개 범위를 바꿨으면 이미 있는 행도 맞춘다 */
    @Override
    public void run(ApplicationArguments args) {
        for (RegDef d : RegCatalog.all()) {
            mapper.syncScope(d.getCode(), d.getScope().name());
        }
    }

    /* ------------------------------------------------------------ 보기 */

    public Map<String, Object> page(RegDef d, Viewer v, String q, String status, boolean open, boolean late, Integer year, int page) {
        Filter f = new Filter(q, status, year, d);
        int total = mapper.count(d.getCode(), v, f.like, f.status, open, late, LocalDate.now(), f.year, f.from, f.to);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(1, page), pages);
        List<Map<String, Object>> rows = mapper.list(d.getCode(), v, f.like, f.status, open, late, LocalDate.now(), f.year, f.from, f.to,
                (p - 1) * PAGE_SIZE, p * PAGE_SIZE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows.stream().map(r -> view(d, r, v, true)).toList());
        out.put("total", total);
        out.put("page", p);
        out.put("pages", pages);
        return out;
    }

    public Map<String, Object> get(long id, Viewer v) {
        Map<String, Object> r = mapper.get(id);
        if (r == null || !canView(r, v)) {
            return null;
        }
        RegDef d = RegCatalog.get(String.valueOf(r.get("code")));
        if (d == null) {
            return null;
        }
        Map<String, Object> m = view(d, r, v, false);
        m.put("history", mapper.history(id).stream().map(h -> {
            Map<String, Object> x = new LinkedHashMap<>(h);
            x.put("at", toDateTime(h.get("at")));
            return x;
        }).toList());
        return m;
    }

    /** 화면에 쓰는 꼴: values(칸별 값), shown(목록용, 개인정보는 가림), dday, late, canEdit */
    Map<String, Object> view(RegDef d, Map<String, Object> r, Viewer v, boolean forList) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> values = decode(r.get("data"));
        m.put("id", ((Number) r.get("id")).longValue());
        m.put("code", d.getCode());
        m.put("regName", d.getName());
        m.put("title", r.get("title"));
        m.put("status", r.get("status"));
        boolean closed = "Y".equals(String.valueOf(r.get("closed")));
        m.put("closed", closed);
        LocalDate due = toDate(r.get("due"));
        m.put("due", due);
        m.put("dday", closed || due == null ? "" : dday(due));
        m.put("late", !closed && due != null && due.isBefore(LocalDate.now()));
        m.put("soon", !closed && due != null && !due.isBefore(LocalDate.now()) && !due.isAfter(LocalDate.now().plusDays(7)));
        m.put("amount", r.get("amount"));
        m.put("dept", r.get("dept"));
        m.put("ownerName", r.get("ownerName"));
        m.put("createdAt", toDateTime(r.get("createdAt")));
        m.put("updatedAt", toDateTime(r.get("updatedAt")));
        m.put("updatedBy", r.get("updatedBy"));
        m.put("canEdit", canEdit(d, r, v));
        Map<String, String> shown = new LinkedHashMap<>();
        for (Field f : d.getFields()) {
            String val = values.getOrDefault(f.key(), "");
            shown.put(f.key(), forList && f.pii() ? mask(val, f.type()) : f.type() == Type.N && !val.isEmpty() ? money(val) : val);
        }
        m.put("values", values);
        m.put("shown", shown);
        return m;
    }

    static String dday(LocalDate due) {
        long n = ChronoUnit.DAYS.between(LocalDate.now(), due);
        return n == 0 ? "D-day" : n > 0 ? "D-" + n : (-n) + "일 지남";
    }

    boolean canView(Map<String, Object> r, Viewer v) {
        if (v.isAdmin()) {
            return true;
        }
        String scope = String.valueOf(r.get("scope"));
        return "ALL".equals(scope)
                || ("DEPT".equals(scope) && !v.getDept().isEmpty() && v.getDept().equals(r.get("dept")))
                || v.getMe().equals(r.get("ownerId"));
    }

    boolean canEdit(RegDef d, Map<String, Object> r, Viewer v) {
        if (v.isAdmin() || v.getMe().equals(r.get("ownerId"))) {
            return true;
        }
        return d.getScope() != RegDef.Scope.SELF && !v.getDept().isEmpty() && v.getDept().equals(r.get("dept"));
    }

    /* ------------------------------------------------------------ 쓰기 */

    @Transactional
    public Result save(RegDef d, Long id, Map<String, String> params, String status, LoginUser me) {
        Viewer v = new Viewer(me);
        Map<String, Object> old = null;
        if (id != null) {
            old = mapper.get(id);
            if (old == null || !d.getCode().equals(old.get("code")) || !canView(old, v) || !canEdit(d, old, v)) {
                return Result.fail("고칠 수 없는 항목입니다.");
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Field f : d.getFields()) {
            String raw = params.get("f_" + f.key());
            String val = raw == null ? "" : raw.strip().replace("\r\n", "\n");
            if (val.isEmpty()) {
                if (f.required()) {
                    return Result.fail(f.label() + "을(를) 적어 주세요.");
                }
                continue;
            }
            if (val.length() > f.maxLen()) {
                return Result.fail(f.label() + "은(는) " + f.maxLen() + "자까지 적을 수 있습니다.");
            }
            switch (f.type()) {
                case D -> {
                    try {
                        LocalDate day = LocalDate.parse(val);
                        if (day.getYear() < 1900 || day.getYear() > 2200) {
                            return Result.fail(f.label() + " 날짜를 확인해 주세요.");
                        }
                    } catch (DateTimeParseException e) {
                        return Result.fail(f.label() + "을(를) 2026-01-31 꼴로 적어 주세요.");
                    }
                }
                case N -> {
                    if (!MONEY.matcher(val).matches()) {
                        return Result.fail(f.label() + "은(는) 숫자로 적어 주세요.");
                    }
                    val = val.replace(",", "");
                }
                case P -> {
                    if (!PHONE.matcher(val).matches()) {
                        return Result.fail(f.label() + "을(를) 010-1234-5678 꼴로 적어 주세요.");
                    }
                    val = phone(val);
                }
                case S -> {
                    if (!f.options().contains(val)) {
                        return Result.fail(f.label() + "에서 고를 수 있는 값이 아닙니다.");
                    }
                }
                default -> {
                    if (val.chars().anyMatch(c -> c < 0x20 && c != '\n' && c != '\t')) {
                        return Result.fail(f.label() + "에 쓸 수 없는 글자가 있습니다.");
                    }
                }
            }
            values.put(f.key(), val);
        }
        String title = values.getOrDefault(d.titleField().key(), "");
        if (title.isEmpty()) {
            title = d.getName();
        }
        title = title.length() > 300 ? title.substring(0, 300) : title;
        String st = status != null && d.knownStatus(status) ? status
                : old != null ? String.valueOf(old.get("status")) : d.getStatuses().get(0).label();
        LocalDate due = dueOf(d, values);
        Long amount = amountOf(d, values);
        String data;
        try {
            data = json.writeValueAsString(values);
        } catch (Exception e) {
            return Result.fail("저장하지 못했습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        String closed = d.closed(st) ? "Y" : "N";
        long eid;
        if (id == null) {
            eid = mapper.nextId();
            mapper.insert(eid, d.getCode(), d.getScope().name(), me.dept(), me.getLoginId(), me.getUserName(), title, st, closed, due, amount,
                    search(d, values), data, now);
            history(eid, me, "등록", st);
        } else {
            eid = id;
            mapper.update(eid, title, st, closed, due, amount, search(d, values), data, now, me.getUserName());
            String before = String.valueOf(old.get("status"));
            history(eid, me, "수정", before.equals(st) ? changed(d, decode(old.get("data")), values) : before + " → " + st);
        }
        return new Result(true, id == null ? "등록했습니다." : "저장했습니다.", eid);
    }

    @Transactional
    public Result setStatus(long id, String status, String note, LoginUser me) {
        Map<String, Object> r = mapper.get(id);
        Viewer v = new Viewer(me);
        RegDef d = r == null ? null : RegCatalog.get(String.valueOf(r.get("code")));
        if (d == null || !canView(r, v) || !canEdit(d, r, v)) {
            return Result.fail("바꿀 수 없는 항목입니다.");
        }
        if (!d.knownStatus(status)) {
            return Result.fail("없는 상태입니다.");
        }
        String n = note == null ? "" : note.strip();
        if (n.length() > 300) {
            return Result.fail("메모는 300자까지 적을 수 있습니다.");
        }
        mapper.setStatus(id, status, d.closed(status) ? "Y" : "N", LocalDateTime.now(), me.getUserName());
        history(id, me, "상태", r.get("status") + " → " + status + (n.isEmpty() ? "" : " (" + n + ")"));
        return new Result(true, "'" + status + "'(으)로 바꿨습니다.", id);
    }

    @Transactional
    public Result delete(long id, String reason, LoginUser me) {
        Map<String, Object> r = mapper.get(id);
        Viewer v = new Viewer(me);
        RegDef d = r == null ? null : RegCatalog.get(String.valueOf(r.get("code")));
        if (d == null || !canView(r, v) || !canEdit(d, r, v)) {
            return Result.fail("지울 수 없는 항목입니다.");
        }
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 2 || why.length() > 200) {
            return Result.fail("지우는 사유를 2~200자로 적어 주세요.");
        }
        mapper.delete(id, LocalDateTime.now(), me.getUserName());
        history(id, me, "삭제", why);
        return new Result(true, "지웠습니다. (기록은 남습니다)", id);
    }

    /** 비슷한 항목을 새로 만들 때: 칸 값을 그대로 가져와 새 항목으로 (상태는 처음으로) */
    @Transactional
    public Result copy(long id, LoginUser me) {
        Map<String, Object> r = mapper.get(id);
        Viewer v = new Viewer(me);
        RegDef d = r == null ? null : RegCatalog.get(String.valueOf(r.get("code")));
        if (d == null || !canView(r, v)) {
            return Result.fail("복사할 수 없는 항목입니다.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        decode(r.get("data")).forEach((k, val) -> params.put("f_" + k, val));
        Result res = save(d, null, params, null, me);
        if (res.ok()) {
            history(res.id(), me, "복사", "#" + id + " 에서");
            return new Result(true, "복사해 새 항목을 만들었습니다. 날짜 등을 고쳐 주세요.", res.id());
        }
        return res;
    }

    private void history(long entryId, LoginUser me, String action, String note) {
        String n = note == null ? null : note.length() > 500 ? note.substring(0, 500) : note;
        mapper.insertHistory(mapper.nextId(), entryId, me.getLoginId(), me.getUserName(), action, n, LocalDateTime.now());
    }

    private static String changed(RegDef d, Map<String, String> before, Map<String, String> after) {
        List<String> names = new ArrayList<>();
        for (Field f : d.getFields()) {
            if (!before.getOrDefault(f.key(), "").equals(after.getOrDefault(f.key(), ""))) {
                names.add(f.label());
            }
        }
        return names.isEmpty() ? "바뀐 칸 없음" : "바뀐 칸: " + String.join(", ", names);
    }

    /* ------------------------------------------------------------ 기한·검색·통계·CSV */

    public List<Map<String, Object>> dueSoon(Viewer v, int days) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.dueSoon(v, LocalDate.now().plusDays(days))) {
            RegDef d = RegCatalog.get(String.valueOf(r.get("code")));
            if (d != null) {
                out.add(view(d, r, v, true));
            }
        }
        return out;
    }

    public Map<String, Map<String, Object>> counts(Viewer v) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> r : mapper.countsByCode(v, LocalDate.now())) {
            m.put(String.valueOf(r.get("code")), r);
        }
        return m;
    }

    public List<Map<String, Object>> searchAll(Viewer v, String q) {
        String like = like(q);
        if (like == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.searchAll(v, like)) {
            RegDef d = RegCatalog.get(String.valueOf(r.get("code")));
            if (d != null) {
                out.add(view(d, r, v, true));
            }
        }
        return out;
    }

    public Map<String, Object> stats(RegDef d, Viewer v, Integer year) {
        Filter f = new Filter(null, null, year, d);
        List<Map<String, Object>> rows = mapper.statusCounts(d.getCode(), v, f.year, f.from, f.to);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        d.getStatuses().forEach(s -> byStatus.put(s.label(), 0L));
        long total = 0;
        long sum = 0;
        for (Map<String, Object> r : rows) {
            long n = ((Number) r.get("n")).longValue();
            byStatus.merge(String.valueOf(r.get("status")), n, Long::sum);
            total += n;
            if (r.get("amount") != null) {
                sum += ((Number) r.get("amount")).longValue();
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byStatus", byStatus);
        out.put("total", total);
        out.put("amount", d.amountField() == null ? null : sum);
        return out;
    }

    public Csv csv(RegDef d, Viewer v, String q, String status, boolean open, boolean late, Integer year) {
        List<String> head = new ArrayList<>(List.of("번호", "상태", "기한"));
        d.getFields().forEach(f -> head.add(f.label()));
        head.addAll(List.of("부서", "등록한 사람", "등록일"));
        Csv csv = Csv.of(head.toArray(String[]::new));
        Filter f = new Filter(q, status, year, d);
        List<Map<String, Object>> rows = mapper.list(d.getCode(), v, f.like, f.status, open, late, LocalDate.now(), f.year, f.from, f.to, 0, 5000);
        for (Map<String, Object> r : rows) {
            Map<String, String> vals = decode(r.get("data"));
            List<Object> row = new ArrayList<>();
            row.add(((Number) r.get("id")).longValue());
            row.add(r.get("status"));
            LocalDate due = toDate(r.get("due"));
            row.add(due == null ? "" : due.toString());
            for (Field fd : d.getFields()) {
                String val = vals.getOrDefault(fd.key(), "");
                row.add(fd.type() == Type.N && !val.isEmpty() ? (Object) Long.valueOf(val) : val);
            }
            row.add(r.get("dept"));
            row.add(r.get("ownerName"));
            LocalDateTime c = toDateTime(r.get("createdAt"));
            row.add(c == null ? "" : c.toLocalDate().toString());
            csv.row(row.toArray());
        }
        return csv;
    }

    /* ------------------------------------------------------------ 도우미 */

    private final class Filter {
        final String like;
        final String status;
        final Integer year;
        final LocalDateTime from;
        final LocalDateTime to;

        Filter(String q, String status, Integer year, RegDef d) {
            this.like = like(q);
            this.status = status != null && d.knownStatus(status) ? status : null;
            this.year = year != null && year >= 2000 && year <= 2100 ? year : null;
            this.from = this.year == null ? null : LocalDate.of(this.year, 1, 1).atStartOfDay();
            this.to = this.year == null ? null : LocalDate.of(this.year + 1, 1, 1).atStartOfDay();
        }
    }

    static String like(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String t = q.strip().toLowerCase(Locale.ROOT);
        t = t.length() > 50 ? t.substring(0, 50) : t;
        return "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String search(RegDef d, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        for (Field f : d.getFields()) {
            String val = values.get(f.key());
            if (val != null && (f.type() == Type.T || f.type() == Type.S || f.type() == Type.L)) {
                sb.append(f.type() == Type.L && val.length() > 100 ? val.substring(0, 100) : val).append(' ');
            }
        }
        String s = sb.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    static LocalDate dueOf(RegDef d, Map<String, String> values) {
        Field f = d.dueField();
        if (f == null || !values.containsKey(f.key())) {
            return null;
        }
        LocalDate day = LocalDate.parse(values.get(f.key()));
        return f.dueDays() > 0 ? day.plusDays(f.dueDays()) : day;
    }

    private static Long amountOf(RegDef d, Map<String, String> values) {
        Field f = d.amountField();
        if (f == null || !values.containsKey(f.key())) {
            return null;
        }
        try {
            return Long.valueOf(values.get(f.key()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Map<String, String> decode(Object data) {
        String s = clob(data);
        if (s == null || s.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return json.readValue(s, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String clob(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Clob c) {
            try (Reader r = c.getCharacterStream()) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[2048];
                int n;
                while ((n = r.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        }
        return o.toString();
    }

    static LocalDate toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (o instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        return LocalDate.parse(o.toString().substring(0, 10));
    }

    static LocalDateTime toDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime t) {
            return t;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        if (o instanceof java.time.OffsetDateTime t) {
            return t.toLocalDateTime();
        }
        return null;
    }

    /** 숫자만 적은 전화번호를 010-1234-5678 꼴로 */
    static String phone(String v) {
        String n = v.replaceAll("\\D", "");
        if (n.startsWith("02") && (n.length() == 9 || n.length() == 10)) {
            return "02-" + n.substring(2, n.length() - 4) + "-" + n.substring(n.length() - 4);
        }
        if (n.length() == 10 || n.length() == 11) {
            return n.substring(0, 3) + "-" + n.substring(3, n.length() - 4) + "-" + n.substring(n.length() - 4);
        }
        return v.strip();
    }

    static String money(String v) {
        try {
            return String.format("%,d", Long.parseLong(v));
        } catch (NumberFormatException e) {
            return v;
        }
    }

    /** 목록에서 개인정보 가리기: 홍길동 → 홍*동, 010-1234-5678 → 010-****-5678 */
    static String mask(String v, Type t) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        if (t == Type.P) {
            return v.replaceAll("(\\d{2,3})[- ]?(\\d{3,4})[- ]?(\\d{4})", "$1-****-$3");
        }
        if (v.length() <= 1) {
            return "*";
        }
        if (v.length() == 2) {
            return v.charAt(0) + "*";
        }
        if (v.length() <= 4) {
            return v.charAt(0) + "*".repeat(v.length() - 2) + v.charAt(v.length() - 1);
        }
        return v.substring(0, 2) + "***";
    }
}
