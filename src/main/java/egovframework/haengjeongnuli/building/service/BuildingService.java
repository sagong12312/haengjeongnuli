package egovframework.haengjeongnuli.building.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.building.service.impl.BuildingMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 건축 인허가 대장.
 * 착수기한: 허가는 허가일부터 2년(공장 승인은 3년) — 건축법 제11조 제7항 제1호,
 * 신고는 신고일부터 1년 — 같은 법 제14조 제5항. 정당한 사유가 있으면 1년 범위에서 한 번 연장한다.
 */
@Service
public class BuildingService {

    public record Result(boolean ok, String message) {}

    private final BuildingMapper mapper;
    private final Set<String> depts;

    public BuildingService(BuildingMapper mapper, @Value("${app.work.building-depts:건축과}") String depts) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public Set<String> getDepts() { return depts; }

    public boolean canEdit(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<BuildingCaseVO> list(String q) {
        return mapper.selectList(q == null || q.isBlank() ? null : q.trim(), 300);
    }

    public BuildingCaseVO get(long id) {
        return mapper.select(id);
    }

    /** 허가일(신고 수리일)로 착수기한을 정한다 */
    public static LocalDate startDeadline(String kind, boolean factory, LocalDate decidedOn) {
        if (BuildingCaseVO.PERMIT.equals(kind)) {
            return decidedOn.plusYears(factory ? 3 : 2);
        }
        return decidedOn.plusYears(1);
    }

    @Transactional
    public Result register(String kind, String receiptNo, String owner, String site, String use, Double area,
                           Integer floors, boolean factory, LocalDate decidedOn, String note, LoginUser by) {
        if (!canEdit(by)) {
            return new Result(false, "건축 인허가 대장은 " + String.join("·", depts) + " 직원과 관리자만 고칠 수 있습니다.");
        }
        if (!BuildingCaseVO.PERMIT.equals(kind) && !BuildingCaseVO.REPORT.equals(kind)) {
            return new Result(false, "허가·신고 구분을 고르세요.");
        }
        if (blank(owner) || blank(site) || blank(use) || decidedOn == null) {
            return new Result(false, "건축주, 대지위치, 용도, 허가(수리)일을 입력하세요.");
        }
        if (owner.length() > 100 || site.length() > 300 || use.length() > 100 || (note != null && note.length() > 1000)
                || (receiptNo != null && receiptNo.length() > 20)) {
            return new Result(false, "입력 길이를 확인하세요.");
        }
        if (decidedOn.isAfter(LocalDate.now())) {
            return new Result(false, "허가(수리)일은 오늘 이전이어야 합니다.");
        }
        if ((area != null && (area <= 0 || area > 10_000_000)) || (floors != null && (floors < 1 || floors > 300))) {
            return new Result(false, "연면적·층수를 확인하세요.");
        }
        BuildingCaseVO c = new BuildingCaseVO();
        c.setCaseId(mapper.nextId());
        c.setKind(kind);
        c.setReceiptNo(blank(receiptNo) ? null : receiptNo.trim());
        c.setOwnerName(owner.trim());
        c.setSite(site.trim());
        c.setBuildingUse(use.trim());
        c.setFloorArea(area);
        c.setFloors(floors);
        c.setFactoryYn(factory && BuildingCaseVO.PERMIT.equals(kind) ? "Y" : "N");
        c.setDecidedOn(decidedOn);
        c.setStartDeadline(startDeadline(kind, factory, decidedOn));
        c.setNote(blank(note) ? null : note.trim());
        c.setCreatedBy(by.getLoginId());
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        return new Result(true, c.getKindLabel() + " 대장에 올렸습니다. 착수기한 " + c.getStartDeadline());
    }

    @Transactional
    public Result started(long id, LocalDate on, LoginUser by) {
        BuildingCaseVO c = get(id);
        if (c == null || !canEdit(by)) {
            return new Result(false, "고칠 권한이 없습니다.");
        }
        if (on == null || on.isBefore(c.getDecidedOn()) || on.isAfter(LocalDate.now())) {
            return new Result(false, "착공일은 허가(수리)일과 오늘 사이여야 합니다.");
        }
        if (on.isAfter(c.getStartDeadline())) {
            return new Result(false, "착공일이 착수기한(" + c.getStartDeadline() + ")을 넘었습니다. 연장 여부를 먼저 확인하세요.");
        }
        return mapper.markStarted(id, on) == 1 ? new Result(true, "착공으로 기록했습니다.")
                : new Result(false, "착공 전 상태인 건만 기록할 수 있습니다.");
    }

    /** 착수기한 연장: 정당한 사유가 있으면 1년 범위에서 한 번 */
    @Transactional
    public Result extend(long id, int months, String reason, LoginUser by) {
        BuildingCaseVO c = get(id);
        if (c == null || !canEdit(by)) {
            return new Result(false, "고칠 권한이 없습니다.");
        }
        if ("Y".equals(c.getExtendedYn())) {
            return new Result(false, "착수기한은 이미 연장했습니다.");
        }
        if (months < 1 || months > 12) {
            return new Result(false, "연장은 1년(12개월) 범위에서 정합니다.");
        }
        if (blank(reason) || reason.length() > 500) {
            return new Result(false, "연장하는 정당한 사유를 적으세요.");
        }
        LocalDate deadline = c.getStartDeadline().plusMonths(months);
        if (mapper.extend(id, deadline) == 0) {
            return new Result(false, "착공 전 상태인 건만 연장할 수 있습니다.");
        }
        String note = (c.getNote() == null ? "" : c.getNote() + " / ") + "착수기한 " + months + "개월 연장: " + reason.trim();
        mapper.updateNote(id, note.length() > 1000 ? note.substring(0, 1000) : note);
        return new Result(true, "착수기한을 " + deadline + "까지 연장했습니다.");
    }

    /** 허가 취소·신고 효력 상실 처리 */
    @Transactional
    public Result cancel(long id, String reason, LoginUser by) {
        BuildingCaseVO c = get(id);
        if (c == null || !canEdit(by)) {
            return new Result(false, "고칠 권한이 없습니다.");
        }
        if (blank(reason) || reason.length() > 500) {
            return new Result(false, "사유를 적으세요.");
        }
        String note = (c.getNote() == null ? "" : c.getNote() + " / ") + (BuildingCaseVO.PERMIT.equals(c.getKind()) ? "허가 취소: " : "효력 상실: ") + reason.trim();
        return mapper.cancel(id, note.length() > 1000 ? note.substring(0, 1000) : note) == 1
                ? new Result(true, BuildingCaseVO.PERMIT.equals(c.getKind()) ? "허가 취소로 처리했습니다." : "신고 효력 상실로 처리했습니다.")
                : new Result(false, "착공 전 상태인 건만 처리할 수 있습니다.");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
