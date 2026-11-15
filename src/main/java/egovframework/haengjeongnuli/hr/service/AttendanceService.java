package egovframework.haengjeongnuli.hr.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.hr.service.impl.AttendanceMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출퇴근 기록.
 * 하루에 한 줄을 두고, 처음 출근 시각과 마지막 퇴근 시각을 남긴다.
 * 자정을 넘겨 일하는 경우를 위해 어제 출근하고 아직 퇴근하지 않았으면 '업무 중'으로 본다.
 */
@Service
public class AttendanceService {

    private final AttendanceMapper mapper;

    public AttendanceService(AttendanceMapper mapper) {
        this.mapper = mapper;
    }

    /** 지금 업무 중인 기록 (없으면 null) */
    public AttendanceVO current(String loginId) {
        AttendanceVO last = mapper.selectLatest(loginId);
        if (last != null && last.getOutAt() == null
                && !last.getWorkDate().isBefore(LocalDate.now().minusDays(1))) {
            return last;
        }
        return null;
    }

    /** 오늘 기록 (없으면 null) */
    public AttendanceVO today(String loginId) {
        return mapper.selectDay(loginId, LocalDate.now());
    }

    public boolean isWorking(String loginId) {
        return current(loginId) != null;
    }

    /** 출근. 이미 업무 중이면 false. 오늘 퇴근했다가 다시 출근하면 퇴근 시각을 지운다. */
    @Transactional
    public boolean checkIn(LoginUser u, String ip) {
        if (isWorking(u.getLoginId())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        AttendanceVO today = mapper.selectDay(u.getLoginId(), now.toLocalDate());
        if (today != null) {
            return mapper.reopen(today.getAttId()) == 1;
        }
        AttendanceVO a = new AttendanceVO();
        a.setAttId(mapper.nextId());
        a.setLoginId(u.getLoginId());
        a.setUserName(u.getUserName());
        a.setDeptName(u.getDeptName());
        a.setWorkDate(now.toLocalDate());
        a.setInAt(now);
        a.setInIp(ip);
        try {
            mapper.insert(a);
        } catch (DuplicateKeyException e) {
            return false; // 같은 순간 두 번 누른 경우
        }
        return true;
    }

    /** 퇴근. 업무 중이 아니면 false */
    @Transactional
    public boolean checkOut(LoginUser u, String ip) {
        AttendanceVO cur = current(u.getLoginId());
        return cur != null && mapper.close(cur.getAttId(), LocalDateTime.now(), ip) == 1;
    }

    /** 한 달 기록 (날짜 순) */
    public List<AttendanceVO> month(String loginId, YearMonth ym) {
        return mapper.selectRange(loginId, ym.atDay(1), ym.plusMonths(1).atDay(1));
    }
}
