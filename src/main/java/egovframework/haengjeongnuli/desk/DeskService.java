package egovframework.haengjeongnuli.desk;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.hr.service.DutyRequestVO;
import egovframework.haengjeongnuli.hr.service.DutyService;
import egovframework.haengjeongnuli.office.OfficeService;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 내 업무 도구.
 * 할 일 · 즐겨찾기 메뉴 · 쪽지(개인·부서 전체) · 업무 분장과 직원 찾기 · 민원 담당자 추천 · 상용 답변 문구 ·
 * 반복 민원 · 물품 대여 · 직원 설문(무기명) · 내 일정 달력 · 회의 참석자 알림 · 오늘 요약
 */
@Service
public class DeskService {

    public record Result(boolean ok, String message, Long id) {
        static Result ok(String m, Long id) { return new Result(true, m, id); }
        static Result fail(String m) { return new Result(false, m, null); }
    }

    /** 즐겨찾기로 고를 수 있는 메뉴 (경로는 이 목록 안에서만 받는다: 다른 주소를 끼워 넣지 못하게) */
    public record Menu(String path, String label) {}

    public static final List<Menu> MENUS = List.of(
            new Menu("/desk", "오늘 요약"),
            new Menu("/desk/todos", "할 일"),
            new Menu("/desk/calendar", "내 일정"),
            new Menu("/desk/messages", "쪽지"),
            new Menu("/desk/staff", "직원 찾기·업무 분장"),
            new Menu("/desk/loans", "물품 대여"),
            new Menu("/desk/surveys", "직원 설문"),
            new Menu("/desk/templates", "민원 답변 문구"),
            new Menu("/approval/draft", "기안하기"),
            new Menu("/approval?box=wait", "결재 대기 문서"),
            new Menu("/records", "기록물 등록대장"),
            new Menu("/civil", "민원 목록"),
            new Menu("/civil/new", "민원 접수"),
            new Menu("/civil/certs", "증명 발급대장"),
            new Menu("/finance?area=fin", "재정·회계"),
            new Menu("/finance/purchases", "구매·계약"),
            new Menu("/hr", "인사·근태"),
            new Menu("/hr/sheet", "근무 기록"),
            new Menu("/hr/trips", "출장 여비"),
            new Menu("/office/rooms", "회의실 예약"),
            new Menu("/office/duty", "당직"),
            new Menu("/office/weekly", "주간 업무 보고"),
            new Menu("/office/contacts", "비상연락망"),
            new Menu("/board", "게시판"),
            new Menu("/forms", "한글 서식함"),
            new Menu("/stats", "통계·보고"));

    static final int MAX_FAVORITES = 12;
    static final int MAX_OPTIONS = 8;
    /** 같은 민원인이 1년 안에 이만큼 넘게 내면 '반복 민원'으로 표시한다 (이번 것 포함) */
    static final int REPEAT_THRESHOLD = 3;

    private final DeskMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private egovframework.haengjeongnuli.biz.BizMapper bizMapper;
    private final UserService users;
    private final NotificationService notifications;
    private final OfficeService office;
    private final DutyService duty;
    private final CivilProcessService civil;

    public DeskService(DeskMapper mapper, UserService users, NotificationService notifications, OfficeService office,
                       DutyService duty, CivilProcessService civil) {
        this.mapper = mapper;
        this.users = users;
        this.notifications = notifications;
        this.office = office;
        this.duty = duty;
        this.civil = civil;
    }

    private static String trim(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    /* ================================================================ 할 일 */

    public Result addTodo(String title, LocalDate due, LoginUser me) {
        String t = trim(title);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("할 일을 200자 이내로 적으세요.");
        }
        if (due != null && (due.isBefore(LocalDate.now().minusYears(1)) || due.isAfter(LocalDate.now().plusYears(2)))) {
            return Result.fail("기한은 1년 전부터 2년 뒤 사이로 고르세요.");
        }
        if (mapper.openTodos(me.getLoginId()) >= 200) {
            return Result.fail("마치지 않은 할 일이 200개입니다. 끝난 일을 먼저 정리하세요.");
        }
        long id = mapper.nextTodoId();
        mapper.insertTodo(id, me.getLoginId(), t, due, LocalDateTime.now());
        return Result.ok("할 일을 적었습니다.", id);
    }

    /** 마치지 않은 일 + 최근 7일 안에 마친 일 */
    public List<Map<String, Object>> todos(LoginUser me) {
        List<Map<String, Object>> list = times(mapper.todos(me.getLoginId(), LocalDateTime.now().minusDays(7)));
        LocalDate today = LocalDate.now();
        for (Map<String, Object> t : list) {
            LocalDate d = (LocalDate) t.get("due");
            t.put("overdue", d != null && "N".equals(t.get("done")) && d.isBefore(today));
            t.put("today", d != null && d.equals(today));
        }
        return list;
    }

    public Result toggleTodo(long id, boolean done, LoginUser me) {
        int n = mapper.setTodoDone(id, me.getLoginId(), done ? "Y" : "N", done ? LocalDateTime.now() : null);
        return n == 0 ? Result.fail("할 일을 찾을 수 없습니다.") : Result.ok(done ? "마친 일로 표시했습니다." : "다시 할 일로 돌렸습니다.", id);
    }

    public Result deleteTodo(long id, LoginUser me) {
        return mapper.deleteTodo(id, me.getLoginId()) == 0 ? Result.fail("할 일을 찾을 수 없습니다.") : Result.ok("지웠습니다.", id);
    }

    /* ================================================================ 즐겨찾기 */

    public List<Map<String, Object>> favorites(LoginUser me) {
        return mapper.favorites(me.getLoginId());
    }

    @Transactional
    public Result saveFavorites(List<String> paths, LoginUser me) {
        Set<String> want = new LinkedHashSet<>(paths == null ? List.of() : paths);
        if (want.size() > MAX_FAVORITES) {
            return Result.fail("즐겨찾기는 " + MAX_FAVORITES + "개까지 고를 수 있습니다.");
        }
        Map<String, String> known = new HashMap<>();
        MENUS.forEach(m -> known.put(m.path(), m.label()));
        mapper.clearFavorites(me.getLoginId());
        int i = 0;
        for (String p : want) {
            if (known.containsKey(p)) {
                mapper.insertFavorite(me.getLoginId(), p, known.get(p), i++);
            }
        }
        return Result.ok("즐겨찾기 " + i + "개를 저장했습니다.", null);
    }

    /* ================================================================ 쪽지 */

    @Transactional
    public Result send(String toId, String body, LoginUser me) {
        String b = trim(body);
        if (b.isEmpty() || b.length() > 1300) {
            return Result.fail("내용을 1,300자 이내로 적으세요.");
        }
        LoginUser to = toId == null ? null : users.findByLoginId(toId.trim());
        if (to == null || !to.isEnabled()) {
            return Result.fail("받는 사람을 고르세요.");
        }
        if (to.getLoginId().equals(me.getLoginId())) {
            return Result.fail("자기 자신에게는 보낼 수 없습니다. 메모는 '할 일'에 적으세요.");
        }
        long id = deliver(to, b, me);
        return Result.ok(to.getUserName() + "님에게 쪽지를 보냈습니다.", id);
    }

    /** 우리 부서 직원 모두에게 (부서장 공지, 회식 안내 같은 것) */
    @Transactional
    public Result sendDept(String body, LoginUser me) {
        String b = trim(body);
        if (b.isEmpty() || b.length() > 1300) {
            return Result.fail("내용을 1,300자 이내로 적으세요.");
        }
        if (me.dept().isEmpty()) {
            return Result.fail("부서가 정해지지 않아 부서 쪽지를 보낼 수 없습니다.");
        }
        int n = 0;
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && me.dept().equals(u.dept()) && !u.getLoginId().equals(me.getLoginId())) {
                deliver(u, "[" + me.dept() + " 전체] " + b, me);
                n++;
            }
        }
        return n == 0 ? Result.fail("우리 부서에 받을 직원이 없습니다.") : Result.ok(me.dept() + " " + n + "명에게 보냈습니다.", null);
    }

    private long deliver(LoginUser to, String body, LoginUser me) {
        long id = mapper.nextMessageId();
        mapper.insertMessage(id, me.getLoginId(), me.getUserName(), to.getLoginId(), to.getUserName(),
                body.length() > 1300 ? body.substring(0, 1300) : body, LocalDateTime.now());
        notifications.notify(to.getLoginId(), me.getUserName() + "님의 쪽지: " + cut(body.replace('\n', ' '), 40), "/desk/messages/" + id);
        return id;
    }

    public List<Map<String, Object>> inbox(LoginUser me) {
        return times(mapper.inbox(me.getLoginId()));
    }

    public List<Map<String, Object>> sent(LoginUser me) {
        return times(mapper.sent(me.getLoginId()));
    }

    /** 보낸 사람·받는 사람만 볼 수 있다. 받는 사람이 열면 읽음 처리 */
    @Transactional
    public Map<String, Object> open(long id, LoginUser me) {
        Map<String, Object> m = times(mapper.message(id));
        if (m == null || (!me.getLoginId().equals(m.get("toId")) && !me.getLoginId().equals(m.get("fromId")))) {
            return null;
        }
        if (me.getLoginId().equals(m.get("toId")) && m.get("readAt") == null) {
            LocalDateTime now = LocalDateTime.now();
            mapper.markRead(id, me.getLoginId(), now);
            m.put("readAt", now);
        }
        return m;
    }

    public Result deleteMessage(long id, LoginUser me) {
        int n = mapper.deleteForTo(id, me.getLoginId()) + mapper.deleteForFrom(id, me.getLoginId());
        return n == 0 ? Result.fail("쪽지를 찾을 수 없습니다.") : Result.ok("쪽지를 지웠습니다.", id);
    }

    public int unread(LoginUser me) {
        return mapper.unreadMessages(me.getLoginId());
    }

    /* ================================================================ 업무 분장 · 직원 찾기 */

    public Map<String, String> dutyMap() {
        Map<String, String> m = new HashMap<>();
        for (Map<String, Object> r : mapper.duties()) {
            m.put((String) r.get("loginId"), (String) r.get("duties"));
        }
        return m;
    }

    /** 본인 또는 같은 부서를 맡은 관리자가 적는다. 쉼표로 나눈 업무 이름 */
    @Transactional
    public Result saveDuty(String loginId, String duties, LoginUser me) {
        String who = loginId == null || loginId.isBlank() ? me.getLoginId() : loginId.trim();
        if (!who.equals(me.getLoginId()) && !me.isAdmin()) {
            return Result.fail("자기 업무 분장만 고칠 수 있습니다.");
        }
        LoginUser u = users.findByLoginId(who);
        if (u == null) {
            return Result.fail("직원을 찾을 수 없습니다.");
        }
        List<String> items = new ArrayList<>();
        for (String s : trim(duties).split("[,\\n]")) {
            String t = s.trim();
            if (!t.isEmpty() && !items.contains(t)) {
                items.add(t);
            }
        }
        String joined = String.join(", ", items);
        if (joined.length() > 500) {
            return Result.fail("업무 분장은 500자 이내로 적으세요.");
        }
        mapper.deleteDuty(who);
        if (!joined.isEmpty()) {
            mapper.insertDuty(who, joined, LocalDateTime.now());
        }
        return Result.ok(u.getUserName() + "님의 업무 분장을 저장했습니다.", null);
    }

    public record Staff(LoginUser user, String duties) {}

    /** 이름·부서·직급·내선·업무로 찾는다. 검색어가 없으면 모두 */
    public List<Staff> staff(String q) {
        String k = trim(q).toLowerCase(Locale.ROOT);
        Map<String, String> duties = dutyMap();
        List<Staff> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (!u.isEnabled()) {
                continue;
            }
            String d = duties.getOrDefault(u.getLoginId(), "");
            String hay = String.join(" ", u.getUserName(), u.dept(), u.position(), u.getExtension() == null ? "" : u.getExtension(), d)
                    .toLowerCase(Locale.ROOT);
            if (k.isEmpty() || hay.contains(k)) {
                out.add(new Staff(u, d));
            }
        }
        out.sort(Comparator.comparing((Staff s) -> s.user().dept()).thenComparing(s -> s.user().getUserName()));
        return out;
    }

    public record Suggestion(LoginUser user, List<String> matched) {}

    /**
     * 민원 담당자 추천: 업무 분장에 적힌 낱말이 민원 제목·내용·분야에 나오는 직원.
     * 맞는 낱말이 많을수록, 긴 낱말이 맞을수록 앞에 둔다. 처리 중 민원이 적은 사람을 고르는 것은 담당자 몫이다.
     */
    public List<Suggestion> suggest(CivilComplaintVO c) {
        if (c == null) {
            return List.of();
        }
        String text = (c.getTitle() + " " + (c.getContent() == null ? "" : c.getContent()) + " "
                + (c.getCategory() == null ? "" : c.getCategory().getLabel())).toLowerCase(Locale.ROOT);
        Map<String, String> duties = dutyMap();
        List<Suggestion> out = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (LoginUser u : users.list()) {
            String d = duties.get(u.getLoginId());
            if (!u.isEnabled() || d == null) {
                continue;
            }
            List<String> hit = new ArrayList<>();
            int score = 0;
            for (String w : d.split(",")) {
                String k = w.trim().toLowerCase(Locale.ROOT);
                if (k.length() >= 2 && text.contains(k)) {
                    hit.add(w.trim());
                    score += 10 + k.length();
                }
            }
            if (!hit.isEmpty()) {
                int i = 0;
                while (i < scores.size() && scores.get(i) >= score) {
                    i++;
                }
                out.add(i, new Suggestion(u, hit));
                scores.add(i, score);
            }
        }
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    /* ================================================================ 상용 답변 문구 */

    public List<Map<String, Object>> templates(LoginUser me) {
        return mapper.templates(me.getLoginId(), me.dept().isEmpty() ? null : me.dept());
    }

    public Result addTemplate(String title, String body, boolean shared, LoginUser me) {
        String t = trim(title), b = trim(body);
        if (t.isEmpty() || t.length() > 100) {
            return Result.fail("문구 이름을 100자 이내로 적으세요.");
        }
        if (b.isEmpty() || b.length() > 1300) {
            return Result.fail("문구를 1,300자 이내로 적으세요.");
        }
        if (shared && me.dept().isEmpty()) {
            return Result.fail("부서가 없어 부서 공용으로 둘 수 없습니다.");
        }
        long id = mapper.nextTemplateId();
        mapper.insertTemplate(id, shared ? null : me.getLoginId(), shared ? me.dept() : null, t, b, LocalDateTime.now());
        return Result.ok((shared ? "부서 공용 " : "") + "문구를 저장했습니다.", id);
    }

    public Result deleteTemplate(long id, LoginUser me) {
        Map<String, Object> t = mapper.template(id);
        if (t == null) {
            return Result.fail("문구를 찾을 수 없습니다.");
        }
        boolean mine = me.getLoginId().equals(t.get("ownerId"));
        boolean deptShared = t.get("ownerId") == null && me.dept().equals(t.get("dept"));
        if (!mine && !deptShared && !me.isAdmin()) {
            return Result.fail("내 문구와 우리 부서 공용 문구만 지울 수 있습니다.");
        }
        mapper.deleteTemplate(id);
        return Result.ok("문구를 지웠습니다.", id);
    }

    /* ================================================================ 같은 민원인 · 반복 민원 */

    /** 같은 민원인(이름 + 휴대전화나 이메일)의 최근 1년 다른 민원. 보는 사람이 볼 수 있는 것만 */
    public List<Map<String, Object>> sameApplicant(CivilComplaintVO c, LoginUser me) {
        if (c == null || c.getApplicantName() == null || (blank(c.getMobile()) && blank(c.getEmail()))) {
            return List.of();
        }
        List<Map<String, Object>> rows = times(mapper.sameApplicant(c.getComplaintId(), c.getApplicantName(),
                blank(c.getMobile()) ? null : c.getMobile(), blank(c.getEmail()) ? null : c.getEmail(), LocalDateTime.now().minusYears(1)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            CivilComplaintVO other = civil.get(((Number) r.get("id")).longValue());
            if (other != null && civil.canView(other, me)) {
                r.put("statusLabel", other.getStatus() == null ? "" : other.getStatus().getLabel());
                r.put("statusBadge", other.getStatus() == null ? "badge-gray" : other.getStatus().getBadge());
                out.add(r);
            }
        }
        return out;
    }

    /** 반복 민원인가: 볼 수 있는지와 상관없이 건수로만 판단한다 (내용은 보여 주지 않는다) */
    public int sameApplicantCount(CivilComplaintVO c) {
        if (c == null || c.getApplicantName() == null || (blank(c.getMobile()) && blank(c.getEmail()))) {
            return 0;
        }
        return mapper.sameApplicant(c.getComplaintId(), c.getApplicantName(), blank(c.getMobile()) ? null : c.getMobile(),
                blank(c.getEmail()) ? null : c.getEmail(), LocalDateTime.now().minusYears(1)).size();
    }

    public static boolean isRepeat(int others) {
        return others + 1 >= REPEAT_THRESHOLD;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /* ================================================================ 물품 대여 */

    public List<Map<String, Object>> items() {
        List<Map<String, Object>> list = times(mapper.items());
        LocalDate today = LocalDate.now();
        for (Map<String, Object> i : list) {
            LocalDate due = (LocalDate) i.get("due");
            i.put("overdue", due != null && due.isBefore(today));
        }
        return list;
    }

    public Result addItem(String name, String place, LoginUser me) {
        if (!me.isAdmin()) {
            return Result.fail("물품 등록은 관리자만 할 수 있습니다.");
        }
        String n = trim(name), p = trim(place);
        if (n.isEmpty() || n.length() > 100 || p.length() > 200) {
            return Result.fail("물품 이름(100자)과 보관 장소(200자)를 적으세요.");
        }
        long id = mapper.nextItemId();
        mapper.insertItem(id, n, p.isEmpty() ? null : p, LocalDateTime.now());
        return Result.ok(n + "을(를) 등록했습니다.", id);
    }

    /** 빌리기: 오늘부터 30일 안에 돌려줄 날을 정한다 */
    @Transactional
    public Result borrow(Long itemId, String purpose, LocalDate due, LoginUser me) {
        if (itemId == null || mapper.lockItem(itemId) == null) {
            return Result.fail("빌릴 물품을 고르세요.");
        }
        String p = trim(purpose);
        if (p.isEmpty() || p.length() > 200) {
            return Result.fail("쓰는 곳을 200자 이내로 적으세요.");
        }
        LocalDate today = LocalDate.now();
        if (due == null || due.isBefore(today) || due.isAfter(today.plusDays(30))) {
            return Result.fail("돌려줄 날은 오늘부터 30일 안에서 고르세요.");
        }
        if (mapper.itemOut(itemId) > 0) {
            return Result.fail("이미 다른 사람이 빌려 갔습니다.");
        }
        long id = mapper.nextLoanId();
        mapper.insertLoan(id, itemId, me.getLoginId(), me.getUserName(), p, due, LocalDateTime.now());
        return Result.ok(due + "까지 빌렸습니다.", id);
    }

    @Transactional
    public Result giveBack(long loanId, LoginUser me) {
        Map<String, Object> l = mapper.loan(loanId);
        if (l == null || !"ON".equals(l.get("status"))) {
            return Result.fail("빌려 간 기록을 찾을 수 없습니다.");
        }
        if (!me.getLoginId().equals(l.get("loginId")) && !me.isAdmin()) {
            return Result.fail("빌린 사람이나 관리자만 반납 처리할 수 있습니다.");
        }
        mapper.returnLoan(loanId, LocalDateTime.now());
        return Result.ok("반납했습니다.", loanId);
    }

    /** 반납 기한이 지난 대여를 빌린 사람에게 알린다 (매일 아침) */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${app.desk.loan-cron:0 40 8 * * *}")
    public int remindOverdue() {
        int n = 0;
        for (Map<String, Object> l : times(mapper.overdueLoans(LocalDate.now()))) {
            notifications.notify((String) l.get("loginId"), "빌린 물품 '" + l.get("item") + "'의 반납 기한(" + l.get("due") + ")이 지났습니다.",
                    "/desk/loans");
            n++;
        }
        return n;
    }

    /* ================================================================ 직원 설문 (무기명) */

    public static List<String> options(Map<String, Object> s) {
        List<String> out = new ArrayList<>();
        if (s != null && s.get("options") != null) {
            for (String o : s.get("options").toString().split("\n")) {
                if (!o.isBlank()) {
                    out.add(o.trim());
                }
            }
        }
        return out;
    }

    @Transactional
    public Result addSurvey(String title, String question, String optionsText, LocalDate closes, LoginUser me) {
        String t = trim(title), q = trim(question);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("설문 제목을 200자 이내로 적으세요.");
        }
        if (q.isEmpty() || q.length() > 500) {
            return Result.fail("질문을 500자 이내로 적으세요.");
        }
        List<String> opts = new ArrayList<>();
        for (String o : trim(optionsText).split("\n")) {
            String s = o.trim();
            if (!s.isEmpty() && !opts.contains(s)) {
                if (s.length() > 100) {
                    return Result.fail("보기 하나는 100자 이내로 적으세요.");
                }
                opts.add(s);
            }
        }
        if (opts.size() < 2 || opts.size() > MAX_OPTIONS) {
            return Result.fail("보기를 한 줄에 하나씩 2~" + MAX_OPTIONS + "개 적으세요.");
        }
        LocalDate today = LocalDate.now();
        if (closes == null || closes.isBefore(today) || closes.isAfter(today.plusDays(60))) {
            return Result.fail("마감일은 오늘부터 60일 안에서 고르세요.");
        }
        long id = mapper.nextSurveyId();
        mapper.insertSurvey(id, t, q, String.join("\n", opts), closes, me.getLoginId(), LocalDateTime.now());
        for (int i = 0; i < opts.size(); i++) {
            mapper.insertCount(id, i + 1);
        }
        return Result.ok("설문을 올렸습니다.", id);
    }

    public List<Map<String, Object>> surveys(LoginUser me) {
        List<Map<String, Object>> list = times(mapper.surveys());
        LocalDate today = LocalDate.now();
        for (Map<String, Object> s : list) {
            long id = ((Number) s.get("id")).longValue();
            s.put("open", !((LocalDate) s.get("closes")).isBefore(today));
            s.put("voted", mapper.voted(id, me.getLoginId()) > 0);
        }
        return list;
    }

    public record SurveyView(Map<String, Object> survey, List<String> options, List<Integer> counts, int total, boolean open,
                             boolean voted) {}

    public SurveyView survey(long id, LoginUser me) {
        Map<String, Object> s = times(mapper.survey(id));
        if (s == null) {
            return null;
        }
        List<String> opts = options(s);
        List<Integer> counts = new ArrayList<>();
        Map<Integer, Integer> byNo = new HashMap<>();
        for (Map<String, Object> c : mapper.counts(id)) {
            byNo.put(((Number) c.get("no")).intValue(), ((Number) c.get("cnt")).intValue());
        }
        int total = 0;
        for (int i = 1; i <= opts.size(); i++) {
            int c = byNo.getOrDefault(i, 0);
            counts.add(c);
            total += c;
        }
        boolean open = !((LocalDate) s.get("closes")).isBefore(LocalDate.now());
        return new SurveyView(s, opts, counts, total, open, mapper.voted(id, me.getLoginId()) > 0);
    }

    /** 누가 했는지와 무엇을 골랐는지를 따로 적어 서로 잇지 못하게 한다 */
    @Transactional
    public Result vote(long id, Integer choice, LoginUser me) {
        SurveyView v = survey(id, me);
        if (v == null) {
            return Result.fail("설문을 찾을 수 없습니다.");
        }
        if (!v.open()) {
            return Result.fail("마감된 설문입니다.");
        }
        if (choice == null || choice < 1 || choice > v.options().size()) {
            return Result.fail("보기 하나를 고르세요.");
        }
        try {
            mapper.insertVoter(id, me.getLoginId(), LocalDateTime.now().withMinute(0).withSecond(0).withNano(0));
        } catch (DuplicateKeyException e) {
            return Result.fail("이미 참여했습니다.");
        }
        mapper.addVote(id, choice);
        return Result.ok("참여했습니다. 고른 보기는 누구에게도 남지 않습니다.", id);
    }

    /* ================================================================ 회의 참석자 */

    /** 예약한 사람만 바꾼다. 새로 들어간 참석자에게 알린다 */
    @Transactional
    public Result setAttendees(long resvId, List<String> ids, LoginUser me) {
        Map<String, Object> r = office.reservations(LocalDate.now().minusDays(1), LocalDate.now().plusDays(91)).stream()
                .filter(x -> ((Number) x.get("id")).longValue() == resvId).findFirst().orElse(null);
        if (r == null) {
            return Result.fail("예약을 찾을 수 없습니다.");
        }
        if (!me.getLoginId().equals(r.get("loginId"))) {
            return Result.fail("예약한 사람만 참석자를 정할 수 있습니다.");
        }
        Set<String> before = attendees(r);
        Set<String> after = new LinkedHashSet<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            LoginUser u = id == null ? null : users.findByLoginId(id.trim());
            if (u != null && u.isEnabled() && !u.getLoginId().equals(me.getLoginId())) {
                after.add(u.getLoginId());
            }
        }
        if (after.size() > 40) {
            return Result.fail("참석자는 40명까지 정할 수 있습니다.");
        }
        String joined = after.isEmpty() ? null : "," + String.join(",", after) + ",";
        if (joined != null && joined.length() > 1000) {
            return Result.fail("참석자가 너무 많습니다.");
        }
        mapper.setAttendees(resvId, joined);
        LocalDate day = date(r.get("day"));
        int told = 0;
        for (String id : after) {
            if (!before.contains(id)) {
                notifications.notify(id, me.getUserName() + "님이 회의 참석을 요청했습니다: " + r.get("title") + " (" + OfficeService.md(day) + " "
                        + r.get("start") + "~" + r.get("end") + ", " + r.get("roomName") + ")", "/office/rooms?date=" + day);
                told++;
            }
        }
        return Result.ok("참석자 " + after.size() + "명을 정했습니다." + (told > 0 ? " 새 참석자 " + told + "명에게 알렸습니다." : ""), resvId);
    }

    public static Set<String> attendees(Map<String, Object> resv) {
        Set<String> s = new LinkedHashSet<>();
        Object a = resv.get("attendees");
        if (a != null) {
            for (String x : a.toString().split(",")) {
                if (!x.isBlank()) {
                    s.add(x);
                }
            }
        }
        return s;
    }

    /* ================================================================ 내 일정 */

    public record Event(LocalDate day, String kind, String text, String link) {}

    /** 한 달 일정: 휴가·출장·시간외(결재 중·승인), 당직, 회의(예약·참석), 할 일 기한 */
    public Map<LocalDate, List<Event>> calendar(LoginUser me, YearMonth ym) {
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        Map<LocalDate, List<Event>> m = new TreeMap<>();
        for (int y : new LinkedHashSet<>(List.of(from.getYear(), from.minusMonths(1).getYear()))) {
            for (DutyRequestVO d : duty.list(me, y, 500)) {
                if (!"DONE".equals(d.getDocStatus()) && !"PROG".equals(d.getDocStatus())) {
                    continue;
                }
                LocalDate s = d.getStartDate(), e = d.getEndDate() == null ? s : d.getEndDate();
                if (s == null || e.isBefore(from) || s.isAfter(to)) {
                    continue;
                }
                String label = d.getKind().getLabel() + ("PROG".equals(d.getDocStatus()) ? " (결재 중)" : "")
                        + (d.getDestination() == null || d.getDestination().isBlank() ? "" : " · " + d.getDestination())
                        + (d.getStartTime() == null ? "" : " " + d.getStartTime() + (d.getEndTime() == null ? "" : "~" + d.getEndTime()));
                for (LocalDate x = s.isBefore(from) ? from : s; !x.isAfter(e) && !x.isAfter(to); x = x.plusDays(1)) {
                    add(m, new Event(x, "duty", label, d.getDocId() == null ? "/hr" : "/approval/" + d.getDocId()));
                }
            }
        }
        for (Map<String, Object> r : office.roster(from, to)) {
            if (me.getLoginId().equals(r.get("loginId"))) {
                LocalDate x = date(r.get("day"));
                add(m, new Event(x, "night", OfficeService.typeLabel((String) r.get("type")), "/office/duty"));
            }
        }
        for (Map<String, Object> r : office.reservations(from, to)) {
            boolean mine = me.getLoginId().equals(r.get("loginId"));
            if (mine || attendees(r).contains(me.getLoginId())) {
                LocalDate x = date(r.get("day"));
                add(m, new Event(x, "meet", r.get("start") + " " + r.get("title") + " (" + r.get("roomName") + (mine ? "" : ", 참석") + ")",
                        "/office/rooms?date=" + x));
            }
        }
        // 우리 부서 공용 일정
        if (bizMapper != null && !me.dept().isEmpty()) {
            for (Map<String, Object> e : bizMapper.deptEvents(me.dept(), from, to)) {
                LocalDate x = date(e.get("day"));
                add(m, new Event(x, "dept", "부서: " + e.get("title"), "/biz/events?ym=" + ym));
            }
        }
        for (Map<String, Object> t : todos(me)) {
            LocalDate x = (LocalDate) t.get("due");
            if (x != null && "N".equals(t.get("done")) && !x.isBefore(from) && !x.isAfter(to)) {
                add(m, new Event(x, "todo", "할 일: " + t.get("title"), "/desk/todos"));
            }
        }
        return m;
    }

    private static void add(Map<LocalDate, List<Event>> m, Event e) {
        m.computeIfAbsent(e.day(), k -> new ArrayList<>()).add(e);
    }

    /** 달력 칸: 첫 주 월요일부터 마지막 주 일요일까지 */
    public static List<List<LocalDate>> weeks(YearMonth ym) {
        LocalDate start = OfficeService.weekStart(ym.atDay(1));
        List<List<LocalDate>> weeks = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(ym.atEndOfMonth()); ) {
            List<LocalDate> w = new ArrayList<>();
            for (int i = 0; i < 7; i++, d = d.plusDays(1)) {
                w.add(d);
            }
            weeks.add(w);
        }
        return weeks;
    }

    /* ================================================================ 도움 */

    /** 날짜만 담는 열 (DB 에 따라 DATE 가 Timestamp 로 올 수 있다) */
    private static final Set<String> DATE_KEYS = Set.of("due", "closes", "day");

    static Map<String, Object> times(Map<String, Object> m) {
        if (m != null) {
            m.replaceAll((k, v) -> DATE_KEYS.contains(k) ? (v == null ? null : date(v))
                    : v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : v instanceof java.sql.Date d ? d.toLocalDate() : v);
        }
        return m;
    }

    static List<Map<String, Object>> times(List<Map<String, Object>> list) {
        list.forEach(DeskService::times);
        return list;
    }

    static LocalDate date(Object o) {
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (o instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        return o instanceof LocalDate l ? l : null;
    }

    /** 오늘 요약에 쓰는 '이번 주 당직' 같은 짧은 목록 */
    public List<Map<String, Object>> myRoster(LoginUser me, LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : office.roster(from, to)) {
            if (me.getLoginId().equals(r.get("loginId"))) {
                Map<String, Object> x = new LinkedHashMap<>(r);
                x.put("day", date(r.get("day")));
                x.put("typeLabel", OfficeService.typeLabel((String) r.get("type")));
                out.add(x);
            }
        }
        return out;
    }

    public List<Map<String, Object>> myMeetings(LoginUser me, LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : office.reservations(from, to)) {
            if (me.getLoginId().equals(r.get("loginId")) || attendees(r).contains(me.getLoginId())) {
                Map<String, Object> x = new LinkedHashMap<>(r);
                x.put("day", date(r.get("day")));
                out.add(x);
            }
        }
        return out;
    }
}
