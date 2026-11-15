package egovframework.haengjeongnuli.main.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.audit.AuditAction;
import egovframework.haengjeongnuli.cmm.audit.AuditService;
import egovframework.haengjeongnuli.hr.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;

/** 메인메뉴와 출퇴근 처리 */
@Controller
public class MainController {

    private final CivilService civilService;
    private final BoardService boardService;
    private final ApprovalService approvalService;
    private final AttendanceService attendanceService;
    private final AuditService auditService;

    public MainController(CivilService civilService, BoardService boardService, ApprovalService approvalService,
                          AttendanceService attendanceService, AuditService auditService) {
        this.civilService = civilService;
        this.boardService = boardService;
        this.approvalService = approvalService;
        this.attendanceService = attendanceService;
        this.auditService = auditService;
    }

    @GetMapping("/")
    public String main(@org.springframework.security.core.annotation.AuthenticationPrincipal
                       LoginUser loginUser, Model model) {
        model.addAttribute("working", attendanceService.isWorking(loginUser.getLoginId()));
        model.addAttribute("civilSummary", civilService.summary(loginUser));
        model.addAttribute("approvalWait", approvalService.count(ApprovalSearchVO.of("wait", loginUser)));
        model.addAttribute("notices", boardService.list(
                egovframework.haengjeongnuli.board.service.BoardSearchVO.of("notice", null, 1, loginUser).size(3)));
        return "main/index";
    }

    /**
     * 출근(action=in)·퇴근(action=out) 후 원래 화면으로 돌아간다.
     * action 이 없으면 지금 상태를 뒤집는다. 두 번 눌러도 반대로 바뀌지 않도록 화면에서는 action 을 보낸다.
     */
    @PostMapping("/attendance")
    public String attendance(@org.springframework.security.core.annotation.AuthenticationPrincipal LoginUser me,
                             @RequestParam(required = false) String action, HttpServletRequest request,
                             @RequestHeader(value = "Referer", required = false) String referer,
                             RedirectAttributes ra) {
        boolean working = attendanceService.isWorking(me.getLoginId());
        boolean in = action == null ? !working : "in".equals(action);
        String ip = request.getRemoteAddr();
        if (in) {
            if (attendanceService.checkIn(me, ip)) {
                auditService.record(AuditAction.ATTEND_IN, me.getLoginId(), null);
            } else {
                ra.addFlashAttribute("message", "이미 출근 처리되어 있습니다.");
            }
        } else {
            if (attendanceService.checkOut(me, ip)) {
                auditService.record(AuditAction.ATTEND_OUT, me.getLoginId(), null);
            } else {
                ra.addFlashAttribute("message", "출근 기록이 없어 퇴근 처리하지 않았습니다.");
            }
        }
        return "redirect:" + safeBack(referer);
    }

    /** 같은 사이트 안의 경로로만 돌아간다 (외부 주소로의 리다이렉트 방지) */
    static String safeBack(String referer) {
        if (referer == null) {
            return "/";
        }
        try {
            java.net.URI uri = java.net.URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
