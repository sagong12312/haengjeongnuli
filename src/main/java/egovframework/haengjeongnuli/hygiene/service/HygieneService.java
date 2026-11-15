package egovframework.haengjeongnuli.hygiene.service;

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
import egovframework.haengjeongnuli.hygiene.service.impl.HygieneMapper;

/** 식품 영업소 대장과 위생 점검 (위생 부서 직원·관리자만 고친다) */
@Service
public class HygieneService {

    public record Result(boolean ok, String message, Long id) {}

    private final HygieneMapper mapper;
    private final Set<String> depts;

    public HygieneService(HygieneMapper mapper, @Value("${app.work.hygiene-depts:위생과}") String depts) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public Set<String> getDepts() { return depts; }

    public boolean canEdit(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<FoodBusinessVO> list(String q, String type) {
        return mapper.selectBusinesses(blank(q) ? null : q.trim(), type != null && FoodBusinessVO.TYPES.contains(type) ? type : null, 500);
    }

    public FoodBusinessVO get(long id) {
        return mapper.selectBusiness(id);
    }

    public List<HygieneCheckVO> checks(long bizId) {
        return mapper.selectChecks(bizId);
    }

    @Transactional
    public Result register(String name, String owner, String address, String type, LocalDate reportedOn, String receiptNo,
                           LoginUser by) {
        if (!canEdit(by)) {
            return new Result(false, "영업소 대장은 " + String.join("·", depts) + " 직원과 관리자만 고칠 수 있습니다.", null);
        }
        if (blank(name) || blank(owner) || blank(address) || reportedOn == null) {
            return new Result(false, "상호, 대표자, 소재지, 신고일을 입력하세요.", null);
        }
        if (type == null || !FoodBusinessVO.TYPES.contains(type)) {
            return new Result(false, "업종을 고르세요.", null);
        }
        if (name.length() > 200 || owner.length() > 100 || address.length() > 300 || (receiptNo != null && receiptNo.length() > 20)) {
            return new Result(false, "입력 길이를 확인하세요.", null);
        }
        if (reportedOn.isAfter(LocalDate.now())) {
            return new Result(false, "신고일은 오늘 이전이어야 합니다.", null);
        }
        FoodBusinessVO b = new FoodBusinessVO();
        b.setBizId(mapper.nextBizId());
        b.setBizName(name.trim());
        b.setOwnerName(owner.trim());
        b.setAddress(address.trim());
        b.setBizType(type);
        b.setReportedOn(reportedOn);
        b.setReceiptNo(blank(receiptNo) ? null : receiptNo.trim());
        b.setCreatedBy(by.getLoginId());
        b.setCreatedAt(LocalDateTime.now());
        mapper.insertBusiness(b);
        return new Result(true, "'" + b.getBizName() + "'을(를) 영업소 대장에 올렸습니다.", b.getBizId());
    }

    @Transactional
    public Result close(long id, LocalDate on, LoginUser by) {
        FoodBusinessVO b = get(id);
        if (b == null || !canEdit(by)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        if (on == null || on.isBefore(b.getReportedOn()) || on.isAfter(LocalDate.now())) {
            return new Result(false, "폐업일은 신고일과 오늘 사이여야 합니다.", null);
        }
        return mapper.closeBusiness(id, on) == 1 ? new Result(true, "폐업으로 처리했습니다.", id)
                : new Result(false, "이미 폐업한 영업소입니다.", null);
    }

    @Transactional
    public Result inspect(long bizId, LocalDate on, String result, String violation, String action, LoginUser by) {
        FoodBusinessVO b = get(bizId);
        if (b == null || !canEdit(by)) {
            return new Result(false, "고칠 권한이 없습니다.", null);
        }
        if (!b.isOpen()) {
            return new Result(false, "폐업한 영업소는 점검할 수 없습니다.", null);
        }
        if (on == null || on.isAfter(LocalDate.now()) || on.isBefore(b.getReportedOn())) {
            return new Result(false, "점검일은 신고일과 오늘 사이여야 합니다.", null);
        }
        if (!"적합".equals(result) && !"부적합".equals(result)) {
            return new Result(false, "점검 결과를 고르세요.", null);
        }
        if (action == null || !HygieneCheckVO.ACTIONS.contains(action)) {
            return new Result(false, "조치를 고르세요.", null);
        }
        String v = blank(violation) ? null : violation.trim();
        if ("부적합".equals(result) && v == null) {
            return new Result(false, "부적합이면 위반 내용을 적으세요.", null);
        }
        if ("부적합".equals(result) && "조치 없음".equals(action)) {
            return new Result(false, "부적합이면 조치를 정하세요.", null);
        }
        if (v != null && v.length() > 1000) {
            return new Result(false, "위반 내용은 1,000자 이내로 적으세요.", null);
        }
        HygieneCheckVO c = new HygieneCheckVO();
        c.setCheckId(mapper.nextCheckId());
        c.setBizId(bizId);
        c.setCheckedOn(on);
        c.setResult(result);
        c.setViolation(v);
        c.setAction(action);
        c.setInspectorId(by.getLoginId());
        c.setInspector(by.getUserName());
        c.setCreatedAt(LocalDateTime.now());
        mapper.insertCheck(c);
        return new Result(true, "점검 결과를 기록했습니다.", bizId);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
