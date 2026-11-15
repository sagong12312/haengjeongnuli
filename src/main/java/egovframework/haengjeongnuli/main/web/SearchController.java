package egovframework.haengjeongnuli.main.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.board.service.BoardPostVO;
import egovframework.haengjeongnuli.board.service.BoardSearchVO;
import egovframework.haengjeongnuli.board.service.BoardService;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 통합검색: 메뉴, 결재 문서(내가 볼 수 있는 것), 민원(접수번호·제목·민원인), 게시글(공개 범위 적용).
 * 각 영역의 기존 권한 규칙을 그대로 쓰므로 볼 수 없는 문서는 나오지 않는다.
 */
@Controller
public class SearchController {

    public record Menu(String name, String keywords, String url) {}

    static final List<Menu> MENUS = List.of(
            new Menu("기안하기", "기안 결재 문서 작성 상신", "/approval/draft"),
            new Menu("결재 대기 문서", "결재 승인 반려 문서함", "/approval?box=wait"),
            new Menu("기록물 등록대장", "문서 등록 기록물 등록대장 생산 접수 등록번호", "/records"),
            new Menu("접수 문서 등록", "문서 등록 접수 종이 팩스 공문", "/records/new?type=RECEIVE"),
            new Menu("지출결의", "지출 결의 집행 예산 지급", "/approval/draft?kind=지출결의"),
            new Menu("민원 목록", "민원 처리 기한", "/civil"),
            new Menu("민원 접수", "민원 접수 창구", "/civil/new"),
            new Menu("증명 발급대장", "증명 발급 등본 초본 인감 수수료", "/civil/certs"),
            new Menu("계약대장", "계약 공사 물품 용역 수의계약 입찰", "/finance/contracts"),
            new Menu("추경·이월", "추가경정예산 추경 이월 명시이월 사고이월", "/finance?area=fin"),
            new Menu("대결자 지정", "대결 직무대리 부재 휴가 출장 전결", "/approval/proxy"),
            new Menu("기록물 생산현황", "생산현황 이관 목록 기록관", "/records/report"),
            new Menu("세출 예산·집행", "재정 예산 편성 집행 회계", "/finance?area=fin"),
            new Menu("세입", "세입 세무 수납 지방세", "/finance?area=rev"),
            new Menu("사업 구조", "정책사업 단위사업 세부사업 재정", "/finance/programs"),
            new Menu("인사·근태", "인사 출근 퇴근 근태 연가", "/hr"),
            new Menu("휴가 신청", "휴가 연가 병가 공가 특별휴가 복무", "/hr/duty/new?kind=ANNUAL"),
            new Menu("출장 신청", "출장 복무", "/hr/duty/new?kind=TRIP"),
            new Menu("시간외근무 신청", "시간외근무 초과근무 야근 복무", "/hr/duty/new?kind=OVERTIME"),
            new Menu("사회복지 급여", "사회복지 기초생활 생계급여 의료급여 주거급여 수급", "/welfare"),
            new Menu("건축·토지", "건축 신고 허가 착공 인허가 토지", "/building"),
            new Menu("환경·위생", "위생 식품 영업신고 음식점 점검 환경", "/hygiene"),
            new Menu("통계·보고", "통계 보고 현황 실적", "/stats"),
            new Menu("공지사항", "게시판 공지", "/board?tab=notice"),
            new Menu("자료실", "게시판 자료 파일", "/board?tab=archive"),
            new Menu("알림", "알림", "/notifications"),
            new Menu("비밀번호 변경", "내 정보 비밀번호", "/mypage/password"));

    private final ApprovalService approvalService;
    private final CivilService civilService;
    private final BoardService boardService;

    public SearchController(ApprovalService approvalService, CivilService civilService, BoardService boardService) {
        this.approvalService = approvalService;
        this.civilService = civilService;
        this.boardService = boardService;
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        model.addAttribute("q", query);
        if (query.isEmpty()) {
            return "main/search";
        }
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        String lower = query.toLowerCase(Locale.ROOT);
        model.addAttribute("menus", MENUS.stream()
                .filter(m -> (m.name() + " " + m.keywords()).toLowerCase(Locale.ROOT).contains(lower)).toList());

        // 결재 문서: 문서함마다 찾아 합친다 (볼 수 있는 문서만)
        Map<Long, ApprovalDocVO> docs = new LinkedHashMap<>();
        for (String box : List.of("wait", "prog", "done", "back", "recv", "temp")) {
            ApprovalSearchVO s = ApprovalSearchVO.of(box, me);
            s.setQ(query);
            approvalService.list(s).forEach(d -> docs.putIfAbsent(d.getDocId(), d));
        }
        model.addAttribute("docs", new ArrayList<>(docs.values()).subList(0, Math.min(20, docs.size())));

        CivilSearchVO cs = CivilSearchVO.forViewer(me);
        cs.setQ(query);
        cs.setLimit(20);
        model.addAttribute("complaints", civilService.search(cs));

        List<BoardPostVO> posts = new ArrayList<>();
        for (String tab : List.of("notice", "suggest", "archive")) {
            posts.addAll(boardService.list(BoardSearchVO.of(tab, query, 1, me).size(10)));
        }
        model.addAttribute("posts", posts);
        return "main/search";
    }
}
