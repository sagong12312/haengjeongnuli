package egovframework.haengjeongnuli.biz;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.desk.DeskMapper;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 업무 지원.
 * 업무 일지 · 부서 일정 · 부서 간 협조 요청 · 회의록(후속 조치는 할 일로) · 전화 응대 기록 · 방문객 출입(보존기간 뒤 개인정보 파기) ·
 * 습득물 · 정보공개청구(결정 기한·연장) · 비품 신청·지급 · 업무 매뉴얼(판 이력) · 경조사 알림 · 칭찬
 */
@Service
public class BizService {

    public record Result(boolean ok, String message, Long id) {
        static Result ok(String m, Long id) { return new Result(true, m, id); }
        static Result fail(String m) { return new Result(false, m, null); }
    }

    /** 정보공개 결정 기간: 청구를 받은 날부터 10일, 부득이하면 10일 연장 (공공기관의 정보공개에 관한 법률 제11조) */
    public static final int INFO_DAYS = 10;
    /** 습득물: 습득일부터 7일 안에 반환·제출 절차를 밟지 않으면 습득자가 권리를 잃는다 (유실물법 제9조) */
    public static final int LOST_DAYS = 7;

    public static final Map<String, String> CALL_RESULTS = Map.of("DONE", "바로 답변", "TRANSFER", "담당자 연결", "CALLBACK", "다시 연락하기로");
    public static final Map<String, String> DECISIONS = Map.of("OPEN", "공개", "PARTIAL", "부분 공개", "CLOSED", "비공개", "NONE", "정보 부존재");
    public static final Map<String, String> FAMILY_KINDS = Map.of("WEDDING", "결혼", "BIRTH", "출산", "DEATH", "부고", "ETC", "기타");

    private final BizMapper mapper;
    private final UserService users;
    private final NotificationService notifications;
    private final HolidayService holidays;
    private final DeskMapper desk;
    private final int visitorKeepDays;

    public BizService(BizMapper mapper, UserService users, NotificationService notifications, HolidayService holidays, DeskMapper desk,
                      @Value("${app.biz.visitor-keep-days:180}") int visitorKeepDays) {
        this.mapper = mapper;
        this.users = users;
        this.notifications = notifications;
        this.holidays = holidays;
        this.desk = desk;
        this.visitorKeepDays = visitorKeepDays;
    }

    private static String trim(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private static String opt(String s) {
        String t = trim(s);
        return t.isEmpty() ? null : t;
    }

    static Map<String, Object> times(Map<String, Object> m) {
        if (m != null) {
            m.replaceAll((k, v) -> v instanceof java.sql.Timestamp t ? (k.equals("day") || k.equals("due") || k.equals("received")
                    || k.equals("decisionOn") ? t.toLocalDateTime().toLocalDate() : t.toLocalDateTime())
                    : v instanceof java.sql.Date d ? d.toLocalDate() : v);
        }
        return m;
    }

    static List<Map<String, Object>> times(List<Map<String, Object>> list) {
        list.forEach(BizService::times);
        return list;
    }

    private void notifyDept(String dept, String exceptId, String msg, String link) {
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && dept.equals(u.dept()) && !u.getLoginId().equals(exceptId)) {
                notifications.notify(u.getLoginId(), msg, link);
            }
        }
    }

    /* ================================================================ 1·2. 업무 일지 */

    /** 오늘과 지난 7일 안의 일지만 쓰고 고칠 수 있다 */
    @Transactional
    public Result saveWorkLog(LocalDate day, String body, LoginUser me) {
        LocalDate today = LocalDate.now();
        if (day == null || day.isAfter(today) || day.isBefore(today.minusDays(7))) {
            return Result.fail("일지는 오늘부터 7일 전까지만 쓰고 고칠 수 있습니다.");
        }
        String b = trim(body);
        if (b.isEmpty() || b.length() > 1300) {
            return Result.fail("한 일을 1,300자 이내로 적으세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateWorkLog(me.getLoginId(), day, b, now) == 0) {
            try {
                mapper.insertWorkLog(mapper.nextId(), me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept(), day, b, now);
            } catch (DuplicateKeyException e) {
                mapper.updateWorkLog(me.getLoginId(), day, b, now);
            }
        }
        return Result.ok(day + " 업무 일지를 저장했습니다.", null);
    }

    public Map<String, Object> workLog(LoginUser me, LocalDate day) {
        return times(mapper.workLog(me.getLoginId(), day));
    }

    public List<Map<String, Object>> myWorkLogs(LoginUser me) {
        return times(mapper.myWorkLogs(me.getLoginId(), LocalDate.now().minusDays(30), LocalDate.now()));
    }

    /** 같은 부서 직원의 그날 일지 (부서 업무 파악용) */
    public List<Map<String, Object>> deptWorkLogs(LoginUser me, LocalDate day) {
        return me.dept().isEmpty() ? List.of() : times(mapper.deptWorkLogs(me.dept(), day));
    }

    /* ================================================================ 3. 부서 일정 */

    @Transactional
    public Result addDeptEvent(LocalDate day, String title, boolean notify, LoginUser me) {
        if (me.dept().isEmpty()) {
            return Result.fail("부서가 정해지지 않아 부서 일정을 올릴 수 없습니다.");
        }
        if (day == null || day.isBefore(LocalDate.now().minusDays(30)) || day.isAfter(LocalDate.now().plusYears(1))) {
            return Result.fail("날짜는 30일 전부터 1년 뒤 사이로 고르세요.");
        }
        String t = trim(title);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("일정 이름을 200자 이내로 적으세요.");
        }
        long id = mapper.nextId();
        mapper.insertDeptEvent(id, me.dept(), day, t, me.getLoginId(), me.getUserName(), LocalDateTime.now());
        if (notify) {
            notifyDept(me.dept(), me.getLoginId(), "부서 일정: " + day + " " + t, "/biz/events?ym=" + YearMonth.from(day));
        }
        return Result.ok("부서 일정을 올렸습니다.", id);
    }

    public List<Map<String, Object>> deptEvents(LoginUser me, LocalDate from, LocalDate to) {
        return me.dept().isEmpty() ? List.of() : times(mapper.deptEvents(me.dept(), from, to));
    }

    @Transactional
    public Result deleteDeptEvent(long id, LoginUser me) {
        Map<String, Object> e = mapper.deptEvent(id);
        if (e == null || !me.dept().equals(e.get("dept"))) {
            return Result.fail("우리 부서 일정이 아닙니다.");
        }
        if (!me.getLoginId().equals(e.get("by")) && !me.isAdmin()) {
            return Result.fail("올린 사람만 지울 수 있습니다.");
        }
        mapper.deleteDeptEvent(id);
        return Result.ok("부서 일정을 지웠습니다.", id);
    }

    /* ================================================================ 4·5. 부서 간 협조 요청 */

    @Transactional
    public Result requestCoop(String toDept, String title, String body, LocalDate due, LoginUser me) {
        String d = trim(toDept), t = trim(title), b = trim(body);
        if (d.isEmpty() || d.length() > 100) {
            return Result.fail("협조를 요청할 부서를 고르세요.");
        }
        if (d.equals(me.dept())) {
            return Result.fail("우리 부서에는 협조 요청을 보낼 수 없습니다. 쪽지·할 일을 쓰세요.");
        }
        if (t.isEmpty() || t.length() > 200 || b.isEmpty() || b.length() > 1300) {
            return Result.fail("제목(200자)과 요청 내용(1,300자)을 적으세요.");
        }
        LocalDate today = LocalDate.now();
        if (due == null || due.isBefore(today) || due.isAfter(today.plusDays(90))) {
            return Result.fail("회신 기한은 오늘부터 90일 안에서 고르세요.");
        }
        boolean known = users.list().stream().anyMatch(u -> u.isEnabled() && d.equals(u.dept()));
        if (!known) {
            return Result.fail("그 부서에 직원이 없습니다. 부서 이름을 확인하세요.");
        }
        long id = mapper.nextId();
        mapper.insertCoop(id, me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept(), d, t, b, due, LocalDateTime.now());
        notifyDept(d, null, (me.dept().isEmpty() ? "" : me.dept() + " ") + me.getUserName() + "님의 협조 요청: " + t + " (회신 기한 " + due + ")",
                "/biz/coops/" + id);
        return Result.ok(d + "에 협조를 요청했습니다.", id);
    }

    public List<Map<String, Object>> coops(LoginUser me) {
        List<Map<String, Object>> list = times(mapper.coops(me.dept().isEmpty() ? "-" : me.dept(), me.getLoginId()));
        LocalDate today = LocalDate.now();
        for (Map<String, Object> c : list) {
            c.put("overdue", "OPEN".equals(c.get("status")) && ((LocalDate) c.get("due")).isBefore(today));
            c.put("incoming", me.dept().equals(c.get("toDept")));
        }
        return list;
    }

    public Map<String, Object> coop(long id, LoginUser me) {
        Map<String, Object> c = times(mapper.coop(id));
        if (c == null) {
            return null;
        }
        boolean mine = me.getLoginId().equals(c.get("fromId")) || me.dept().equals(c.get("toDept")) || me.dept().equals(c.get("fromDept"));
        return mine || me.isAdmin() ? c : null;
    }

    @Transactional
    public Result replyCoop(long id, String reply, LoginUser me) {
        Map<String, Object> c = mapper.coop(id);
        if (c == null || !me.dept().equals(c.get("toDept"))) {
            return Result.fail("우리 부서가 받은 협조 요청이 아닙니다.");
        }
        String r = trim(reply);
        if (r.isEmpty() || r.length() > 1300) {
            return Result.fail("회신 내용을 1,300자 이내로 적으세요.");
        }
        if (mapper.replyCoop(id, r, me.getUserName(), LocalDateTime.now()) == 0) {
            return Result.fail("이미 회신한 요청입니다.");
        }
        notifications.notify((String) c.get("fromId"), me.dept() + "에서 협조 요청에 회신했습니다: " + c.get("title"), "/biz/coops/" + id);
        return Result.ok("회신했습니다.", id);
    }

    /** 회신 기한이 지난 협조 요청을 받은 부서와 요청한 사람에게 한 번 알린다 (매일 아침) */
    @Scheduled(cron = "${app.biz.morning-cron:0 50 8 * * *}")
    public void morning() {
        remindCoops();
        remindLost();
        remindInfo();
    }

    @Transactional
    public int remindCoops() {
        int n = 0;
        for (Map<String, Object> c : times(mapper.overdueCoops(LocalDate.now()))) {
            notifyDept((String) c.get("toDept"), null, "회신 기한(" + c.get("due") + ")이 지난 협조 요청이 있습니다: " + c.get("title"),
                    "/biz/coops/" + c.get("id"));
            notifications.notify((String) c.get("fromId"), "보낸 협조 요청의 회신 기한이 지났습니다: " + c.get("title"), "/biz/coops/" + c.get("id"));
            mapper.coopReminded(((Number) c.get("id")).longValue());
            n++;
        }
        return n;
    }

    /* ================================================================ 6·7. 회의록 (후속 조치는 할 일로) */

    /**
     * 후속 조치는 한 줄에 하나 "아이디: 할 일 @기한(yyyy-MM-dd, 선택)" 으로 적으면 그 직원의 할 일에 들어가고 알림이 간다.
     */
    @Transactional
    public Result writeMinutes(String title, LocalDate day, List<String> attendeeIds, String body, String decisions, String actions,
                               LoginUser me) {
        String t = trim(title), b = trim(body), dc = opt(decisions);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("회의 이름을 200자 이내로 적으세요.");
        }
        if (day == null || day.isAfter(LocalDate.now().plusDays(1)) || day.isBefore(LocalDate.now().minusYears(1))) {
            return Result.fail("회의 날짜를 확인하세요.");
        }
        if (b.isEmpty() || b.length() > 1300 || (dc != null && dc.length() > 1300)) {
            return Result.fail("회의 내용과 결정 사항은 각각 1,300자 이내로 적으세요.");
        }
        Set<String> att = new LinkedHashSet<>();
        for (String a : attendeeIds == null ? List.<String>of() : attendeeIds) {
            LoginUser u = a == null ? null : users.findByLoginId(a.trim());
            if (u != null && u.isEnabled()) {
                att.add(u.getLoginId());
            }
        }
        att.add(me.getLoginId());
        String attendees = "," + String.join(",", att) + ",";
        if (attendees.length() > 1000) {
            return Result.fail("참석자가 너무 많습니다.");
        }
        // 후속 조치 해석 (먼저 모두 확인하고 저장한다)
        record Action(LoginUser who, String what, LocalDate due) {}
        List<Action> todo = new ArrayList<>();
        for (String line : trim(actions).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 1) {
                return Result.fail("후속 조치는 '아이디: 할 일 @2026-12-01' 꼴로 적으세요: " + line.trim());
            }
            LoginUser who = users.findByLoginId(line.substring(0, colon).trim());
            if (who == null || !who.isEnabled()) {
                return Result.fail("후속 조치 담당자 아이디를 찾을 수 없습니다: " + line.substring(0, colon).trim());
            }
            String what = line.substring(colon + 1).trim();
            LocalDate due = null;
            int at = what.lastIndexOf('@');
            if (at >= 0) {
                try {
                    due = LocalDate.parse(what.substring(at + 1).trim());
                } catch (java.time.format.DateTimeParseException e) {
                    return Result.fail("후속 조치 기한은 @2026-12-01 꼴로 적으세요: " + line.trim());
                }
                what = what.substring(0, at).trim();
            }
            if (what.isEmpty() || what.length() > 150) {
                return Result.fail("후속 조치 내용을 150자 이내로 적으세요.");
            }
            todo.add(new Action(who, what, due));
        }
        if (todo.size() > 20) {
            return Result.fail("후속 조치는 20개까지 적을 수 있습니다.");
        }
        long id = mapper.nextId();
        LocalDateTime now = LocalDateTime.now();
        mapper.insertMinutes(id, t, day, attendees, b, dc, me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept(), now);
        for (Action a : todo) {
            desk.insertTodo(desk.nextTodoId(), a.who().getLoginId(), ("[회의] " + t + " - " + a.what()).length() > 200
                    ? ("[회의] " + a.what()) : "[회의] " + t + " - " + a.what(), a.due(), now);
            if (!a.who().getLoginId().equals(me.getLoginId())) {
                notifications.notify(a.who().getLoginId(), me.getUserName() + "님이 회의 후속 조치를 맡겼습니다: " + a.what(), "/desk/todos");
            }
        }
        for (String a : att) {
            if (!a.equals(me.getLoginId())) {
                notifications.notify(a, "회의록이 올라왔습니다: " + t, "/biz/minutes/" + id);
            }
        }
        return Result.ok("회의록을 저장했습니다." + (todo.isEmpty() ? "" : " 후속 조치 " + todo.size() + "건을 담당자 할 일에 넣었습니다."), id);
    }

    public List<Map<String, Object>> minutesList(LoginUser me) {
        return times(mapper.minutesList(me.getLoginId(), me.dept().isEmpty() ? "-" : me.dept(), "%," + me.getLoginId() + ",%"));
    }

    public Map<String, Object> minutes(long id, LoginUser me) {
        Map<String, Object> m = times(mapper.minutes(id));
        if (m == null) {
            return null;
        }
        boolean ok = me.getLoginId().equals(m.get("writerId")) || me.dept().equals(m.get("dept"))
                || String.valueOf(m.get("attendees")).contains("," + me.getLoginId() + ",") || me.isAdmin();
        if (!ok) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String id2 : String.valueOf(m.get("attendees")).split(",")) {
            if (!id2.isBlank()) {
                LoginUser u = users.findByLoginId(id2);
                names.add(u == null ? id2 : u.getUserName());
            }
        }
        m.put("attendeeNames", String.join(", ", names));
        return m;
    }

    /* ================================================================ 8·9. 전화 응대 기록 */

    public Result logCall(String name, String phone, String topic, String summary, String result, LoginUser me) {
        String t = trim(topic), s = opt(summary), n = opt(name), p = opt(phone);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("문의 내용(요지)을 200자 이내로 적으세요.");
        }
        if (s != null && s.length() > 1300) {
            return Result.fail("통화 요약은 1,300자 이내로 적으세요.");
        }
        if ((n != null && n.length() > 100) || (p != null && !p.matches("[0-9\\-+ ]{2,20}"))) {
            return Result.fail("이름(100자)과 전화번호(숫자·- 20자)를 확인하세요.");
        }
        if (!CALL_RESULTS.containsKey(result)) {
            return Result.fail("처리 결과를 고르세요.");
        }
        long id = mapper.nextId();
        mapper.insertCall(id, LocalDateTime.now(), n, p, t, s, result, me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept());
        if ("CALLBACK".equals(result)) {
            desk.insertTodo(desk.nextTodoId(), me.getLoginId(), ("다시 연락: " + (n == null ? "" : n + " ") + (p == null ? "" : p + " ") + t)
                    .substring(0, Math.min(200, ("다시 연락: " + (n == null ? "" : n + " ") + (p == null ? "" : p + " ") + t).length())),
                    LocalDate.now(), LocalDateTime.now());
        }
        return Result.ok("전화 응대를 기록했습니다." + ("CALLBACK".equals(result) ? " 다시 연락할 일을 오늘 할 일에 넣었습니다." : ""), id);
    }

    public record CallStats(List<Map<String, Object>> rows, Map<String, Integer> byResult, int total) {}

    /** 한 달 전화 기록과 결과별 건수 (내 부서, 관리자는 전체) */
    public CallStats calls(LoginUser me, YearMonth ym, String q) {
        String like = opt(q) == null ? null : "%" + trim(q) + "%";
        List<Map<String, Object>> rows = times(mapper.calls(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay(),
                me.isAdmin() ? null : (me.dept().isEmpty() ? "-" : me.dept()), like));
        Map<String, Integer> by = new LinkedHashMap<>();
        CALL_RESULTS.keySet().stream().sorted().forEach(k -> by.put(k, 0));
        for (Map<String, Object> r : rows) {
            by.merge((String) r.get("result"), 1, Integer::sum);
        }
        return new CallStats(rows, by, rows.size());
    }

    /* ================================================================ 10·11. 방문객 출입 */

    public Result visitIn(String name, String phone, String org, String purpose, String hostId, LoginUser me) {
        String n = trim(name), p = opt(phone), o = opt(org), pu = trim(purpose);
        if (n.isEmpty() || n.length() > 100 || pu.isEmpty() || pu.length() > 200) {
            return Result.fail("방문객 이름(100자)과 방문 목적(200자)을 적으세요.");
        }
        if ((p != null && !p.matches("[0-9\\-+ ]{2,20}")) || (o != null && o.length() > 100)) {
            return Result.fail("연락처(숫자·-)와 소속(100자)을 확인하세요.");
        }
        LoginUser host = hostId == null || hostId.isBlank() ? me : users.findByLoginId(hostId.trim());
        if (host == null || !host.isEnabled()) {
            return Result.fail("만날 직원을 고르세요.");
        }
        long id = mapper.nextId();
        mapper.insertVisit(id, n, p, o, pu, host.getLoginId(), host.getUserName(), LocalDateTime.now());
        if (!host.getLoginId().equals(me.getLoginId())) {
            notifications.notify(host.getLoginId(), "방문객이 왔습니다: " + n + (o == null ? "" : " (" + o + ")") + " - " + pu, "/biz/visitors");
        }
        return Result.ok(n + "님 입실을 기록했습니다.", id);
    }

    public Result visitOut(long id) {
        return mapper.visitOut(id, LocalDateTime.now()) == 0 ? Result.fail("이미 퇴실했거나 없는 기록입니다.") : Result.ok("퇴실을 기록했습니다.", id);
    }

    public List<Map<String, Object>> visits() {
        return times(mapper.visits(LocalDateTime.now().minusDays(visitorKeepDays + 30L)));
    }

    public int visitorKeepDays() {
        return visitorKeepDays;
    }

    /** 보존기간(기본 180일)이 지난 방문객의 이름·연락처를 지운다 (매일 새벽) */
    @Scheduled(cron = "${app.biz.purge-cron:0 40 3 * * *}")
    @Transactional
    public int purgeVisitors() {
        return visitorKeepDays <= 0 ? 0 : mapper.purgeVisits(LocalDateTime.now().minusDays(visitorKeepDays));
    }

    /* ================================================================ 12·13. 습득물 */

    public Result addLost(LocalDateTime foundAt, String place, String desc, String finder, String store, LoginUser me) {
        String pl = trim(place), d = trim(desc), st = trim(store), f = opt(finder);
        if (foundAt == null || foundAt.isAfter(LocalDateTime.now().plusMinutes(5)) || foundAt.isBefore(LocalDateTime.now().minusDays(30))) {
            return Result.fail("습득 일시를 확인하세요 (30일 전부터 지금까지).");
        }
        if (pl.isEmpty() || pl.length() > 200 || d.isEmpty() || d.length() > 300 || st.isEmpty() || st.length() > 200) {
            return Result.fail("습득 장소, 물건 설명, 보관 장소를 적으세요.");
        }
        if (f != null && f.length() > 100) {
            return Result.fail("습득자는 100자 이내로 적으세요.");
        }
        long id = mapper.nextId();
        mapper.insertLost(id, foundAt, pl, d, f, st, me.getLoginId());
        return Result.ok("습득물을 등록했습니다. 습득일부터 " + LOST_DAYS + "일 안에 주인에게 돌려주거나 경찰서에 제출하세요.", id);
    }

    public List<Map<String, Object>> lostItems() {
        List<Map<String, Object>> list = times(mapper.lostItems());
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> i : list) {
            LocalDateTime f = (LocalDateTime) i.get("foundAt");
            i.put("deadline", f.toLocalDate().plusDays(LOST_DAYS));
            i.put("late", "KEEP".equals(i.get("status")) && f.plusDays(LOST_DAYS).isBefore(now));
        }
        return list;
    }

    /** RETURNED(주인에게 돌려줌) · POLICE(경찰서 제출) */
    public Result handleLost(long id, String status, String owner, LoginUser me) {
        if (!"RETURNED".equals(status) && !"POLICE".equals(status)) {
            return Result.fail("처리 방법을 고르세요.");
        }
        String o = opt(owner);
        if ("RETURNED".equals(status) && (o == null || o.length() > 100)) {
            return Result.fail("돌려받은 사람 이름을 적으세요 (신분 확인 뒤).");
        }
        return mapper.handleLost(id, status, o, LocalDateTime.now(), me.getUserName()) == 0 ? Result.fail("보관 중인 습득물이 아닙니다.")
                : Result.ok("RETURNED".equals(status) ? "주인에게 돌려줬습니다." : "경찰서 제출로 기록했습니다.", id);
    }

    /** 보관 5일이 지난 습득물을 등록한 직원에게 한 번 알린다 (7일 기한 전에) */
    @Transactional
    public int remindLost() {
        int n = 0;
        for (Map<String, Object> i : times(mapper.lostToRemind(LocalDateTime.now().minusDays(LOST_DAYS - 2L)))) {
            notifications.notify((String) i.get("createdBy"), "습득물 '" + i.get("desc") + "'이(가) 곧 습득 " + LOST_DAYS
                    + "일이 됩니다. 주인에게 돌려주거나 경찰서에 제출하세요.", "/biz/lost");
            mapper.lostReminded(((Number) i.get("id")).longValue());
            n++;
        }
        return n;
    }

    /* ================================================================ 14~17. 정보공개청구 */

    /** 기간의 끝날이 토요일·공휴일이면 그다음 날 (민법 제161조) */
    LocalDate dueFrom(LocalDate start, int days) {
        LocalDate d = start.plusDays(days);
        while (!holidays.isWorkingDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Transactional
    public Result receiveInfo(String requester, String contact, String content, LocalDate received, String dept, String assigneeId,
                              LoginUser me) {
        String r = trim(requester), c = opt(contact), ct = trim(content), d = opt(dept);
        if (r.isEmpty() || r.length() > 100 || ct.isEmpty() || ct.length() > 1300) {
            return Result.fail("청구인(100자)과 청구 내용(1,300자)을 적으세요.");
        }
        if (c != null && c.length() > 100) {
            return Result.fail("연락처는 100자 이내로 적으세요.");
        }
        LocalDate today = LocalDate.now();
        if (received == null || received.isAfter(today) || received.isBefore(today.minusDays(30))) {
            return Result.fail("접수일을 확인하세요 (30일 전부터 오늘까지).");
        }
        LoginUser assignee = assigneeId == null || assigneeId.isBlank() ? null : users.findByLoginId(assigneeId.trim());
        if (assigneeId != null && !assigneeId.isBlank() && (assignee == null || !assignee.isEnabled())) {
            return Result.fail("담당자를 찾을 수 없습니다.");
        }
        String prefix = received.getYear() + "-";
        String no = prefix + String.format("%05d", mapper.infoCount(prefix + "%") + 1);
        long id = mapper.nextId();
        LocalDate due = dueFrom(received, INFO_DAYS);
        mapper.insertInfo(id, no, r, c, ct, received, due, d != null ? d : assignee != null && !assignee.dept().isEmpty() ? assignee.dept() : null,
                assignee == null ? null : assignee.getLoginId(), me.getLoginId(), LocalDateTime.now());
        if (assignee != null) {
            notifications.notify(assignee.getLoginId(), "정보공개청구 " + no + "을(를) 맡았습니다. 결정 기한 " + due, "/biz/info/" + id);
        }
        return Result.ok("정보공개청구 " + no + "를 접수했습니다. 결정 기한은 " + due + "입니다.", id);
    }

    public List<Map<String, Object>> infoReqs() {
        List<Map<String, Object>> list = times(mapper.infoReqs());
        LocalDate today = LocalDate.now();
        for (Map<String, Object> i : list) {
            LocalDate due = (LocalDate) i.get("due");
            i.put("late", "WAIT".equals(i.get("status")) && due.isBefore(today));
            i.put("soon", "WAIT".equals(i.get("status")) && !due.isBefore(today) && !due.isAfter(today.plusDays(2)));
        }
        return list;
    }

    public Map<String, Object> infoReq(long id) {
        return times(mapper.infoReq(id));
    }

    /** 부득이한 사유로 한 번, 기간 끝난 다음 날부터 10일 연장. 청구인에게 알렸다고 기록 */
    @Transactional
    public Result extendInfo(long id, String reason) {
        Map<String, Object> i = infoReq(id);
        if (i == null || !"WAIT".equals(i.get("status"))) {
            return Result.fail("결정 전인 청구가 아닙니다.");
        }
        if ("Y".equals(i.get("extended"))) {
            return Result.fail("결정 기간은 한 번만(10일) 연장할 수 있습니다.");
        }
        String r = trim(reason);
        if (r.length() < 5 || r.length() > 300) {
            return Result.fail("연장하는 부득이한 사유를 5~300자로 적으세요 (청구인에게 문서로 알려야 합니다).");
        }
        LocalDate due = (LocalDate) i.get("due");
        if (due.isBefore(LocalDate.now())) {
            return Result.fail("결정 기한이 이미 지나 연장할 수 없습니다.");
        }
        LocalDate newDue = dueFrom(due, INFO_DAYS);
        mapper.extendInfo(id, newDue, r);
        return Result.ok("결정 기한을 " + newDue + "까지 연장했습니다. 연장 사실과 사유를 청구인에게 지체 없이 문서로 알리세요.", id);
    }

    @Transactional
    public Result decideInfo(long id, String decision, String note) {
        if (!DECISIONS.containsKey(decision)) {
            return Result.fail("결정 내용을 고르세요.");
        }
        String n = opt(note);
        if (!"OPEN".equals(decision) && (n == null || n.length() < 5)) {
            return Result.fail("부분 공개·비공개·부존재는 이유(근거 조항)를 적으세요.");
        }
        if (n != null && n.length() > 1000) {
            return Result.fail("결정 이유는 1,000자 이내로 적으세요.");
        }
        return mapper.decideInfo(id, decision, LocalDate.now(), n) == 0 ? Result.fail("결정 전인 청구가 아닙니다.")
                : Result.ok(DECISIONS.get(decision) + " 결정을 기록했습니다. 청구인에게 결정 내용을 통지하세요.", id);
    }

    /** 결정 기한이 2일 안으로 다가왔거나 지난 청구를 담당자에게 알린다 (매일 아침) */
    public int remindInfo() {
        int n = 0;
        for (Map<String, Object> i : times(mapper.infoDueSoon(LocalDate.now().plusDays(2)))) {
            if (i.get("assignee") != null) {
                notifications.notify((String) i.get("assignee"), "정보공개청구 " + i.get("no") + "의 결정 기한(" + i.get("due") + ")이 다가왔습니다.",
                        "/biz/info/" + i.get("id"));
                n++;
            }
        }
        return n;
    }

    /* ================================================================ 18·19. 비품 신청·지급 */

    public Result addSupplyItem(String name, String unit, Integer stock, LoginUser me) {
        if (!me.isAdmin()) {
            return Result.fail("비품 등록은 관리자만 할 수 있습니다.");
        }
        String n = trim(name), u = trim(unit);
        if (n.isEmpty() || n.length() > 100 || u.isEmpty() || u.length() > 20 || stock == null || stock < 0 || stock > 1_000_000) {
            return Result.fail("비품 이름(100자), 단위(예: 개·권·박스), 재고(0 이상)를 적으세요.");
        }
        long id = mapper.nextId();
        mapper.insertSupplyItem(id, n, u, stock);
        return Result.ok(n + "을(를) 등록했습니다.", id);
    }

    public Result addStock(long itemId, Integer qty, LoginUser me) {
        if (!me.isAdmin()) {
            return Result.fail("재고는 관리자만 늘릴 수 있습니다.");
        }
        if (qty == null || qty < 1 || qty > 100_000) {
            return Result.fail("들어온 수량을 1 이상으로 적으세요.");
        }
        return mapper.changeStock(itemId, qty) == 0 ? Result.fail("비품을 찾을 수 없습니다.") : Result.ok("재고를 " + qty + " 늘렸습니다.", itemId);
    }

    public List<Map<String, Object>> supplyItems() {
        return mapper.supplyItems();
    }

    public Result requestSupply(Long itemId, Integer qty, String note, LoginUser me) {
        Map<String, Object> item = itemId == null ? null : mapper.supplyItems().stream()
                .filter(i -> ((Number) i.get("id")).longValue() == itemId).findFirst().orElse(null);
        if (item == null) {
            return Result.fail("비품을 고르세요.");
        }
        if (qty == null || qty < 1 || qty > 1000) {
            return Result.fail("수량을 1~1,000으로 적으세요.");
        }
        String n = opt(note);
        if (n != null && n.length() > 200) {
            return Result.fail("쓰는 곳은 200자 이내로 적으세요.");
        }
        long id = mapper.nextId();
        mapper.insertSupplyReq(id, itemId, qty, me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept(), n, LocalDateTime.now());
        for (LoginUser a : users.list()) {
            if (a.isEnabled() && a.isAdmin()) {
                notifications.notify(a.getLoginId(), me.getUserName() + "님 비품 신청: " + item.get("name") + " " + qty + item.get("unit"), "/biz/supplies");
            }
        }
        return Result.ok(item.get("name") + " " + qty + item.get("unit") + "을(를) 신청했습니다.", id);
    }

    public List<Map<String, Object>> supplyReqs(LoginUser me, boolean all) {
        return times(mapper.supplyReqs(all && me.isAdmin() ? null : me.getLoginId()));
    }

    /** 지급하면 재고에서 뺀다. 재고가 모자라면 지급하지 않는다 */
    @Transactional
    public Result handleSupply(long reqId, boolean give, String note, LoginUser me) {
        if (!me.isAdmin()) {
            return Result.fail("비품 지급은 관리자만 할 수 있습니다.");
        }
        Map<String, Object> r = mapper.supplyReq(reqId);
        if (r == null || !"WAIT".equals(r.get("status"))) {
            return Result.fail("처리할 신청이 아닙니다.");
        }
        String n = opt(note);
        if (!give && n == null) {
            return Result.fail("반려하는 이유를 적으세요.");
        }
        long itemId = ((Number) r.get("itemId")).longValue();
        int qty = ((Number) r.get("qty")).intValue();
        if (give) {
            mapper.lockSupplyItem(itemId);
            if (mapper.changeStock(itemId, -qty) == 0) {
                return Result.fail("재고가 모자랍니다. 재고를 먼저 늘리세요.");
            }
        }
        mapper.handleSupplyReq(reqId, give ? "DONE" : "NO", n, me.getUserName(), LocalDateTime.now());
        notifications.notify((String) r.get("loginId"), "비품 신청(" + r.get("item") + " " + qty + r.get("unit") + ")을 "
                + (give ? "지급했습니다. 총무 담당에게서 받아 가세요." : "반려했습니다: " + n), "/biz/supplies");
        return Result.ok(give ? "지급하고 재고에서 뺐습니다." : "반려했습니다.", reqId);
    }

    /* ================================================================ 20·21. 업무 매뉴얼 (판 이력) */

    public Result saveManual(Long id, Integer version, String title, String body, LoginUser me) {
        String t = trim(title), b = trim(body);
        if (t.isEmpty() || t.length() > 200 || b.isEmpty() || b.length() > 1300) {
            return Result.fail("제목(200자)과 내용(1,300자)을 적으세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            long nid = mapper.nextId();
            mapper.insertManual(nid, me.dept().isEmpty() ? null : me.dept(), t, b, me.getUserName(), now);
            return Result.ok("매뉴얼을 만들었습니다.", nid);
        }
        return updateManual(id, version, t, b, me.getUserName(), now);
    }

    @Transactional
    Result updateManual(long id, Integer version, String t, String b, String by, LocalDateTime now) {
        Map<String, Object> m = mapper.manual(id);
        if (m == null) {
            return Result.fail("매뉴얼을 찾을 수 없습니다.");
        }
        int v = ((Number) m.get("version")).intValue();
        if (version == null || version != v) {
            return Result.fail("그사이 다른 사람이 고쳤습니다 (지금 " + v + "판). 새로 열어 다시 고치세요.");
        }
        mapper.keepManualRev(id);
        mapper.updateManual(id, v, t, b, by, now);
        return Result.ok((v + 1) + "판으로 저장했습니다. 이전 판은 이력에 남았습니다.", id);
    }

    public List<Map<String, Object>> manuals(String q) {
        return times(mapper.manuals(opt(q) == null ? null : "%" + trim(q) + "%"));
    }

    public Map<String, Object> manual(long id) {
        return times(mapper.manual(id));
    }

    public List<Map<String, Object>> manualRevs(long id) {
        return times(mapper.manualRevs(id));
    }

    /** 예전 판 내용으로 되돌린다 (새 판으로 저장되어 지금 내용도 이력에 남는다) */
    @Transactional
    public Result restoreManual(long id, int version, LoginUser me) {
        Map<String, Object> rev = mapper.manualRev(id, version);
        Map<String, Object> cur = mapper.manual(id);
        if (rev == null || cur == null) {
            return Result.fail("그 판을 찾을 수 없습니다.");
        }
        Result r = updateManual(id, ((Number) cur.get("version")).intValue(), (String) rev.get("title"), (String) rev.get("body"),
                me.getUserName(), LocalDateTime.now());
        return r.ok() ? Result.ok(version + "판 내용으로 되돌렸습니다.", id) : r;
    }

    /* ================================================================ 22. 경조사 알림 */

    @Transactional
    public Result postFamilyEvent(String kind, String title, String body, LocalDate day, boolean notifyAll, LoginUser me) {
        if (!FAMILY_KINDS.containsKey(kind)) {
            return Result.fail("경조사 종류를 고르세요.");
        }
        String t = trim(title), b = opt(body);
        if (t.isEmpty() || t.length() > 200 || (b != null && b.length() > 1000)) {
            return Result.fail("제목(200자)과 내용(1,000자)을 확인하세요.");
        }
        if (day == null || day.isBefore(LocalDate.now().minusDays(30)) || day.isAfter(LocalDate.now().plusDays(180))) {
            return Result.fail("날짜를 확인하세요.");
        }
        long id = mapper.nextId();
        mapper.insertFamilyEvent(id, kind, t, b, day, me.getLoginId(), me.getUserName(), LocalDateTime.now());
        if (notifyAll) {
            for (LoginUser u : users.list()) {
                if (u.isEnabled() && !u.getLoginId().equals(me.getLoginId())) {
                    notifications.notify(u.getLoginId(), "[" + FAMILY_KINDS.get(kind) + "] " + t, "/biz/family");
                }
            }
        }
        return Result.ok("경조사를 알렸습니다.", id);
    }

    public List<Map<String, Object>> familyEvents() {
        return times(mapper.familyEvents(LocalDate.now().minusDays(60)));
    }

    public Result deleteFamilyEvent(long id, LoginUser me) {
        String owner = mapper.familyEventOwner(id);
        if (owner == null) {
            return Result.fail("없는 글입니다.");
        }
        if (!owner.equals(me.getLoginId()) && !me.isAdmin()) {
            return Result.fail("올린 사람만 지울 수 있습니다.");
        }
        mapper.deleteFamilyEvent(id);
        return Result.ok("지웠습니다.", id);
    }

    /* ================================================================ 23. 칭찬 */

    public Result praise(String toId, String body, LoginUser me) {
        LoginUser to = toId == null ? null : users.findByLoginId(toId.trim());
        if (to == null || !to.isEnabled()) {
            return Result.fail("칭찬할 직원을 고르세요.");
        }
        if (to.getLoginId().equals(me.getLoginId())) {
            return Result.fail("자기 자신은 칭찬할 수 없습니다.");
        }
        String b = trim(body);
        if (b.length() < 5 || b.length() > 500) {
            return Result.fail("칭찬하는 내용을 5~500자로 적으세요.");
        }
        if (mapper.praisedRecently(me.getLoginId(), to.getLoginId(), LocalDateTime.now().minusDays(1)) > 0) {
            return Result.fail("같은 직원은 하루에 한 번만 칭찬할 수 있습니다.");
        }
        long id = mapper.nextId();
        mapper.insertPraise(id, me.getLoginId(), me.getUserName(), to.getLoginId(), to.getUserName(), b, LocalDateTime.now());
        notifications.notify(to.getLoginId(), me.getUserName() + "님이 칭찬했습니다: " + (b.length() > 40 ? b.substring(0, 40) + "…" : b), "/biz/praise");
        return Result.ok(to.getUserName() + "님을 칭찬했습니다.", id);
    }

    public List<Map<String, Object>> praises() {
        return times(mapper.praises());
    }

    public List<Map<String, Object>> praiseTop() {
        return mapper.praiseTop(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }
}
