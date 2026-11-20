package egovframework.haengjeongnuli.reg;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 업무 계산 도구. 입력은 저장하지 않는다.
 * 개인정보 가리기·글자 수는 POST 로 받아 주소창·접속 기록에 내용이 남지 않게 한다.
 */
@Controller
public class ToolsController {

    private static final int MAX_TEXT = 20_000;

    private final ToolsService tools;

    public ToolsController(ToolsService tools) {
        this.tools = tools;
    }

    private static int clamp(Integer v, int min, int max, int def) {
        return v == null ? def : Math.max(min, Math.min(max, v));
    }

    @GetMapping("/tools")
    public String index(@RequestParam(required = false) String tool,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                        @RequestParam(required = false) Integer n, @RequestParam(required = false) String unit,
                        @RequestParam(defaultValue = "false") boolean firstDay,
                        @RequestParam(required = false) Long amount, @RequestParam(defaultValue = "total") String vatMode,
                        @RequestParam(required = false) Double area, @RequestParam(defaultValue = "pyeong") String areaFrom,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
                        Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("tool", tool == null ? "" : tool);
        LocalDate s = start == null || start.getYear() < 1900 || start.getYear() > 2200 ? today : start;
        model.addAttribute("start", s);
        model.addAttribute("presets", tools.presets(s));
        if (tool == null) {
            return "tools/index";
        }
        switch (tool) {
            case "term" -> {
                int days = clamp(n, 1, 3650, 10);
                String u = "M".equals(unit) || "Y".equals(unit) ? unit : "D";
                model.addAttribute("n", days);
                model.addAttribute("unit", u);
                model.addAttribute("firstDay", firstDay);
                model.addAttribute("term", tools.term(s, days, u, firstDay));
            }
            case "work" -> {
                int days = clamp(n, 1, 365, 7);
                model.addAttribute("n", days);
                model.addAttribute("workEnd", tools.addWorkingDays(s, days));
            }
            case "between" -> {
                LocalDate e = end == null || end.getYear() < 1900 || end.getYear() > 2200 ? today : end;
                model.addAttribute("end", e);
                if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(s, e)) <= 3650) {
                    model.addAttribute("between", tools.between(s, e));
                } else {
                    model.addAttribute("toolError", "두 날짜는 10년 안이어야 합니다.");
                }
            }
            case "age" -> {
                if (s.isAfter(today)) {
                    model.addAttribute("toolError", "생일이 오늘보다 뒤입니다.");
                } else {
                    LocalDate on = end == null ? today : end;
                    model.addAttribute("end", on);
                    model.addAttribute("age", tools.age(s, on.isBefore(s) ? s : on));
                }
            }
            case "won" -> {
                if (amount != null && Math.abs(amount) < 10_000_000_000_000_000L) {
                    model.addAttribute("amount", amount);
                    model.addAttribute("won", "일금 " + ToolsService.koreanAmount(amount) + "원정 (₩" + String.format("%,d", amount) + ")");
                }
            }
            case "vat" -> {
                if (amount != null && amount >= 0 && amount < 1_000_000_000_000_000L) {
                    model.addAttribute("amount", amount);
                    model.addAttribute("vatMode", vatMode);
                    model.addAttribute("vat", tools.vat(amount, "total".equals(vatMode)));
                }
            }
            case "official" -> {
                LocalTime t = time == null ? LocalTime.now() : time;
                model.addAttribute("time", t);
                model.addAttribute("officialDate", ToolsService.officialDateWithDay(s));
                model.addAttribute("officialTime", ToolsService.officialTime(t));
            }
            case "area" -> {
                if (area != null && area > 0 && area < 1e12) {
                    model.addAttribute("area", area);
                    model.addAttribute("areaFrom", areaFrom);
                    model.addAttribute("areaResult", tools.area(area, "pyeong".equals(areaFrom)));
                }
            }
            default -> { }
        }
        return "tools/index";
    }

    @PostMapping("/tools/text")
    public String text(@RequestParam(required = false) String text, @RequestParam(defaultValue = "count") String action, Model model) {
        String t = text == null ? "" : text;
        if (t.length() > MAX_TEXT) {
            model.addAttribute("toolError", "글이 너무 깁니다 (" + MAX_TEXT + "자까지).");
            t = t.substring(0, MAX_TEXT);
        }
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("start", LocalDate.now());
        model.addAttribute("presets", tools.presets(LocalDate.now()));
        model.addAttribute("tool", "mask".equals(action) ? "mask" : "count");
        model.addAttribute("text", t);
        if ("mask".equals(action)) {
            model.addAttribute("masked", tools.mask(t));
        } else {
            model.addAttribute("count", tools.textCount(t));
        }
        return "tools/index";
    }
}
