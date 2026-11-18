package egovframework.haengjeongnuli.office;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.cmm.text.PhoneNumbers;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 공통 업무.
 * 회의실 예약 · 당직 편성/교대/근무일지 · 주간 업무 보고 · 업무 인계인수 · 법정의무교육 이수 · 비상연락망
 */
@Service
public class OfficeService {

    public record Result(boolean ok, String message, Long id) {
        static Result ok(String m, Long id) { return new Result(true, m, id); }
        static Result fail(String m) { return new Result(false, m, null); }
    }

    /** 당직 종류: 평일·휴일 밤 숙직, 휴일 낮 일직 */
    public static final String NIGHT = "NIGHT";
    public static final String DAY = "DAY";

    private final OfficeMapper mapper;
    private final UserService users;
    private final HolidayService holidays;
    private final NotificationService notifications;

    public OfficeService(OfficeMapper mapper, UserService users, HolidayService holidays, NotificationService notifications) {
        this.mapper = mapper;
        this.users = users;
        this.holidays = holidays;
        this.notifications = notifications;
    }

    private static String trim(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    /* ================================================================ 회의실 */

    public List<Map<String, Object>> rooms(boolean onlyUsed) {
        return mapper.rooms(onlyUsed);
    }

    @Transactional
    public Result addRoom(String name, String place, Integer capacity, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 회의실을 등록할 수 있습니다.");
        }
        String n = trim(name);
        if (n.isEmpty() || n.length() > 100) {
            return Result.fail("회의실 이름을 100자 이내로 적으세요.");
        }
        if (capacity != null && (capacity < 1 || capacity > 9999)) {
            return Result.fail("수용 인원을 확인하세요.");
        }
        if (mapper.roomNameUsed(n) > 0) {
            return Result.fail("같은 이름의 회의실이 있습니다.");
        }
        long id = mapper.nextRoomId();
        String p = trim(place);
        mapper.insertRoom(id, n, p.isEmpty() ? null : p.substring(0, Math.min(200, p.length())), capacity, LocalDateTime.now());
        return Result.ok("회의실 '" + n + "'을(를) 등록했습니다.", id);
    }

    @Transactional
    public Result setRoomUse(long id, boolean use, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 바꿀 수 있습니다.");
        }
        if (mapper.setRoomUse(id, use ? "Y" : "N") != 1) {
            return Result.fail("회의실을 찾을 수 없습니다.");
        }
        if (use) {
            return Result.ok("다시 예약을 받습니다.", id);
        }
        // 이미 잡힌 예약은 지우지 않고, 예약한 사람에게 다른 방을 찾도록 알린다
        Map<String, Object> room = mapper.room(id);
        List<Map<String, Object>> left = mapper.futureResvs(id, LocalDate.now());
        for (Map<String, Object> r : left) {
            notifications.notify((String) r.get("loginId"), room.get("name") + " 회의실을 쓸 수 없게 되었습니다. " + date(r.get("day")) + " "
                    + r.get("start") + " 예약을 확인하고 다른 방을 잡으세요.", "/office/rooms?date=" + date(r.get("day")));
        }
        return Result.ok("예약을 받지 않게 했습니다." + (left.isEmpty() ? "" : " 남은 예약 " + left.size() + "건의 예약자에게 알렸습니다."), id);
    }

    /** 30분 단위, 07:00~22:00, 오늘부터 90일 안 */
    @Transactional
    public Result reserve(Long roomId, LocalDate day, String start, String end, String title, LoginUser me) {
        Map<String, Object> room = roomId == null ? null : mapper.room(roomId);
        if (room == null || !"Y".equals(room.get("useYn"))) {
            return Result.fail("회의실을 고르세요.");
        }
        if (day == null || day.isBefore(LocalDate.now()) || day.isAfter(LocalDate.now().plusDays(90))) {
            return Result.fail("날짜는 오늘부터 90일 안에서 고르세요.");
        }
        LocalTime s, e;
        try {
            s = LocalTime.parse(start);
            e = LocalTime.parse(end);
        } catch (DateTimeParseException | NullPointerException ex) {
            return Result.fail("시작·끝 시각을 고르세요.");
        }
        if (s.getMinute() % 30 != 0 || e.getMinute() % 30 != 0) {
            return Result.fail("30분 단위로 예약하세요.");
        }
        if (s.isBefore(LocalTime.of(7, 0)) || e.isAfter(LocalTime.of(22, 0)) || !e.isAfter(s)) {
            return Result.fail("07:00~22:00 사이에서 끝 시각이 시작보다 늦게 고르세요.");
        }
        if (day.equals(LocalDate.now()) && s.isBefore(LocalTime.now().withSecond(0).withNano(0).minusMinutes(29))) {
            return Result.fail("이미 지난 시각은 예약할 수 없습니다.");
        }
        String t = trim(title);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("회의 이름을 200자 이내로 적으세요.");
        }
        mapper.lockRoom(roomId); // 같은 방 동시 예약은 한 사람씩 확인한다
        if (mapper.overlap(roomId, day, s.toString(), e.toString()) > 0) {
            return Result.fail("그 시간에 이미 예약이 있습니다. 예약 현황을 보고 다른 시간을 고르세요.");
        }
        long id = mapper.nextResvId();
        mapper.insertResv(id, roomId, day, s.toString(), e.toString(), t, me.getLoginId(), me.getUserName(), me.getDeptName(),
                LocalDateTime.now());
        return Result.ok(room.get("name") + " " + day + " " + s + "~" + e + " 예약했습니다.", id);
    }

    public List<Map<String, Object>> reservations(LocalDate from, LocalDate to) {
        return mapper.resvs(from, to);
    }

    @Transactional
    public Result cancel(long id, LoginUser me) {
        Map<String, Object> r = mapper.resv(id);
        if (r == null) {
            return Result.fail("예약을 찾을 수 없습니다.");
        }
        if (!me.getLoginId().equals(r.get("loginId")) && !me.isAdmin()) {
            return Result.fail("예약한 사람과 관리자만 취소할 수 있습니다.");
        }
        if (date(r.get("day")).isBefore(LocalDate.now())) {
            return Result.fail("지난 예약은 기록으로 남기므로 취소할 수 없습니다.");
        }
        mapper.deleteResv(id);
        if (!me.getLoginId().equals(r.get("loginId"))) {
            notifications.notify((String) r.get("loginId"), "관리자가 회의실 예약(" + date(r.get("day")) + " " + r.get("start") + ")을 취소했습니다.",
                    "/office/rooms");
        }
        return Result.ok("예약을 취소했습니다.", id);
    }

    /* ================================================================ 당직 */

    /** 당직을 서는 사람: 사용 중인 직원 가운데 시스템 관리자 계정 빼고 */
    private List<LoginUser> dutyPool() {
        List<LoginUser> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && !u.isAdmin()) {
                out.add(u);
            }
        }
        return out;
    }

    /**
     * 기간 당직을 자동으로 짠다 (관리자). 평일은 숙직 1명, 토·일·공휴일은 일직 1명 + 숙직 1명.
     * 당직을 적게 선 사람·오래전에 선 사람부터, 그날 휴가·출장(결재 중 포함)인 사람과 전날 당직자는 뺀다.
     * 이미 짠 날은 건너뛴다 (다시 짜려면 '근무일지 없는 편성 지우기' 뒤 다시).
     */
    @Transactional
    public Result buildRoster(LocalDate from, LocalDate to, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 당직을 편성할 수 있습니다.");
        }
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            return Result.fail("기간을 92일 안으로 고르세요.");
        }
        List<LoginUser> pool = dutyPool();
        if (pool.size() < 3) {
            return Result.fail("당직을 설 직원이 3명 이상 있어야 편성할 수 있습니다.");
        }
        Map<String, Integer> count = new HashMap<>();
        Map<String, LocalDate> last = new HashMap<>();
        for (Map<String, Object> m : mapper.rosterStats()) {
            count.put((String) m.get("loginId"), ((Number) m.get("cnt")).intValue());
            last.put((String) m.get("loginId"), date(m.get("last")));
        }
        int made = 0;
        List<String> empty = new ArrayList<>();
        Set<String> yesterday = new HashSet<>();
        for (Map<String, Object> m : mapper.roster(from.minusDays(1), from.minusDays(1))) {
            yesterday.add((String) m.get("loginId"));
        }
        LocalDateTime now = LocalDateTime.now();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean holiday = !holidays.isWorkingDay(d);
            List<String> types = holiday ? List.of(DAY, NIGHT) : List.of(NIGHT);
            Set<String> away = new HashSet<>(mapper.awayOn(d));
            Set<String> today = new HashSet<>();
            for (Map<String, Object> m : mapper.roster(d, d)) {
                today.add((String) m.get("loginId"));
            }
            final Set<String> prev = yesterday;
            for (String type : types) {
                if (mapper.rosterExists(d, type) > 0) {
                    continue;
                }
                final LocalDate day = d;
                LoginUser pick = pool.stream()
                        .filter(u -> !away.contains(u.getLoginId()) && !today.contains(u.getLoginId()) && !prev.contains(u.getLoginId()))
                        .min(Comparator.<LoginUser>comparingInt(u -> count.getOrDefault(u.getLoginId(), 0))
                                .thenComparing(u -> last.getOrDefault(u.getLoginId(), LocalDate.MIN))
                                .thenComparing(LoginUser::getLoginId))
                        .orElse(null);
                if (pick == null) {
                    empty.add(d + " " + (DAY.equals(type) ? "일직" : "숙직"));
                    continue; // 설 사람이 없으면 비워 두고 알린다
                }
                try {
                    mapper.insertRoster(mapper.nextRosterId(), d, type, pick.getLoginId(), pick.getUserName(), pick.getDeptName(),
                            me.getLoginId(), now);
                } catch (DuplicateKeyException ex) {
                    continue; // 다른 관리자가 같은 때 같은 날을 짰다
                }
                count.merge(pick.getLoginId(), 1, Integer::sum);
                last.put(pick.getLoginId(), day);
                today.add(pick.getLoginId());
                notifications.notify(pick.getLoginId(), d + " " + typeLabel(type) + " 당직이 편성되었습니다.", "/office/duty?month=" + d.toString().substring(0, 7));
                made++;
            }
            yesterday = today;
        }
        String gap = empty.isEmpty() ? "" : " 설 사람이 없어 비운 자리: " + String.join(", ", empty.subList(0, Math.min(5, empty.size())))
                + (empty.size() > 5 ? " 외 " + (empty.size() - 5) + "건" : "") + " (휴가·출장이 많은 날입니다).";
        return Result.ok((made == 0 ? "새로 편성할 날이 없습니다 (이미 편성됨)." : "당직 " + made + "건을 편성하고 당직자에게 알렸습니다.") + gap, null);
    }

    @Transactional
    public Result clearRoster(LocalDate from, LocalDate to, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 지울 수 있습니다.");
        }
        if (from == null || to == null || to.isBefore(from)) {
            return Result.fail("기간을 고르세요.");
        }
        LocalDate f = from.isBefore(LocalDate.now()) ? LocalDate.now() : from;
        int n = mapper.clearRoster(f, to);
        return Result.ok("오늘 이후 근무일지가 없는 편성 " + n + "건을 지웠습니다.", null);
    }

    public List<Map<String, Object>> roster(LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = mapper.roster(from, to);
        for (Map<String, Object> m : out) {
            m.put("typeLabel", typeLabel((String) m.get("type")));
            m.put("day", date(m.get("day")));
        }
        return out;
    }

    public static String typeLabel(String type) {
        return DAY.equals(type) ? "일직 (09:00~18:00)" : "숙직 (18:00~다음 날 09:00)";
    }

    /**
     * 교대 신청: 내 당직을 다른 직원에게 부탁한다. backRosterId 를 주면 그 사람의 당직과 맞바꾼다.
     * 상대가 받아들이면 바뀌고, 둘 다에게 알린다.
     */
    @Transactional
    public Result requestSwap(long rosterId, String toId, Long backRosterId, String reason, LoginUser me) {
        Map<String, Object> r = mapper.rosterOne(rosterId);
        if (r == null || !me.getLoginId().equals(r.get("loginId"))) {
            return Result.fail("본인의 당직만 교대를 신청할 수 있습니다.");
        }
        if (!date(r.get("day")).isAfter(LocalDate.now())) {
            return Result.fail("내일 이후 당직만 교대를 신청할 수 있습니다.");
        }
        LoginUser to = toId == null ? null : users.findByLoginId(toId);
        if (to == null || !to.isEnabled() || to.getLoginId().equals(me.getLoginId())) {
            return Result.fail("교대할 직원을 고르세요.");
        }
        if (backRosterId != null) {
            Map<String, Object> b = mapper.rosterOne(backRosterId);
            if (b == null || !to.getLoginId().equals(b.get("loginId")) || !date(b.get("day")).isAfter(LocalDate.now())) {
                return Result.fail("맞바꿀 당직은 상대 직원의 내일 이후 당직이어야 합니다.");
            }
        }
        String rs = trim(reason);
        if (rs.isEmpty() || rs.length() > 300) {
            return Result.fail("교대 사유를 300자 이내로 적으세요.");
        }
        if (mapper.openSwapFor(rosterId) > 0) {
            return Result.fail("이 당직은 교대 신청이 이미 기다리는 중입니다.");
        }
        long id = mapper.nextSwapId();
        mapper.insertSwap(id, rosterId, me.getLoginId(), to.getLoginId(), backRosterId, rs, LocalDateTime.now());
        notifications.notify(to.getLoginId(), me.getUserName() + " 님이 " + date(r.get("day")) + " 당직 교대를 부탁했습니다.", "/office/duty");
        return Result.ok(to.getUserName() + " 님에게 교대를 신청했습니다. 받아들이면 바뀝니다.", id);
    }

    @Transactional
    public Result decideSwap(long swapId, boolean accept, LoginUser me) {
        Map<String, Object> s = mapper.swap(swapId);
        if (s == null || !"REQ".equals(s.get("status"))) {
            return Result.fail("기다리는 교대 신청이 아닙니다.");
        }
        boolean isTo = me.getLoginId().equals(s.get("toId"));
        boolean isFrom = me.getLoginId().equals(s.get("fromId"));
        if (!isTo && !(isFrom && !accept)) {
            return Result.fail("부탁받은 직원만 받아들일 수 있습니다 (신청한 사람은 철회만).");
        }
        long rosterId = ((Number) s.get("rosterId")).longValue();
        Map<String, Object> r = mapper.rosterOne(rosterId);
        if (accept) {
            String why = swapProblem(s, r);
            if (why != null) {
                mapper.decideSwap(swapId, "NO", LocalDateTime.now());
                return Result.fail(why);
            }
        }
        if (mapper.decideSwap(swapId, accept ? "OK" : (isFrom ? "CANCEL" : "NO"), LocalDateTime.now()) != 1) {
            return Result.fail("이미 처리된 신청입니다.");
        }
        LoginUser from = users.findByLoginId((String) s.get("fromId"));
        LoginUser to = users.findByLoginId((String) s.get("toId"));
        if (!accept) {
            notifications.notify(isFrom ? to.getLoginId() : from.getLoginId(),
                    (isFrom ? from.getUserName() + " 님이 당직 교대 신청을 철회했습니다." : to.getUserName() + " 님이 당직 교대를 받아들이지 않았습니다."),
                    "/office/duty");
            return Result.ok(isFrom ? "신청을 철회했습니다." : "교대를 거절했습니다.", swapId);
        }
        mapper.reassign(rosterId, to.getLoginId(), to.getUserName(), to.getDeptName());
        Object back = s.get("backId");
        if (back != null) {
            mapper.reassign(((Number) back).longValue(), from.getLoginId(), from.getUserName(), from.getDeptName());
        }
        notifications.notify(from.getLoginId(), to.getUserName() + " 님이 " + date(r.get("day")) + " 당직 교대를 받아들였습니다.", "/office/duty");
        return Result.ok("당직을 교대했습니다.", swapId);
    }

    /** 교대를 받아들일 수 없는 까닭 (없으면 null) */
    private String swapProblem(Map<String, Object> s, Map<String, Object> r) {
        if (r == null || !s.get("fromId").equals(r.get("loginId"))) {
            return "그 사이에 당직이 바뀌어 교대할 수 없습니다.";
        }
        LocalDate day = date(r.get("day"));
        if (!day.isAfter(LocalDate.now())) {
            return "이미 지났거나 오늘인 당직은 교대할 수 없습니다. 관리자에게 바꿔 달라고 하세요.";
        }
        String toId = (String) s.get("toId");
        long rid = ((Number) r.get("id")).longValue();
        Object back = s.get("backId");
        long backId = back == null ? -1 : ((Number) back).longValue();
        if (mapper.awayOn(day).contains(toId)) {
            return "그날 휴가·출장이 있어 교대를 받을 수 없습니다.";
        }
        if (back != null) {
            Map<String, Object> b = mapper.rosterOne(backId);
            if (b == null || !toId.equals(b.get("loginId")) || !date(b.get("day")).isAfter(LocalDate.now())) {
                return "맞바꾸기로 한 당직이 그 사이에 바뀌었습니다.";
            }
            LocalDate bd = date(b.get("day"));
            if (mapper.awayOn(bd).contains((String) s.get("fromId"))) {
                return "신청한 직원이 맞바꿀 날에 휴가·출장이 있습니다.";
            }
        }
        // 받는 사람이 그 날 앞뒤로 다른 당직이 있으면 (맞바꿀 당직은 빼고) 잇달아 서게 되므로 막는다
        if (mapper.dutyNear(toId, day.minusDays(1), day.plusDays(1), backId) > 0) {
            return "받는 직원이 그날 앞뒤로 당직이 있어 잇달아 서게 됩니다.";
        }
        return null;
    }

    public List<Map<String, Object>> openSwaps(LoginUser me) {
        List<Map<String, Object>> out = mapper.openSwaps(me.getLoginId());
        for (Map<String, Object> m : out) {
            m.put("day", date(m.get("day")));
            m.put("typeLabel", typeLabel((String) m.get("type")));
            if (m.get("backDay") != null) {
                m.put("backDay", date(m.get("backDay")));
                m.put("backTypeLabel", typeLabel((String) m.get("backType")));
            }
            LoginUser f = users.findByLoginId((String) m.get("fromId"));
            LoginUser t = users.findByLoginId((String) m.get("toId"));
            m.put("fromName", f == null ? m.get("fromId") : f.getUserName());
            m.put("toName", t == null ? m.get("toId") : t.getUserName());
            m.put("mine", me.getLoginId().equals(m.get("fromId")));
        }
        return out;
    }

    /** 당직 근무일지: 그날 당직자만, 당직 날부터 이틀 안에 */
    @Transactional
    public Result writeLog(long rosterId, String log, LoginUser me) {
        Map<String, Object> r = mapper.rosterOne(rosterId);
        if (r == null || !me.getLoginId().equals(r.get("loginId"))) {
            return Result.fail("그날 당직자만 근무일지를 쓸 수 있습니다.");
        }
        LocalDate d = date(r.get("day"));
        if (d.isAfter(LocalDate.now()) || d.isBefore(LocalDate.now().minusDays(2))) {
            return Result.fail("근무일지는 당직 날부터 이틀 안에 씁니다.");
        }
        String l = trim(log);
        if (l.isEmpty() || l.length() > 1300) {
            return Result.fail("근무 내용을 1,300자 이내로 적으세요 (순찰·전화·특이사항 등).");
        }
        mapper.writeLog(rosterId, l, LocalDateTime.now());
        return Result.ok("근무일지를 저장했습니다.", rosterId);
    }

    /* ================================================================ 주간 업무 보고 */

    public static LocalDate weekStart(LocalDate d) {
        return (d == null ? LocalDate.now() : d).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public Map<String, Object> myReport(LoginUser me, LocalDate week) {
        return times(mapper.report(me.getLoginId(), weekStart(week)));
    }

    @Transactional
    public Result saveReport(LocalDate week, String done, String plan, String issue, LoginUser me) {
        LocalDate w = weekStart(week);
        if (w.isAfter(weekStart(LocalDate.now()))) {
            return Result.fail("다음 주 보고는 미리 쓸 수 없습니다.");
        }
        if (w.isBefore(weekStart(LocalDate.now()).minusWeeks(4))) {
            return Result.fail("4주가 지난 보고는 고칠 수 없습니다.");
        }
        String d = trim(done), p = trim(plan), i = trim(issue);
        if (d.isEmpty() || d.length() > 1300 || p.isEmpty() || p.length() > 1300) {
            return Result.fail("이번 주 한 일과 다음 주 할 일을 각각 1,300자 이내로 적으세요.");
        }
        if (i.length() > 1000) {
            return Result.fail("건의·협조 사항은 1,000자 이내로 적으세요.");
        }
        Map<String, Object> cur = mapper.report(me.getLoginId(), w);
        LocalDateTime now = LocalDateTime.now();
        if (cur == null) {
            try {
                mapper.insertReport(mapper.nextReportId(), me.getLoginId(), me.getUserName(), me.getDeptName(), w, d, p,
                        i.isEmpty() ? null : i, now);
            } catch (DuplicateKeyException e) {
                return Result.fail("같은 주 보고가 이미 있습니다. 다시 열어 고치세요.");
            }
        } else {
            mapper.updateReport(((Number) cur.get("id")).longValue(), me.getDeptName(), d, p, i.isEmpty() ? null : i, now);
        }
        return Result.ok(w + " 주간 업무 보고를 저장했습니다.", null);
    }

    /** 부서 취합: 같은 부서만 (관리자는 아무 부서) */
    public List<Map<String, Object>> deptReports(LoginUser me, String dept, LocalDate week) {
        String d = me.isAdmin() && dept != null && !dept.isBlank() ? dept : me.dept();
        return times(mapper.deptReports(d, weekStart(week)));
    }

    /** 부서 사람 가운데 아직 안 쓴 사람 */
    public List<String> missingReports(LoginUser me, String dept, LocalDate week) {
        String d = me.isAdmin() && dept != null && !dept.isBlank() ? dept : me.dept();
        Set<String> wrote = new HashSet<>();
        for (Map<String, Object> m : mapper.deptReports(d, weekStart(week))) {
            wrote.add((String) m.get("loginId"));
        }
        List<String> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && d.equals(u.dept()) && !wrote.contains(u.getLoginId())) {
                out.add(u.getUserName());
            }
        }
        return out;
    }

    /* ================================================================ 업무 인계인수 */

    @Transactional
    public Result handover(String toId, String title, String tasks, String docs, String notes, LoginUser me) {
        LoginUser to = toId == null ? null : users.findByLoginId(toId);
        if (to == null || !to.isEnabled() || to.getLoginId().equals(me.getLoginId())) {
            return Result.fail("업무를 넘겨받을 직원을 고르세요.");
        }
        String t = trim(title), k = trim(tasks), d = trim(docs), n = trim(notes);
        if (t.isEmpty() || t.length() > 200) {
            return Result.fail("제목을 200자 이내로 적으세요 (예: 세외수입 업무 인계).");
        }
        if (k.isEmpty() || k.length() > 1300 || d.length() > 1300 || n.length() > 1300) {
            return Result.fail("진행 중인 업무를 적고, 각 칸은 1,300자 이내로 적으세요.");
        }
        long id = mapper.nextHandoverId();
        mapper.insertHandover(id, me.getLoginId(), me.getUserName(), to.getLoginId(), to.getUserName(), me.getDeptName(), t, k,
                d.isEmpty() ? null : d, n.isEmpty() ? null : n, LocalDateTime.now());
        notifications.notify(to.getLoginId(), me.getUserName() + " 님이 업무 인계서를 보냈습니다: " + t, "/office/handovers/" + id);
        return Result.ok(to.getUserName() + " 님에게 인계서를 보냈습니다. 인수 확인을 받으면 끝납니다.", id);
    }

    public List<Map<String, Object>> handovers(LoginUser me) {
        return times(mapper.handovers(me.getLoginId()));
    }

    /** 보낸 사람·받은 사람·관리자만 */
    public Map<String, Object> handoverFor(long id, LoginUser me) {
        Map<String, Object> h = times(mapper.handover(id));
        if (h == null) {
            return null;
        }
        boolean party = me.getLoginId().equals(h.get("fromId")) || me.getLoginId().equals(h.get("toId"));
        return party || me.isAdmin() ? h : null;
    }

    /** 인수 확인 전이면 보낸 사람이 회수(삭제)할 수 있다 */
    @Transactional
    public Result withdrawHandover(long id, LoginUser me) {
        Map<String, Object> h = mapper.handover(id);
        if (h == null || !me.getLoginId().equals(h.get("fromId"))) {
            return Result.fail("보낸 사람만 회수할 수 있습니다.");
        }
        if (mapper.withdrawHandover(id, me.getLoginId()) != 1) {
            return Result.fail("이미 인수 확인한 인계서는 회수할 수 없습니다.");
        }
        notifications.notify((String) h.get("toId"), me.getUserName() + " 님이 인계서를 회수했습니다: " + h.get("title"), "/office/handovers");
        return Result.ok("인계서를 회수했습니다.", id);
    }

    @Transactional
    public Result acceptHandover(long id, String note, LoginUser me) {
        Map<String, Object> h = mapper.handover(id);
        if (h == null || !me.getLoginId().equals(h.get("toId"))) {
            return Result.fail("넘겨받는 직원만 인수 확인을 할 수 있습니다.");
        }
        String n = trim(note);
        if (n.length() > 500) {
            return Result.fail("인수 의견은 500자 이내로 적으세요.");
        }
        if (mapper.acceptHandover(id, n.isEmpty() ? null : n, LocalDateTime.now()) != 1) {
            return Result.fail("이미 인수 확인한 인계서입니다.");
        }
        notifications.notify((String) h.get("fromId"), me.getUserName() + " 님이 인계서를 인수 확인했습니다: " + h.get("title"),
                "/office/handovers/" + id);
        return Result.ok("인수 확인했습니다.", id);
    }

    /* ================================================================ 법정의무교육 */

    /** 처음 열 때 기본 과목을 넣는다. 이수 시간은 기관 지침에 맞게 관리자가 고친다 */
    @Transactional
    public void seedCourses() {
        if (mapper.courseCount() > 0) {
            return;
        }
        Object[][] base = {
            {"청렴교육", 2.0, "부패방지 교육 운영 지침 (국민권익위원회): 연 2시간 이상"},
            {"장애인 인식개선 교육", 1.0, "장애인복지법 제25조: 연 1회 1시간 이상"},
            {"폭력예방교육 (성희롱·성폭력·성매매·가정폭력)", 1.0, "양성평등기본법 등 관련 법령 (기관 지침 확인)"},
            {"개인정보보호 교육", 1.0, "개인정보 보호법 제28조 (기관 지침 확인)"},
        };
        int i = 0;
        for (Object[] c : base) {
            mapper.insertCourse(mapper.nextCourseId(), (String) c[0], (Double) c[1], (String) c[2], i++);
        }
    }

    public List<Map<String, Object>> courses(boolean onlyUsed) {
        seedCourses();
        return mapper.courses(onlyUsed);
    }

    @Transactional
    public Result addCourse(String name, Double hours, String basis, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 과목을 정할 수 있습니다.");
        }
        String n = trim(name), b = trim(basis);
        if (n.isEmpty() || n.length() > 100 || hours == null || hours <= 0 || hours > 100 || b.length() > 200) {
            return Result.fail("과목 이름(100자)과 연간 이수 시간(0보다 크게)을 적으세요.");
        }
        if (mapper.courseNameUsed(n) > 0) {
            return Result.fail("같은 이름의 과목이 있습니다.");
        }
        long id = mapper.nextCourseId();
        mapper.insertCourse(id, n, hours, b.isEmpty() ? null : b, 100);
        return Result.ok("과목을 추가했습니다.", id);
    }

    @Transactional
    public Result setCourseUse(long id, boolean use, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 바꿀 수 있습니다.");
        }
        return mapper.setCourseUse(id, use ? "Y" : "N") == 1 ? Result.ok("바꿨습니다.", id) : Result.fail("과목을 찾을 수 없습니다.");
    }

    /** 이수 등록: 본인 것만, 올해·작년만 */
    @Transactional
    public Result addEdu(Long courseId, LocalDate on, Double hours, String provider, LoginUser me) {
        seedCourses();
        boolean ok = false;
        for (Map<String, Object> c : mapper.courses(true)) {
            if (courseId != null && ((Number) c.get("id")).longValue() == courseId) {
                ok = true;
            }
        }
        if (!ok) {
            return Result.fail("교육 과목을 고르세요.");
        }
        if (on == null || on.isAfter(LocalDate.now()) || on.getYear() < LocalDate.now().getYear() - 1) {
            return Result.fail("이수한 날을 올해나 작년 안에서 적으세요.");
        }
        if (hours == null || hours <= 0 || hours > 100) {
            return Result.fail("이수 시간을 적으세요 (예: 1, 2.5).");
        }
        String p = trim(provider);
        if (p.length() > 100) {
            return Result.fail("교육 기관은 100자 이내로 적으세요.");
        }
        if (mapper.eduSameDay(me.getLoginId(), courseId, on) > 0) {
            return Result.fail("같은 날 같은 과목을 이미 등록했습니다. 시간을 바꾸려면 기록을 지우고 다시 등록하세요.");
        }
        long id = mapper.nextEduId();
        mapper.insertEdu(id, courseId, me.getLoginId(), on.getYear(), on, hours, p.isEmpty() ? null : p, LocalDateTime.now());
        return Result.ok("이수 기록을 등록했습니다.", id);
    }

    @Transactional
    public Result deleteEdu(long id, LoginUser me) {
        Map<String, Object> r = mapper.eduRecord(id);
        if (r == null || !(me.getLoginId().equals(r.get("loginId")) || me.isAdmin())) {
            return Result.fail("본인 기록만 지울 수 있습니다.");
        }
        mapper.deleteEdu(id);
        return Result.ok("기록을 지웠습니다.", id);
    }

    /** 직원 한 사람의 그해 과목별 이수 시간 */
    public record EduStatus(Map<String, Object> course, double done, double need, boolean complete) {}

    public List<EduStatus> eduStatus(String loginId, int year) {
        Map<Long, Double> sum = new HashMap<>();
        for (Map<String, Object> r : mapper.eduRecords(year)) {
            if (loginId.equals(r.get("loginId"))) {
                sum.merge(((Number) r.get("courseId")).longValue(), ((Number) r.get("hours")).doubleValue(), Double::sum);
            }
        }
        List<EduStatus> out = new ArrayList<>();
        for (Map<String, Object> c : courses(true)) {
            double need = ((Number) c.get("hours")).doubleValue();
            double done = sum.getOrDefault(((Number) c.get("id")).longValue(), 0.0);
            out.add(new EduStatus(c, done, need, done >= need));
        }
        return out;
    }

    public List<Map<String, Object>> myEduRecords(String loginId, int year) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : mapper.eduRecords(year)) {
            if (loginId.equals(r.get("loginId"))) {
                r.put("on", date(r.get("on")));
                out.add(r);
            }
        }
        return out;
    }

    /** 관리자: 과목별 미이수자 */
    public Map<String, List<String>> missingEdu(int year) {
        Map<String, Map<Long, Double>> sum = new HashMap<>();
        for (Map<String, Object> r : mapper.eduRecords(year)) {
            sum.computeIfAbsent((String) r.get("loginId"), k -> new HashMap<>())
                    .merge(((Number) r.get("courseId")).longValue(), ((Number) r.get("hours")).doubleValue(), Double::sum);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map<String, Object> c : courses(true)) {
            long cid = ((Number) c.get("id")).longValue();
            double need = ((Number) c.get("hours")).doubleValue();
            List<String> names = new ArrayList<>();
            for (LoginUser u : users.list()) {
                if (u.isEnabled() && !u.isAdmin() && sum.getOrDefault(u.getLoginId(), Map.of()).getOrDefault(cid, 0.0) < need) {
                    names.add(u.getUserName() + (u.dept().isEmpty() ? "" : " (" + u.dept() + ")"));
                }
            }
            out.put((String) c.get("name"), names);
        }
        return out;
    }

    /** 관리자: 미이수자에게 알림 */
    public Result remindEdu(int year, LoginUser me) {
        if (me == null || !me.isAdmin()) {
            return Result.fail("시스템 관리자만 알림을 보낼 수 있습니다.");
        }
        int n = 0;
        for (LoginUser u : users.list()) {
            if (!u.isEnabled() || u.isAdmin()) {
                continue;
            }
            List<String> left = new ArrayList<>();
            for (EduStatus s : eduStatus(u.getLoginId(), year)) {
                if (!s.complete()) {
                    left.add((String) s.course().get("name"));
                }
            }
            if (!left.isEmpty()) {
                notifications.notify(u.getLoginId(), year + "년 법정의무교육 미이수: " + String.join(", ", left), "/office/edu");
                n++;
            }
        }
        return Result.ok("미이수 직원 " + n + "명에게 알렸습니다.", null);
    }

    /* ================================================================ 비상연락망 */

    /**
     * 부서별 비상연락망. 내선은 모두에게, 휴대전화는 본인이 공개에 동의한 경우에만 보인다.
     * 관리자는 동의하지 않은 번호도 볼 수 있다 (재난·비상 소집용).
     */
    public Map<String, List<Map<String, Object>>> contacts(LoginUser me) {
        Map<String, Map<String, Object>> mobiles = new HashMap<>();
        for (Map<String, Object> c : mapper.contacts()) {
            mobiles.put((String) c.get("loginId"), c);
        }
        Map<String, List<Map<String, Object>>> out = new java.util.TreeMap<>();
        for (LoginUser u : users.list()) {
            if (!u.isEnabled()) {
                continue;
            }
            Map<String, Object> c = mobiles.get(u.getLoginId());
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", u.getUserName());
            it.put("position", u.position());
            it.put("extension", u.getExtension());
            boolean share = c != null && "Y".equals(c.get("shareYn"));
            it.put("mobile", c != null && (share || me.isAdmin() || me.getLoginId().equals(u.getLoginId())) ? c.get("mobile") : null);
            it.put("hidden", c != null && !share);
            out.computeIfAbsent(u.dept().isEmpty() ? "(부서 없음)" : u.dept(), k -> new ArrayList<>()).add(it);
        }
        return out;
    }

    public Map<String, Object> myContact(LoginUser me) {
        return mapper.contact(me.getLoginId());
    }

    @Transactional
    public Result saveContact(String mobile, boolean share, LoginUser me) {
        if (mobile == null || mobile.isBlank()) {
            mapper.deleteContact(me.getLoginId());
            return Result.ok("휴대전화 번호를 지웠습니다.", null);
        }
        String m = PhoneNumbers.mobile(mobile);
        if (m == null) {
            return Result.fail("휴대전화 번호를 010-1234-5678 꼴로 적으세요.");
        }
        mapper.deleteContact(me.getLoginId());
        mapper.insertContact(me.getLoginId(), m, share ? "Y" : "N", LocalDateTime.now());
        return Result.ok(share ? "저장했습니다. 직원들에게 번호가 보입니다." : "저장했습니다. 번호는 관리자(비상 소집용)에게만 보입니다.", null);
    }

    /* ================================================================ 도움 */

    /** DB 날짜 값(Timestamp·Date)을 화면에서 쓰는 LocalDateTime·LocalDate 로 바꾼다 */
    static Map<String, Object> times(Map<String, Object> m) {
        if (m != null) {
            m.replaceAll((k, v) -> v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : v instanceof java.sql.Date d ? d.toLocalDate() : v);
        }
        return m;
    }

    static List<Map<String, Object>> times(List<Map<String, Object>> list) {
        list.forEach(OfficeService::times);
        return list;
    }

    private static final String[] WD = {"월", "화", "수", "목", "금", "토", "일"};

    /** "11-19 (목)" */
    public static String md(LocalDate d) {
        return d == null ? "" : String.format("%02d-%02d (%s)", d.getMonthValue(), d.getDayOfMonth(), WD[d.getDayOfWeek().getValue() - 1]);
    }

    static LocalDate date(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (o instanceof LocalDate l) {
            return l;
        }
        if (o instanceof LocalDateTime l) {
            return l.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(o).substring(0, 10));
    }
}
