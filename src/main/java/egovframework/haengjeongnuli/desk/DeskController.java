package egovframework.haengjeongnuli.desk;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.office.OfficeService;
import egovframework.haengjeongnuli.user.service.UserService;

/** 내 업무 도구 화면 */
@Controller
public class DeskController {

    private final DeskService service;
    private final UserService users;
    private final AuditService audit;
    private final ApprovalService approvals;
    private final CivilService civil;

    public DeskController(DeskService service, UserService users, AuditService audit, ApprovalService approvals, CivilService civil) {
        this.service = service;
        this.users = users;
        this.audit = audit;
        this.approvals = approvals;
        this.civil = civil;
    }

    private LoginUser fresh(LoginUser me) {
        LoginUser u = users.findByLoginId(me.getLoginId());
        return u == null ? me : u;
    }

    private static String done(DeskService.Result r, RedirectAttributes ra, String target) {
        ra.addFlashAttribute("message", r.message());
        return "redirect:" + target;
    }

    private List<LoginUser> colleagues(LoginUser me) {
        List<LoginUser> out = new ArrayList<>();
        for (LoginUser u : users.list()) {
            if (u.isEnabled() && !u.getLoginId().equals(me.getLoginId())) {
                out.add(u);
            }
        }
        out.sort((a, b) -> (a.dept() + a.getUserName()).compareTo(b.dept() + b.getUserName()));
        return out;
    }

    /* ---------------------------------------------------------------- 오늘 요약 */

    @GetMapping("/desk")
    public String summary(@AuthenticationPrincipal LoginUser me, Model model) {
        LoginUser u = fresh(me);
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("todayText", today + " " + OfficeService.md(today).substring(6));
        model.addAttribute("approvalWait", approvals.count(ApprovalSearchVO.of("wait", u)));
        model.addAttribute("civilSummary", civil.summary(u));
        model.addAttribute("unread", service.unread(u));
        List<Map<String, Object>> todos = service.todos(u).stream()
                .filter(t -> "N".equals(t.get("done")) && (Boolean.TRUE.equals(t.get("overdue")) || Boolean.TRUE.equals(t.get("today"))))
                .toList();
        model.addAttribute("todos", todos);
        model.addAttribute("meetings", service.myMeetings(u, today, today));
        model.addAttribute("roster", service.myRoster(u, today, today.plusDays(6)));
        model.addAttribute("favorites", service.favorites(u));
        model.addAttribute("surveys", service.surveys(u).stream()
                .filter(s -> Boolean.TRUE.equals(s.get("open")) && !Boolean.TRUE.equals(s.get("voted"))).toList());
        model.addAttribute("loans", service.items().stream().filter(i -> u.getLoginId().equals(i.get("borrowerId"))).toList());
        return "desk/summary";
    }

    /* ---------------------------------------------------------------- 할 일 */

    @GetMapping("/desk/todos")
    public String todos(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("todos", service.todos(me));
        model.addAttribute("today", LocalDate.now());
        return "desk/todos";
    }

    @PostMapping("/desk/todos")
    public String addTodo(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate due, RedirectAttributes ra) {
        return done(service.addTodo(title, due, me), ra, "/desk/todos");
    }

    @PostMapping("/desk/todos/{id:\\d+}/done")
    public String toggleTodo(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(defaultValue = "true") boolean on,
                             @RequestParam(required = false) String back, RedirectAttributes ra) {
        return done(service.toggleTodo(id, on, me), ra, "desk".equals(back) ? "/desk" : "/desk/todos");
    }

    @PostMapping("/desk/todos/{id:\\d+}/delete")
    public String deleteTodo(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteTodo(id, me), ra, "/desk/todos");
    }

    /* ---------------------------------------------------------------- 즐겨찾기 */

    @GetMapping("/desk/favorites")
    public String favorites(@AuthenticationPrincipal LoginUser me, Model model) {
        List<String> chosen = new ArrayList<>();
        service.favorites(me).forEach(f -> chosen.add((String) f.get("path")));
        model.addAttribute("menus", DeskService.MENUS);
        model.addAttribute("chosen", chosen);
        model.addAttribute("max", DeskService.MAX_FAVORITES);
        return "desk/favorites";
    }

    @PostMapping("/desk/favorites")
    public String saveFavorites(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) List<String> path, RedirectAttributes ra) {
        DeskService.Result r = service.saveFavorites(path, me);
        return done(r, ra, r.ok() ? "/desk" : "/desk/favorites");
    }

    /* ---------------------------------------------------------------- 쪽지 */

    @GetMapping("/desk/messages")
    public String messages(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "in") String box,
                           @RequestParam(required = false) String to, Model model) {
        boolean sent = "sent".equals(box);
        model.addAttribute("box", sent ? "sent" : "in");
        model.addAttribute("messages", sent ? service.sent(me) : service.inbox(me));
        model.addAttribute("colleagues", colleagues(me));
        model.addAttribute("to", to);
        model.addAttribute("myDept", fresh(me).dept());
        return "desk/messages";
    }

    @PostMapping("/desk/messages/send")
    public String send(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String to,
                       @RequestParam(required = false) String body, @RequestParam(defaultValue = "false") boolean dept, RedirectAttributes ra) {
        DeskService.Result r = dept ? service.sendDept(body, fresh(me)) : service.send(to, body, fresh(me));
        if (!r.ok()) {
            ra.addFlashAttribute("draft", body);
        }
        return done(r, ra, r.ok() ? "/desk/messages?box=sent" : "/desk/messages" + (to == null || !to.matches("[A-Za-z0-9._-]{1,50}") ? "" : "?to=" + to));
    }

    @GetMapping("/desk/messages/{id:\\d+}")
    public String message(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        Map<String, Object> m = service.open(id, me);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("m", m);
        model.addAttribute("incoming", me.getLoginId().equals(m.get("toId")));
        return "desk/message";
    }

    @PostMapping("/desk/messages/{id:\\d+}/delete")
    public String deleteMessage(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteMessage(id, me), ra, "/desk/messages");
    }

    /* ---------------------------------------------------------------- 직원 찾기 · 업무 분장 */

    @GetMapping("/desk/staff")
    public String staff(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        model.addAttribute("q", query.length() > 50 ? query.substring(0, 50) : query);
        model.addAttribute("staff", service.staff(query.length() > 50 ? query.substring(0, 50) : query));
        model.addAttribute("myDuty", service.dutyMap().getOrDefault(me.getLoginId(), ""));
        return "desk/staff";
    }

    @PostMapping("/desk/staff/duty")
    public String saveDuty(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String loginId,
                           @RequestParam(required = false) String duties, RedirectAttributes ra) {
        DeskService.Result r = service.saveDuty(loginId, duties, me);
        if (r.ok() && loginId != null && !loginId.isBlank() && !loginId.equals(me.getLoginId())) {
            audit.record(AuditAction.OFFICE, "업무 분장 " + loginId, "관리자 수정");
        }
        return done(r, ra, "/desk/staff");
    }

    /* ---------------------------------------------------------------- 상용 답변 문구 */

    @GetMapping("/desk/templates")
    public String templates(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("templates", service.templates(fresh(me)));
        model.addAttribute("myDept", fresh(me).dept());
        return "desk/templates";
    }

    @PostMapping("/desk/templates")
    public String addTemplate(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                              @RequestParam(required = false) String body, @RequestParam(defaultValue = "false") boolean shared,
                              RedirectAttributes ra) {
        return done(service.addTemplate(title, body, shared, fresh(me)), ra, "/desk/templates");
    }

    @PostMapping("/desk/templates/{id:\\d+}/delete")
    public String deleteTemplate(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.deleteTemplate(id, fresh(me)), ra, "/desk/templates");
    }

    /* ---------------------------------------------------------------- 물품 대여 */

    @GetMapping("/desk/loans")
    public String loans(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("items", service.items());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("maxDue", LocalDate.now().plusDays(30));
        return "desk/loans";
    }

    @PostMapping("/desk/loans/items")
    public String addItem(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String name,
                          @RequestParam(required = false) String place, RedirectAttributes ra) {
        DeskService.Result r = service.addItem(name, place, me);
        if (r.ok()) {
            audit.record(AuditAction.OFFICE, "대여 물품 " + r.id(), r.message());
        }
        return done(r, ra, "/desk/loans");
    }

    @PostMapping("/desk/loans/borrow")
    public String borrow(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long itemId,
                         @RequestParam(required = false) String purpose,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate due, RedirectAttributes ra) {
        return done(service.borrow(itemId, purpose, due, fresh(me)), ra, "/desk/loans");
    }

    @PostMapping("/desk/loans/{id:\\d+}/return")
    public String giveBack(@AuthenticationPrincipal LoginUser me, @PathVariable long id, RedirectAttributes ra) {
        return done(service.giveBack(id, me), ra, "/desk/loans");
    }

    /* ---------------------------------------------------------------- 직원 설문 */

    @GetMapping("/desk/surveys")
    public String surveys(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("surveys", service.surveys(me));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("maxClose", LocalDate.now().plusDays(60));
        return "desk/surveys";
    }

    @PostMapping("/desk/surveys")
    public String addSurvey(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String title,
                            @RequestParam(required = false) String question, @RequestParam(required = false) String options,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closes,
                            RedirectAttributes ra) {
        DeskService.Result r = service.addSurvey(title, question, options, closes, me);
        return done(r, ra, r.ok() ? "/desk/surveys/" + r.id() : "/desk/surveys");
    }

    @GetMapping("/desk/surveys/{id:\\d+}")
    public String survey(@AuthenticationPrincipal LoginUser me, @PathVariable long id, Model model) {
        DeskService.SurveyView v = service.survey(id, me);
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("v", v);
        return "desk/survey";
    }

    @PostMapping("/desk/surveys/{id:\\d+}/vote")
    public String vote(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestParam(required = false) Integer choice,
                       RedirectAttributes ra) {
        return done(service.vote(id, choice, me), ra, "/desk/surveys/" + id);
    }

    /* ---------------------------------------------------------------- 내 일정 */

    @GetMapping("/desk/calendar")
    public String calendar(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String ym, Model model) {
        YearMonth m;
        try {
            m = ym == null ? YearMonth.now() : YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            m = YearMonth.now();
        }
        if (m.isBefore(YearMonth.now().minusYears(2)) || m.isAfter(YearMonth.now().plusYears(1))) {
            m = YearMonth.now();
        }
        model.addAttribute("ym", m);
        model.addAttribute("title", m.getYear() + "년 " + m.getMonthValue() + "월");
        model.addAttribute("prev", m.minusMonths(1));
        model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("weeks", DeskService.weeks(m));
        model.addAttribute("events", service.calendar(fresh(me), m));
        model.addAttribute("today", LocalDate.now());
        return "desk/calendar";
    }

    /* ---------------------------------------------------------------- 회의 참석자 */

    @PostMapping("/office/rooms/resv/{id:\\d+}/attendees")
    public String attendees(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
                            @RequestParam(required = false) List<String> attendee, @RequestParam(required = false) String back,
                            RedirectAttributes ra) {
        DeskService.Result r = service.setAttendees(id, attendee, fresh(me));
        return done(r, ra, back != null && back.matches("\\d{4}-\\d{2}-\\d{2}") ? "/office/rooms?date=" + back : "/office/rooms");
    }
}
