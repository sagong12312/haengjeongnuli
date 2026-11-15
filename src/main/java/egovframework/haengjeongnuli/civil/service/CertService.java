package egovframework.haengjeongnuli.civil.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.impl.CertMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;

/** 증명 발급대장 (창구 직원·관리자) */
@Service
public class CertService {

    public record Result(boolean ok, String message, String issueNo) {}

    private final CertMapper mapper;

    public CertService(CertMapper mapper) {
        this.mapper = mapper;
    }

    public boolean canUse(LoginUser u) {
        return u.isCounterStaff() || u.isAdmin();
    }

    public List<CertIssueVO> ofDay(LocalDate day) {
        return between(day, day.plusDays(1));
    }

    /** from 이상 to 미만 (최대 20000건) */
    public List<CertIssueVO> between(LocalDate from, LocalDate to) {
        return mapper.selectBetween(from.atStartOfDay(), to.atStartOfDay());
    }

    @Transactional
    public Result issue(String type, String name, String applicantType, String purpose, Integer copies, boolean idChecked,
                        boolean exempt, String exemptReason, LoginUser me) {
        if (type == null || !CertIssueVO.FEES.containsKey(type)) {
            return new Result(false, "증명 종류를 고르세요.", null);
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            return new Result(false, "신청인 이름을 입력하세요.", null);
        }
        if (applicantType == null || !CertIssueVO.APPLICANT_TYPES.contains(applicantType)) {
            return new Result(false, "신청인 구분을 고르세요.", null);
        }
        if (copies == null || copies < 1 || copies > 100) {
            return new Result(false, "통수는 1~100 사이로 입력하세요.", null);
        }
        if (!idChecked) {
            return new Result(false, "신청인 신분증을 확인했는지 표시하세요.", null);
        }
        if (purpose != null && purpose.trim().length() > 200) {
            return new Result(false, "용도는 200자 이내로 입력하세요.", null);
        }
        String why = exemptReason == null || exemptReason.isBlank() ? null : exemptReason.trim();
        if (exempt && (why == null || why.length() > 200)) {
            return new Result(false, "수수료 면제 사유를 200자 이내로 입력하세요.", null);
        }
        LocalDateTime now = LocalDateTime.now();
        CertIssueVO v = new CertIssueVO();
        v.setIssueId(mapper.nextId());
        v.setIssueYear(now.getYear());
        v.setIssueNo(now.getYear() + "-" + String.format("%06d", v.getIssueId()));
        v.setCertType(type);
        v.setApplicantName(n);
        v.setApplicantType(applicantType);
        v.setPurpose(purpose == null || purpose.isBlank() ? null : purpose.trim());
        v.setCopies(copies);
        v.setFee(exempt ? 0 : CertIssueVO.standardFee(type, applicantType, copies));
        v.setExemptReason(exempt ? why : null);
        v.setIdCheckedYn("Y");
        v.setDeptName(me.getDeptName());
        v.setIssuedBy(me.getLoginId());
        v.setIssuedName(me.getUserName());
        v.setIssuedAt(now);
        mapper.insert(v);
        return new Result(true, String.format("발급을 기록했습니다. 발급번호 %s, 수수료 %,d원", v.getIssueNo(), v.getFee()), v.getIssueNo());
    }
}
