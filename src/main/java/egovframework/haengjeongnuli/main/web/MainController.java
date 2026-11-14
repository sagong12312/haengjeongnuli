package egovframework.haengjeongnuli.main.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.civil.service.CivilService;
import jakarta.servlet.http.HttpSession;

/** 메인메뉴와 출퇴근 처리 */
@Controller
public class MainController {

    /** 세션에 저장하는 출근 상태 키 (근태 기록 연동 전 임시) */
    public static final String WORKING = "working";

    private final CivilService civilService;
    private final BoardService boardService;

    public MainController(CivilService civilService, BoardService boardService) {
        this.civilService = civilService;
        this.boardService = boardService;
    }

    @GetMapping("/")
    public String main(@org.springframework.security.core.annotation.AuthenticationPrincipal
                       egovframework.haengjeongnuli.cmm.LoginUser loginUser, HttpSession session, Model model) {
        model.addAttribute("working", isWorking(session));
        model.addAttribute("civilSummary", civilService.summary());
        model.addAttribute("notices", boardService.list(
                egovframework.haengjeongnuli.board.service.BoardSearchVO.of("notice", null, 1, loginUser).size(3)));
        return "main/index";
    }

    /** 출근·퇴근 전환 후 원래 화면으로 돌아간다 */
    @PostMapping("/attendance")
    public String toggleAttendance(HttpSession session,
                                   @RequestHeader(value = "Referer", required = false) String referer) {
        boolean now = !isWorking(session);
        session.setAttribute(WORKING, now);
        String key = now ? "inTime" : "outTime";
        session.setAttribute(key, java.time.LocalTime.now().withSecond(0).withNano(0).toString());
        if (now) {
            session.removeAttribute("outTime");
        }
        return "redirect:" + safeBack(referer);
    }

    public static boolean isWorking(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(WORKING));
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
