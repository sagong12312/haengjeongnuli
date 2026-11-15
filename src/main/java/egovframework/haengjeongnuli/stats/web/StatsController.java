package egovframework.haengjeongnuli.stats.web;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import egovframework.haengjeongnuli.stats.service.StatsService;

/** 통계·보고 (합계만 보여 주므로 모든 직원이 본다) */
@Controller
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    private static int year(Integer y) {
        int now = Year.now().getValue();
        return y == null || y < 2000 || y > now ? now : y;
    }

    @GetMapping("/stats")
    public String index(@RequestParam(required = false) Integer year, Model model) {
        int y = year(year);
        int now = Year.now().getValue();
        model.addAttribute("year", y);
        model.addAttribute("years", List.of(now - 2, now - 1, now));
        model.addAttribute("civilMonthly", stats.civilMonthly(y));
        model.addAttribute("civilByKind", stats.civilByKind(y));
        model.addAttribute("approval", stats.approval(y));
        model.addAttribute("finance", stats.finance(y));
        model.addAttribute("welfare", stats.welfare(y));
        model.addAttribute("hygiene", stats.hygiene(y));
        model.addAttribute("building", stats.building());
        model.addAttribute("duty", stats.duty(y));
        return "stats/index";
    }

    @GetMapping("/stats/civil.csv")
    public ResponseEntity<byte[]> civilCsv(@RequestParam(required = false) Integer year) {
        int y = year(year);
        ContentDisposition cd = ContentDisposition.attachment().filename(y + "년_민원통계.csv", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .body(stats.civilCsv(y).getBytes(StandardCharsets.UTF_8));
    }
}
