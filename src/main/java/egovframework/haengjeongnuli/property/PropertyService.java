package egovframework.haengjeongnuli.property;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 공유재산 대장과 사용허가·대부.
 * 행정재산은 목적·용도에 장애가 없는 범위에서 사용허가(공유재산 및 물품 관리법 제20조), 사용료는 매년(제22조),
 * 연간 사용료는 평정가격의 연 1천분의 10 이상 범위에서 조례로 정한다(시행령 제14조). 그보다 적게 받으면 감면 근거를 적는다.
 * 일반재산은 대부로 기록한다.
 */
@Service
public class PropertyService {

    public record Result(boolean ok, String message, Long id) {}

    private final PropertyMapper mapper;
    private final Set<String> depts;

    public PropertyService(PropertyMapper mapper, @Value("${app.work.property-depts:회계과}") String depts) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public Set<String> getDepts() { return depts; }

    /** 재산 총괄 부서·관리자는 모든 재산, 관리부서 직원은 자기 부서 재산 */
    public boolean canEdit(PropertyVO p, LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept()) || (p != null && !u.dept().isEmpty() && u.dept().equals(p.getDeptName()));
    }

    public boolean canRegister(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<PropertyVO> list(String cls, String q, boolean all) {
        String c = cls != null && PropertyVO.CLASSES.contains(cls) ? cls : null;
        String like = trim(q) == null ? null : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return mapper.selectList(c, like, all);
    }

    public PropertyVO get(long id) { return mapper.selectById(id); }
    public List<PropertyUseVO> uses(long id) { return mapper.selectUses(id); }

    /** 끝나는 날이 30일 안에 오거나 지난 허가·대부 */
    public List<PropertyUseVO> ending() { return mapper.selectEnding(LocalDate.now().plusDays(30)); }

    @Transactional
    public Result register(PropertyVO in, LoginUser me) {
        if (!canRegister(me)) {
            return new Result(false, "공유재산 대장은 " + String.join("·", depts) + " 직원과 관리자만 올립니다.", null);
        }
        if (in.getPropKind() == null || !PropertyVO.KINDS.contains(in.getPropKind())
                || in.getPropClass() == null || !PropertyVO.CLASSES.contains(in.getPropClass())) {
            return new Result(false, "재산 종류와 구분(행정재산·일반재산)을 고르세요.", null);
        }
        String name = trim(in.getName()), loc = trim(in.getLocation()), dept = trim(in.getDeptName());
        if (name == null || name.length() > 200 || loc == null || loc.length() > 300 || dept == null || dept.length() > 100) {
            return new Result(false, "재산명, 소재지, 관리부서를 입력하세요.", null);
        }
        if (in.getAppraised() < 0 || in.getAppraised() > 999_999_999_999_999L) {
            return new Result(false, "평정가격(재산 가격)을 원 단위로 입력하세요.", null);
        }
        if (in.getArea() != null && (in.getArea().signum() < 0 || in.getArea().compareTo(new BigDecimal("9999999999")) > 0)) {
            return new Result(false, "면적을 바르게 입력하세요.", null);
        }
        if (in.getAcquiredOn() == null || in.getAcquiredOn().isAfter(LocalDate.now())) {
            return new Result(false, "취득일은 오늘 이전이어야 합니다.", null);
        }
        String purpose = trim(in.getUsePurpose());
        if (purpose != null && purpose.length() > 300) {
            return new Result(false, "용도는 300자 이내로 적으세요.", null);
        }
        PropertyVO p = new PropertyVO();
        p.setPropId(mapper.nextId());
        p.setPropNo("재산-" + in.getAcquiredOn().getYear() + "-" + String.format("%05d", p.getPropId()));
        p.setPropKind(in.getPropKind());
        p.setPropClass(in.getPropClass());
        p.setName(name);
        p.setLocation(loc);
        p.setArea(in.getArea());
        p.setAppraised(in.getAppraised());
        p.setAcquiredOn(in.getAcquiredOn());
        p.setDeptName(dept);
        p.setUsePurpose(purpose);
        p.setCreatedBy(me.getLoginId());
        p.setCreatedAt(LocalDateTime.now());
        mapper.insert(p);
        return new Result(true, "공유재산 대장에 올렸습니다. " + p.getPropNo(), p.getPropId());
    }

    @Transactional
    public Result updateValue(long id, Long appraised, String purpose, LoginUser me) {
        PropertyVO p = get(id);
        if (p == null || !p.isHeld() || !canEdit(p, me)) {
            return new Result(false, "보유 중인 재산을 고칠 권한이 없습니다.", null);
        }
        if (appraised == null || appraised < 0) {
            return new Result(false, "평정가격을 원 단위로 입력하세요.", null);
        }
        String pp = trim(purpose);
        mapper.updateValue(id, appraised, pp == null ? null : pp.length() > 300 ? pp.substring(0, 300) : pp);
        return new Result(true, "평정가격·용도를 고쳤습니다.", id);
    }

    /** 사용허가(행정재산) 또는 대부(일반재산) */
    @Transactional
    public Result addUse(long id, String userName, String purpose, LocalDate start, LocalDate end, Long fee, String feeNote, LoginUser me) {
        PropertyVO p = get(id);
        if (p == null || !p.isHeld() || !canEdit(p, me)) {
            return new Result(false, "보유 중인 재산에만 " + (p == null ? "사용허가·대부" : p.getUseType()) + "를 기록할 수 있습니다.", null);
        }
        String user = trim(userName), pur = trim(purpose), note = trim(feeNote);
        if (user == null || user.length() > 200 || pur == null || pur.length() > 300) {
            return new Result(false, "상대방과 사용 목적을 입력하세요.", null);
        }
        if (start == null || end == null || end.isBefore(start)) {
            return new Result(false, "기간(시작일·끝나는 날)을 바르게 입력하세요.", null);
        }
        if (fee == null || fee < 0) {
            return new Result(false, "연간 " + ("사용허가".equals(p.getUseType()) ? "사용료" : "대부료") + "를 원 단위로 입력하세요 (무상이면 0).", null);
        }
        if ("사용허가".equals(p.getUseType()) && fee < p.getMinAnnualFee() && note == null) {
            return new Result(false, String.format("연간 사용료가 평정가격의 1천분의 10(%,d원)보다 적습니다. 감면·무상 근거(조례·제24조 등)를 적으세요.",
                    p.getMinAnnualFee()), null);
        }
        if (note != null && note.length() > 300) {
            return new Result(false, "근거는 300자 이내로 적으세요.", null);
        }
        if (mapper.countOverlap(id, start, end) > 0) {
            return new Result(false, "같은 기간에 이미 진행 중인 " + p.getUseType() + "가 있습니다.", null);
        }
        PropertyUseVO u = new PropertyUseVO();
        u.setUseId(mapper.nextUseId());
        u.setPropId(id);
        u.setUseType(p.getUseType());
        u.setUserName(user);
        u.setPurpose(pur);
        u.setStartDate(start);
        u.setEndDate(end);
        u.setAnnualFee(fee);
        u.setFeeNote(note);
        u.setCreatedBy(me.getLoginId());
        u.setCreatedAt(LocalDateTime.now());
        mapper.insertUse(u);
        return new Result(true, p.getUseType() + "를 기록했습니다: " + user + " " + start + " ~ " + end, id);
    }

    @Transactional
    public Result endUse(long useId, String note, LoginUser me) {
        PropertyUseVO u = mapper.selectUse(useId);
        PropertyVO p = u == null ? null : get(u.getPropId());
        if (p == null || !canEdit(p, me)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        String n = trim(note);
        if (n == null || n.length() > 300) {
            return new Result(false, "종료 사유(기간 만료·반환·허가 취소 등)를 적으세요.", null);
        }
        return mapper.endUse(useId, n) == 1 ? new Result(true, "끝난 것으로 기록했습니다.", p.getPropId())
                : new Result(false, "이미 끝난 " + u.getUseType() + "입니다.", null);
    }

    @Transactional
    public Result dispose(long id, LocalDate on, String reason, LoginUser me) {
        PropertyVO p = get(id);
        if (p == null || !p.isHeld() || !(me.isAdmin() || depts.contains(me.dept()))) {
            return new Result(false, "재산 처분은 " + String.join("·", depts) + " 직원과 관리자만 기록합니다.", null);
        }
        String r = trim(reason);
        if (on == null || on.isAfter(LocalDate.now()) || on.isBefore(p.getAcquiredOn()) || r == null || r.length() > 500) {
            return new Result(false, "처분일과 처분 내용(매각·교환·양여 등과 근거 문서)을 적으세요.", null);
        }
        if (mapper.countActive(id) > 0) {
            return new Result(false, "진행 중인 사용허가·대부를 먼저 끝내세요.", null);
        }
        mapper.dispose(id, on, r);
        return new Result(true, p.getPropNo() + " 처분을 기록했습니다.", id);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
