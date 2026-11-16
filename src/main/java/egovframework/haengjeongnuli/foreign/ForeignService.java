package egovframework.haengjeongnuli.foreign;

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
 * 외국인주민 지원 대장. 수집 근거 법령을 확인하지 못해 본인 동의(개인정보 보호법 제15조)를 받은 경우에만 올린다.
 * 외국인등록번호는 받지 않는다(제24조). 열람은 담당 부서만, 지원이 끝난 뒤 보유기간이 지나면 파기(제21조).
 */
@Service
public class ForeignService {

    public record Result(boolean ok, String message, Long id) {}

    private final ForeignMapper mapper;
    private final Set<String> depts;
    private final int keepDays;

    public ForeignService(ForeignMapper mapper, @Value("${app.work.foreign-depts:가족정책과}") String depts,
                          @Value("${app.privacy.foreign-keep-days:365}") int keepDays) {
        this.mapper = mapper;
        this.depts = Arrays.stream(depts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.keepDays = keepDays;
    }

    public Set<String> getDepts() { return depts; }
    public int getKeepDays() { return keepDays; }

    public boolean canAccess(LoginUser u) {
        return u.isAdmin() || depts.contains(u.dept());
    }

    public List<ForeignResidentVO> list(boolean active, String nationality, String q) {
        String like = trim(q) == null ? null : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return mapper.selectList(active ? ForeignResidentVO.ACTIVE : ForeignResidentVO.ENDED, trim(nationality), like);
    }

    public ForeignResidentVO get(long id) { return mapper.selectById(id); }
    public List<ForeignSupportVO> supports(long id) { return mapper.selectSupports(id); }
    public List<String> nationalities() { return mapper.nationalities(); }

    /** 숫자만 있는 통계 (모든 직원이 볼 수 있다) */
    public List<Map<String, Object>> stats() {
        return mapper.stats(LocalDate.now().withDayOfYear(1));
    }

    @Transactional
    public Result register(String name, String nationality, LocalDate birth, String address, String phone, String language,
                           String stayStatus, LocalDate consentOn, boolean consent, LoginUser me) {
        if (!canAccess(me)) {
            return new Result(false, "외국인주민 대장은 " + String.join("·", depts) + " 직원과 관리자만 다룹니다.", null);
        }
        if (!consent || consentOn == null || consentOn.isAfter(LocalDate.now())) {
            return new Result(false, "개인정보 수집·이용 동의를 받은 날을 입력하고 동의를 확인하세요.", null);
        }
        String n = trim(name), nat = trim(nationality), a = trim(address), p = trim(phone), l = trim(language), st = trim(stayStatus);
        if (n == null || n.length() > 100 || nat == null || nat.length() > 50 || birth == null || birth.isAfter(LocalDate.now())) {
            return new Result(false, "이름, 국적, 생년월일을 입력하세요.", null);
        }
        if ((a != null && a.length() > 300) || (l != null && l.length() > 50) || (st != null && st.length() > 20)
                || (p != null && !p.matches("[0-9+ -]{8,20}"))) {
            return new Result(false, "주소·연락처·사용 언어·체류자격을 바르게 입력하세요.", null);
        }
        if (st != null && st.matches("\\d{6}-?\\d{7}")) {
            return new Result(false, "외국인등록번호는 받지 않습니다. 체류자격(예: E-9, F-6)만 적으세요.", null);
        }
        ForeignResidentVO r = new ForeignResidentVO();
        r.setResId(mapper.nextId());
        r.setName(n);
        r.setNationality(nat);
        r.setBirthDate(birth);
        r.setAddress(a);
        r.setPhone(p);
        r.setLanguage(l);
        r.setStayStatus(st);
        r.setConsentOn(consentOn);
        r.setCreatedBy(me.getLoginId());
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        return new Result(true, "외국인주민 지원 대장에 올렸습니다.", r.getResId());
    }

    @Transactional
    public Result addSupport(long id, LocalDate on, String kind, String content, LoginUser me) {
        ForeignResidentVO r = get(id);
        String c = trim(content);
        if (!canAccess(me) || r == null || !r.isActive()) {
            return new Result(false, "지원 중인 주민에게만 기록합니다.", null);
        }
        if (on == null || on.isAfter(LocalDate.now()) || kind == null || !ForeignSupportVO.KINDS.contains(kind) || c == null || c.length() > 1000) {
            return new Result(false, "날짜, 지원 종류, 내용을 입력하세요.", null);
        }
        ForeignSupportVO s = new ForeignSupportVO();
        s.setSupportId(mapper.nextSupportId());
        s.setResId(id);
        s.setSupportOn(on);
        s.setKind(kind);
        s.setContent(c);
        s.setStaffName(me.getUserName());
        s.setCreatedAt(LocalDateTime.now());
        mapper.insertSupport(s);
        return new Result(true, "지원 내용을 기록했습니다.", id);
    }

    @Transactional
    public Result end(long id, LocalDate on, String reason, LoginUser me) {
        String r = trim(reason);
        if (!canAccess(me) || on == null || on.isAfter(LocalDate.now()) || r == null || r.length() > 200) {
            return new Result(false, "종료일과 사유(전출·지원 완료·동의 철회 등)를 입력하세요.", null);
        }
        // 동의 철회는 바로 파기 대상
        LocalDate purge = r.contains("동의 철회") ? on.minusDays(1) : on.plusDays(keepDays);
        return mapper.end(id, on, r, purge) == 1 ? new Result(true, "지원을 종료했습니다. " + purge.plusDays(1) + " 이후 대장에서 파기됩니다.", id)
                : new Result(false, "지원 중인 주민이 아닙니다.", null);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
