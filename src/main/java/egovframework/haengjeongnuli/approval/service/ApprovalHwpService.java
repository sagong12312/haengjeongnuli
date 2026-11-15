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
 * 표는 칸을 공백으로 나눈 줄로 옮긴다 (한글에서 표로 바꾸어 쓸 수 있다).
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
        List<Line> l = new ArrayList<>();
        l.add(Line.title(org.get("orgName", "(행정기관명)")));
        l.add(Line.blank());
        String recv = receiver != null ? receiver : (d.isExternal() ? String.join(", ", d.getReceiverList()) : "내부결재");
        l.add(Line.of("수신  " + recv));
        if (d.getVia() != null) {
            l.add(Line.of("(경유)  " + d.getVia()));
        }
        l.add(Line.bold("제목  " + d.getTitle()));
        l.add(Line.of("────────────────────────────────────────"));
        for (String line : (d.getBody() == null ? "" : d.getBody()).replace("\r\n", "\n").split("\n", -1)) {
            l.add(Line.of(tableRow(line)));
        }
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

    /** '| 구분 | 금액 |' 줄은 칸을 공백으로 나누고, '|---|' 줄은 가로줄로 */
    static String tableRow(String line) {
        String t = line.strip();
        if (!t.startsWith("|")) {
            return line;
        }
        if (t.matches("\\|?\\s*:?-{3,}.*")) {
            return "  " + "─".repeat(30);
        }
        String inner = t.substring(1, t.endsWith("|") ? t.length() - 1 : t.length());
        StringBuilder b = new StringBuilder("  ");
        for (String c : inner.split("\\|", -1)) {
            b.append(c.strip()).append("    ");
        }
        return b.toString().stripTrailing();
    }
}
