package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.impl.CivilMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 민원 처리: 담당 배정, 처리기간 연장, 진행상황 통지, 처리 완료.
 * 근거: 민원처리법 제27조(처리결과의 통지), 같은 법 시행령 제21조(처리기간의 연장)·제23조(처리진행상황 통지).
 * 민원인 연락처 등 개인정보가 있으므로 상세 화면은 처리에 관계된 직원만 본다.
 */
@Service
public class CivilProcessService {

    /** 처리 결과 통지 방법. 문서가 원칙이고, 그 밖의 방법은 기타민원이거나 민원인이 요청한 경우 (법 제27조 제1항) */
    public static final List<String> NOTIFY_METHODS = List.of("문서(우편·교부)", "전자문서", "문자", "팩스", "전화", "전자우편", "구술");
    private static final Set<String> DOCUMENT_METHODS = Set.of("문서(우편·교부)", "전자문서");
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Result(boolean ok, String message) {}

    private final CivilMapper mapper;
    private final DeadlineCalculator calculator;
    private final UserService userService;
    private final NotificationService notificationService;

    public CivilProcessService(CivilMapper mapper, DeadlineCalculator calculator, UserService userService,
                               NotificationService notificationService) {
        this.mapper = mapper;
        this.calculator = calculator;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public CivilComplaintVO get(long id) {
        CivilComplaintVO c = mapper.selectComplaint(id);
        if (c != null && c.getPeriodDays() == null) {
            // 이 기능 전에 접수한 민원은 종류로 처리기간을 다시 구한다
            c.setPeriodDays(c.getKind() == CivilKind.LEGAL
                    ? (c.getLegalAffair() == null ? 0 : c.getLegalAffair().periodDays())
                    : c.getKind().periodDays("Y".equals(c.getLawInterpretYn())));
        }
        return c;
    }

    public List<CivilHistoryVO> history(long id) {
        return mapper.selectHistory(id);
    }

    /** 상세(민원인 개인정보 포함)를 볼 수 있는 직원: 창구 직원, 관리자, 처리 부서 직원, 담당자, 접수한 직원 */
    public boolean canView(CivilComplaintVO c, LoginUser u) {
        return u.isCounterStaff() || u.isAdmin() || canProcess(c, u) || u.getLoginId().equals(c.getRegisterId());
    }

    /** 처리(배정·연장·완료)할 수 있는 직원: 담당자, 처리 부서 직원, 창구 직원, 관리자 */
    public boolean canProcess(CivilComplaintVO c, LoginUser u) {
        return u.isCounterStaff() || u.isAdmin() || u.getLoginId().equals(c.getAssigneeId())
                || (c.getDept() != null && !u.dept().isEmpty() && c.getDept().equals(u.dept()));
    }

    /** 배정할 수 있는 직원 (사용 중인 계정) */
    public List<LoginUser> staffList() {
        return userService.list().stream().filter(LoginUser::isEnabled).toList();
    }

    public LoginUser findStaff(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        return staffList().stream().filter(u -> u.getLoginId().equals(loginId)).findFirst().orElse(null);
    }

    @Transactional
    public Result assign(long id, String dept, String assigneeId, LoginUser by) {
        CivilComplaintVO c = get(id);
        if (c == null || !canProcess(c, by)) {
            return new Result(false, "이 민원을 배정할 권한이 없습니다.");
        }
        if (c.isDone()) {
            return new Result(false, "이미 처리를 마친 민원입니다.");
        }
        String d = dept == null || dept.isBlank() ? null : dept.trim();
        if (d != null && d.length() > 100) {
            return new Result(false, "부서 이름은 100자 이내로 입력하세요.");
        }
        LoginUser who = findStaff(assigneeId);
        if (assigneeId != null && !assigneeId.isBlank() && who == null) {
            return new Result(false, "담당자를 직원 목록에서 고르세요.");
        }
        if (who != null && d == null) {
            d = who.getDeptName();
        }
        mapper.updateAssign(id, d, who == null ? null : who.getUserName(), who == null ? null : who.getLoginId());
        history(id, "ASSIGN", (d == null ? "" : d) + (who == null ? " (담당자 없음)" : " / " + who.getUserName()), by);
        if (who != null && !who.getLoginId().equals(by.getLoginId())) {
            notificationService.notify(who.getLoginId(),
                    "민원 " + c.getReceiptNo() + " '" + c.getTitle() + "' 담당으로 배정되었습니다. 처리기한 "
                            + c.getDeadlineAt().format(AT), "/civil/" + id);
        }
        return new Result(true, who == null ? "처리 부서를 정했습니다." : who.getUserName() + "님에게 배정했습니다.");
    }

    /**
     * 처리기간 연장 (시행령 제21조).
     *
     * @param days       연장 일수 (처리기간의 범위 안)
     * @param consent    두 번째 연장일 때 민원인 동의를 받았는지
     * @param notified   연장 사유와 처리완료 예정일을 민원인에게 문서로 알렸는지
     */
    @Transactional
    public Result extend(long id, ExtendReason reason, Integer days, String detail, boolean consent, boolean notified,
                         LoginUser by) {
        CivilComplaintVO c = get(id);
        if (c == null || !canProcess(c, by)) {
            return new Result(false, "이 민원을 처리할 권한이 없습니다.");
        }
        if (!c.isExtendable()) {
            return new Result(false, c.getPeriodDays() != null && c.getPeriodDays() == 0
                    ? "즉시 처리하는 민원은 처리기간을 연장할 수 없습니다."
                    : "처리기간은 한 차례, 민원인 동의를 받아 한 차례 더 연장할 수 있습니다. 더는 연장할 수 없습니다.");
        }
        if (reason == null) {
            return new Result(false, "연장 사유를 고르세요. 단순 업무과중이나 담당자 부재는 연장 사유가 아닙니다.");
        }
        if (days == null || days < 1 || days > c.getPeriodDays()) {
            return new Result(false, "연장 일수는 1일에서 처리기간(" + c.getPeriodDays() + "일) 사이로 정하세요.");
        }
        String d = detail == null ? "" : detail.trim();
        if (d.isEmpty() || d.length() > 500) {
            return new Result(false, "구체적인 연장 사유를 500자 이내로 적으세요.");
        }
        if (c.getExtendCount() == 1 && !consent) {
            return new Result(false, "두 번째 연장은 민원인의 동의를 받아야 합니다.");
        }
        if (!notified) {
            return new Result(false, "연장 사유와 처리완료 예정일을 민원인에게 문서로 통지해야 합니다.");
        }
        LocalDateTime newDeadline = calculator.extend(c.getDeadlineAt(), c.getPeriodDays(), days);
        if (mapper.updateDeadline(id, newDeadline.toLocalDate(), newDeadline, c.getExtendCount() + 1) == 0) {
            return new Result(false, "다른 직원이 먼저 처리했습니다. 화면을 새로 고치세요.");
        }
        history(id, "EXTEND", (c.getExtendCount() + 1) + "차 연장 " + days + "일 · " + reason.getLabel() + " · " + d
                + " · 새 처리기한 " + newDeadline.format(AT) + (consent ? " · 민원인 동의" : "") + " · 민원인에게 문서 통지", by);
        return new Result(true, "처리기한을 " + newDeadline.format(AT) + "까지로 연장했습니다.");
    }

    /** 처리진행상황 통지 기록 (시행령 제23조: 접수 30일이 지날 때마다) */
    @Transactional
    public Result progressNotice(long id, String method, String detail, LoginUser by) {
        CivilComplaintVO c = get(id);
        if (c == null || !canProcess(c, by)) {
            return new Result(false, "이 민원을 처리할 권한이 없습니다.");
        }
        if (c.isDone()) {
            return new Result(false, "이미 처리를 마친 민원입니다.");
        }
        if (method == null || !NOTIFY_METHODS.contains(method)) {
            return new Result(false, "통지 방법을 고르세요.");
        }
        String d = detail == null ? "" : detail.trim();
        if (d.isEmpty() || d.length() > 500) {
            return new Result(false, "알린 진행상황과 처리완료 예정일을 500자 이내로 적으세요.");
        }
        history(id, "PROGRESS", method + " · " + d, by);
        return new Result(true, "진행상황 통지를 기록했습니다.");
    }

    /** 처리 완료와 결과 통지 (법 제27조) */
    @Transactional
    public Result complete(long id, ResultType type, String result, String remedy, String notifyMethod, LoginUser by) {
        return complete(id, type, result, remedy, notifyMethod, null, by);
    }

    /** 처리 완료와 결과 통지. 팩스로 알리면 보낸 번호를 남긴다 (비우면 접수 때 받은 번호) */
    @Transactional
    public Result complete(long id, ResultType type, String result, String remedy, String notifyMethod, String faxNo, LoginUser by) {
        CivilComplaintVO c = get(id);
        if (c == null || !canProcess(c, by)) {
            return new Result(false, "이 민원을 처리할 권한이 없습니다.");
        }
        if (c.isDone()) {
            return new Result(false, "이미 처리를 마친 민원입니다.");
        }
        if (type == null) {
            return new Result(false, "처리 결과 구분을 고르세요.");
        }
        String r = result == null ? "" : result.trim();
        if (r.isEmpty() || r.length() > 20000) {
            return new Result(false, "처리 결과를 적으세요.");
        }
        String rem = remedy == null ? "" : remedy.trim();
        if (type.needsRemedy() && rem.isEmpty()) {
            return new Result(false, "민원 내용을 거부하는 경우 거부 이유와 구제절차를 함께 알려야 합니다 (민원처리법 제27조 제3항).");
        }
        if (rem.length() > 1300) {
            return new Result(false, "거부 이유·구제절차는 1,300자 이내로 적으세요.");
        }
        if (notifyMethod == null || !NOTIFY_METHODS.contains(notifyMethod)) {
            return new Result(false, "결과 통지 방법을 고르세요.");
        }
        if (!DOCUMENT_METHODS.contains(notifyMethod) && c.getKind() != CivilKind.OTHER
                && !requestedByApplicant(c, notifyMethod)) {
            return new Result(false, "처리 결과는 문서로 알리는 것이 원칙입니다. 기타민원이거나 민원인이 요청한 경우에만 "
                    + notifyMethod + "(으)로 알릴 수 있습니다 (민원처리법 제27조 제1항).");
        }
        String fax = null;
        if ("팩스".equals(notifyMethod)) {
            fax = faxNo == null || faxNo.isBlank() ? c.getFaxNo() : faxNo.trim();
            if (!CivilForm.isFaxNumber(fax)) {
                return new Result(false, "팩스로 알리려면 보낼 팩스 번호를 지역번호부터 입력하세요 (예: 02-123-4567).");
            }
        }
        c.setResultFaxNo(fax);
        c.setCompletedAt(LocalDateTime.now());
        c.setCompletedBy(by.getLoginId());
        c.setResultType(type.name());
        c.setResultText(r);
        c.setRejectRemedy(rem.isEmpty() ? null : rem);
        c.setResultNotify(notifyMethod);
        if (mapper.complete(c) == 0) {
            return new Result(false, "이미 처리를 마친 민원입니다.");
        }
        history(id, "DONE", type.getLabel() + " · 결과 통지: " + notifyMethod + (fax == null ? "" : " (" + fax + ")"), by);
        if (c.getRegisterId() != null && !c.getRegisterId().equals(by.getLoginId())) {
            notificationService.notify(c.getRegisterId(), "접수한 민원 " + c.getReceiptNo() + " 처리가 끝났습니다.", "/civil/" + id);
        }
        return new Result(true, "처리를 마쳤습니다." + (c.isLate() ? " 처리기한을 넘겼습니다." : ""));
    }

    /** 접수 때 민원인이 고른 통지 방법과 같은 방법인지 */
    private static boolean requestedByApplicant(CivilComplaintVO c, String method) {
        String asked = c.getNotifyMethod();
        if (asked == null) {
            return false;
        }
        return switch (method) {
            case "문자" -> asked.equals("문자");
            case "전자우편" -> asked.equals("이메일");
            case "팩스" -> asked.equals("팩스");
            default -> false;
        };
    }

    private void history(long id, String action, String detail, LoginUser by) {
        CivilHistoryVO h = new CivilHistoryVO();
        h.setHistId(mapper.nextHistoryId());
        h.setComplaintId(id);
        h.setAction(action);
        h.setDetail(detail.length() > 1300 ? detail.substring(0, 1300) : detail);
        h.setActorId(by.getLoginId());
        h.setActorName(by.getUserName());
        h.setActedAt(LocalDateTime.now());
        mapper.insertHistory(h);
    }
}
