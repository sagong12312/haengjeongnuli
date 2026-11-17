package egovframework.haengjeongnuli.epetition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;

/**
 * 행정누리 전자민원(국민용 서버)이 부르는 연동 API. 사람이 직접 부르지 않는다.
 * 인증은 {@link EPetitionAuthFilter} (허용 주소 + HMAC 서명 + 재전송 방지).
 * X-EP-Client-IP: 전자민원에 접속한 국민의 주소 (접속기록용, 선택)
 */
@RestController
@RequestMapping("/api/epetition/v1")
public class EPetitionApiController {

    private final EPetitionService service;
    private final AuditService audit;
    private final egovframework.haengjeongnuli.hygiene.service.SanctionService sanctions;
    @org.springframework.beans.factory.annotation.Autowired
    private EPetitionPortalService portal;
    @org.springframework.beans.factory.annotation.Autowired
    private EPetitionMemberService members;

    public EPetitionApiController(EPetitionService service, AuditService audit,
                                  egovframework.haengjeongnuli.hygiene.service.SanctionService sanctions) {
        this.service = service;
        this.audit = audit;
        this.sanctions = sanctions;
    }

    /** 행정처분 공개: 담당자가 공개로 고른 유효한 처분, 처분일부터 1년. 대표자 이름은 보내지 않는다 */
    @GetMapping("/dispositions")
    public ResponseEntity<Map<String, Object>> dispositions(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(required = false) String q) {
        egovframework.haengjeongnuli.hygiene.service.SanctionService.PublicPage p = sanctions.publicPage(page, q == null || q.length() > 100 ? null : q);
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (egovframework.haengjeongnuli.hygiene.service.SanctionVO s : p.items()) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("decidedOn", s.getDecidedOn().toString());
            it.put("bizName", s.getBizName());
            it.put("bizType", s.getBizType());
            it.put("address", s.getAddress());
            it.put("law", s.getLawName());
            it.put("kind", s.getKind());
            it.put("startDate", s.getStartDate() == null ? null : s.getStartDate().toString());
            it.put("endDate", s.getEndDate() == null ? null : s.getEndDate().toString());
            it.put("amount", s.getAmount());
            it.put("violation", s.getViolation());
            it.put("basis", s.getBasis());
            items.add(it);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", p.total());
        out.put("page", p.page());
        out.put("totalPages", p.totalPages());
        out.put("items", items);
        return ok(out);
    }

    @PostMapping("/complaints")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody(required = false) EPetitionService.ReceiveRequest req,
                                                       @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = service.receive(req);
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_RECEIVE, "민원 " + out.get("receiptNo"), client(clientIp));
        out.remove("complaintId");
        return ok(out);
    }

    public record LookupRequest(String receiptNo, String password) {}

    /** 처리상황 조회 (POST: 비밀번호를 주소에 남기지 않는다) */
    @PostMapping("/complaints/lookup")
    public ResponseEntity<Map<String, Object>> lookup(@RequestBody(required = false) LookupRequest req,
                                                      @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        String no = req == null ? null : req.receiptNo();
        try {
            Map<String, Object> out = service.lookup(no, req == null ? null : req.password(), clientIp);
            audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_LOOKUP, "민원 " + no, "성공 " + client(clientIp));
            return ok(out);
        } catch (EPetitionService.Problem p) {
            audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_LOOKUP, "민원 " + (no == null ? "-" : no.length() > 20 ? no.substring(0, 20) : no),
                    p.code() + " " + client(clientIp));
            throw p;
        }
    }

    /** 공개 상담 목록. kind: QUERY 등 (없으면 전체), sort: recent(기본)·views·likes */
    @GetMapping("/consultations")
    public ResponseEntity<Map<String, Object>> consultations(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(required = false) String kind,
                                                             @RequestParam(required = false) String sort) {
        return ok(portal.consultations(page, q, kind, sort));
    }

    /** 공개 상담 한 건 + 조회수·공감·비슷한 상담 */
    @GetMapping("/consultations/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> consultation(@PathVariable long id,
                                                            @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = new LinkedHashMap<>(service.publicDetail(id));
        out.putAll(portal.consultationExtras(id, clientIp));
        return ok(out);
    }

    @PostMapping("/consultations/{id:\\d+}/like")
    public ResponseEntity<Map<String, Object>> like(@PathVariable long id,
                                                    @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        return ok(portal.like(id, clientIp));
    }

    /* ---------------------------------------------------------------- 안내·통계·공지·자주 묻는 질문 */

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ok(portal.info());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ok(portal.stats());
    }

    @GetMapping("/notices")
    public ResponseEntity<Map<String, Object>> notices(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(required = false) String q) {
        return ok(portal.notices(page, q));
    }

    @GetMapping("/notices/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> notice(@PathVariable long id,
                                                      @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        return ok(portal.notice(id, clientIp));
    }

    @GetMapping("/faqs")
    public ResponseEntity<Map<String, Object>> faqs() {
        return ok(portal.faqs());
    }

    /* ---------------------------------------------------------------- 민원인이 하는 일 (조회 때 받은 actionToken) */

    public record WithdrawRequest(String token, String reason) {}

    public record AddendumRequest(String token, String text) {}

    public record RateRequest(String token, Integer score, String comment) {}

    @PostMapping("/complaints/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@RequestBody(required = false) WithdrawRequest req,
                                                        @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = portal.withdraw(req == null ? null : req.token(), req == null ? null : req.reason());
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_WITHDRAW, "민원 " + out.get("receiptNo"), client(clientIp));
        return ok(out);
    }

    @PostMapping("/complaints/addendum")
    public ResponseEntity<Map<String, Object>> addendum(@RequestBody(required = false) AddendumRequest req,
                                                        @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = portal.addendum(req == null ? null : req.token(), req == null ? null : req.text());
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_ADDENDUM, "민원 " + out.get("receiptNo"), client(clientIp));
        return ok(out);
    }

    @PostMapping("/complaints/satisfaction")
    public ResponseEntity<Map<String, Object>> satisfaction(@RequestBody(required = false) RateRequest req,
                                                            @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = portal.rate(req == null ? null : req.token(), req == null ? null : req.score(),
                req == null ? null : req.comment());
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_SATISFACTION, "민원 " + out.get("receiptNo"), client(clientIp));
        return ok(out);
    }

    /* ---------------------------------------------------------------- 회원 (memberId 는 전자민원이 로그인 세션에서 알려 준다) */

    @PostMapping("/members")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody(required = false) EPetitionMemberService.SignupRequest req,
                                                      @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = members.signup(req);
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원 " + out.get("loginId"), "가입 " + client(clientIp));
        return ok(out);
    }

    @PostMapping("/members/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) EPetitionMemberService.LoginRequest req,
                                                     @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        String id = req == null || req.loginId() == null ? "-" : req.loginId().replaceAll("[^a-z0-9_]", "");
        id = id.length() > 30 ? id.substring(0, 30) : id;
        try {
            Map<String, Object> out = members.login(req);
            audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원 " + id, "로그인 " + client(clientIp));
            return ok(out);
        } catch (EPetitionService.Problem p) {
            audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원 " + id, "로그인 실패 " + p.code() + " " + client(clientIp));
            throw p;
        }
    }

    @GetMapping("/members/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> member(@PathVariable long id) {
        return ok(members.get(id));
    }

    @PostMapping("/members/{id:\\d+}/profile")
    public ResponseEntity<Map<String, Object>> profile(@PathVariable long id,
                                                       @RequestBody(required = false) EPetitionMemberService.ProfileRequest req,
                                                       @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = members.update(id, req);
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원 " + out.get("loginId"), "정보 변경 " + client(clientIp));
        return ok(out);
    }

    @PostMapping("/members/{id:\\d+}/password")
    public ResponseEntity<Map<String, Object>> password(@PathVariable long id,
                                                        @RequestBody(required = false) EPetitionMemberService.PasswordRequest req,
                                                        @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = members.changePassword(id, req);
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원번호 " + id, "비밀번호 변경 " + client(clientIp));
        return ok(out);
    }

    public record DeleteRequest(String password) {}

    @PostMapping("/members/{id:\\d+}/delete")
    public ResponseEntity<Map<String, Object>> deleteMember(@PathVariable long id, @RequestBody(required = false) DeleteRequest req,
                                                            @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = members.delete(id, req == null ? null : req.password());
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_MEMBER, "회원번호 " + id, "탈퇴 " + client(clientIp));
        return ok(out);
    }

    @GetMapping("/members/{id:\\d+}/complaints")
    public ResponseEntity<Map<String, Object>> memberComplaints(@PathVariable long id, @RequestParam(defaultValue = "1") int page) {
        return ok(members.complaints(id, page));
    }

    @GetMapping("/members/{id:\\d+}/complaints/{receiptNo}")
    public ResponseEntity<Map<String, Object>> memberComplaint(@PathVariable long id, @PathVariable String receiptNo,
                                                               @RequestHeader(value = "X-EP-Client-IP", required = false) String clientIp) {
        Map<String, Object> out = members.complaint(id, receiptNo);
        audit.recordAs(EPetitionService.REGISTER_ID, AuditAction.EPETITION_LOOKUP, "민원 " + out.get("receiptNo"), "회원 " + client(clientIp));
        return ok(out);
    }

    @ExceptionHandler(EPetitionService.Problem.class)
    public ResponseEntity<Map<String, Object>> problem(EPetitionService.Problem p) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", p.code());
        body.put("message", p.getMessage());
        return ResponseEntity.status(p.status()).body(body);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable() {
        return problem(new EPetitionService.Problem(400, "bad_request", "JSON 형식이 맞지 않습니다."));
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("data", data);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    private static String client(String ip) {
        return ip == null ? "" : "국민 접속 " + (ip.length() > 45 ? ip.substring(0, 45) : ip).replaceAll("[^0-9a-fA-F.:]", "");
    }
}
