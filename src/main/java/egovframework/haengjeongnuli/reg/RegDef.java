package egovframework.haengjeongnuli.reg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대장 하나의 정의. 칸(Field)과 처리 상태(Status)만 정하면 등록·수정·검색·기한 알림·CSV·인쇄·이력이 모두 된다.
 *
 * 칸 정의 글: "키|이름|종류|표시|선택지"
 *  - 종류: T 한 줄, L 여러 줄, D 날짜, N 금액·숫자, S 고르기, P 전화
 *  - 표시: r 필수, p 개인정보(목록에서 가림), t 제목, l 목록에 보임, a 합계 금액, d 기한(그 날짜), d+10 (그 날짜부터 10일 뒤가 기한)
 */
public final class RegDef {

    /** DEPT: 같은 부서만, SELF: 본인과 관리자만, ALL: 기관 전체 */
    public enum Scope { DEPT, SELF, ALL }

    public enum Type { T, L, D, N, S, P }

    public record Field(String key, String label, Type type, boolean required, boolean pii, boolean title, boolean list,
                        boolean amount, boolean due, int dueDays, List<String> options) {
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getType() { return type.name(); }
        public boolean isRequired() { return required; }
        public boolean isPii() { return pii; }
        public boolean isList() { return list; }
        public boolean isDue() { return due; }
        public int getDueDays() { return dueDays; }
        public List<String> getOptions() { return options; }
        public int maxLen() { return type == Type.L ? 2000 : type == Type.N ? 15 : 200; }
    }

    public record Status(String label, boolean closed) {
        public String getLabel() { return label; }
        public boolean isClosed() { return closed; }
    }

    private final String code;
    private final String group;
    private final String name;
    private final Scope scope;
    private final String guide;
    private final List<Field> fields = new ArrayList<>();
    private final List<Status> statuses = new ArrayList<>();

    RegDef(String code, String group, String name, Scope scope, String guide) {
        this.code = code;
        this.group = group;
        this.name = name;
        this.scope = scope;
        this.guide = guide;
    }

    RegDef f(String spec) {
        String[] p = spec.split("\\|", -1);
        String flags = p.length > 3 ? p[3] : "";
        int dueDays = 0;
        boolean due = flags.contains("d");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("d\\+(\\d+)").matcher(flags);
        if (m.find()) {
            dueDays = Integer.parseInt(m.group(1));
        }
        List<String> opts = p.length > 4 && !p[4].isEmpty() ? List.of(p[4].split(",")) : List.of();
        fields.add(new Field(p[0], p[1], Type.valueOf(p[2]), flags.contains("r"), flags.contains("p"), flags.contains("t"),
                flags.contains("l") || flags.contains("t"), flags.contains("a"), due, dueDays, opts));
        return this;
    }

    /** "접수,처리 중,완료*" — * 는 끝난 상태 (기한 알림에서 뺀다) */
    RegDef st(String spec) {
        statuses.clear();
        Arrays.stream(spec.split(",")).forEach(s -> statuses.add(new Status(s.replace("*", ""), s.endsWith("*"))));
        return this;
    }

    public String getCode() { return code; }
    public String getGroup() { return group; }
    public String getName() { return name; }
    public Scope getScope() { return scope; }
    public String getScopeLabel() { return scope == Scope.DEPT ? "부서 안에서만" : scope == Scope.SELF ? "본인과 관리자만" : "기관 전체"; }
    public String getGuide() { return guide; }
    public List<Field> getFields() { return fields; }
    public List<Status> getStatuses() { return statuses.isEmpty() ? List.of(new Status("진행", false), new Status("완료", true)) : statuses; }

    public Field titleField() {
        return fields.stream().filter(Field::title).findFirst().orElse(fields.get(0));
    }

    public Field dueField() {
        return fields.stream().filter(Field::due).findFirst().orElse(null);
    }

    public Field amountField() {
        return fields.stream().filter(Field::amount).findFirst().orElse(null);
    }

    public Field field(String key) {
        return fields.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
    }

    public boolean closed(String status) {
        return getStatuses().stream().anyMatch(s -> s.label().equals(status) && s.closed());
    }

    public boolean knownStatus(String status) {
        return getStatuses().stream().anyMatch(s -> s.label().equals(status));
    }

    public List<Field> getListFields() {
        return fields.stream().filter(Field::list).toList();
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("group", group);
        m.put("scope", getScopeLabel());
        return m;
    }
}
