package egovframework.haengjeongnuli.biz;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.user.service.UserService;

/** 업무 지원 화면 */
@Controller
public class BizController {

    private final BizService service;
    private final UserService users;
    private final AuditService audit;

    public BizController(BizService service, UserService users, AuditService audit) {
        this.service = service;
        this.users = users;
        this.audit = audit;
    }

    private LoginUser fresh(LoginUser me) {
        LoginUser u = users.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    private static String done(BizService.Result r, RedirectAttributes ra, String okTarget, String failTarget) {
        ra.addFlashAttribute("message", r.message());
        return "redirect:" + (r.ok() ? okTarget : failTarget);
    }

    private List<LoginUser> staff() {
        List<LoginUser> out = new ArrayList<>(users.list().stream().filter(LoginUser::isEnabled).toList());
        out.sort((a, b) -> (a.dept() + a.getUserName()).compareTo(b.dept() + b.getUserName()));
        return out;
    }

    private Set<String> depts() {
        Set<String> d = new TreeSet<>();
        users.list().forEach(u -> {
            if (u.isEnabled() && !u.dept().isEmpty()) {
                d.add(u.dept());
            }
        });
        return d;
    }

    private static YearMonth ym(String s) {
        try {
            YearMonth m = s == null ? YearMonth.now() : YearMonth.parse(s);
            return m.isBefore(YearMonth.now().minusYears(3)) || m.isAfter(YearMonth.now().plusYears(1)) ? YearMonth.now() : m;
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }

    @GetMapping("/biz")
    public String hub() {
        return "biz/hub";
    }

    /* ---------------------------------------------------------------- 업무 일지 */

    @GetMapping("/biz/worklog")
    public String worklog(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Model model) {
        LoginUser u = fresh(me);
        LocalDate d = date == null || date.isAfter(LocalDate.now()) ? LocalDate.now() : date;
        model.addAttribute("date", d);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("minDate", LocalDate.now().minusDays(7));
        model.addAttribute("editable", !d.isBefore(LocalDate.now().minusDays(7)));
        model.addAttribute("log", service.workLog(u, d));
        model.addAttribute("mine", service.myWorkLogs(u));
        model.addAttribute("dept", service.deptWorkLogs(u, d));
        model.addAttribute("myDept", u.dept());
        return "biz/worklog";
    }

    @PostMapping("/biz/worklog")
    public String saveWorklog(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(required = false) String body, RedirectAttributes ra) {
        BizService.Result r = service.saveWorkLog(date, body, fresh(me));
        String back = "/biz/worklog" + (date == null ? "" : "?date=" + date);
        return done(r, ra, back, back);
    }

    /* ---------------------------------------------------------------- 부서 일정 */

    @GetMapping("/biz/events")
    public String events(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String ym, Model model) {
        LoginUser u = fresh(me);
        YearMonth m = ym(ym);
        model.addAttribute("ym", m);
        model.addAttribute("prev", m.minusMonths(1));
        model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("events", service.deptEvents(u, m.atDay(1), m.atEndOfMonth()));
        model.addAttribute("myDept", u.dept());
        return "biz/events";
    }

    @PostMapping("/biz/events")
    public String addEvent(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                           @RequestParam(required = false) String title, @RequestParam(defaultValue = "false") boolean notify, RedirectAttributes ra) {
        BizService.Result r = service.addDeptEvent(day, title, notify, fresh(me));
        return done(r, ra, "/biz/events" + (day == null ? "" : "?ym=" + YearMonth.from(day)), "/biz/events");
    }

    @PostMapping("/biz/events/{id:\\d+}/delete")
    public String deleteEvent(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteDeptEvent(id, fresh(me)), ra, "/biz/events", "/biz/events");
    }

    /* ---------------------------------------------------------------- 협조 요청 */

    @GetMapping("/biz/coops")
    public String coops(@AuthenticationPrincipal LoginUser me, Model model) {
        LoginUser u = fresh(me);
        model.addAttribute("coops", service.coops(u));
        Set<String> d = depts();
        d.remove(u.dept());
        model.addAttribute("depts", d);
        model.addAttribute("today", LocalDate.now());
        return "biz/coops";
    }

    @PostMapping("/biz/coops")
    public String requestCoop(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String toDept,
                              @RequestParam(required = false) String title, @RequestParam(required = false) String body,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate due, RedirectAttributes ra) {
        BizService.Result r = service.requestCoop(toDept, title, body, due, fresh(me));
        return done(r, ra, "/biz/coops/" + r.id(), "/biz/coops");
    }

    @GetMapping("/biz/coops/{id:\\d+}")
    public String coop(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        LoginUser u = fresh(me);
        Map<String, Object> c = service.coop(id, u);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("c", c);
        model.addAttribute("canReply", "OPEN".equals(c.get("status")) && u.dept().equals(c.get("toDept")));
        return "biz/coop";
    }

    @PostMapping("/biz/coops/{id:\\d+}/reply")
    public String reply(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String reply,
                        RedirectAttributes ra) {
        return done(service.replyCoop(id, reply, fresh(me)), ra, "/biz/coops/" + id, "/biz/coops/" + id);
    }

    /* ---------------------------------------------------------------- 회의록 */

    @GetMapping("/biz/minutes")
    public String minutesList(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("list", service.minutesList(fresh(me)));
        model.addAttribute("staff", staff());
        model.addAttribute("today", LocalDate.now());
        return "biz/minutes";
    }

    @PostMapping("/biz/minutes")
    public String writeMinutes(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                               @RequestParam(required = false) List<String> attendee, @RequestParam(required = false) String body,
                               @RequestParam(required = false) String decisions, @RequestParam(required = false) String actions,
                               RedirectAttributes ra) {
        BizService.Result r = service.writeMinutes(title, day, attendee, body, decisions, actions, fresh(me));
        if (!r.ok()) {
            ra.addFlashAttribute("draftBody", body);
            ra.addFlashAttribute("draftActions", actions);
        }
        return done(r, ra, "/biz/minutes/" + r.id(), "/biz/minutes");
    }

    @GetMapping("/biz/minutes/{id:\\d+}")
    public String minutes(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        Map<String, Object> m = service.minutes(id, fresh(me));
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("m", m);
        return "biz/minute";
    }

    /* ---------------------------------------------------------------- 전화 응대 */

    @GetMapping("/biz/calls")
    public String calls(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String ym, @RequestParam(required = false) String q,
                        Model model) {
        YearMonth m = ym(ym);
        model.addAttribute("ym", m);
        model.addAttribute("prev", m.minusMonths(1));
        model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("stats", service.calls(fresh(me), m, q == null || q.length() > 50 ? null : q));
        model.addAttribute("results", BizService.CALL_RESULTS);
        return "biz/calls";
    }

    @PostMapping("/biz/calls")
    public String logCall(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                          @RequestParam(required = false) String phone, @RequestParam(required = false) String topic,
                          @RequestParam(required = false) String summary, @RequestParam(required = false) String result, RedirectAttributes ra) {
        return done(service.logCall(name, phone, topic, summary, result, fresh(me)), ra, "/biz/calls", "/biz/calls");
    }

    /* ---------------------------------------------------------------- 방문객 */

    @GetMapping("/biz/visitors")
    public String visitors(Model model) {
        model.addAttribute("visits", service.visits());
        model.addAttribute("staff", staff());
        model.addAttribute("keepDays", service.visitorKeepDays());
        return "biz/visitors";
    }

    @PostMapping("/biz/visitors")
    public String visitIn(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                          @RequestParam(required = false) String phone, @RequestParam(required = false) String org,
                          @RequestParam(required = false) String purpose, @RequestParam(required = false) String hostId, RedirectAttributes ra) {
        BizService.Result r = service.visitIn(name, phone, org, purpose, hostId, fresh(me));
        if (r.ok()) {
            audit.record(AuditAction.BIZ, "방문객 " + r.id(), "입실");
        }
        return done(r, ra, "/biz/visitors", "/biz/visitors");
    }

    @PostMapping("/biz/visitors/{id:\\d+}/out")
    public String visitOut(@PathVariable long id, RedirectAttributes ra) {
        return done(service.visitOut(id), ra, "/biz/visitors", "/biz/visitors");
    }

    /* ---------------------------------------------------------------- 습득물 */

    @GetMapping("/biz/lost")
    public String lost(Model model) {
        model.addAttribute("items", service.lostItems());
        model.addAttribute("now", LocalDateTime.now().withSecond(0).withNano(0));
        model.addAttribute("days", BizService.LOST_DAYS);
        return "biz/lost";
    }

    @PostMapping("/biz/lost")
    public String addLost(@AuthenticationPrincipal LoginUser me,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime foundAt,
                          @RequestParam(required = false) String place, @RequestParam(required = false) String desc,
                          @RequestParam(required = false) String finder, @RequestParam(required = false) String store, RedirectAttributes ra) {
        return done(service.addLost(foundAt, place, desc, finder, store, fresh(me)), ra, "/biz/lost", "/biz/lost");
    }

    @PostMapping("/biz/lost/{id:\\d+}/handle")
    public String handleLost(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) String status,
                             @RequestParam(required = false) String owner, RedirectAttributes ra) {
        BizService.Result r = service.handleLost(id, status, owner, fresh(me));
        if (r.ok()) {
            audit.record(AuditAction.BIZ, "습득물 " + id, status);
        }
        return done(r, ra, "/biz/lost", "/biz/lost");
    }

    /* ---------------------------------------------------------------- 정보공개청구 */

    private boolean canSeeInfo(Map<String, Object> i, LoginUser u) {
        return u.isAdmin() || u.getLoginId().equals(i.get("assignee")) || (!u.dept().isEmpty() && u.dept().equals(i.get("dept")));
    }

    @GetMapping("/biz/info")
    public String infos(@AuthenticationPrincipal LoginUser me, Model model) {
        LoginUser u = fresh(me);
        model.addAttribute("list", service.infoReqs().stream().filter(i -> canSeeInfo(i, u)).toList());
        model.addAttribute("staff", staff());
        model.addAttribute("depts", depts());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("decisions", BizService.DECISIONS);
        return "biz/info";
    }

    @PostMapping("/biz/info")
    public String receiveInfo(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String requester,
                              @RequestParam(required = false) String contact, @RequestParam(required = false) String content,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate received,
                              @RequestParam(required = false) String dept, @RequestParam(required = false) String assigneeId, RedirectAttributes ra) {
        BizService.Result r = service.receiveInfo(requester, contact, content, received, dept, assigneeId, fresh(me));
        if (r.ok()) {
            audit.record(AuditAction.BIZ, "정보공개청구 " + r.id(), "접수");
        }
        return done(r, ra, "/biz/info/" + r.id(), "/biz/info");
    }

    @GetMapping("/biz/info/{id:\\d+}")
    public String info(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        LoginUser u = fresh(me);
        Map<String, Object> i = service.infoReq(id);
        if (i == null || !canSeeInfo(i, u)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("i", i);
        model.addAttribute("decisions", BizService.DECISIONS);
        model.addAttribute("today", LocalDate.now());
        return "biz/info-view";
    }

    @PostMapping("/biz/info/{id:\\d+}/{action:extend|decide}")
    public String infoAction(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                             @RequestParam(required = false) String reason, @RequestParam(required = false) String decision,
                             @RequestParam(required = false) String note, RedirectAttributes ra) {
        LoginUser u = fresh(me);
        Map<String, Object> i = service.infoReq(id);
        if (i == null || !canSeeInfo(i, u)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        BizService.Result r = "extend".equals(action) ? service.extendInfo(id, reason) : service.decideInfo(id, decision, note);
        if (r.ok()) {
            audit.record(AuditAction.BIZ, "정보공개청구 " + id, action);
        }
        return done(r, ra, "/biz/info/" + id, "/biz/info/" + id);
    }

    /* ---------------------------------------------------------------- 비품 */

    @GetMapping("/biz/supplies")
    public String supplies(@AuthenticationPrincipal LoginUser me, Model model) {
        LoginUser u = fresh(me);
        model.addAttribute("items", service.supplyItems());
        model.addAttribute("mine", service.supplyReqs(u, false));
        model.addAttribute("all", u.isAdmin() ? service.supplyReqs(u, true) : List.of());
        return "biz/supplies";
    }

    @PostMapping("/biz/supplies")
    public String requestSupply(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long itemId,
                                @RequestParam(required = false) Integer qty, @RequestParam(required = false) String note, RedirectAttributes ra) {
        return done(service.requestSupply(itemId, qty, note, fresh(me)), ra, "/biz/supplies", "/biz/supplies");
    }

    @PostMapping("/biz/supplies/items")
    public String addSupplyItem(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                                @RequestParam(required = false) String unit, @RequestParam(required = false) Integer stock, RedirectAttributes ra) {
        return done(service.addSupplyItem(name, unit, stock, fresh(me)), ra, "/biz/supplies", "/biz/supplies");
    }

    @PostMapping("/biz/supplies/items/{id:\\d+}/stock")
    public String addStock(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) Integer qty,
                           RedirectAttributes ra) {
        return done(service.addStock(id, qty, fresh(me)), ra, "/biz/supplies", "/biz/supplies");
    }

    @PostMapping("/biz/supplies/{id:\\d+}/{action:give|reject}")
    public String handleSupply(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @PathVariable String action,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        return done(service.handleSupply(id, "give".equals(action), note, fresh(me)), ra, "/biz/supplies", "/biz/supplies");
    }

    /* ---------------------------------------------------------------- 업무 매뉴얼 */

    @GetMapping("/biz/manuals")
    public String manuals(@RequestParam(required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        model.addAttribute("q", query);
        model.addAttribute("list", service.manuals(query.length() > 50 ? query.substring(0, 50) : query));
        return "biz/manuals";
    }

    @PostMapping("/biz/manuals")
    public String saveManual(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long id,
                             @RequestParam(required = false) Integer version, @RequestParam(required = false) String title,
                             @RequestParam(required = false) String body, RedirectAttributes ra) {
        BizService.Result r = service.saveManual(id, version, title, body, fresh(me));
        return done(r, ra, "/biz/manuals/" + r.id(), id == null ? "/biz/manuals" : "/biz/manuals/" + id + "?edit=1");
    }

    @GetMapping("/biz/manuals/{id:\\d+}")
    public String manual(@PathVariable long id, @RequestParam(defaultValue = "false") boolean edit, Model model) {
        Map<String, Object> m = service.manual(id);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("m", m);
        model.addAttribute("edit", edit);
        model.addAttribute("revs", service.manualRevs(id));
        return "biz/manual";
    }

    @PostMapping("/biz/manuals/{id:\\d+}/restore")
    public String restore(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) Integer version,
                          RedirectAttributes ra) {
        return done(version == null ? new BizService.Result(false, "판을 고르세요.", null) : service.restoreManual(id, version, fresh(me)), ra,
                "/biz/manuals/" + id, "/biz/manuals/" + id);
    }

    /* ---------------------------------------------------------------- 경조사 · 칭찬 */

    @GetMapping("/biz/family")
    public String family(Model model) {
        model.addAttribute("list", service.familyEvents());
        model.addAttribute("kinds", BizService.FAMILY_KINDS);
        model.addAttribute("today", LocalDate.now());
        return "biz/family";
    }

    @PostMapping("/biz/family")
    public String postFamily(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String kind,
                             @RequestParam(required = false) String title, @RequestParam(required = false) String body,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                             @RequestParam(defaultValue = "false") boolean notifyAll, RedirectAttributes ra) {
        return done(service.postFamilyEvent(kind, title, body, day, notifyAll, fresh(me)), ra, "/biz/family", "/biz/family");
    }

    @PostMapping("/biz/family/{id:\\d+}/delete")
    public String deleteFamily(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteFamilyEvent(id, fresh(me)), ra, "/biz/family", "/biz/family");
    }

    @GetMapping("/biz/praise")
    public String praise(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("list", service.praises());
        model.addAttribute("top", service.praiseTop());
        model.addAttribute("staff", staff().stream().filter(u -> !u.getLoginId().equals(me.getLoginId())).toList());
        return "biz/praise";
    }

    @PostMapping("/biz/praise")
    public String doPraise(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String toId,
                           @RequestParam(required = false) String body, RedirectAttributes ra) {
        return done(service.praise(toId, body, fresh(me)), ra, "/biz/praise", "/biz/praise");
    }
}
