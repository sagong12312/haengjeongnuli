package egovframework.haengjeongnuli.welfare.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.welfare.service.impl.WelfareMapper;

/**
 * 기초생활보장 급여 신청 처리 (국민기초생활 보장법).
 * <ul>
 *   <li>결정 통지는 신청일부터 30일 이내 (제26조 제4항). 부양의무자 조사에 시일이 걸리는 특별한 사유,
 *       조사·자료제출 거부·방해·기피가 있으면 사유를 밝혀 60일 이내</li>
 *   <li>통지는 서면으로, 결정의 요지(산출 근거 포함)·급여의 종류·방법·개시 시기를 적는다 (제26조 제3항)</li>
 *   <li>급여는 신청일부터 시작한다 (제27조 제1항 본문)</li>
 *   <li>이의신청: 결정 통지를 받은 날부터 90일 이내 시·도지사에게 (제38조)</li>
 * </ul>
 * 기간은 "신청일부터 30일"을 신청일에 30일을 더한 날로 계산했다(첫날 불산입).
 * 민감한 개인정보를 다루므로 사회복지 담당으로 지정된 직원만 쓴다.
 */
@Service
public class WelfareService {

    public record Result(boolean ok, String message, Long id) {}

    /** 통지 기한 연장 사유 (제26조 제4항 단서) */
    public static final List<String> EXTEND_REASONS = List.of(
            "부양의무자의 소득·재산 등의 조사에 시일이 걸리는 특별한 사유가 있는 경우",
            "수급권자 또는 부양의무자가 조사나 자료제출 요구를 거부·방해 또는 기피하는 경우");

    public static final String OBJECTION_GUIDE =
            "이 결정에 이의가 있으면 결정 통지를 받은 날부터 90일 이내에 보장기관을 거쳐 시·도지사에게 서면 또는 구두로 이의신청을 할 수 있습니다 (국민기초생활 보장법 제38조).";

    private final WelfareMapper mapper;

    public WelfareService(WelfareMapper mapper) {
        this.mapper = mapper;
    }

    public boolean canAccess(LoginUser u) {
        return u != null && u.isWelfareStaff();
    }

    public List<WelfareCaseVO> list(String status, String q) {
        return mapper.selectList(status == null || status.isBlank() ? null : status, q == null || q.isBlank() ? null : q.trim(), 300);
    }

    public WelfareCaseVO get(long id) {
        return mapper.select(id);
    }

    @Transactional
    public Result apply(String name, LocalDate birth, String address, String phone, LocalDate appliedOn, List<String> benefits,
                        LoginUser by) {
        if (!canAccess(by)) {
            return new Result(false, "사회복지 담당 직원만 접수할 수 있습니다.", null);
        }
        if (blank(name) || name.length() > 100 || appliedOn == null) {
            return new Result(false, "신청인 이름과 신청일을 입력하세요.", null);
        }
        if (appliedOn.isAfter(LocalDate.now())) {
            return new Result(false, "신청일은 오늘 이전이어야 합니다.", null);
        }
        if (birth != null && birth.isAfter(LocalDate.now())) {
            return new Result(false, "생년월일을 확인하세요.", null);
        }
        if (benefits == null || benefits.isEmpty() || !WelfareCaseVO.BENEFITS.containsAll(benefits)) {
            return new Result(false, "신청하는 급여를 하나 이상 고르세요.", null);
        }
        if ((address != null && address.length() > 300) || (phone != null && phone.length() > 20)) {
            return new Result(false, "입력 길이를 확인하세요.", null);
        }
        WelfareCaseVO c = new WelfareCaseVO();
        c.setCaseId(mapper.nextId());
        c.setCaseNo(String.format("복지%d-%06d", appliedOn.getYear(), c.getCaseId()));
        c.setApplicantName(name.trim());
        c.setBirthDate(birth);
        c.setAddress(blank(address) ? null : address.trim());
        c.setPhone(blank(phone) ? null : phone.trim());
        c.setAppliedOn(appliedOn);
        c.setBenefits(String.join(",", WelfareCaseVO.BENEFITS.stream().filter(benefits::contains).toList()));
        c.setDeadline(appliedOn.plusDays(30));
        c.setAssigneeId(by.getLoginId());
        c.setAssigneeName(by.getUserName());
        c.setCreatedBy(by.getLoginId());
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        return new Result(true, "접수했습니다. 접수번호 " + c.getCaseNo() + ", 결정 통지 기한 " + c.getDeadline(), c.getCaseId());
    }

    @Transactional
    public Result survey(long id, String note, LoginUser by) {
        WelfareCaseVO c = get(id);
        if (c == null || !canAccess(by)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (blank(note) || note.length() > 20000) {
            return new Result(false, "조사 내용을 적으세요.", null);
        }
        return mapper.updateSurvey(id, note.trim()) == 1 ? new Result(true, "조사 내용을 저장했습니다.", id)
                : new Result(false, "결정이 끝난 신청입니다.", null);
    }

    /** 통지 기한을 신청일부터 60일로 늘린다 (사유 명시, 한 번) */
    @Transactional
    public Result extend(long id, Integer reasonNo, String detail, LoginUser by) {
        WelfareCaseVO c = get(id);
        if (c == null || !canAccess(by)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (reasonNo == null || reasonNo < 0 || reasonNo >= EXTEND_REASONS.size()) {
            return new Result(false, "법에서 정한 연장 사유를 고르세요.", null);
        }
        if (blank(detail) || detail.length() > 300) {
            return new Result(false, "통지서에 적을 구체적인 사유를 300자 이내로 적으세요.", null);
        }
        if (c.getExtendReason() != null) {
            return new Result(false, "이미 연장했습니다 (신청일부터 60일까지).", null);
        }
        c.setDeadline(c.getAppliedOn().plusDays(60));
        c.setExtendReason(EXTEND_REASONS.get(reasonNo) + " — " + detail.trim());
        return mapper.extend(c) == 1 ? new Result(true, "통지 기한을 " + c.getDeadline() + "까지로 늘렸습니다.", id)
                : new Result(false, "결정이 끝났거나 이미 연장한 신청입니다.", null);
    }

    @Transactional
    public Result decide(long id, boolean grant, List<String> granted, String summary, String method, LocalDate start,
                         LocalDate notifiedOn, LoginUser by) {
        WelfareCaseVO c = get(id);
        if (c == null || !canAccess(by)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (!c.isOpen()) {
            return new Result(false, "이미 결정한 신청입니다.", null);
        }
        if (blank(summary) || summary.length() > 1300) {
            return new Result(false, grant ? "결정의 요지와 급여의 산출 근거를 적으세요 (제26조 제3항)." : "부적합 결정의 요지와 이유를 적으세요.", null);
        }
        if (notifiedOn == null || notifiedOn.isBefore(c.getAppliedOn()) || notifiedOn.isAfter(LocalDate.now())) {
            return new Result(false, "신청인에게 서면으로 통지한 날을 입력하세요.", null);
        }
        c.setStatus(grant ? "GRANTED" : "DENIED");
        if (grant) {
            if (granted == null || granted.isEmpty() || !c.getBenefitList().containsAll(granted)) {
                return new Result(false, "지급할 급여를 신청한 급여 중에서 고르세요.", null);
            }
            if (blank(method) || method.length() > 500) {
                return new Result(false, "급여의 방법을 적으세요 (예: 매월 20일 계좌 입금).", null);
            }
            c.setGrantedBenefits(String.join(",", c.getBenefitList().stream().filter(granted::contains).toList()));
            c.setBenefitMethod(method.trim());
            // 급여는 신청일부터 시작한다 (제27조 제1항)
            c.setBenefitStart(start == null ? c.getAppliedOn() : start);
        }
        c.setDecisionSummary(summary.trim());
        c.setDecidedOn(LocalDate.now());
        c.setNotifiedOn(notifiedOn);
        if (mapper.decide(c) != 1) {
            return new Result(false, "이미 결정한 신청입니다.", null);
        }
        boolean late = notifiedOn.isAfter(c.getDeadline());
        return new Result(true, (grant ? "지급으로 결정했습니다." : "부적합으로 결정했습니다.")
                + (late ? " 통지 기한(" + c.getDeadline() + ")을 넘겼습니다." : ""), id);
    }

    @Transactional
    public Result withdraw(long id, String note, LoginUser by) {
        WelfareCaseVO c = get(id);
        if (c == null || !canAccess(by)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (blank(note) || note.length() > 500) {
            return new Result(false, "취하 사유를 적으세요.", null);
        }
        return mapper.withdraw(id, "취하: " + note.trim()) == 1 ? new Result(true, "취하로 처리했습니다.", id)
                : new Result(false, "결정이 끝난 신청입니다.", null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
