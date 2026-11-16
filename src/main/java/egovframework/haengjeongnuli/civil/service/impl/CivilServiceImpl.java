package egovframework.haengjeongnuli.civil.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilForm;
import egovframework.haengjeongnuli.civil.service.CivilKind;
import egovframework.haengjeongnuli.civil.service.CivilSearchVO;
import egovframework.haengjeongnuli.civil.service.CivilService;
import egovframework.haengjeongnuli.civil.service.CivilStatus;
import egovframework.haengjeongnuli.civil.service.CivilSummaryVO;
import egovframework.haengjeongnuli.civil.service.DeadlineCalculator;

@Service("civilService")
public class CivilServiceImpl implements CivilService {

    private final CivilMapper civilMapper;
    private final DeadlineCalculator deadlineCalculator;
    private final int imminentDays;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CivilServiceImpl(CivilMapper civilMapper, DeadlineCalculator deadlineCalculator,
                            @Value("${app.civil.imminent-days:2}") int imminentDays) {
        this(civilMapper, deadlineCalculator, imminentDays, Clock.systemDefaultZone());
    }

    CivilServiceImpl(CivilMapper civilMapper, DeadlineCalculator deadlineCalculator, int imminentDays, Clock clock) {
        this.civilMapper = civilMapper;
        this.deadlineCalculator = deadlineCalculator;
        this.imminentDays = imminentDays;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CivilComplaintVO receive(CivilForm form, String registerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long id = civilMapper.nextId();

        CivilComplaintVO vo = new CivilComplaintVO();
        vo.setComplaintId(id);
        vo.setReceiptNo(String.format("%d-%06d", now.getYear(), id));
        vo.setKind(form.getKind());
        vo.setLawInterpretYn(form.getKind() == CivilKind.QUERY && form.isLawInterpretation() ? "Y" : "N");
        vo.setLegalAffair(form.getKind() == CivilKind.LEGAL ? form.getAffair() : null);
        vo.setRoute(form.getRoute());
        vo.setApplicantName(form.getName().trim());
        // 전화번호는 010-1234-5678 꼴로 통일해 저장한다 (검증은 CivilForm 에서 끝남)
        vo.setMobile(form.isNoMobile() ? null : egovframework.haengjeongnuli.cmm.text.PhoneNumbers.mobile(form.getMobile()));
        vo.setHomePhone(form.isNoMobile() && !form.isNoHome() ? egovframework.haengjeongnuli.cmm.text.PhoneNumbers.phone(form.getHomePhone()) : null);
        vo.setEmail(blankToNull(form.getEmail()));
        vo.setAddress(blankToNull(form.getAddress()));
        vo.setFaxNo(egovframework.haengjeongnuli.cmm.text.PhoneNumbers.phone(form.getFax()));
        vo.setNotifyMethod(form.getNotify());
        vo.setTitle(form.getTitle().trim());
        vo.setContent(form.getContent());
        vo.setDept(blankToNull(form.getDept()));
        vo.setAssignee(blankToNull(form.getStaff()));
        vo.setAssigneeId(vo.getAssignee() == null ? null : blankToNull(form.getStaffId()));
        vo.setStatus(vo.getAssignee() == null ? CivilStatus.RECEIVED : CivilStatus.IN_PROGRESS);
        vo.setReceivedAt(now);
        vo.setPeriodDays(form.getKind() == CivilKind.LEGAL ? form.getAffair().periodDays()
                : form.getKind().periodDays(form.isLawInterpretation()));
        LocalDateTime deadlineAt = deadlineFor(form, now);
        vo.setDeadlineAt(deadlineAt);
        vo.setDeadline(deadlineAt.toLocalDate());
        vo.setRegisterId(registerId);

        civilMapper.insertComplaint(vo);
        return vo;
    }

    /** 처리기한 시각. 법정민원은 사무별 기간, 나머지는 종류별 기간으로 계산 (민원처리법 제19조) */
    LocalDateTime deadlineFor(CivilForm form, LocalDateTime received) {
        int days = form.getKind() == CivilKind.LEGAL
                ? form.getAffair().periodDays()
                : form.getKind().periodDays(form.isLawInterpretation());
        return deadlineCalculator.deadlineAt(received, days);
    }

    @Override
    public List<CivilComplaintVO> search(CivilSearchVO search) {
        LocalDate until = LocalDate.now(clock).plusDays(imminentDays);
        List<CivilComplaintVO> list = civilMapper.selectComplaintList(search);
        list.forEach(c -> c.setImminent(c.getStatus() != CivilStatus.DONE
                && c.getDeadline() != null && !c.getDeadline().isAfter(until)));
        return list;
    }

    @Override
    public int count(CivilSearchVO search) {
        return civilMapper.selectComplaintCount(search);
    }

    @Override
    public CivilSummaryVO summary(egovframework.haengjeongnuli.cmm.LoginUser viewer) {
        LocalDate today = LocalDate.now(clock);
        return civilMapper.selectSummary(CivilSearchVO.forViewer(viewer), today.plusDays(imminentDays), today.withDayOfMonth(1));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
