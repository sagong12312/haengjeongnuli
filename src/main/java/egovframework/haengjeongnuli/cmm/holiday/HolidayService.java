package egovframework.haengjeongnuli.cmm.holiday;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * 관공서 공휴일과 근무일.
 * 법정 공휴일·대체공휴일은 {@link KoreanHolidays}로 계산하고,
 * 선거일·임시공휴일은 관리자가 등록한 날짜(HOLIDAY_EXTRA)를 더한다.
 */
@Service
public class HolidayService implements ApplicationRunner {

    private final HolidayMapper mapper;
    private final Map<Integer, Map<LocalDate, String>> cache = new ConcurrentHashMap<>();
    private volatile Map<LocalDate, String> extra;

    public HolidayService(HolidayMapper mapper) {
        this.mapper = mapper;
    }

    /** 처음 실행할 때 이미 정해진 선거일을 넣어 둔다 */
    @Override
    public void run(ApplicationArguments args) {
        LocalDate localElection = LocalDate.of(2026, 6, 3);
        if (mapper.countExtra(localElection) == 0) {
            mapper.insertExtra(localElection, "제9회 전국동시지방선거", "system");
            reload();
        }
    }

    /** 그 해 공휴일(일요일 제외)과 이름 */
    public Map<LocalDate, String> holidays(int year) {
        return cache.computeIfAbsent(year, y -> KoreanHolidays.of(y, extra()));
    }

    public boolean isHoliday(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays(d.getYear()).containsKey(d);
    }

    /** 토요일·일요일·공휴일이 아니면 근무일 */
    public boolean isWorkingDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && !isHoliday(d);
    }

    /** 관리자가 등록한 선거일·임시공휴일 */
    public Map<LocalDate, String> extra() {
        Map<LocalDate, String> e = extra;
        if (e == null) {
            e = new TreeMap<>();
            for (Map<String, Object> row : mapper.selectExtra()) {
                Object day = row.get("day");
                LocalDate d = day instanceof Timestamp ts ? ts.toLocalDateTime().toLocalDate()
                        : day instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) day;
                e.put(d, String.valueOf(row.get("name")));
            }
            extra = e;
        }
        return e;
    }

    public boolean addExtra(LocalDate day, String name, String by) {
        if (mapper.countExtra(day) > 0) {
            return false;
        }
        mapper.insertExtra(day, name, by);
        reload();
        return true;
    }

    public boolean removeExtra(LocalDate day) {
        boolean removed = mapper.deleteExtra(day) > 0;
        reload();
        return removed;
    }

    private void reload() {
        extra = null;
        cache.clear();
    }
}
