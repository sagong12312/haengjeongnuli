package egovframework.haengjeongnuli.approval.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import egovframework.haengjeongnuli.cmm.hwp.HwpMaker;
import egovframework.haengjeongnuli.cmm.hwp.HwpMaker.Line;
import egovframework.haengjeongnuli.cmm.hwp.HwpMaker.Style;
import egovframework.haengjeongnuli.cmm.org.OrgService;

/**
 * 결재 문서·시행문을 한글(HWP) 파일로 만든다. 행정기관명, 수신, (경유), 제목, 본문(항목·표), 붙임, 발신명의,
 * 결재선 서명과 전결·대결 표시, 시행 번호·일자, 주소·연락처·공개 구분을 화면과 같은 순서로 넣는다.
 * 본문의 '|' 줄은 한글 표로 넣는다 (|---| 줄 앞의 첫 줄은 제목 줄).
 * 관인 이미지는 넣지 않는다: 고칠 수 있는 파일에 관인을 넣어 나누면 위조에 쓰일 수 있으므로, 관인이 찍힌 시행문은 인쇄 화면으로만 낸다.
 */
@Service
public class ApprovalHwpService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy. M. d.");

    private final HwpMaker maker;
    private final OrgService org;

    public ApprovalHwpService(HwpMaker maker, OrgService org) {
        this.maker = maker;
        this.org = org;
    }

    /** @param receiver 시행문 수신자 (null 이면 결재 문서 그대로) */
    public byte[] make(ApprovalDocVO d, String receiver) throws Exception {
        return make(d, receiver, null);
    }

    /** mark: 보이지 않는 추적 번호 (기관명 아래 빈 줄 끝에 빈칸으로 숨긴다) */
    public byte[] make(ApprovalDocVO d, String receiver, String mark) throws Exception {
        List<Line> l = new ArrayList<>();
        l.add(Line.title(org.get("orgName", "(행정기관명)")));
        l.add(mark == null ? Line.blank() : Line.of(egovframework.haengjeongnuli.cmm.mark.DownloadMark.spaces(mark)));
        String recv = receiver != null ? receiver : (d.isExternal() ? String.join(", ", d.getReceiverList()) : "내부결재");
        l.add(Line.of("수신  " + recv));
        if (d.getVia() != null) {
            l.add(Line.of("(경유)  " + d.getVia()));
        }
        l.add(Line.bold("제목  " + d.getTitle()));
        l.add(Line.of("────────────────────────────────────────"));
        body(d.getBody(), l);
        if (!d.getFiles().isEmpty()) {
            l.add(Line.blank());
            for (int i = 0; i < d.getFiles().size(); i++) {
                l.add(Line.of((i == 0 ? "붙임  " : "      ") + (i + 1) + ". " + d.getFiles().get(i).getOriginalName() + " 1부."
                        + (i == d.getFiles().size() - 1 ? "  끝." : "")));
            }
        } else {
            l.add(Line.right("끝."));
        }
        if (d.isExternal()) {
            l.add(Line.blank());
            l.add(Line.center(d.getSender() != null ? d.getSender() : org.get("senderTitle", "(발신명의)"), Style.TITLE));
            // 관인 이미지는 고칠 수 있는 파일에 넣지 않는다 (위조 방지). 관인이 찍힌 시행문은 인쇄 화면에서 출력한다
            l.add(Line.center("(관인은 시행문 인쇄 화면에서 찍습니다)", Style.NORMAL));
        }
        l.add(Line.blank());
        l.add(Line.of("────────────────────────────────────────"));
        StringBuilder signs = new StringBuilder((d.getDrafterPosition() == null ? "기안자" : d.getDrafterPosition()) + " " + d.getDrafterName());
        for (ApprovalLineVO line : d.getLines()) {
            signs.append("    ").append("협조".equals(line.getRole()) ? "협조자" : line.getApproverPosition() == null ? line.getRole() : line.getApproverPosition());
            if (line.isFinalRole() && d.isDelegated()) {
                signs.append(" 전결");
            }
            if (line.isProxyActed()) {
                signs.append(" 대결 ").append(line.getActedByName());
            } else {
                signs.append(" ").append(line.getApproverName());
            }
            if (line.getActedAt() != null) {
                signs.append(" ").append(line.getActedAt().format(DateTimeFormatter.ofPattern("M. d.")));
            }
        }
        l.add(Line.of(signs.toString()));
        l.add(Line.of("시행 " + (d.getDocNo() == null ? "(결재 후 부여)" : d.getDocNo())
                + (d.getCompletedAt() == null ? "" : " (" + d.getCompletedAt().format(DATE) + ")") + "    접수 (        )"));
        String addr = (org.get("zipCode", "") + " " + org.get("address", "")).strip();
        if (!addr.isEmpty() || org.get("homepage") != null) {
            l.add(Line.of(addr + (org.get("homepage") != null ? "  / " + org.get("homepage") : "")));
        }
        l.add(Line.of("전화 " + org.get("phone", "—") + "  전송 " + org.get("fax", "—") + "  / " + d.getDisclosure()
                + (d.getRestrictReasonList().isEmpty() ? "" : "(" + String.join(",", d.getRestrictReasonList()) + ")")));
        return maker.make(l);
    }

    /** 본문을 문단으로, 이어진 '|' 줄은 표 하나로 */
    static void body(String text, List<Line> out) {
        String[] lines = (text == null ? "" : text).replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].strip().startsWith("|")) {
                out.add(Line.of(lines[i]));
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            while (i < lines.length && lines[i].strip().startsWith("|")) {
                String t = lines[i].strip();
                if (!t.matches("\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?")) {
                    String inner = t.substring(1, t.endsWith("|") && t.length() > 1 ? t.length() - 1 : t.length());
                    List<String> cells = new ArrayList<>();
                    for (String c : inner.split("\\|", -1)) {
                        cells.add(c.strip());
                    }
                    rows.add(cells);
                }
                i++;
            }
            i--;
            if (!rows.isEmpty()) {
                out.add(Line.table(rows));
            }
        }
    }
}
