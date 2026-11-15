package egovframework.haengjeongnuli.hr.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.holiday.HolidayService;
import egovframework.haengjeongnuli.hr.service.impl.DutyMapper;

/**
 * 복무 신청: 신청 내용을 확인하고 전자결재 문서('복무 신청')로 올린다. 결재 결과가 곧 신청 결과다.
 * 연가는 남은 일수(부여 − 승인 − 결재 중) 안에서, 병가는 연 60일 안에서만 신청할 수 있다.
 */
@Service
public class DutyService {

    public record Result(boolean ok, String message, Long docId) {}

    /** 연가 현황 */
    public record LeaveSummary(Integer granted, double used, double pending) {
        public Double remaining() {
            return granted == null ? null : granted - used - pending;
        }
    }

    private final DutyMapper mapper;
    private final ApprovalService approvalService;
    private final HolidayService holidays;

    public DutyService(DutyMapper mapper, ApprovalService approvalService, HolidayService holidays) {
        this.mapper = mapper;
        this.approvalService = approvalService;
        this.holidays = holidays;
    }

    public LeaveSummary leave(LoginUser u, int year) {
        return new LeaveSummary(LeavePolicy.granted(u.getHiredOn(), year),
                nz(mapper.sumDays(u.getLoginId(), DutyKind.ANNUAL.name(), year, "DONE")),
                nz(mapper.sumDays(u.getLoginId(), DutyKind.ANNUAL.name(), year, "PROG")));
    }

    public double sickUsed(LoginUser u, int year) {
        return nz(mapper.sumDays(u.getLoginId(), DutyKind.SICK.name(), year, "DONE"))
                + nz(mapper.sumDays(u.getLoginId(), DutyKind.SICK.name(), year, "PROG"));
    }

    public List<DutyRequestVO> list(LoginUser u, Integer year, int limit) {
        return mapper.selectByUser(u.getLoginId(), year, limit);
    }

    /** 근무일(토요일·일요일·공휴일 제외) 수 */
    public int workingDays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (holidays.isWorkingDay(d)) {
                n++;
            }
        }
        return n;
    }

    @Transactional
    public Result apply(DutyKind kind, LocalDate start, LocalDate end, String halfDay, String startTime, String endTime,
                        String destination, String reason, String approverId, LoginUser me) throws IOException {
        if (kind == null) {
            return fail("신청 종류를 고르세요.");
        }
        if (start == null) {
            return fail("날짜를 입력하세요.");
        }
        if (end == null || kind == DutyKind.OVERTIME) {
            end = start;
        }
        if (end.isBefore(start)) {
            return fail("끝나는 날이 시작하는 날보다 앞설 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            return fail("기간이 너무 깁니다.");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty() || r.length() > 1000) {
            return fail("사유를 1,000자 이내로 적으세요.");
        }
        DutyRequestVO v = new DutyRequestVO();
        v.setKind(kind);
        v.setStartDate(start);
        v.setEndDate(end);
        v.setReason(r);

        if (kind.isLeave()) {
            String half = "AM".equals(halfDay) || "PM".equals(halfDay) ? halfDay : null;
            if (half != null && !start.equals(end)) {
                return fail("반일 휴가는 하루만 신청할 수 있습니다.");
            }
            int wd = workingDays(start, end);
            if (wd == 0) {
                return fail("고른 기간에 근무일이 없습니다 (토요일·일요일·공휴일 제외).");
            }
            double days = half != null ? 0.5 : wd;
            v.setHalfDay(half);
            v.setDays(days);
            if (mapper.countOverlap(me.getLoginId(), start, end, false) > 0) {
                return fail("이미 신청한 휴가·출장 기간과 겹칩니다.");
            }
            int year = start.getYear();
            if (end.getYear() != year) {
                return fail("해를 넘기는 휴가는 해마다 나누어 신청하세요.");
            }
            if (kind == DutyKind.ANNUAL) {
                LeaveSummary s = leave(me, year);
                if (s.granted() == null) {
                    return fail("임용일이 등록되지 않아 연가 일수를 계산할 수 없습니다. 관리자에게 인사정보 등록을 요청하세요.");
                }
                if (days > s.remaining()) {
                    return fail(String.format("남은 연가(%s일, 결재 중 포함)를 넘습니다.", fmt(s.remaining())));
                }
            }
            if (kind == DutyKind.SICK && sickUsed(me, year) + days > LeavePolicy.SICK_LIMIT_DAYS) {
                return fail("병가는 연 60일 범위에서 허가할 수 있습니다 (지방공무원 복무규정 제7조의5).");
            }
        } else if (kind == DutyKind.TRIP) {
            String dest = destination == null ? "" : destination.trim();
            if (dest.isEmpty() || dest.length() > 200) {
                return fail("출장지를 입력하세요.");
            }
            if (mapper.countOverlap(me.getLoginId(), start, end, false) > 0) {
                return fail("이미 신청한 휴가·출장 기간과 겹칩니다.");
            }
            v.setDestination(dest);
            v.setDays((double) (ChronoUnit.DAYS.between(start, end) + 1));
        } else {
            LocalTime from;
            LocalTime to;
            try {
                from = LocalTime.parse(startTime);
                to = LocalTime.parse(endTime);
            } catch (DateTimeParseException | NullPointerException e) {
                return fail("시작·끝 시각을 입력하세요.");
            }
            if (!to.isAfter(from)) {
                return fail("끝 시각이 시작 시각보다 늦어야 합니다.");
            }
            double hours = Math.round(ChronoUnit.MINUTES.between(from, to) / 6.0) / 10.0;
            if (hours > 12) {
                return fail("시간외근무는 하루 12시간을 넘겨 신청할 수 없습니다.");
            }
            if (mapper.countOverlap(me.getLoginId(), start, end, true) > 0) {
                return fail("그날 시간외근무를 이미 신청했습니다.");
            }
            v.setStartTime(from.toString());
            v.setEndTime(to.toString());
            v.setHours(hours);
        }

        // 전자결재 문서로 올린다
        ApprovalForm f = new ApprovalForm();
        f.setKind(ApprovalForm.DUTY);
        f.setRetention("1년");
        f.setDisclosure("공개");
        f.setReceiverType("internal");
        f.setTitle("[" + kind.getLabel() + "] " + me.getUserName() + " " + v.getPeriodText());
        f.setBody("1. 신청자: " + me.getUserName() + " " + me.position() + " (" + me.dept() + ")\n"
                + "2. 종류: " + kind.getLabel() + "\n"
                + "3. 기간: " + v.getPeriodText() + "\n"
                + (v.getDestination() != null ? "4. 출장지: " + v.getDestination() + "\n5. 사유: " : "4. 사유: ") + r);
        f.setFinalApprover(approverId);
        Map<String, String> errors = approvalService.validate(f, true, me, null);
        if (!errors.isEmpty()) {
            return fail(errors.containsKey("finalApprover") || errors.containsKey("reviewers")
                    ? "결재권자를 고르세요 (본인이 아닌 사용 중인 직원)." : errors.values().iterator().next());
        }
        long docId = approvalService.create(f, true, null, me);

        v.setReqId(mapper.nextId());
        v.setLoginId(me.getLoginId());
        v.setUserName(me.getUserName());
        v.setDeptName(me.getDeptName());
        v.setDocId(docId);
        v.setCreatedAt(LocalDateTime.now());
        mapper.insert(v);
        return new Result(true, kind.getLabel() + " 신청을 결재에 올렸습니다.", docId);
    }

    private static Result fail(String m) {
        return new Result(false, m, null);
    }

    private static double nz(Double d) {
        return d == null ? 0 : d;
    }

    public static String fmt(Double d) {
        if (d == null) {
            return "—";
        }
        return d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }
}
