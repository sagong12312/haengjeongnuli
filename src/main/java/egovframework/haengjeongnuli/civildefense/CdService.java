package egovframework.haengjeongnuli.civildefense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 민방위 대원 명부와 교육 통지·참석.
 * 편성 대상: 20세가 되는 해 1월 1일부터 정한 나이가 되는 해 12월 31일까지 (민방위기본법 제17조, 기본 40세 — 현행 원문 확인 필요).
 * 개인정보는 최소한(이름·생년월일·주소·연락처)만, 주민등록번호는 받지 않는다. 열람은 담당 부서만, 편성 해제 뒤 보유기간이 지나면 파기.
 */
@Service
public class CdService {

    public record Result(boolean ok, String message, Long id) {}

    public static final int MIN_AGE = 20;

    private final CdMapper mapper;
    private final Set<String> depts;
    private final int maxAge;
    private final int keepDays;

    public CdService(CdMapper mapper, @Value("${app.work.civil-defense-depts:안전총괄과}") String depts,
                     @Value("${app.civil-defense.max-age:40}") int maxAge, @Value("${app.privacy.civil-defense-keep-days:365}") int keepDays) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.maxAge = maxAge;
        this.keepDays = keepDays;
    }

    public Set<String> getDepts() { return depts; }
    public int getMaxAge() { return maxAge; }
    public int getKeepDays() { return keepDays; }

    /** 명부는 담당 부서 직원과 관리자만 */
    public boolean canAccess(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<CdMemberVO> list(boolean active, String unit, String q) {
        String like = trim(q) == null ? null : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return mapper.selectList(active ? CdMemberVO.ACTIVE : CdMemberVO.RELEASED, trim(unit), like);
    }

    public CdMemberVO get(long id) { return mapper.selectById(id); }
    public List<String> units() { return mapper.units(); }
    public List<Map<String, Object>> history(long id) { return mapper.selectHistory(id); }

    /** 올해 편성 나이를 넘은 대원 (해제 대상) */
    public List<CdMemberVO> overAge() {
        return mapper.selectOverAge(LocalDate.of(LocalDate.now().getYear() - maxAge, 1, 1));
    }

    @Transactional
    public Result register(String name, LocalDate birth, String address, String phone, String unit, LocalDate joinedOn, LoginUser me) {
        if (!canAccess(me)) {
            return new Result(false, "민방위 명부는 " + String.join("·", depts) + " 직원과 관리자만 다룹니다.", null);
        }
        String n = trim(name), a = trim(address), p = trim(phone), u = trim(unit);
        if (n == null || n.length() > 100 || a == null || a.length() > 300 || u == null || u.length() > 100) {
            return new Result(false, "이름, 주소, 소속 민방위대를 입력하세요.", null);
        }
        if (p != null && !p.matches("[0-9-]{9,20}")) {
            return new Result(false, "연락처는 숫자와 '-'로 입력하세요.", null);
        }
        if (birth == null || joinedOn == null || joinedOn.isAfter(LocalDate.now())) {
            return new Result(false, "생년월일과 편성일을 입력하세요.", null);
        }
        int age = joinedOn.getYear() - birth.getYear();
        if (age < MIN_AGE || age > maxAge) {
            return new Result(false, "편성 대상 나이(" + MIN_AGE + "세가 되는 해부터 " + maxAge + "세가 되는 해까지)가 아닙니다.", null);
        }
        if (mapper.countSame(n, birth) > 0) {
            return new Result(false, "같은 이름·생년월일의 대원이 이미 편성되어 있습니다.", null);
        }
        CdMemberVO m = new CdMemberVO();
        m.setMemberId(mapper.nextId());
        m.setName(n);
        m.setBirthDate(birth);
        m.setAddress(a);
        m.setPhone(p);
        m.setUnitName(u);
        m.setJoinedOn(joinedOn);
        m.setCreatedBy(me.getLoginId());
        m.setCreatedAt(LocalDateTime.now());
        mapper.insert(m);
        return new Result(true, "민방위 대원으로 편성했습니다.", m.getMemberId());
    }

    @Transactional
    public Result update(long id, String address, String phone, String unit, LoginUser me) {
        String a = trim(address), p = trim(phone), u = trim(unit);
        if (!canAccess(me) || a == null || u == null || a.length() > 300 || u.length() > 100 || (p != null && !p.matches("[0-9-]{9,20}"))) {
            return new Result(false, "주소·연락처·소속을 바르게 입력하세요.", null);
        }
        return mapper.update(id, a, p, u) == 1 ? new Result(true, "고쳤습니다.", id) : new Result(false, "편성 중인 대원이 아닙니다.", null);
    }

    /** 편성 해제. 보유기간이 지나면 명부에서 지운다 */
    @Transactional
    public Result release(long id, LocalDate on, String reason, LoginUser me) {
        if (!canAccess(me)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (on == null || on.isAfter(LocalDate.now()) || reason == null || !CdMemberVO.RELEASE_REASONS.contains(reason)) {
            return new Result(false, "해제일과 사유를 고르세요.", null);
        }
        return mapper.release(id, on, reason, on.plusDays(keepDays)) == 1
                ? new Result(true, "편성을 해제했습니다. " + on.plusDays(keepDays) + " 이후 명부에서 파기됩니다.", id)
                : new Result(false, "편성 중인 대원이 아닙니다.", null);
    }

    // ===== 교육 =====

    public List<CdTrainingVO> trainings(int year) {
        return mapper.selectTrainings(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1));
    }

    public CdTrainingVO training(long id) { return mapper.selectTraining(id); }
    public List<CdAttendVO> attends(long id) { return mapper.selectAttends(id); }

    @Transactional
    public Result createTraining(String title, LocalDate heldOn, String place, Integer hours, LoginUser me) {
        String t = trim(title), p = trim(place);
        if (!canAccess(me)) {
            return new Result(false, "권한이 없습니다.", null);
        }
        if (t == null || t.length() > 200 || p == null || p.length() > 200 || heldOn == null || hours == null || hours < 1 || hours > 99) {
            return new Result(false, "교육명, 날짜, 장소, 시간을 입력하세요.", null);
        }
        CdTrainingVO v = new CdTrainingVO();
        v.setTrainingId(mapper.nextTrainingId());
        v.setTitle(t);
        v.setHeldOn(heldOn);
        v.setPlace(p);
        v.setHours(hours);
        v.setCreatedBy(me.getLoginId());
        v.setCreatedAt(LocalDateTime.now());
        mapper.insertTraining(v);
        return new Result(true, "교육을 만들었습니다. 대상자를 넣고 통지하세요.", v.getTrainingId());
    }

    /** 편성된 대원(부대 지정 시 그 부대)을 대상자로 넣고 통지일을 기록한다 */
    @Transactional
    public Result invite(long trainingId, String unit, LoginUser me) {
        if (!canAccess(me) || training(trainingId) == null) {
            return new Result(false, "권한이 없습니다.", null);
        }
        int n = mapper.invite(trainingId, trim(unit), LocalDate.now());
        return new Result(true, "대상자 " + n + "명에게 통지한 것으로 기록했습니다. (문자·우편 발송은 기관 통지 방식으로)", trainingId);
    }

    @Transactional
    public Result setResult(long trainingId, long memberId, String result, String note, LoginUser me) {
        String n = trim(note);
        if (!canAccess(me) || result == null || !CdAttendVO.RESULTS.contains(result) || (n != null && n.length() > 200)) {
            return new Result(false, "결과를 고르세요.", null);
        }
        if ("면제".equals(result) && n == null) {
            return new Result(false, "면제 사유를 적으세요.", null);
        }
        return mapper.setResult(trainingId, memberId, result, n) == 1 ? new Result(true, "기록했습니다.", trainingId)
                : new Result(false, "대상자가 아닙니다.", null);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
